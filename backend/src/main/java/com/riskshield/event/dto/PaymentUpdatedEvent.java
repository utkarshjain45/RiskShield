package com.riskshield.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentUpdatedEvent extends BaseEvent {

    public static final String EVENT_TYPE = "payment.updated";

    @JsonProperty("previous_status")
    private String previousStatus;

    @JsonProperty("new_status")
    private String newStatus;

    @JsonProperty("update_reason")
    private String updateReason;
}
