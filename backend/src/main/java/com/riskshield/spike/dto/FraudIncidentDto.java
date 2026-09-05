package com.riskshield.spike.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.riskshield.spike.entity.FraudIncident;
import com.riskshield.spike.entity.IncidentSeverity;
import com.riskshield.spike.entity.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class FraudIncidentDto {

    private String incidentId;
    private String merchantId;
    private IncidentSeverity severity;
    private Instant detectedAt;
    private Double baselineRate;
    private Double currentRate;
    private Double percentageIncrease;
    private Long affectedTransactions;
    private Long estimatedExposure;
    private IncidentStatus status;
    private String timeWindow;
    private Double zScore;
    private String explanationSummary;
    private Instant acknowledgedAt;
    private Instant resolvedAt;

    public static FraudIncidentDto fromEntity(FraudIncident entity) {
        if (entity == null) {
            return null;
        }
        return FraudIncidentDto.builder()
                .incidentId(entity.getIncidentId())
                .merchantId(entity.getMerchant() != null ? entity.getMerchant().getId() : entity.getMerchantId())
                .severity(entity.getSeverity())
                .detectedAt(entity.getDetectedAt())
                .baselineRate(entity.getBaselineRate())
                .currentRate(entity.getCurrentRate())
                .percentageIncrease(entity.getPercentageIncrease())
                .affectedTransactions(entity.getAffectedTransactions())
                .estimatedExposure(entity.getEstimatedExposure())
                .status(entity.getStatus())
                .timeWindow(entity.getTimeWindow())
                .zScore(entity.getZScore())
                .explanationSummary(entity.getExplanationSummary())
                .acknowledgedAt(entity.getAcknowledgedAt())
                .resolvedAt(entity.getResolvedAt())
                .build();
    }
}
