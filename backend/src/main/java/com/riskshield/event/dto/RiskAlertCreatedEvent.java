package com.riskshield.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RiskAlertCreatedEvent extends BaseEvent {

    public static final String EVENT_TYPE = "risk.alert.created";

    @JsonProperty("alert_id")
    private String alertId;

    @JsonProperty("alert_type")
    private String alertType;

    @JsonProperty("severity")
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL

    @JsonProperty("status")
    private String status; // OPEN

    @JsonProperty("details")
    private String details;
}
