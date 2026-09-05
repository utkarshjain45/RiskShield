package com.riskshield.feature.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerVelocityDto {

    @JsonProperty("transactions_5m")
    private long transactions5m;

    @JsonProperty("transactions_30m")
    private long transactions30m;

    @JsonProperty("transactions_1h")
    private long transactions1h;

    @JsonProperty("amount_1h")
    private long amount1h; // In paise
}
