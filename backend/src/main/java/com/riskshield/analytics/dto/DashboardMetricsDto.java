package com.riskshield.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetricsDto {
    @JsonProperty("today_transactions")
    private long todayTransactions;

    @JsonProperty("suspicious_transactions")
    private long suspiciousTransactions;

    @JsonProperty("blocked_transactions")
    private long blockedTransactions;

    @JsonProperty("review_queue")
    private long reviewQueue;

    @JsonProperty("fraud_exposure_paise")
    private long fraudExposurePaise;

    @JsonProperty("fraud_exposure_inr")
    private double fraudExposureInr;

    @JsonProperty("prevented_loss_paise")
    private long preventedLossPaise;

    @JsonProperty("prevented_loss_inr")
    private double preventedLossInr;

    @JsonProperty("fraud_rate")
    private double fraudRate; // e.g. 2.45%

    @JsonProperty("critical_incidents")
    private long criticalIncidents;
}
