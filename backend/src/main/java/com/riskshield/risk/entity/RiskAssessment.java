package com.riskshield.risk.entity;

import com.riskshield.transaction.entity.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "risk_assessments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskAssessment {

    @Id
    @Column(length = 64)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private Transaction transaction;

    @Column(name = "model_version", length = 64, nullable = false)
    private String modelVersion;

    @Column(name = "fraud_probability", nullable = false)
    private Double fraudProbability;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore; // 0.00 to 100.00

    @Column(name = "inference_latency_ms", nullable = false)
    @Builder.Default
    private Double inferenceLatencyMs = 0.0;

    @Column(name = "prediction_timestamp", nullable = false)
    @Builder.Default
    private Instant predictionTimestamp = Instant.now();

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<RiskSignal> signals = new ArrayList<>();

    public void addSignal(RiskSignal signal) {
        signals.add(signal);
        signal.setAssessment(this);
    }
}
