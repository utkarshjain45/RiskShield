package com.riskshield.spike.entity;

import com.riskshield.merchant.entity.Merchant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "fraud_incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudIncident {

    @Id
    @Column(name = "incident_id", length = 64)
    private String incidentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private IncidentSeverity severity;

    @Column(name = "detected_at", nullable = false)
    @Builder.Default
    private Instant detectedAt = Instant.now();

    @Column(name = "baseline_rate", nullable = false)
    private Double baselineRate;

    @Column(name = "current_rate", nullable = false)
    private Double currentRate;

    @Column(name = "percentage_increase", nullable = false)
    private Double percentageIncrease;

    @Column(name = "affected_transactions", nullable = false)
    private Long affectedTransactions;

    @Column(name = "estimated_exposure", nullable = false)
    private Long estimatedExposure; // In paise

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    @Builder.Default
    private IncidentStatus status = IncidentStatus.OPEN;

    @Column(name = "time_window", length = 16, nullable = false)
    @Builder.Default
    private String timeWindow = "15m";

    @Column(name = "z_score")
    private Double zScore;

    @Column(name = "explanation_summary", columnDefinition = "TEXT")
    private String explanationSummary;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public String getMerchantId() {
        return merchant != null ? merchant.getId() : null;
    }
}
