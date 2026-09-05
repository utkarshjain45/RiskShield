package com.riskshield.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelEvaluationDto {
    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("model_version")
    private String modelVersion;

    private String framework;

    @JsonProperty("optimal_threshold")
    private double optimalThreshold;

    private double precision;
    private double recall;
    private double f1;

    @JsonProperty("pr_auc")
    private double prAuc;

    @JsonProperty("roc_auc")
    private double rocAuc;

    @JsonProperty("false_positive_rate")
    private double falsePositiveRate;

    @JsonProperty("confusion_matrix")
    private Map<String, Long> confusionMatrix;

    @JsonProperty("monetary_impact")
    private Map<String, Object> monetaryImpact;

    @JsonProperty("threshold_analysis")
    private Object thresholdAnalysis;
}
