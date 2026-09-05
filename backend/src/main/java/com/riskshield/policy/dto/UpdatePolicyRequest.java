package com.riskshield.policy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePolicyRequest {

    private String name;

    private String policyVersion;

    @DecimalMin(value = "0.0", message = "low_risk_threshold must be at least 0.0")
    @DecimalMax(value = "100.0", message = "low_risk_threshold must not exceed 100.0")
    private Double lowRiskThreshold;

    @DecimalMin(value = "0.0", message = "review_threshold must be at least 0.0")
    @DecimalMax(value = "100.0", message = "review_threshold must not exceed 100.0")
    private Double reviewThreshold;

    @DecimalMin(value = "0.0", message = "block_threshold must be at least 0.0")
    @DecimalMax(value = "100.0", message = "block_threshold must not exceed 100.0")
    private Double blockThreshold;

    @DecimalMin(value = "0.0", message = "false_positive_cost_weight must not be negative")
    private Double falsePositiveCostWeight;

    @Positive(message = "maxSingleTxPaise must be greater than 0")
    private Long maxSingleTxPaise;

    private Boolean enabled;

    @Valid
    private List<PolicyRuleDto> rules;
}
