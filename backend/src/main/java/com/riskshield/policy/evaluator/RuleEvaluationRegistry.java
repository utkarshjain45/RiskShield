package com.riskshield.policy.evaluator;

import com.riskshield.policy.entity.RiskPolicyRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEvaluationRegistry {

    private final List<PolicyRuleEvaluator> evaluators;

    public Optional<RuleEvaluationResult> evaluateRule(RiskPolicyRule rule, EvaluationContext context) {
        if (!rule.isEnabled()) {
            return Optional.empty();
        }

        for (PolicyRuleEvaluator evaluator : evaluators) {
            if (evaluator.supports(rule.getRuleType())) {
                RuleEvaluationResult result = evaluator.evaluate(rule, context);
                if (result.isTriggered()) {
                    return Optional.of(result);
                }
            }
        }
        return Optional.empty();
    }
}
