package com.riskshield.policy.evaluator;

import com.riskshield.policy.entity.RiskPolicy;
import com.riskshield.transaction.entity.Transaction;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EvaluationContext {
    private final Transaction transaction;
    private final Double riskScore;
    private final RiskPolicy policy;
}
