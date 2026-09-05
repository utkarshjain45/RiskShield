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
public class ConfusionMatrixDto {

    @JsonProperty("true_negatives")
    private long trueNegatives;

    @JsonProperty("false_positives")
    private long falsePositives;

    @JsonProperty("false_negatives")
    private long falseNegatives;

    @JsonProperty("true_positives")
    private long truePositives;
}
