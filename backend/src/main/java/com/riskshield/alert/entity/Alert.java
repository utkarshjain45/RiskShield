package com.riskshield.alert.entity;

import com.riskshield.merchant.entity.Merchant;
import com.riskshield.transaction.entity.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "alert_type", length = 64, nullable = false)
    private String alertType;

    @Column(length = 16, nullable = false)
    @Builder.Default
    private String severity = "HIGH"; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(length = 32, nullable = false)
    @Builder.Default
    private String status = "OPEN";   // OPEN, ACKNOWLEDGED, RESOLVED

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
