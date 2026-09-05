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
public class RiskDecisionedEvent extends BaseEvent {

    public static final String EVENT_TYPE = "risk.decisioned";

    @JsonProperty("risk_score")
    private Double riskScore;

    @JsonProperty("decision")
    private RiskDecisionType decision; // ALLOW, REVIEW, BLOCK

    @JsonProperty("policy_id")
    private String policyId;

    @JsonProperty("policy_version")
    private String policyVersion;

    @JsonProperty("decision_reason")
    private String decisionReason;
}
