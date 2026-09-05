package com.riskshield.risk.dto;

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
public class RiskAssessmentResponse {

    private String id;
    private String transactionId;
    private String modelVersion;
    private Double fraudProbability;
    private Double riskScore; // 0 - 100
    private Double inferenceLatencyMs;
    private Instant predictionTimestamp;

    // Contributing Signals
    private List<RiskSignalDto> contributingSignals;

    // Policy Decision
    private RiskDecisionType decision; // ALLOW, REVIEW, BLOCK
    private String policyVersion;
    private String policyReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskSignalDto {
        private String signalName;
        private String signalValue;
        private Double shapImpact;
        private String direction;
        private String description;
        private String displayName;
        private String formattedImpact;
    }
}
