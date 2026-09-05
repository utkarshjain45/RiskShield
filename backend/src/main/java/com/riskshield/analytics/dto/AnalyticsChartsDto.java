package com.riskshield.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsChartsDto {

    @JsonProperty("fraud_rate_over_time")
    private List<TimePointDto> fraudRateOverTime;

    @JsonProperty("transaction_volume")
    private List<VolumePointDto> transactionVolume;

    @JsonProperty("risk_distribution")
    private List<RiskBucketDto> riskDistribution;

    @JsonProperty("decision_distribution")
    private List<DecisionShareDto> decisionDistribution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimePointDto {
        private String time;
        @JsonProperty("fraud_rate")
        private double fraudRate;
        private long total;
        private long fraud;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumePointDto {
        private String time;
        private long volume;
        @JsonProperty("amount_inr")
        private double amountInr;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskBucketDto {
        private String range;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecisionShareDto {
        private String name;
        private long count;
        private double percentage;
    }
}
