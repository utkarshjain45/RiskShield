package com.riskshield.risk.client.dto;

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
public class MlRiskScoreResponse {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("fraud_probability")
    private Double fraudProbability;

    @JsonProperty("risk_score")
    private Double riskScore; // 0 - 100

    @JsonProperty("model_version")
    private String modelVersion;

    @JsonProperty("top_risk_signals")
    private List<MlRiskSignalDto> topRiskSignals;

    private String timestamp;

    @JsonProperty("inference_latency_ms")
    private Double inferenceLatencyMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MlRiskSignalDto {
        @JsonProperty("feature_name")
        private String featureName;

        @JsonProperty("feature_value")
        private Object featureValue;

        @JsonProperty("shap_impact")
        private Double shapImpact;

        private String direction;
        private String description;

        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("formatted_impact")
        private String formattedImpact;
    }
}
