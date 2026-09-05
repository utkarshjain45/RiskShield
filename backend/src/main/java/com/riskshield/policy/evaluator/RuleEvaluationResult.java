package com.riskshield.policy.evaluator;

import com.riskshield.common.enums.RiskDecisionType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RuleEvaluationResult {
    private final boolean triggered;
    private final RiskDecisionType action;
    private final String reason;

    public static RuleEvaluationResult notTriggered() {
        return RuleEvaluationResult.builder()
                .triggered(false)
                .build();
    }

    public static RuleEvaluationResult triggered(RiskDecisionType action, String reason) {
        return RuleEvaluationResult.builder()
                .triggered(true)
                .action(action)
                .reason(reason)
                .build();
    }
}
