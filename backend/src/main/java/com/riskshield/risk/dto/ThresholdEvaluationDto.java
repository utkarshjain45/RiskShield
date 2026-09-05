package com.riskshield.risk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdEvaluationDto {

    @JsonProperty("threshold")
    private double threshold;

    @JsonProperty("precision")
    private double precision;

    @JsonProperty("recall")
    private double recall;

    @JsonProperty("f1")
    private double f1;

    @JsonProperty("roc_auc")
    private double rocAuc;

    @JsonProperty("pr_auc")
    private double prAuc;

    @JsonProperty("false_positive_rate")
    private double falsePositiveRate;

    @JsonProperty("false_negative_rate")
    private double falseNegativeRate;

    @JsonProperty("confusion_matrix")
    private ConfusionMatrixDto confusionMatrix;

    @JsonProperty("false_positive_cost")
    private double falsePositiveCost;

    @JsonProperty("false_negative_cost")
    private double falseNegativeCost;

    @JsonProperty("estimated_prevented_loss")
    private double estimatedPreventedLoss;
}
