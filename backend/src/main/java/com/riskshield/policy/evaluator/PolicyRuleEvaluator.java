package com.riskshield.policy.evaluator;

import com.riskshield.policy.entity.RiskPolicyRule;

/**
 * Extensible strategy interface for evaluating policy rules.
 * Enables adding new rule types (e.g., Velocity, Geolocation, Device trust)
 * without modifying the core PolicyEvaluationService.
 */
public interface PolicyRuleEvaluator {

    /**
     * Checks if this evaluator supports the specified rule type.
     */
    boolean supports(String ruleType);

    /**
     * Evaluates a rule against the provided transaction and risk context.
     */
    RuleEvaluationResult evaluate(RiskPolicyRule rule, EvaluationContext context);
}
