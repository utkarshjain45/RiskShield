package com.riskshield.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentCreatedEvent extends BaseEvent {

    public static final String EVENT_TYPE = "payment.created";

    @JsonProperty("customer_id")
    private String customerId;

    @JsonProperty("customer_email")
    private String customerEmail;

    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("ip_address")
    private String ipAddress;

    @JsonProperty("amount_in_paise")
    private Long amountInPaise;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("customer_account_age_days")
    private Integer customerAccountAgeDays;

    @JsonProperty("is_new_device")
    private Boolean isNewDevice;

    @JsonProperty("is_new_ip")
    private Boolean isNewIp;
}
