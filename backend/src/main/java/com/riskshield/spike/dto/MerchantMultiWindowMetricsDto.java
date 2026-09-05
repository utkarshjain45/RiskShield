package com.riskshield.spike.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MerchantMultiWindowMetricsDto {

    private String merchantId;
    private Instant calculatedAt;

    @JsonProperty("metrics_5m")
    private TimeWindowMetrics metrics5m;

    @JsonProperty("metrics_15m")
    private TimeWindowMetrics metrics15m;

    @JsonProperty("metrics_1h")
    private TimeWindowMetrics metrics1h;

    @JsonProperty("metrics_6h")
    private TimeWindowMetrics metrics6h;

    @JsonProperty("metrics_24h")
    private TimeWindowMetrics metrics24h;

    @JsonProperty("all_windows")
    private Map<String, TimeWindowMetrics> allWindows;
}
