package com.riskshield.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RiskScoredEvent extends BaseEvent {

    public static final String EVENT_TYPE = "risk.scored";

    @JsonProperty("fraud_probability")
    private Double fraudProbability;

    @JsonProperty("risk_score")
    private Double riskScore;

    @JsonProperty("model_version")
    private String modelVersion;

    @JsonProperty("inference_latency_ms")
    private Double inferenceLatencyMs;

    @JsonProperty("top_risk_signals")
    private List<SignalDto> topRiskSignals;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalDto {
        @JsonProperty("feature_name")
        private String featureName;

        @JsonProperty("feature_value")
        private String featureValue;

        @JsonProperty("shap_impact")
        private Double shapImpact;

        @JsonProperty("direction")
        private String direction;

        @JsonProperty("description")
        private String description;
    }
}
