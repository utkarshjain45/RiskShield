package com.riskshield.risk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentEvaluationResponse {

    @JsonProperty("evaluation_id")
    private String evaluationId;

    @JsonProperty("model_version")
    private String modelVersion;

    @JsonProperty("dataset_version")
    private String datasetVersion;

    @JsonProperty("test_set_version")
    private String testSetVersion;

    @JsonProperty("evaluation_label")
    @Builder.Default
    private String evaluationLabel = "Final evaluation on held-out test set";

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("dataset_size")
    private long datasetSize;

    @JsonProperty("fraud_count")
    private long fraudCount;

    @JsonProperty("non_fraud_count")
    private long nonFraudCount;

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
