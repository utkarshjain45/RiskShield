package com.riskshield.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.riskshield.demo.entity.SimulationMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationRequest {

    @NotNull(message = "Simulation mode is required")
    @JsonProperty("mode")
    private SimulationMode mode;

    @JsonProperty("merchant_id")
    private String merchantId;

    @Min(value = 5, message = "Transaction count must be at least 5")
    @Max(value = 300, message = "Transaction count cannot exceed 300")
    @JsonProperty("transaction_count")
    @Builder.Default
    private Integer transactionCount = 30;

    @Min(value = 50, message = "Interval must be at least 50ms")
    @Max(value = 5000, message = "Interval cannot exceed 5000ms")
    @JsonProperty("interval_ms")
    @Builder.Default
    private Integer intervalMs = 350;
}
