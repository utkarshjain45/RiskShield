package com.riskshield.policy.evaluator;

import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.policy.entity.RiskPolicyRule;
import com.riskshield.transaction.entity.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AmountLimitRuleEvaluator implements PolicyRuleEvaluator {

    @Override
    public boolean supports(String ruleType) {
        return "AMOUNT_LIMIT".equalsIgnoreCase(ruleType);
    }

    @Override
    public RuleEvaluationResult evaluate(RiskPolicyRule rule, EvaluationContext context) {
        Transaction tx = context.getTransaction();
        if (tx == null || tx.getAmountInPaise() == null) {
            return RuleEvaluationResult.notTriggered();
        }

        long limitPaise;
        try {
            limitPaise = Long.parseLong(rule.getConditionValue());
        } catch (NumberFormatException e) {
            log.warn("Invalid numerical amount conditionValue '{}' in rule {}", rule.getConditionValue(), rule.getId());
            return RuleEvaluationResult.notTriggered();
        }

        long txAmountPaise = tx.getAmountInPaise();
        String operator = rule.getConditionOperator();
        boolean conditionMet = switch (operator.toUpperCase()) {
            case "GREATER_THAN_OR_EQUAL", "GTE", ">=" -> txAmountPaise >= limitPaise;
            case "GREATER_THAN", "GT", ">" -> txAmountPaise > limitPaise;
            case "LESS_THAN_OR_EQUAL", "LTE", "<=" -> txAmountPaise <= limitPaise;
            case "LESS_THAN", "LT", "<" -> txAmountPaise < limitPaise;
            case "EQUALS", "EQ", "==" -> txAmountPaise == limitPaise;
            default -> false;
        };

        if (conditionMet) {
            String reason = String.format("%s because transaction amount (₹%.2f) %s limit (₹%.2f) (Rule: %s).",
                    rule.getAction(), txAmountPaise / 100.0, operator.toLowerCase().replace("_", " "), limitPaise / 100.0, rule.getRuleName());
            return RuleEvaluationResult.triggered(rule.getAction(), reason);
        }

        return RuleEvaluationResult.notTriggered();
    }
}
