package com.riskshield.policy.evaluator;

import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.policy.entity.RiskPolicyRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ThresholdRuleEvaluator implements PolicyRuleEvaluator {

    @Override
    public boolean supports(String ruleType) {
        return "THRESHOLD".equalsIgnoreCase(ruleType);
    }

    @Override
    public RuleEvaluationResult evaluate(RiskPolicyRule rule, EvaluationContext context) {
        Double riskScore = context.getRiskScore();
        if (riskScore == null) {
            return RuleEvaluationResult.notTriggered();
        }

        double thresholdValue;
        try {
            thresholdValue = Double.parseDouble(rule.getConditionValue());
        } catch (NumberFormatException e) {
            log.warn("Invalid numerical conditionValue '{}' in policy rule {}", rule.getConditionValue(), rule.getId());
            return RuleEvaluationResult.notTriggered();
        }

        String operator = rule.getConditionOperator();
        boolean conditionMet = switch (operator.toUpperCase()) {
            case "GREATER_THAN_OR_EQUAL", "GTE", ">=" -> riskScore >= thresholdValue;
            case "GREATER_THAN", "GT", ">" -> riskScore > thresholdValue;
            case "LESS_THAN_OR_EQUAL", "LTE", "<=" -> riskScore <= thresholdValue;
            case "LESS_THAN", "LT", "<" -> riskScore < thresholdValue;
            case "EQUALS", "EQ", "==" -> Math.abs(riskScore - thresholdValue) < 0.001;
            default -> false;
        };

        if (conditionMet) {
            String reason = String.format("%s because risk score %.2f %s threshold %.2f (Rule: %s).",
                    rule.getAction(), riskScore, operator.toLowerCase().replace("_", " "), thresholdValue, rule.getRuleName());
            return RuleEvaluationResult.triggered(rule.getAction(), reason);
        }

        return RuleEvaluationResult.notTriggered();
    }
}
