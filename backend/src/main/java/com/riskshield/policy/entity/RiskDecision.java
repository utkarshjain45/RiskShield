package com.riskshield.policy.entity;

import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.transaction.entity.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "risk_decisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskDecision {

    @Id
    @Column(length = 64)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private Transaction transaction;

    @Column(name = "risk_score", nullable = false, updatable = false)
    private Double riskScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false, updatable = false)
    private RiskDecisionType decision; // ALLOW, REVIEW, BLOCK

    @Column(name = "policy_id", length = 64, updatable = false)
    private String policyId;

    @Column(name = "policy_version", length = 32, nullable = false, updatable = false)
    private String policyVersion;

    @Column(columnDefinition = "TEXT", nullable = false, updatable = false)
    private String reason;

    @Column(name = "decision_reason", columnDefinition = "TEXT", updatable = false)
    private String decisionReason;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant evaluatedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    public void syncReasonAndTimestamps() {
        if (this.decisionReason == null && this.reason != null) {
            this.decisionReason = this.reason;
        } else if (this.reason == null && this.decisionReason != null) {
            this.reason = this.decisionReason;
        }
        if (this.evaluatedAt == null) {
            this.evaluatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void preventUpdate() {
        throw new UnsupportedOperationException("Risk decisions are immutable and cannot be updated once recorded");
    }

    @PreRemove
    public void preventDelete() {
        throw new UnsupportedOperationException("Risk decisions are immutable and cannot be deleted");
    }
}
