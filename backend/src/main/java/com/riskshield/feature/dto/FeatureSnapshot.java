package com.riskshield.feature.dto;

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
public class FeatureSnapshot {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("customer_velocity")
    private CustomerVelocityDto customerVelocity;

    @JsonProperty("device_velocity")
    private DeviceVelocityDto deviceVelocity;

    @JsonProperty("ip_velocity")
    private IpVelocityDto ipVelocity;

    @JsonProperty("amount_velocity")
    private Long amountVelocity; // Amount in paise in the last hour

    @JsonProperty("device_account_count")
    private Long deviceAccountCount;

    @JsonProperty("ip_account_count")
    private Long ipAccountCount;

    @JsonProperty("is_new_device")
    private Boolean isNewDevice;

    @JsonProperty("is_new_ip")
    private Boolean isNewIp;

    @JsonProperty("captured_at")
    @Builder.Default
    private Instant capturedAt = Instant.now();
}
