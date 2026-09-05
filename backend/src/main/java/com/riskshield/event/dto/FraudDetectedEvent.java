package com.riskshield.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.riskshield.common.enums.RiskDecisionType;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FraudDetectedEvent extends BaseEvent {

    public static final String EVENT_TYPE = "fraud.detected";

    @JsonProperty("risk_score")
    private Double riskScore;

    @JsonProperty("decision")
    private RiskDecisionType decision;

    @JsonProperty("fraud_severity")
    private String fraudSeverity; // CRITICAL, HIGH, MEDIUM

    @JsonProperty("trigger_reason")
    private String triggerReason;
}
