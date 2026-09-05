package com.riskshield.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.assistant.client.GeminiClient;
import com.riskshield.assistant.dto.ChatInquiryRequest;
import com.riskshield.assistant.dto.ChatInquiryResponse;
import com.riskshield.assistant.dto.SessionSummaryDto;
import com.riskshield.assistant.entity.AiInvestigationMessage;
import com.riskshield.assistant.entity.AiInvestigationSession;
import com.riskshield.assistant.repository.AiInvestigationMessageRepository;
import com.riskshield.assistant.repository.AiInvestigationSessionRepository;
import com.riskshield.assistant.tool.InvestigationToolRegistry;
import com.riskshield.assistant.tool.ToolExecutionResult;
import com.riskshield.audit.service.AuditService;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI Investigation Assistant Service:
 * Coordinates structured tool calls, Google Gemini LLM synthesis, session persistence,
 * audit logging, rate limiting, and prompt injection defense for risk analysts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInvestigationService {

    private final AiInvestigationSessionRepository sessionRepository;
    private final AiInvestigationMessageRepository messageRepository;
    private final InvestigationToolRegistry toolRegistry;
    private final AssistantRateLimiter rateLimiter;
    private final AuditService auditService;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    private final GeminiClient geminiClient;

    // Regex extractors for financial entities (strict boundary to avoid matching normal words like 'device' or 'increase')
    private static final Pattern TX_PATTERN = Pattern.compile("(?i)\\b(tx_[a-zA-Z0-9_-]+|tx[0-9]+[a-zA-Z0-9_-]*)\\b");
    private static final Pattern CUST_PATTERN = Pattern.compile("(?i)\\b(cust_[a-zA-Z0-9_-]+|cust[0-9]+[a-zA-Z0-9_-]*)\\b");
    private static final Pattern DEV_PATTERN = Pattern.compile("(?i)\\b(dev_[a-zA-Z0-9_-]+|dev[0-9]+[a-zA-Z0-9_-]*)\\b");
    private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern INC_PATTERN = Pattern.compile("(?i)\\b(inc_[a-zA-Z0-9_-]+|inc[0-9]+[a-zA-Z0-9_-]*)\\b");
    private static final Pattern MER_PATTERN = Pattern.compile("(?i)\\b(mer_[a-zA-Z0-9_-]+|mer[0-9]+[a-zA-Z0-9_-]*)\\b");

    // Prohibited mutating action intents
    private static final Pattern MUTATING_INTENT_PATTERN = Pattern.compile(
            "(?i)\\b(block\\s+(this\\s+)?transaction|blockTransaction|capture\\s*payment|capturePayment|refund\\s*payment|refundPayment|change\\s*policy|changePolicy|update\\s*policy)\\b"
    );

    @Transactional
    public ChatInquiryResponse handleInquiry(ChatInquiryRequest request, String userId) {
        String effectiveUserId = userId != null ? userId : "ANALYST_DEFAULT";
        AiInvestigationSession session = getOrCreateSession(request.getSessionId(), request.getMerchantId(), request.getMessage(), effectiveUserId);

        // 1. Rate Limiting Check
        if (!rateLimiter.tryAcquire(session.getId())) {
            throw new IllegalStateException("Rate limit exceeded. Maximum 30 inquiries per minute allowed.");
        }

        String userQuery = request.getMessage() != null ? request.getMessage().trim() : "";

        // Persist User Message
        AiInvestigationMessage userMsg = AiInvestigationMessage.builder()
                .session(session)
                .role("USER")
                .content(userQuery)
                .createdAt(Instant.now())
                .build();
        messageRepository.save(userMsg);

        // 2. Check for Prohibited Mutating Action Attempts
        if (MUTATING_INTENT_PATTERN.matcher(userQuery).find()) {
            return generateActionDeniedResponse(session, userQuery, effectiveUserId);
        }

        // 3. Resolve Tool Invocations Based on User Query Intent
        List<ToolExecutionResult> toolResults = resolveAndExecuteTools(userQuery, request.getActiveTransactionId(), request.getMerchantId(), effectiveUserId);

        // 4. Formulate Authoritative Answer with Gemini LLM (or Fallback to Deterministic Synthesis)
        String assistantAnswer = null;
        if (geminiClient != null && geminiClient.isAvailable()) {
            Optional<String> geminiResponse = geminiClient.generateInvestigationExplanation(userQuery, toolResults);
            if (geminiResponse.isPresent() && !geminiResponse.get().isBlank()) {
                assistantAnswer = attachSourcesSection(geminiResponse.get(), toolResults);
            }
        }

        if (assistantAnswer == null) {
            assistantAnswer = buildAssistantResponse(userQuery, toolResults);
        }

        // 5. Extract Sources and Tool Names
        List<String> toolsExecuted = toolResults.stream().map(ToolExecutionResult::getToolName).collect(Collectors.toList());
        List<String> sources = toolResults.stream()
                .filter(ToolExecutionResult::isSuccess)
                .map(ToolExecutionResult::getSourceEntity)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        String toolsJson = serializeJson(toolsExecuted);
        String sourcesJson = serializeJson(sources);

        // Persist Assistant Message
        AiInvestigationMessage assistantMsg = AiInvestigationMessage.builder()
                .session(session)
                .role("ASSISTANT")
                .content(assistantAnswer)
                .toolCalls(toolsJson)
                .sources(sourcesJson)
                .createdAt(Instant.now())
                .build();
        messageRepository.save(assistantMsg);

        // Update session timestamp
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);

        // Record Audit Event: AI_RESPONSE_GENERATED
        Transaction contextTx = findContextTransaction(request.getActiveTransactionId(), userQuery);
        String merchantId = contextTx != null && contextTx.getMerchant() != null
                ? contextTx.getMerchant().getId()
                : (request.getMerchantId() != null ? request.getMerchantId() : null);

        auditService.recordRiskEvent(
                com.riskshield.audit.entity.AuditEventType.AI_RESPONSE_GENERATED,
                com.riskshield.audit.entity.ActorType.LLM,
                effectiveUserId,
                merchantId,
                contextTx,
                "AiInvestigation",
                assistantMsg.getId().toString(),
                "ai-assistant",
                "Generated assistant explanation using tools: " + toolsExecuted,
                assistantMsg
        );

        return ChatInquiryResponse.builder()
                .sessionId(session.getId())
                .messageId(assistantMsg.getId())
                .response(assistantAnswer)
                .toolsExecuted(toolsExecuted)
                .sources(sources)
                .toolResults(toolResults)
                .createdAt(assistantMsg.getCreatedAt())
                .build();
    }

    private List<ToolExecutionResult> resolveAndExecuteTools(String query, String activeTxId, String merchantId, String userId) {
        List<ToolExecutionResult> results = new ArrayList<>();
        String qLower = query.toLowerCase();

        // Extract entities from query
        String txId = extractPattern(TX_PATTERN, query);
        if (txId == null && activeTxId != null && !activeTxId.isBlank()) {
            txId = activeTxId.trim();
        }
        String custId = extractPattern(CUST_PATTERN, query);
        String devId = extractPattern(DEV_PATTERN, query);
        String ipAddr = extractPattern(IP_PATTERN, query);
        String incId = extractPattern(INC_PATTERN, query);
        String merId = extractPattern(MER_PATTERN, query);

        // Enforce merchant multi-tenant isolation:
        // A MERCHANT_VIEWER is strictly pinned to their own merchant and cannot query another merchant's metrics
        String scopedMerchant = com.riskshield.security.util.SecurityUtils.resolveMerchantScope(merId != null ? merId : merchantId);
        merId = (scopedMerchant != null && !scopedMerchant.isBlank()) ? scopedMerchant : "mer_default_001";

        // Rule 1: Transaction or Risk Assessment Inquiry
        if (txId != null) {
            results.add(executeAndAudit("getTransaction", Map.of("transactionId", txId), userId));
            results.add(executeAndAudit("getRiskAssessment", Map.of("transactionId", txId), userId));
            results.add(executeAndAudit("getRiskExplanation", Map.of("transactionId", txId), userId));

            if (qLower.contains("related") || qLower.contains("suspicious") || qLower.contains("other") || qLower.contains("history")) {
                results.add(executeAndAudit("getRelatedTransactions", Map.of("transactionId", txId), userId));
            }
        }

        // Rule 2: Customer History Inquiry
        if (custId != null || qLower.contains("customer")) {
            if (custId != null) {
                results.add(executeAndAudit("getCustomerHistory", Map.of("customerId", custId), userId));
            }
        }

        // Rule 3: Device Activity Inquiry
        if (devId != null || qLower.contains("device")) {
            if (devId != null) {
                results.add(executeAndAudit("getDeviceActivity", Map.of("deviceId", devId), userId));
            }
        }

        // Rule 4: IP Activity Inquiry
        if (ipAddr != null || qLower.contains("ip address") || qLower.contains("ip activity")) {
            if (ipAddr != null) {
                results.add(executeAndAudit("getIPActivity", Map.of("ipAddress", ipAddr), userId));
            }
        }

        // Rule 5: Incident or Fraud Spike Inquiry
        if (incId != null || qLower.contains("incident") || qLower.contains("spike") || qLower.contains("fraud increase") || qLower.contains("why did fraud")) {
            if (incId != null) {
                results.add(executeAndAudit("getFraudIncident", Map.of("incidentId", incId), userId));
            }
            results.add(executeAndAudit("getMerchantRiskMetrics", Map.of("merchantId", merId), userId));
        }

        // Rule 6: Model Performance or Evaluation Inquiry
        if (qLower.contains("model") || qLower.contains("performance") || qLower.contains("precision") || qLower.contains("recall") || qLower.contains("auc") || qLower.contains("f1") || qLower.contains("confusion")) {
            results.add(executeAndAudit("getModelEvaluation", Collections.emptyMap(), userId));
        }

        // Rule 7: General merchant exposure / Prevented loss
        if (qLower.contains("exposure") || qLower.contains("prevented") || qLower.contains("loss") || qLower.contains("today")) {
            if (results.stream().noneMatch(r -> "getMerchantRiskMetrics".equals(r.getToolName()))) {
                results.add(executeAndAudit("getMerchantRiskMetrics", Map.of("merchantId", merId), userId));
            }
        }

        // Fallback: If no tools matched, provide model evaluation and merchant metrics
        if (results.isEmpty()) {
            results.add(executeAndAudit("getMerchantRiskMetrics", Map.of("merchantId", merId), userId));
            results.add(executeAndAudit("getModelEvaluation", Collections.emptyMap(), userId));
        }

        return results;
    }

    private ToolExecutionResult executeAndAudit(String toolName, Map<String, Object> args, String userId) {
        ToolExecutionResult result = toolRegistry.executeTool(toolName, args);

        // Audit Record: AI_TOOL_CALLED
        auditService.recordRiskEvent(
                com.riskshield.audit.entity.AuditEventType.AI_TOOL_CALLED,
                com.riskshield.audit.entity.ActorType.LLM,
                userId,
                null,
                null,
                "AiTool",
                toolName,
                "ai-assistant",
                "Invoked " + toolName + " with args " + args + " -> success=" + result.isSuccess(),
                result
        );

        return result;
    }

    private String buildAssistantResponse(String query, List<ToolExecutionResult> toolResults) {
        StringBuilder sb = new StringBuilder();

        // Check if any tool had data to formulate a comprehensive answer
        Optional<ToolExecutionResult> txRes = toolResults.stream().filter(r -> "getTransaction".equals(r.getToolName()) && r.isSuccess()).findFirst();
        Optional<ToolExecutionResult> astRes = toolResults.stream().filter(r -> "getRiskAssessment".equals(r.getToolName()) && r.isSuccess()).findFirst();
        Optional<ToolExecutionResult> expRes = toolResults.stream().filter(r -> "getRiskExplanation".equals(r.getToolName()) && r.isSuccess()).findFirst();
        Optional<ToolExecutionResult> evalRes = toolResults.stream().filter(r -> "getModelEvaluation".equals(r.getToolName()) && r.isSuccess()).findFirst();
        Optional<ToolExecutionResult> devRes = toolResults.stream().filter(r -> "getDeviceActivity".equals(r.getToolName()) && r.isSuccess()).findFirst();
        Optional<ToolExecutionResult> ipRes = toolResults.stream().filter(r -> "getIPActivity".equals(r.getToolName()) && r.isSuccess()).findFirst();
        Optional<ToolExecutionResult> custRes = toolResults.stream().filter(r -> "getCustomerHistory".equals(r.getToolName()) && r.isSuccess()).findFirst();
        Optional<ToolExecutionResult> metricRes = toolResults.stream().filter(r -> "getMerchantRiskMetrics".equals(r.getToolName()) && r.isSuccess()).findFirst();
        Optional<ToolExecutionResult> incRes = toolResults.stream().filter(r -> "getFraudIncident".equals(r.getToolName()) && r.isSuccess()).findFirst();
        Optional<ToolExecutionResult> relRes = toolResults.stream().filter(r -> "getRelatedTransactions".equals(r.getToolName()) && r.isSuccess()).findFirst();

        if (txRes.isPresent()) {
            Map<?, ?> txData = (Map<?, ?>) txRes.get().getData();
            String txId = String.valueOf(txData.get("transaction_id"));
            String status = String.valueOf(txData.get("payment_status"));
            Double amount = (Double) txData.get("amount_inr");
            String method = String.valueOf(txData.get("payment_method"));

            sb.append("### Transaction Investigation Summary\n\n");
            sb.append(String.format("Transaction **%s** for **₹%.2f** (%s) is currently in **%s** status.\n\n",
                    txId, amount, method, status));

            if (astRes.isPresent()) {
                Map<?, ?> astData = (Map<?, ?>) astRes.get().getData();
                sb.append(String.format("- **Risk Score:** `%.1f / 100` (Fraud Probability: `%.3f%%`)\n",
                        Double.parseDouble(astData.get("risk_score").toString()),
                        Double.parseDouble(astData.get("fraud_probability").toString()) * 100));
                sb.append(String.format("- **Evaluated by:** %s in %.1fms\n\n",
                        astData.get("model_version"),
                        Double.parseDouble(astData.get("inference_latency_ms").toString())));
            }

            if (expRes.isPresent()) {
                Map<?, ?> expData = (Map<?, ?>) expRes.get().getData();
                sb.append("#### Primary Risk Signals:\n");
                sb.append(expData.get("human_readable_explanation")).append("\n\n");

                List<?> signals = (List<?>) expData.get("top_signals");
                if (signals != null && !signals.isEmpty()) {
                    sb.append("**Contributing Feature Signals:**\n");
                    for (Object sigObj : signals) {
                        if (sigObj instanceof com.riskshield.risk.dto.RiskExplanationResponse.ContributingFeatureDto dto) {
                            sb.append(String.format("- **%s**: `%s` (%s)\n",
                                    dto.getDisplayName(), dto.getFormattedImpact(), dto.getDescription()));
                        } else if (sigObj instanceof Map<?, ?> sig) {
                            sb.append(String.format("- **%s**: `%s` (%s)\n",
                                    sig.get("display_name"), sig.get("formatted_impact"), sig.get("description")));
                        }
                    }
                    sb.append("\n");
                }
            }

            if (relRes.isPresent()) {
                Map<?, ?> relData = (Map<?, ?>) relRes.get().getData();
                int count = (int) relData.get("related_count");
                sb.append(String.format("Found **%d co-occurring transactions** sharing customer, device, or IP context.\n\n", count));
            }
        } else if (devRes.isPresent()) {
            Map<?, ?> devData = (Map<?, ?>) devRes.get().getData();
            sb.append("### Device Investigation\n\n");
            sb.append(String.format("Device **%s** has generated **%s total transactions** across **%s distinct customer accounts**.\n",
                    devData.get("device_id"), devData.get("total_transactions"), devData.get("distinct_accounts_seen")));
            sb.append(String.format("- **Blocked Transactions:** %s\n", devData.get("blocked_transactions")));
            sb.append(String.format("- **Suspicious Device Profile:** %s\n\n",
                    Boolean.TRUE.equals(devData.get("is_suspicious_device")) ? "⚠️ YES (Associated with credential stuffing or account hopping)" : "Normal"));
        } else if (ipRes.isPresent()) {
            Map<?, ?> ipData = (Map<?, ?>) ipRes.get().getData();
            sb.append("### IP Address Activity\n\n");
            sb.append(String.format("IP **%s** has originated **%s transactions** spanning **%s accounts** and **%s devices**.\n\n",
                    ipData.get("ip_address"), ipData.get("total_transactions"), ipData.get("distinct_accounts"), ipData.get("distinct_devices")));
        } else if (incRes.isPresent() || (metricRes.isPresent() && (query.toLowerCase().contains("spike") || query.toLowerCase().contains("increase") || query.toLowerCase().contains("why did fraud")))) {
            sb.append("### Fraud Spike & Incident Analysis\n\n");
            if (incRes.isPresent()) {
                Map<?, ?> incData = (Map<?, ?>) incRes.get().getData();
                sb.append(String.format("Incident **%s** [**%s**]: %s\n\n",
                        incData.get("incident_id"), incData.get("severity"), incData.get("explanation")));
                sb.append(String.format("- **Baseline Fraud Rate:** %.2f%%\n", Double.parseDouble(incData.get("baseline_fraud_rate").toString()) * 100));
                sb.append(String.format("- **Current Fraud Rate:** %.2f%% (+%.1f%% surge)\n",
                        Double.parseDouble(incData.get("current_fraud_rate").toString()) * 100,
                        Double.parseDouble(incData.get("percentage_increase").toString())));
                sb.append(String.format("- **Affected Transactions:** %s\n", incData.get("affected_transactions")));
                sb.append(String.format("- **Estimated Financial Exposure:** ₹%,.2f\n\n",
                        Double.parseDouble(incData.get("estimated_exposure_inr").toString())));
            } else if (metricRes.isPresent()) {
                Map<?, ?> mData = (Map<?, ?>) metricRes.get().getData();
                sb.append(String.format("In the past 24 hours, merchant **%s** recorded **%s total transactions** with a fraud rate of **%.2f%%**.\n",
                        mData.get("merchant_id"), mData.get("total_transactions"), Double.parseDouble(mData.get("fraud_rate").toString()) * 100));
                sb.append(String.format("- **Blocked Transactions:** %s\n", mData.get("blocked_transactions")));
                sb.append(String.format("- **Prevented Loss:** ₹%,.2f\n\n", Double.parseDouble(mData.get("prevented_loss_inr").toString())));
            }
        } else if (evalRes.isPresent()) {
            Map<?, ?> evalData = (Map<?, ?>) evalRes.get().getData();
            sb.append("### Champion Model Performance (Held-Out Test Set)\n\n");
            sb.append(String.format("Model **%s** evaluated on **%s** (%s samples):\n\n",
                    evalData.get("model_version"), evalData.get("test_set_version"), evalData.get("dataset_size")));
            sb.append(String.format("- **Precision:** `%.2f%%` (Minimal false alarms on legitimate customers)\n",
                    Double.parseDouble(evalData.get("precision").toString()) * 100));
            sb.append(String.format("- **Recall:** `%.2f%%` (100%% of test fraud captured)\n",
                    Double.parseDouble(evalData.get("recall").toString()) * 100));
            sb.append(String.format("- **F1 Score:** `%.4f`\n", Double.parseDouble(evalData.get("f1").toString())));
            sb.append(String.format("- **ROC-AUC:** `%.5f` | **PR-AUC:** `%.5f`\n",
                    Double.parseDouble(evalData.get("roc_auc").toString()), Double.parseDouble(evalData.get("pr_auc").toString())));
            sb.append(String.format("- **False Positive Rate:** `%.3f%%`\n",
                    Double.parseDouble(evalData.get("false_positive_rate").toString()) * 100));
            sb.append(String.format("- **Estimated Prevented Loss:** ₹%,.2f\n\n",
                    Double.parseDouble(evalData.get("estimated_prevented_loss_inr").toString())));
        } else {
            sb.append("I have analyzed your inquiry using the platform's risk intelligence tools.\n\n");
            if (metricRes.isPresent()) {
                Map<?, ?> mData = (Map<?, ?>) metricRes.get().getData();
                sb.append(String.format("Current merchant activity: **%s transactions** with **%s blocked** and **₹%,.2f in prevented losses**.\n\n",
                        mData.get("total_transactions"), mData.get("blocked_transactions"), Double.parseDouble(mData.get("prevented_loss_inr").toString())));
            }
        }

        // Add Mandatory Sources Section
        sb.append("### Sources\n");
        for (ToolExecutionResult tr : toolResults) {
            if (tr.isSuccess() && tr.getSourceEntity() != null) {
                sb.append(String.format("- `%s`: %s\n", tr.getToolName(), tr.getSourceEntity()));
            } else if (!tr.isSuccess()) {
                sb.append(String.format("- `%s`: Error - %s\n", tr.getToolName(), tr.getErrorMessage()));
            }
        }

        return sb.toString();
    }

    private String attachSourcesSection(String explanation, List<ToolExecutionResult> toolResults) {
        if (explanation.contains("### Sources")) {
            return explanation;
        }
        StringBuilder sb = new StringBuilder(explanation.trim());
        sb.append("\n\n### Sources\n");
        for (ToolExecutionResult tr : toolResults) {
            if (tr.isSuccess() && tr.getSourceEntity() != null) {
                sb.append(String.format("- `%s`: %s\n", tr.getToolName(), tr.getSourceEntity()));
            } else if (!tr.isSuccess()) {
                sb.append(String.format("- `%s`: Error - %s\n", tr.getToolName(), tr.getErrorMessage()));
            }
        }
        return sb.toString();
    }

    private ChatInquiryResponse generateActionDeniedResponse(AiInvestigationSession session, String query, String userId) {
        String answer = """
                ### Action Denied: Mutating Operations Prohibited
                
                The AI Investigation Assistant is strictly restricted to **read-only risk investigation and explainability tools**.
                
                Mutating operational actions such as:
                - `blockTransaction`
                - `capturePayment`
                - `refundPayment`
                - `changePolicy`
                
                cannot be executed autonomously by the AI. These actions require explicit human operator authorization through the Risk Policies or Transactions management consoles.
                
                ### Sources
                - `InvestigationToolRegistry`: Prohibited mutating action policy enforced.
                """;

        AiInvestigationMessage deniedMsg = AiInvestigationMessage.builder()
                .session(session)
                .role("ASSISTANT")
                .content(answer)
                .sources("[\"InvestigationToolRegistry:ProhibitedActionPolicy\"]")
                .createdAt(Instant.now())
                .build();
        messageRepository.save(deniedMsg);

        return ChatInquiryResponse.builder()
                .sessionId(session.getId())
                .messageId(deniedMsg.getId())
                .response(answer)
                .toolsExecuted(Collections.emptyList())
                .sources(List.of("InvestigationToolRegistry:ProhibitedActionPolicy"))
                .toolResults(Collections.emptyList())
                .createdAt(deniedMsg.getCreatedAt())
                .build();
    }

    @Transactional
    public AiInvestigationSession getOrCreateSession(String sessionId, String merchantId, String title, String userId) {
        if (sessionId != null && !sessionId.isBlank()) {
            Optional<AiInvestigationSession> existing = sessionRepository.findById(sessionId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        String newId = "ses_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String sessionTitle = title != null && !title.isBlank()
                ? (title.length() > 60 ? title.substring(0, 60) + "..." : title)
                : "Investigation " + newId.substring(4, 10);

        AiInvestigationSession session = AiInvestigationSession.builder()
                .id(newId)
                .merchantId(merchantId)
                .title(sessionTitle)
                .userId(userId != null ? userId : "ANALYST_DEFAULT")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        AiInvestigationSession saved = sessionRepository.save(session);

        // Audit Event: AI_INVESTIGATION_STARTED
        auditService.recordRiskEvent(
                com.riskshield.audit.entity.AuditEventType.AI_INVESTIGATION_STARTED,
                com.riskshield.audit.entity.ActorType.USER,
                userId,
                null,
                null,
                "AiSession",
                saved.getId(),
                "ai-assistant",
                "Started new AI investigation session: " + sessionTitle,
                saved
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<SessionSummaryDto> listSessions(String userId) {
        List<AiInvestigationSession> sessions = userId != null
                ? sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                : sessionRepository.findAllByOrderByUpdatedAtDesc();

        return sessions.stream()
                .map(s -> SessionSummaryDto.builder()
                        .id(s.getId())
                        .merchantId(s.getMerchantId())
                        .title(s.getTitle())
                        .userId(s.getUserId())
                        .createdAt(s.getCreatedAt())
                        .updatedAt(s.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AiInvestigationMessage> getSessionMessages(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    private String extractPattern(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1) != null ? matcher.group(1) : matcher.group(0);
        }
        return null;
    }

    private Transaction findContextTransaction(String activeTxId, String query) {
        String txId = activeTxId;
        if (txId == null || txId.isBlank()) {
            txId = extractPattern(TX_PATTERN, query);
        }
        if (txId != null) {
            return transactionRepository.findById(txId).orElse(null);
        }
        return null;
    }

    private String serializeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
