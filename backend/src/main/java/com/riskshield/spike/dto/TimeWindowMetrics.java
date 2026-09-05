package com.riskshield.spike.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TimeWindowMetrics {

    private String window;
    private long totalTransactions;
    private long suspiciousTransactions;
    private long blockedTransactions;
    private long reviewTransactions;
    private double fraudRate;
    private String fraudRatePercentage;
    private long averageTransactionAmountPaise;
    private double averageTransactionAmountInr;
    private long fraudExposurePaise;
    private double fraudExposureInr;
    private String fraudExposureFormatted;
}
