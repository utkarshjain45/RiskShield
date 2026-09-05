package com.riskshield.policy.entity;

import com.riskshield.common.enums.RiskDecisionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "risk_policy_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskPolicyRule {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private RiskPolicy policy;

    @Column(name = "rule_name", length = 128, nullable = false)
    private String ruleName;

    @Column(name = "rule_type", length = 64, nullable = false)
    @Builder.Default
    private String ruleType = "THRESHOLD";

    @Column(name = "condition_operator", length = 32, nullable = false)
    private String conditionOperator;

    @Column(name = "condition_value", length = 128, nullable = false)
    private String conditionValue;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private RiskDecisionType action;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 100;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
