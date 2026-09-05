package com.riskshield.policy.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyResponse {

    private String id;
    private String merchantId;
    private String name;
    private String policyVersion;
    private Double lowRiskThreshold;
    private Double reviewThreshold;
    private Double blockThreshold;
    private Double falsePositiveCostWeight;
    private Long maxSingleTxPaise;
    private boolean enabled;
    private Instant createdAt;
    private List<PolicyRuleDto> rules;
}
