package com.riskshield.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "demo_simulations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemoSimulation {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 64, nullable = false)
    private SimulationMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private SimulationStatus status;

    @Column(name = "merchant_id", length = 64, nullable = false)
    private String merchantId;

    @Column(name = "target_count", nullable = false)
    private Integer targetCount;

    @Column(name = "generated_count", nullable = false)
    @Builder.Default
    private Integer generatedCount = 0;

    @Column(name = "allowed_count", nullable = false)
    @Builder.Default
    private Integer allowedCount = 0;

    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    @Column(name = "blocked_count", nullable = false)
    @Builder.Default
    private Integer blockedCount = 0;

    @Column(name = "interval_ms", nullable = false)
    @Builder.Default
    private Integer intervalMs = 300;

    @Column(name = "active_incident_id", length = 64)
    private String activeIncidentId;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public double calculateFraudRate() {
        if (generatedCount == null || generatedCount == 0) {
            return 0.0;
        }
        int suspiciousOrBlocked = (reviewCount != null ? reviewCount : 0) + (blockedCount != null ? blockedCount : 0);
        return (double) suspiciousOrBlocked / generatedCount;
    }
}
