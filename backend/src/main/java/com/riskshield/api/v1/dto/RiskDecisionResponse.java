package com.riskshield.api.v1.dto;

import com.riskshield.common.enums.RiskDecisionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskDecisionResponse {

    private String decisionId;
    private String transactionId;
    private RiskDecisionType decision; // ALLOW, REVIEW, BLOCK
    private Double riskScore;          // Range [0.0, 1.0]
    private String modelVersion;
    private String policyVersion;
    private String policyRuleTriggered;
    private List<ContributingSignalDto> contributingSignals;
    private boolean spikeDetected;
    private Long executionTimeMs;
    private Instant evaluatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContributingSignalDto {
        private String signalName;
        private Double signalValue;
        private Double shapImpact;
        private String description;
    }
}
