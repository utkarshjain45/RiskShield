package com.riskshield.policy.service;

import com.riskshield.common.exception.ResourceNotFoundException;
import com.riskshield.merchant.entity.Merchant;
import com.riskshield.merchant.repository.MerchantRepository;
import com.riskshield.policy.dto.*;
import com.riskshield.policy.entity.RiskDecision;
import com.riskshield.policy.entity.RiskPolicy;
import com.riskshield.policy.entity.RiskPolicyRule;
import com.riskshield.policy.repository.RiskPolicyRepository;
import com.riskshield.policy.repository.RiskPolicyRuleRepository;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskPolicyService {

    private final RiskPolicyRepository riskPolicyRepository;
    private final RiskPolicyRuleRepository riskPolicyRuleRepository;
    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;
    private final PolicyEvaluationService policyEvaluationService;

    @Transactional(readOnly = true)
    public List<PolicyResponse> getPolicies(String merchantId, Boolean enabled) {
        String effectiveMerchant = com.riskshield.security.util.SecurityUtils.resolveMerchantScope(merchantId);
        List<RiskPolicy> policies;
        if (effectiveMerchant != null && enabled != null) {
            policies = riskPolicyRepository.findByMerchantIdAndEnabled(effectiveMerchant, enabled);
        } else if (effectiveMerchant != null) {
            policies = riskPolicyRepository.findByMerchantId(effectiveMerchant);
        } else if (enabled != null) {
            policies = riskPolicyRepository.findByEnabled(enabled);
        } else {
            policies = riskPolicyRepository.findAll();
        }
        return policies.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PolicyResponse getPolicyById(String id) {
        RiskPolicy policy = riskPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk policy not found: " + id));
        if (policy.getMerchant() != null) {
            com.riskshield.security.util.SecurityUtils.assertMerchantAccess(policy.getMerchant().getId());
        }
        return mapToResponse(policy);
    }

    @Transactional
    public PolicyResponse createPolicy(CreatePolicyRequest req) {
        if (req.getMerchantId() != null) {
            com.riskshield.security.util.SecurityUtils.assertMerchantAccess(req.getMerchantId());
        }
        log.info("Creating risk policy: {} (merchantId: {})", req.getName(), req.getMerchantId());

        Merchant merchant = null;
        if (req.getMerchantId() != null && !req.getMerchantId().isBlank()) {
            merchant = merchantRepository.findById(req.getMerchantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Merchant not found: " + req.getMerchantId()));
        }

        RiskPolicy policy = RiskPolicy.builder()
                .id("pol_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .merchant(merchant)
                .name(req.getName())
                .policyVersion(req.getPolicyVersion() != null ? req.getPolicyVersion() : "v1.0.0")
                .lowRiskThreshold(req.getLowRiskThreshold())
                .reviewThreshold(req.getReviewThreshold())
                .blockThreshold(req.getBlockThreshold())
                .falsePositiveCostWeight(req.getFalsePositiveCostWeight() != null ? req.getFalsePositiveCostWeight() : 1.0)
                .maxSingleTxPaise(req.getMaxSingleTxPaise() != null ? req.getMaxSingleTxPaise() : 10000000L)
                .enabled(req.isEnabled())
                .createdAt(Instant.now())
                .build();

        policy.validateThresholds();

        if (req.getRules() != null && !req.getRules().isEmpty()) {
            List<RiskPolicyRule> rules = new ArrayList<>();
            for (PolicyRuleDto ruleDto : req.getRules()) {
                rules.add(RiskPolicyRule.builder()
                        .id("rule_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                        .policy(policy)
                        .ruleName(ruleDto.getRuleName())
                        .ruleType(ruleDto.getRuleType() != null ? ruleDto.getRuleType() : "THRESHOLD")
                        .conditionOperator(ruleDto.getConditionOperator())
                        .conditionValue(ruleDto.getConditionValue())
                        .action(ruleDto.getAction())
                        .priority(ruleDto.getPriority() != null ? ruleDto.getPriority() : 100)
                        .enabled(ruleDto.isEnabled())
                        .createdAt(Instant.now())
                        .build());
            }
            policy.setRules(rules);
        }

        RiskPolicy savedPolicy = riskPolicyRepository.save(policy);
        return mapToResponse(savedPolicy);
    }

    @Transactional
    public PolicyResponse updatePolicy(String id, UpdatePolicyRequest req) {
        log.info("Updating risk policy: {}", id);
        RiskPolicy policy = riskPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk policy not found: " + id));

        if (policy.getMerchant() != null) {
            com.riskshield.security.util.SecurityUtils.assertMerchantAccess(policy.getMerchant().getId());
        }

        if (req.getName() != null) {
            policy.setName(req.getName());
        }
        if (req.getPolicyVersion() != null) {
            policy.setPolicyVersion(req.getPolicyVersion());
        }
        if (req.getLowRiskThreshold() != null) {
            policy.setLowRiskThreshold(req.getLowRiskThreshold());
        }
        if (req.getReviewThreshold() != null) {
            policy.setReviewThreshold(req.getReviewThreshold());
        }
        if (req.getBlockThreshold() != null) {
            policy.setBlockThreshold(req.getBlockThreshold());
        }
        if (req.getFalsePositiveCostWeight() != null) {
            policy.setFalsePositiveCostWeight(req.getFalsePositiveCostWeight());
        }
        if (req.getMaxSingleTxPaise() != null) {
            policy.setMaxSingleTxPaise(req.getMaxSingleTxPaise());
        }
        if (req.getEnabled() != null) {
            policy.setEnabled(req.getEnabled());
        }

        policy.validateThresholds();

        if (req.getRules() != null) {
            policy.getRules().clear();
            for (PolicyRuleDto ruleDto : req.getRules()) {
                policy.getRules().add(RiskPolicyRule.builder()
                        .id(ruleDto.getId() != null ? ruleDto.getId() : "rule_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                        .policy(policy)
                        .ruleName(ruleDto.getRuleName())
                        .ruleType(ruleDto.getRuleType() != null ? ruleDto.getRuleType() : "THRESHOLD")
                        .conditionOperator(ruleDto.getConditionOperator())
                        .conditionValue(ruleDto.getConditionValue())
                        .action(ruleDto.getAction())
                        .priority(ruleDto.getPriority() != null ? ruleDto.getPriority() : 100)
                        .enabled(ruleDto.isEnabled())
                        .createdAt(Instant.now())
                        .build());
            }
        }

        RiskPolicy updatedPolicy = riskPolicyRepository.save(policy);
        return mapToResponse(updatedPolicy);
    }

    @Transactional
    public PolicyEvaluationResponse evaluatePolicy(String id, EvaluatePolicyRequest req) {
        RiskPolicy policy = riskPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk policy not found: " + id));

        Transaction transaction;
        if (req.getTransactionId() != null && !req.getTransactionId().isBlank()) {
            transaction = transactionRepository.findById(req.getTransactionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + req.getTransactionId()));
        } else {
            // Ephemeral synthetic transaction for testing evaluation
            transaction = Transaction.builder()
                    .id("tx_eval_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                    .merchant(policy.getMerchant())
                    .amountInPaise(req.getAmountInPaise() != null ? req.getAmountInPaise() : 100000L)
                    .currency("INR")
                    .paymentMethod("card")
                    .createdAt(Instant.now())
                    .build();
        }

        RiskDecision decision = policyEvaluationService.evaluateWithPolicy(policy, transaction, req.getRiskScore());

        return PolicyEvaluationResponse.builder()
                .transactionId(transaction.getId())
                .policyId(policy.getId())
                .policyVersion(policy.getPolicyVersion())
                .riskScore(req.getRiskScore())
                .decision(decision.getDecision())
                .decisionReason(decision.getDecisionReason())
                .evaluatedAt(decision.getEvaluatedAt())
                .build();
    }

    private PolicyResponse mapToResponse(RiskPolicy policy) {
        List<PolicyRuleDto> ruleDtos = null;
        if (policy.getRules() != null) {
            ruleDtos = policy.getRules().stream()
                    .map(r -> PolicyRuleDto.builder()
                            .id(r.getId())
                            .ruleName(r.getRuleName())
                            .ruleType(r.getRuleType())
                            .conditionOperator(r.getConditionOperator())
                            .conditionValue(r.getConditionValue())
                            .action(r.getAction())
                            .priority(r.getPriority())
                            .enabled(r.isEnabled())
                            .build())
                    .collect(Collectors.toList());
        }

        return PolicyResponse.builder()
                .id(policy.getId())
                .merchantId(policy.getMerchant() != null ? policy.getMerchant().getId() : null)
                .name(policy.getName())
                .policyVersion(policy.getPolicyVersion())
                .lowRiskThreshold(policy.getLowRiskThreshold())
                .reviewThreshold(policy.getReviewThreshold())
                .blockThreshold(policy.getBlockThreshold())
                .falsePositiveCostWeight(policy.getFalsePositiveCostWeight())
                .maxSingleTxPaise(policy.getMaxSingleTxPaise())
                .enabled(policy.isEnabled())
                .createdAt(policy.getCreatedAt())
                .rules(ruleDtos)
                .build();
    }
}
