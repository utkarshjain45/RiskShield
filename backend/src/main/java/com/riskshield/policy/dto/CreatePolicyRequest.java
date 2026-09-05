package com.riskshield.policy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePolicyRequest {

    private String merchantId; // Optional: null means default/global policy

    @NotBlank(message = "Policy name is required")
    private String name;

    @Builder.Default
    private String policyVersion = "v1.0.0";

    @NotNull(message = "low_risk_threshold is required")
    @DecimalMin(value = "0.0", message = "low_risk_threshold must be at least 0.0")
    @DecimalMax(value = "100.0", message = "low_risk_threshold must not exceed 100.0")
    private Double lowRiskThreshold;

    @NotNull(message = "review_threshold is required")
    @DecimalMin(value = "0.0", message = "review_threshold must be at least 0.0")
    @DecimalMax(value = "100.0", message = "review_threshold must not exceed 100.0")
    private Double reviewThreshold;

    @NotNull(message = "block_threshold is required")
    @DecimalMin(value = "0.0", message = "block_threshold must be at least 0.0")
    @DecimalMax(value = "100.0", message = "block_threshold must not exceed 100.0")
    private Double blockThreshold;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "false_positive_cost_weight must not be negative")
    private Double falsePositiveCostWeight = 1.00;

    @Builder.Default
    @Positive(message = "maxSingleTxPaise must be greater than 0")
    private Long maxSingleTxPaise = 10000000L;

    @Builder.Default
    private boolean enabled = true;

    @Valid
    private List<PolicyRuleDto> rules;
}
