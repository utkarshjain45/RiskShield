package com.riskshield.policy.dto;

import com.riskshield.common.enums.RiskDecisionType;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyEvaluationResponse {

    private String transactionId;
    private String policyId;
    private String policyVersion;
    private Double riskScore;
    private RiskDecisionType decision;
    private String decisionReason;
    private Instant evaluatedAt;
}
