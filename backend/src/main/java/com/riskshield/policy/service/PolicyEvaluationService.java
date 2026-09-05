package com.riskshield.policy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.audit.service.AuditService;
import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.policy.entity.RiskDecision;
import com.riskshield.policy.entity.RiskPolicy;
import com.riskshield.policy.entity.RiskPolicyRule;
import com.riskshield.policy.evaluator.EvaluationContext;
import com.riskshield.policy.evaluator.RuleEvaluationRegistry;
import com.riskshield.policy.evaluator.RuleEvaluationResult;
import com.riskshield.policy.repository.RiskDecisionRepository;
import com.riskshield.policy.repository.RiskPolicyRepository;
import com.riskshield.policy.repository.RiskPolicyRuleRepository;
import com.riskshield.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Deterministic Risk Policy Engine.
 *
 * Core Principle:
 * - The ML model produces risk.
 * - The policy engine produces action (ALLOW / REVIEW / BLOCK).
 * - The policy engine must NEVER use an LLM.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEvaluationService {

    private final RiskPolicyRepository riskPolicyRepository;
    private final RiskPolicyRuleRepository riskPolicyRuleRepository;
    private final RiskDecisionRepository riskDecisionRepository;
    private final RuleEvaluationRegistry ruleEvaluationRegistry;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // Default global baseline policy (0-30 ALLOW, 31-89 REVIEW, 90-100 BLOCK)
    public static final double DEFAULT_LOW_RISK_THRESHOLD = 30.0;
    public static final double DEFAULT_REVIEW_THRESHOLD = 70.0;
    public static final double DEFAULT_BLOCK_THRESHOLD = 90.0;

    /**
     * Evaluates a payment transaction using active merchant or fallback policy.
     */
    @Transactional
    public RiskDecision evaluate(Transaction transaction, Double riskScore) {
        String merchantId = (transaction.getMerchant() != null) ? transaction.getMerchant().getId() : null;
        RiskPolicy policy = resolvePolicyForMerchant(merchantId);
        return evaluateWithPolicy(policy, transaction, riskScore);
    }

    /**
     * Resolves the effective policy for a merchant.
     * If merchant has an enabled custom policy, returns it; otherwise falls back to active default policy.
     */
    @Transactional(readOnly = true)
    public RiskPolicy resolvePolicyForMerchant(String merchantId) {
        if (merchantId != null) {
            Optional<RiskPolicy> merchantPolicy = riskPolicyRepository.findByMerchantIdAndEnabledTrue(merchantId);
            if (merchantPolicy.isPresent()) {
                return merchantPolicy.get();
            }
        }

        // Fallback to active global default policy
        return riskPolicyRepository.findFirstByMerchantIsNullAndEnabledTrue()
                .orElseGet(this::createInMemoryDefaultPolicy);
    }

    /**
     * Evaluates a specific policy explicitly.
     */
    @Transactional
    public RiskDecision evaluateWithPolicy(RiskPolicy policy, Transaction transaction, Double riskScore) {
        log.info("Evaluating deterministic policy '{}' (ver: {}) for txId: {} with riskScore: {}",
                policy.getName(), policy.getPolicyVersion(), transaction.getId(), riskScore);

        EvaluationContext context = EvaluationContext.builder()
                .transaction(transaction)
                .riskScore(riskScore)
                .policy(policy)
                .build();

        RiskDecisionType decision = null;
        String reason = null;

        // 1. Evaluate discrete policy rules ordered by priority if any exist
        if (policy.getId() != null) {
            List<RiskPolicyRule> rules = riskPolicyRuleRepository.findByPolicyIdAndEnabledTrueOrderByPriorityAsc(policy.getId());
            for (RiskPolicyRule rule : rules) {
                Optional<RuleEvaluationResult> ruleResult = ruleEvaluationRegistry.evaluateRule(rule, context);
                if (ruleResult.isPresent()) {
                    decision = ruleResult.get().getAction();
                    reason = ruleResult.get().getReason();
                    log.info("Policy rule '{}' triggered -> Decision: {}, Reason: {}", rule.getRuleName(), decision, reason);
                    break;
                }
            }
        }

        // 2. If no custom rule triggered, apply deterministic threshold matrix
        boolean isMerchantCustom = (policy.getMerchant() != null);
        String prefix = isMerchantCustom ? "merchant " : "";

        if (decision == null) {
            double blockThreshold = policy.getBlockThreshold();
            double reviewThreshold = policy.getReviewThreshold();
            double lowRiskThreshold = policy.getLowRiskThreshold();

            String scoreStr = formatNumber(riskScore);
            String blockStr = formatNumber(blockThreshold);
            String reviewStr = formatNumber(reviewThreshold);
            String lowStr = formatNumber(lowRiskThreshold);

            // Amount threshold check
            if (transaction.getAmountInPaise() != null && transaction.getAmountInPaise() > policy.getMaxSingleTxPaise()) {
                decision = (riskScore >= reviewThreshold) ? RiskDecisionType.BLOCK : RiskDecisionType.REVIEW;
                reason = String.format("%s because transaction amount (₹%.2f) exceeded %smaximum single transaction limit (₹%.2f).",
                        decision, transaction.getAmountInPaise() / 100.0, prefix, policy.getMaxSingleTxPaise() / 100.0);
            } else if (riskScore >= blockThreshold) {
                // 90-100 -> BLOCK
                decision = RiskDecisionType.BLOCK;
                reason = String.format("BLOCK because risk score %s exceeded %sblock threshold %s.",
                        scoreStr, prefix, blockStr);
            } else if (riskScore >= reviewThreshold) {
                // 71-89 -> REVIEW (elevated)
                decision = RiskDecisionType.REVIEW;
                reason = String.format("REVIEW because risk score %s exceeded %sreview threshold %s.",
                        scoreStr, prefix, reviewStr);
            } else if (riskScore > lowRiskThreshold) {
                // 31-70 -> REVIEW (standard)
                decision = RiskDecisionType.REVIEW;
                reason = String.format("REVIEW because risk score %s exceeded %slow risk threshold %s.",
                        scoreStr, prefix, lowStr);
            } else {
                // 0-30 -> ALLOW
                decision = RiskDecisionType.ALLOW;
                reason = String.format("ALLOW because risk score %s is within %slow risk threshold %s.",
                        scoreStr, prefix, lowStr);
            }
        }

        log.info("Deterministic decision: {} (Reason: {})", decision, reason);

        // 3. Store RiskDecision
        Instant now = Instant.now();
        String storedPolicyId = policy.getId();
        if (storedPolicyId != null && !riskPolicyRepository.existsById(storedPolicyId)) {
            storedPolicyId = null;
        }

        RiskDecision riskDecision = RiskDecision.builder()
                .id("dec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .transaction(transaction)
                .riskScore(riskScore)
                .decision(decision)
                .policyId(storedPolicyId)
                .policyVersion(policy.getPolicyVersion())
                .reason(reason)
                .decisionReason(reason)
                .evaluatedAt(now)
                .createdAt(now)
                .build();

        RiskDecision savedDecision = riskDecisionRepository.save(riskDecision);

        // 4. Record Audit Event
        recordPolicyAuditEvent(transaction, savedDecision, policy);

        return savedDecision;
    }

    private void recordPolicyAuditEvent(Transaction transaction, RiskDecision decision, RiskPolicy policy) {
        try {
            String merchantId = transaction.getMerchant() != null ? transaction.getMerchant().getId() : null;

            Map<String, Object> auditDetails = new LinkedHashMap<>();
            auditDetails.put("policyId", policy.getId());
            auditDetails.put("policyName", policy.getName());
            auditDetails.put("policyVersion", policy.getPolicyVersion());
            auditDetails.put("decision", decision.getDecision().name());
            auditDetails.put("riskScore", decision.getRiskScore());
            auditDetails.put("reason", decision.getReason());
            auditDetails.put("evaluatedAt", decision.getEvaluatedAt().toString());

            // 1. Audit Event: POLICY_EVALUATED
            auditService.recordRiskEvent(
                    com.riskshield.audit.entity.AuditEventType.POLICY_EVALUATED,
                    com.riskshield.audit.entity.ActorType.SYSTEM,
                    "POLICY_ENGINE",
                    merchantId,
                    transaction,
                    "RiskPolicy",
                    policy.getId() != null ? policy.getId() : "pol_default_baseline",
                    "policy-engine",
                    String.format("Evaluated risk policy '%s' against score %.2f -> %s",
                            policy.getName(), decision.getRiskScore(), decision.getDecision()),
                    auditDetails
            );

            // 2. Audit Event: RISK_DECISION_CREATED
            auditService.recordRiskEvent(
                    com.riskshield.audit.entity.AuditEventType.RISK_DECISION_CREATED,
                    com.riskshield.audit.entity.ActorType.SYSTEM,
                    "POLICY_ENGINE",
                    merchantId,
                    transaction,
                    "RiskDecision",
                    decision.getId(),
                    "policy-engine",
                    String.format("Final risk decision created: %s (%s)", decision.getDecision(), decision.getReason()),
                    decision
            );
        } catch (Exception e) {
            log.warn("Failed to record policy audit details: {}", e.getMessage());
        }
    }

    private RiskPolicy createInMemoryDefaultPolicy() {
        return RiskPolicy.builder()
                .id("pol_default_baseline")
                .name("Default System Policy")
                .policyVersion("v1.0.0-default")
                .lowRiskThreshold(DEFAULT_LOW_RISK_THRESHOLD)
                .reviewThreshold(DEFAULT_REVIEW_THRESHOLD)
                .blockThreshold(DEFAULT_BLOCK_THRESHOLD)
                .falsePositiveCostWeight(1.00)
                .maxSingleTxPaise(10000000L)
                .enabled(true)
                .createdAt(Instant.now())
                .build();
    }

    private String formatNumber(double num) {
        if (num == Math.floor(num)) {
            return String.format("%.0f", num);
        } else {
            return String.format("%.2f", num);
        }
    }
}
