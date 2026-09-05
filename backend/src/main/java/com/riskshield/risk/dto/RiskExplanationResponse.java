package com.riskshield.risk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class RiskExplanationResponse {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("risk_score")
    private Double riskScore;

    @JsonProperty("fraud_probability")
    private Double fraudProbability;

    private String decision;

    @JsonProperty("model_version")
    private String modelVersion;

    @JsonProperty("top_contributing_features")
    private List<ContributingFeatureDto> topContributingFeatures;

    @JsonProperty("human_readable_explanation")
    private String humanReadableExplanation;

    @JsonProperty("evaluated_at")
    private Instant evaluatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContributingFeatureDto {
        @JsonProperty("feature_name")
        private String featureName;

        @JsonProperty("display_name")
        private String displayName;

        private Double impact;

        @JsonProperty("formatted_impact")
        private String formattedImpact;

        private String direction;

        private String description;
    }
}
