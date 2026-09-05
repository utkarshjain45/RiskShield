package com.riskshield.spike.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.riskshield.spike.entity.IncidentSeverity;
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
public class SpikeDetectionResultDto {

    private String merchantId;
    private IncidentSeverity severity;
    private Instant detectedAt;
    private String timeWindow;
    private double baselineRate;
    private String baselineRateFormatted;
    private double currentRate;
    private String currentRateFormatted;
    private double percentageIncrease;
    private String percentageIncreaseFormatted;
    private long affectedTransactions;
    private long estimatedExposurePaise;
    private double estimatedExposureInr;
    private String estimatedExposureFormatted;
    private double zScore;
    private String explanationNarrative;
    private String incidentId;
}
