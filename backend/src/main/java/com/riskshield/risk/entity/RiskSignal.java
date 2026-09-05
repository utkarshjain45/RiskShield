package com.riskshield.risk.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "risk_signals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private RiskAssessment assessment;

    @Column(name = "signal_name", length = 128, nullable = false)
    private String signalName;

    @Column(name = "signal_value")
    private String signalValue;

    @Column(name = "shap_impact")
    private Double shapImpact;

    @Column(length = 32, nullable = false)
    @Builder.Default
    private String direction = "INCREASES_RISK";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "formatted_impact", length = 32)
    private String formattedImpact;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
