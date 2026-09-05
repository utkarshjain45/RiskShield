package com.riskshield.policy.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluatePolicyRequest {

    private String transactionId;

    @NotNull(message = "riskScore is required")
    @DecimalMin(value = "0.0", message = "riskScore must be at least 0.0")
    @DecimalMax(value = "100.0", message = "riskScore must not exceed 100.0")
    private Double riskScore;

    private Long amountInPaise;
}
