package com.riskshield.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.audit.service.AuditService;
import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.merchant.entity.Merchant;
import com.riskshield.policy.entity.RiskDecision;
import com.riskshield.policy.entity.RiskPolicy;
import com.riskshield.policy.entity.RiskPolicyRule;
import com.riskshield.policy.evaluator.AmountLimitRuleEvaluator;
import com.riskshield.policy.evaluator.RuleEvaluationRegistry;
import com.riskshield.policy.evaluator.ThresholdRuleEvaluator;
import com.riskshield.policy.repository.RiskDecisionRepository;
import com.riskshield.policy.repository.RiskPolicyRepository;
import com.riskshield.policy.repository.RiskPolicyRuleRepository;
import com.riskshield.policy.service.PolicyEvaluationService;
import com.riskshield.transaction.entity.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyEvaluationServiceTest {

    @Mock
    private RiskPolicyRepository riskPolicyRepository;

    @Mock
    private RiskPolicyRuleRepository riskPolicyRuleRepository;

    @Mock
    private RiskDecisionRepository riskDecisionRepository;

    @Mock
    private AuditService auditService;

    private ObjectMapper objectMapper;
    private PolicyEvaluationService policyEvaluationService;

    private RiskPolicy defaultPolicy;
    private Merchant testMerchant;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RuleEvaluationRegistry registry = new RuleEvaluationRegistry(List.of(
                new ThresholdRuleEvaluator(),
                new AmountLimitRuleEvaluator()
        ));

        policyEvaluationService = new PolicyEvaluationService(
                riskPolicyRepository,
                riskPolicyRuleRepository,
                riskDecisionRepository,
                registry,
                auditService,
                objectMapper
        );

        testMerchant = Merchant.builder()
                .id("mer_test_001")
                .name("Acme Electronics")
                .build();

        defaultPolicy = RiskPolicy.builder()
                .id("pol_default_global")
                .name("Global Baseline Policy")
                .policyVersion("v1.0.0")
                .lowRiskThreshold(30.00)
                .reviewThreshold(70.00)
                .blockThreshold(90.00)
                .maxSingleTxPaise(10000000L)
                .enabled(true)
                .build();

        // Default lenient mock for saving decisions and repository existsById
        lenient().when(riskDecisionRepository.save(any(RiskDecision.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(riskPolicyRepository.existsById(anyString())).thenReturn(true);
    }

    @ParameterizedTest(name = "Risk score {0} should result in decision {1}")
    @CsvSource({
            "0.0, ALLOW",
            "15.5, ALLOW",
            "30.0, ALLOW",
            "30.01, REVIEW",
            "50.0, REVIEW",
            "70.0, REVIEW",
            "71.0, REVIEW",
            "89.99, REVIEW",
            "90.0, BLOCK",
            "95.0, BLOCK",
            "100.0, BLOCK"
    })
    @DisplayName("Boundary Value Tests: Default policy threshold boundaries")
    void testBoundaryValues(double riskScore, RiskDecisionType expectedDecision) {
        when(riskPolicyRepository.findByMerchantIdAndEnabledTrue("mer_test_001"))
                .thenReturn(Optional.empty());
        when(riskPolicyRepository.findFirstByMerchantIsNullAndEnabledTrue())
                .thenReturn(Optional.of(defaultPolicy));

        Transaction tx = Transaction.builder()
                .id("tx_boundary_" + (int) riskScore)
                .merchant(testMerchant)
                .amountInPaise(50000L)
                .currency("INR")
                .build();

        RiskDecision decision = policyEvaluationService.evaluate(tx, riskScore);

        assertThat(decision.getDecision()).isEqualTo(expectedDecision);
        assertThat(decision.getRiskScore()).isEqualTo(riskScore);
        assertThat(decision.getPolicyId()).isEqualTo("pol_default_global");

        // Verify reason format
        if (expectedDecision == RiskDecisionType.BLOCK) {
            assertThat(decision.getReason()).contains("BLOCK because risk score");
            assertThat(decision.getReason()).contains("exceeded block threshold 90");
        } else if (expectedDecision == RiskDecisionType.ALLOW) {
            assertThat(decision.getReason()).contains("ALLOW because risk score");
            assertThat(decision.getReason()).contains("is within low risk threshold 30");
        } else {
            assertThat(decision.getReason()).contains("REVIEW because risk score");
        }

        // Verify audit event dispatched
        verify(auditService, atLeastOnce()).recordRiskEvent(
                eq(com.riskshield.audit.entity.AuditEventType.POLICY_EVALUATED),
                eq(com.riskshield.audit.entity.ActorType.SYSTEM),
                any(),
                any(),
                eq(tx),
                eq("RiskPolicy"),
                eq("pol_default_global"),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Merchant-Specific Policy: Custom thresholds take precedence over default")
    void testMerchantSpecificPolicy() {
        RiskPolicy merchantCustomPolicy = RiskPolicy.builder()
                .id("pol_merchant_custom")
                .merchant(testMerchant)
                .name("High-Risk Merchant Custom Policy")
                .policyVersion("v2.1.0")
                .lowRiskThreshold(20.00)
                .reviewThreshold(50.00)
                .blockThreshold(75.00)
                .maxSingleTxPaise(5000000L)
                .enabled(true)
                .build();

        when(riskPolicyRepository.findByMerchantIdAndEnabledTrue("mer_test_001"))
                .thenReturn(Optional.of(merchantCustomPolicy));

        Transaction tx = Transaction.builder()
                .id("tx_merchant_001")
                .merchant(testMerchant)
                .amountInPaise(30000L)
                .build();

        // Score 76 is above merchant block threshold (75) even though below global default (90)
        RiskDecision decision = policyEvaluationService.evaluate(tx, 76.0);

        assertThat(decision.getDecision()).isEqualTo(RiskDecisionType.BLOCK);
        assertThat(decision.getPolicyId()).isEqualTo("pol_merchant_custom");
        assertThat(decision.getPolicyVersion()).isEqualTo("v2.1.0");
        assertThat(decision.getReason()).isEqualTo("BLOCK because risk score 76 exceeded merchant block threshold 75.");
    }

    @Test
    @DisplayName("Disabled Policy Fallback: When merchant policy is disabled, falls back to default")
    void testDisabledPolicyFallback() {
        // findByMerchantIdAndEnabledTrue returns empty because policy is disabled (enabled=false)
        when(riskPolicyRepository.findByMerchantIdAndEnabledTrue("mer_test_001"))
                .thenReturn(Optional.empty());
        when(riskPolicyRepository.findFirstByMerchantIsNullAndEnabledTrue())
                .thenReturn(Optional.of(defaultPolicy));

        Transaction tx = Transaction.builder()
                .id("tx_fallback_001")
                .merchant(testMerchant)
                .amountInPaise(10000L)
                .build();

        RiskDecision decision = policyEvaluationService.evaluate(tx, 25.0);

        assertThat(decision.getDecision()).isEqualTo(RiskDecisionType.ALLOW);
        assertThat(decision.getPolicyId()).isEqualTo("pol_default_global");
    }

    @Test
    @DisplayName("Default Policy In-Memory Fallback: Operates safely when no DB policy exists at all")
    void testDefaultPolicyInMemoryFallback() {
        when(riskPolicyRepository.findFirstByMerchantIsNullAndEnabledTrue())
                .thenReturn(Optional.empty());

        Transaction tx = Transaction.builder()
                .id("tx_nodefault_001")
                .amountInPaise(10000L)
                .build();

        RiskDecision decision = policyEvaluationService.evaluate(tx, 94.0);

        assertThat(decision.getDecision()).isEqualTo(RiskDecisionType.BLOCK);
        assertThat(decision.getPolicyId()).isEqualTo("pol_default_baseline");
        assertThat(decision.getReason()).isEqualTo("BLOCK because risk score 94 exceeded block threshold 90.");
    }

    @Test
    @DisplayName("Invalid Policies: Validates threshold mathematical order and bounds")
    void testInvalidPolicyValidation() {
        // 1. low > review
        RiskPolicy invalidLow = RiskPolicy.builder()
                .lowRiskThreshold(60.0)
                .reviewThreshold(50.0)
                .blockThreshold(90.0)
                .build();
        assertThatThrownBy(invalidLow::validateThresholds)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("low_risk_threshold (60.00) cannot exceed review_threshold (50.00)");

        // 2. review > block
        RiskPolicy invalidReview = RiskPolicy.builder()
                .lowRiskThreshold(30.0)
                .reviewThreshold(95.0)
                .blockThreshold(90.0)
                .build();
        assertThatThrownBy(invalidReview::validateThresholds)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("review_threshold (95.00) cannot exceed block_threshold (90.00)");

        // 3. Out of bounds (< 0 or > 100)
        RiskPolicy outOfBounds = RiskPolicy.builder()
                .lowRiskThreshold(-5.0)
                .reviewThreshold(50.0)
                .blockThreshold(110.0)
                .build();
        assertThatThrownBy(outOfBounds::validateThresholds)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Thresholds must be between 0.0 and 100.0");
    }

    @Test
    @DisplayName("Extensible Rule Engine: Custom discrete rules evaluate prior to baseline thresholds")
    void testDiscreteRulesEvaluation() {
        RiskPolicy policyWithRules = RiskPolicy.builder()
                .id("pol_custom_rules")
                .name("Rule Engine Test Policy")
                .lowRiskThreshold(30.0)
                .reviewThreshold(70.0)
                .blockThreshold(90.0)
                .maxSingleTxPaise(10000000L)
                .enabled(true)
                .build();

        RiskPolicyRule hardBlockRule = RiskPolicyRule.builder()
                .id("rule_strict_review")
                .policy(policyWithRules)
                .ruleName("Flag medium velocity transactions")
                .ruleType("THRESHOLD")
                .conditionOperator("GREATER_THAN_OR_EQUAL")
                .conditionValue("45.0")
                .action(RiskDecisionType.BLOCK)
                .priority(10)
                .enabled(true)
                .build();

        when(riskPolicyRuleRepository.findByPolicyIdAndEnabledTrueOrderByPriorityAsc("pol_custom_rules"))
                .thenReturn(List.of(hardBlockRule));

        Transaction tx = Transaction.builder()
                .id("tx_rule_001")
                .amountInPaise(10000L)
                .build();

        // Score 48 would normally be REVIEW under default threshold, but rule forces BLOCK
        RiskDecision decision = policyEvaluationService.evaluateWithPolicy(policyWithRules, tx, 48.0);

        assertThat(decision.getDecision()).isEqualTo(RiskDecisionType.BLOCK);
        assertThat(decision.getReason()).contains("Flag medium velocity transactions");
    }
}
