package com.riskshield.assistant.tool;

import com.riskshield.assistant.service.AiSecuritySanitizer;
import com.riskshield.risk.dto.CurrentEvaluationResponse;
import com.riskshield.risk.dto.RiskExplanationResponse;
import com.riskshield.risk.entity.RiskAssessment;
import com.riskshield.risk.repository.RiskAssessmentRepository;
import com.riskshield.risk.service.ModelEvaluationService;
import com.riskshield.risk.service.RiskAssessmentService;
import com.riskshield.spike.dto.TimeWindowMetrics;
import com.riskshield.spike.entity.FraudIncident;
import com.riskshield.spike.repository.FraudIncidentRepository;
import com.riskshield.spike.service.MerchantMetricsService;
import com.riskshield.transaction.entity.Customer;
import com.riskshield.transaction.entity.Device;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.CustomerRepository;
import com.riskshield.transaction.repository.DeviceRepository;
import com.riskshield.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Registry of 10 structured, read-only tools available to the AI Investigation Assistant.
 * Strictly blocks mutating actions (blockTransaction, capturePayment, refundPayment, changePolicy).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvestigationToolRegistry {

    private final TransactionRepository transactionRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final RiskAssessmentService riskAssessmentService;
    private final CustomerRepository customerRepository;
    private final DeviceRepository deviceRepository;
    private final FraudIncidentRepository fraudIncidentRepository;
    private final MerchantMetricsService merchantMetricsService;
    private final ModelEvaluationService modelEvaluationService;
    private final AiSecuritySanitizer securitySanitizer;

    public static final Set<String> PROHIBITED_MUTATING_ACTIONS = Set.of(
            "blockTransaction",
            "capturePayment",
            "refundPayment",
            "changePolicy"
    );

    /**
     * Dispatches tool calls by name.
     */
    public ToolExecutionResult executeTool(String toolName, Map<String, Object> args) {
        if (toolName == null || toolName.isBlank()) {
            return ToolExecutionResult.builder()
                    .toolName("unknown")
                    .success(false)
                    .errorMessage("Tool name cannot be empty")
                    .build();
        }

        // Hard security enforcement: Deny any mutating action
        if (PROHIBITED_MUTATING_ACTIONS.contains(toolName)) {
            log.warn("BLOCKED MUTATING TOOL ATTEMPT: {}", toolName);
            return ToolExecutionResult.builder()
                    .toolName(toolName)
                    .arguments(args)
                    .success(false)
                    .errorMessage("ACTION DENIED: AI Assistant is strictly restricted to read-only investigation. " +
                            "Mutating actions ('" + toolName + "') require explicit human operator authorization.")
                    .build();
        }

        try {
            switch (toolName) {
                case "getTransaction":
                    return getTransaction(getStringArg(args, "transactionId"));
                case "getRiskAssessment":
                    return getRiskAssessment(getStringArg(args, "transactionId"));
                case "getRiskExplanation":
                    return getRiskExplanation(getStringArg(args, "transactionId"));
                case "getRelatedTransactions":
                    return getRelatedTransactions(getStringArg(args, "transactionId"));
                case "getCustomerHistory":
                    return getCustomerHistory(getStringArg(args, "customerId"));
                case "getDeviceActivity":
                    return getDeviceActivity(getStringArg(args, "deviceId"));
                case "getIPActivity":
                    return getIPActivity(getStringArg(args, "ipAddress"));
                case "getFraudIncident":
                    return getFraudIncident(getStringArg(args, "incidentId"));
                case "getMerchantRiskMetrics":
                    return getMerchantRiskMetrics(getStringArg(args, "merchantId"));
                case "getModelEvaluation":
                    return getModelEvaluation();
                default:
                    return ToolExecutionResult.builder()
                            .toolName(toolName)
                            .arguments(args)
                            .success(false)
                            .errorMessage("Unknown investigation tool: '" + toolName + "'")
                            .build();
            }
        } catch (Exception e) {
            log.error("Tool execution failed for {}: {}", toolName, e.getMessage(), e);
            return ToolExecutionResult.builder()
                    .toolName(toolName)
                    .arguments(args)
                    .success(false)
                    .errorMessage("Tool error: " + e.getMessage())
                    .build();
        }
    }

    // --- 1. getTransaction(transactionId) ---
    public ToolExecutionResult getTransaction(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return error("getTransaction", "transactionId parameter is required");
        }
        Optional<Transaction> txOpt = transactionRepository.findById(transactionId);
        if (txOpt.isEmpty()) {
            return error("getTransaction", "Transaction not found: " + transactionId);
        }

        Transaction tx = txOpt.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("transaction_id", tx.getId());
        data.put("amount_in_paise", tx.getAmountInPaise());
        data.put("amount_inr", tx.getAmountInPaise() != null ? tx.getAmountInPaise() / 100.0 : 0.0);
        data.put("currency", tx.getCurrency());
        data.put("payment_status", tx.getPaymentStatus());
        data.put("payment_method", tx.getPaymentMethod());
        data.put("created_at", tx.getCreatedAt().toString());
        data.put("merchant_id", tx.getMerchant() != null ? tx.getMerchant().getId() : null);
        data.put("customer_id", tx.getCustomer() != null ? tx.getCustomer().getId() : null);
        data.put("customer_email", tx.getCustomer() != null ? tx.getCustomer().getEmail() : null);
        data.put("device_id", tx.getDevice() != null ? tx.getDevice().getId() : null);
        data.put("ip_address", tx.getIpAddress());
        data.put("is_new_device", tx.isNewDevice());
        data.put("is_new_ip", tx.isNewIp());

        return ToolExecutionResult.builder()
                .toolName("getTransaction")
                .arguments(Map.of("transactionId", transactionId))
                .success(true)
                .data(data)
                .sourceEntity("Transaction[" + tx.getId() + "]")
                .build();
    }

    // --- 2. getRiskAssessment(transactionId) ---
    public ToolExecutionResult getRiskAssessment(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return error("getRiskAssessment", "transactionId parameter is required");
        }
        Optional<RiskAssessment> astOpt = riskAssessmentRepository.findByTransactionId(transactionId);
        if (astOpt.isEmpty()) {
            return error("getRiskAssessment", "No risk assessment recorded for transaction: " + transactionId);
        }

        RiskAssessment ast = astOpt.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assessment_id", ast.getId());
        data.put("transaction_id", ast.getTransaction() != null ? ast.getTransaction().getId() : transactionId);
        data.put("risk_score", ast.getRiskScore());
        data.put("fraud_probability", ast.getFraudProbability());
        data.put("model_version", ast.getModelVersion());
        data.put("inference_latency_ms", ast.getInferenceLatencyMs());
        data.put("prediction_timestamp", ast.getPredictionTimestamp().toString());

        return ToolExecutionResult.builder()
                .toolName("getRiskAssessment")
                .arguments(Map.of("transactionId", transactionId))
                .success(true)
                .data(data)
                .sourceEntity("RiskAssessment[" + ast.getId() + "]")
                .build();
    }

    // --- 3. getRiskExplanation(transactionId) ---
    public ToolExecutionResult getRiskExplanation(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return error("getRiskExplanation", "transactionId parameter is required");
        }
        try {
            RiskExplanationResponse explanation = riskAssessmentService.getTransactionExplanation(transactionId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("transaction_id", explanation.getTransactionId());
            data.put("risk_score", explanation.getRiskScore());
            data.put("fraud_probability", explanation.getFraudProbability());
            data.put("decision", explanation.getDecision());
            data.put("human_readable_explanation", explanation.getHumanReadableExplanation());
            data.put("top_signals", explanation.getTopContributingFeatures());

            return ToolExecutionResult.builder()
                    .toolName("getRiskExplanation")
                    .arguments(Map.of("transactionId", transactionId))
                    .success(true)
                    .data(data)
                    .sourceEntity("RiskExplanation[" + transactionId + "]")
                    .build();
        } catch (Exception e) {
            return error("getRiskExplanation", "Could not produce explanation for " + transactionId + ": " + e.getMessage());
        }
    }

    // --- 4. getRelatedTransactions(transactionId) ---
    public ToolExecutionResult getRelatedTransactions(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return error("getRelatedTransactions", "transactionId parameter is required");
        }
        Optional<Transaction> txOpt = transactionRepository.findById(transactionId);
        if (txOpt.isEmpty()) {
            return error("getRelatedTransactions", "Transaction not found: " + transactionId);
        }

        Transaction tx = txOpt.get();
        Customer cust = tx.getCustomer();
        Device dev = tx.getDevice();
        String ip = tx.getIpAddress();

        Map<String, Transaction> relatedMap = new LinkedHashMap<>();

        if (cust != null) {
            transactionRepository.findByCustomerOrderByCreatedAtDesc(cust, PageRequest.of(0, 5))
                    .forEach(t -> { if (!t.getId().equals(tx.getId())) relatedMap.put(t.getId(), t); });
        }
        if (dev != null) {
            transactionRepository.findByDeviceOrderByCreatedAtDesc(dev, PageRequest.of(0, 5))
                    .forEach(t -> { if (!t.getId().equals(tx.getId())) relatedMap.put(t.getId(), t); });
        }
        if (ip != null) {
            transactionRepository.findByIpAddressOrderByCreatedAtDesc(ip, PageRequest.of(0, 5))
                    .forEach(t -> { if (!t.getId().equals(tx.getId())) relatedMap.put(t.getId(), t); });
        }

        List<Map<String, Object>> relatedSummary = new ArrayList<>();
        for (Transaction r : relatedMap.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("transaction_id", r.getId());
            item.put("amount_in_paise", r.getAmountInPaise());
            item.put("payment_status", r.getPaymentStatus());
            item.put("payment_method", r.getPaymentMethod());
            item.put("created_at", r.getCreatedAt().toString());
            item.put("shared_customer", cust != null && r.getCustomer() != null && cust.getId().equals(r.getCustomer().getId()));
            item.put("shared_device", dev != null && r.getDevice() != null && dev.getId().equals(r.getDevice().getId()));
            item.put("shared_ip", ip != null && ip.equals(r.getIpAddress()));
            relatedSummary.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("target_transaction_id", transactionId);
        data.put("related_count", relatedSummary.size());
        data.put("related_transactions", relatedSummary);

        return ToolExecutionResult.builder()
                .toolName("getRelatedTransactions")
                .arguments(Map.of("transactionId", transactionId))
                .success(true)
                .data(data)
                .sourceEntity("RelatedTransactions[txCount=" + relatedSummary.size() + "]")
                .build();
    }

    // --- 5. getCustomerHistory(customerId) ---
    public ToolExecutionResult getCustomerHistory(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return error("getCustomerHistory", "customerId parameter is required");
        }
        Optional<Customer> custOpt = customerRepository.findById(customerId);
        List<Transaction> txList = custOpt.isPresent()
                ? transactionRepository.findByCustomerOrderByCreatedAtDesc(custOpt.get(), PageRequest.of(0, 20))
                : Collections.emptyList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customer_id", customerId);
        if (custOpt.isPresent()) {
            Customer c = custOpt.get();
            data.put("email", securitySanitizer.sanitizeAndWrap(c.getEmail(), "customer_email"));
            long accountAgeDays = c.getAccountCreatedAt() != null
                    ? ChronoUnit.DAYS.between(c.getAccountCreatedAt(), Instant.now())
                    : 30L;
            data.put("account_age_days", accountAgeDays);
            data.put("risk_segment", c.getRiskSegment());
        }

        long totalAmount = 0L;
        long blockedCount = 0L;
        long reviewCount = 0L;
        long successCount = 0L;

        for (Transaction t : txList) {
            if (t.getAmountInPaise() != null) totalAmount += t.getAmountInPaise();
            if ("BLOCKED".equalsIgnoreCase(t.getPaymentStatus()) || "FAILED".equalsIgnoreCase(t.getPaymentStatus())) blockedCount++;
            else if ("REVIEW".equalsIgnoreCase(t.getPaymentStatus())) reviewCount++;
            else successCount++;
        }

        data.put("total_transactions", txList.size());
        data.put("total_spent_inr", totalAmount / 100.0);
        data.put("successful_transactions", successCount);
        data.put("blocked_transactions", blockedCount);
        data.put("review_transactions", reviewCount);

        return ToolExecutionResult.builder()
                .toolName("getCustomerHistory")
                .arguments(Map.of("customerId", customerId))
                .success(true)
                .data(data)
                .sourceEntity("Customer[" + customerId + "]")
                .build();
    }

    // --- 6. getDeviceActivity(deviceId) ---
    public ToolExecutionResult getDeviceActivity(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return error("getDeviceActivity", "deviceId parameter is required");
        }
        Optional<Device> devOpt = deviceRepository.findById(deviceId);
        List<Transaction> txList = devOpt.isPresent()
                ? transactionRepository.findByDeviceOrderByCreatedAtDesc(devOpt.get(), PageRequest.of(0, 20))
                : Collections.emptyList();

        long distinctCustomers = devOpt.isPresent()
                ? transactionRepository.countDistinctCustomersByDevice(devOpt.get())
                : 0L;

        long blockedCount = 0L;
        for (Transaction t : txList) {
            if ("BLOCKED".equalsIgnoreCase(t.getPaymentStatus())) blockedCount++;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("device_id", deviceId);
        data.put("device_registered", devOpt.isPresent());
        data.put("total_transactions", txList.size());
        data.put("distinct_accounts_seen", distinctCustomers);
        data.put("blocked_transactions", blockedCount);
        data.put("is_suspicious_device", distinctCustomers > 2 || blockedCount > 1);

        return ToolExecutionResult.builder()
                .toolName("getDeviceActivity")
                .arguments(Map.of("deviceId", deviceId))
                .success(true)
                .data(data)
                .sourceEntity("Device[" + deviceId + "]")
                .build();
    }

    // --- 7. getIPActivity(ipAddress) ---
    public ToolExecutionResult getIPActivity(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return error("getIPActivity", "ipAddress parameter is required");
        }
        List<Transaction> txList = transactionRepository.findByIpAddressOrderByCreatedAtDesc(ipAddress, PageRequest.of(0, 20));
        long distinctCustomers = transactionRepository.countDistinctCustomersByIpAddress(ipAddress);

        long blockedCount = 0L;
        for (Transaction t : txList) {
            if ("BLOCKED".equalsIgnoreCase(t.getPaymentStatus())) blockedCount++;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ip_address", ipAddress);
        data.put("total_transactions", txList.size());
        data.put("distinct_accounts", distinctCustomers);
        data.put("blocked_transactions", blockedCount);
        data.put("is_high_risk_ip", distinctCustomers > 3 || blockedCount > 1);

        return ToolExecutionResult.builder()
                .toolName("getIPActivity")
                .arguments(Map.of("ipAddress", ipAddress))
                .success(true)
                .data(data)
                .sourceEntity("IPActivity[" + ipAddress + "]")
                .build();
    }

    // --- 8. getFraudIncident(incidentId) ---
    public ToolExecutionResult getFraudIncident(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            return error("getFraudIncident", "incidentId parameter is required");
        }
        Optional<FraudIncident> incOpt = fraudIncidentRepository.findById(incidentId);
        if (incOpt.isEmpty()) {
            return error("getFraudIncident", "Fraud incident not found: " + incidentId);
        }

        FraudIncident inc = incOpt.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("incident_id", inc.getIncidentId());
        data.put("merchant_id", inc.getMerchantId());
        data.put("severity", inc.getSeverity().name());
        data.put("status", inc.getStatus().name());
        data.put("baseline_fraud_rate", inc.getBaselineRate());
        data.put("current_fraud_rate", inc.getCurrentRate());
        data.put("percentage_increase", inc.getPercentageIncrease());
        data.put("affected_transactions", inc.getAffectedTransactions());
        data.put("estimated_exposure_inr", inc.getEstimatedExposure() != null ? inc.getEstimatedExposure() / 100.0 : 0.0);
        data.put("explanation", inc.getExplanationSummary());
        data.put("detected_at", inc.getDetectedAt().toString());

        return ToolExecutionResult.builder()
                .toolName("getFraudIncident")
                .arguments(Map.of("incidentId", incidentId))
                .success(true)
                .data(data)
                .sourceEntity("FraudIncident[" + inc.getIncidentId() + "]")
                .build();
    }

    // --- 9. getMerchantRiskMetrics(merchantId) ---
    public ToolExecutionResult getMerchantRiskMetrics(String merchantId) {
        if (merchantId == null || merchantId.isBlank()) {
            merchantId = "mer_default_001";
        }
        try {
            Instant now = Instant.now();
            TimeWindowMetrics metrics = merchantMetricsService.calculateWindowMetrics(
                    merchantId, "24h", now.minus(Duration.ofHours(24)), now
            );
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("merchant_id", merchantId);
            data.put("time_window", metrics.getWindow());
            data.put("total_transactions", metrics.getTotalTransactions());
            data.put("suspicious_transactions", metrics.getSuspiciousTransactions());
            data.put("blocked_transactions", metrics.getBlockedTransactions());
            data.put("review_transactions", metrics.getReviewTransactions());
            data.put("fraud_rate", metrics.getFraudRate());
            data.put("average_transaction_amount_inr", metrics.getAverageTransactionAmountInr());
            data.put("fraud_exposure_inr", metrics.getFraudExposureInr());
            data.put("prevented_loss_inr", metrics.getBlockedTransactions() * metrics.getAverageTransactionAmountInr());

            return ToolExecutionResult.builder()
                    .toolName("getMerchantRiskMetrics")
                    .arguments(Map.of("merchantId", merchantId))
                    .success(true)
                    .data(data)
                    .sourceEntity("MerchantMetrics[" + merchantId + ":24h]")
                    .build();
        } catch (Exception e) {
            return error("getMerchantRiskMetrics", "Failed to compute merchant metrics: " + e.getMessage());
        }
    }

    // --- 10. getModelEvaluation() ---
    public ToolExecutionResult getModelEvaluation() {
        try {
            CurrentEvaluationResponse eval = modelEvaluationService.getCurrentEvaluation();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("model_version", eval.getModelVersion());
            data.put("dataset_version", eval.getDatasetVersion());
            data.put("test_set_version", eval.getTestSetVersion());
            data.put("evaluation_label", eval.getEvaluationLabel());
            data.put("dataset_size", eval.getDatasetSize());
            data.put("fraud_count", eval.getFraudCount());
            data.put("non_fraud_count", eval.getNonFraudCount());
            data.put("precision", eval.getPrecision());
            data.put("recall", eval.getRecall());
            data.put("f1", eval.getF1());
            data.put("roc_auc", eval.getRocAuc());
            data.put("pr_auc", eval.getPrAuc());
            data.put("false_positive_rate", eval.getFalsePositiveRate());
            data.put("confusion_matrix", eval.getConfusionMatrix());
            data.put("false_positive_cost_inr", eval.getFalsePositiveCost());
            data.put("estimated_prevented_loss_inr", eval.getEstimatedPreventedLoss());

            return ToolExecutionResult.builder()
                    .toolName("getModelEvaluation")
                    .arguments(Collections.emptyMap())
                    .success(true)
                    .data(data)
                    .sourceEntity("ModelEvaluation[" + eval.getModelVersion() + " on " + eval.getTestSetVersion() + "]")
                    .build();
        } catch (Exception e) {
            return error("getModelEvaluation", "Could not retrieve model evaluation: " + e.getMessage());
        }
    }

    private ToolExecutionResult error(String toolName, String message) {
        return ToolExecutionResult.builder()
                .toolName(toolName)
                .success(false)
                .errorMessage(message)
                .build();
    }

    private String getStringArg(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key)) return null;
        Object val = args.get(key);
        return val != null ? val.toString().trim() : null;
    }
}
