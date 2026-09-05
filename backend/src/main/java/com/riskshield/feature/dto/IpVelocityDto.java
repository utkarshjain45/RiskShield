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
public class IpVelocityDto {

    @JsonProperty("transactions_5m")
    private long transactions5m;

    @JsonProperty("transactions_1h")
    private long transactions1h;

    @JsonProperty("account_count_1h")
    private long accountCount1h;
}
