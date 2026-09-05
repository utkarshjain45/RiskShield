package com.riskshield.risk.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Immutable evaluation run record on the held-out test set.
 * Stored as an append-only audit trail of model quality benchmarks.
 * Direct modification or tuning against this test set is strictly prohibited.
 */
@Entity
@Table(name = "model_evaluation_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ModelEvaluationRun {

    @Id
    @Column(name = "evaluation_id", length = 64, nullable = false, updatable = false)
    private String evaluationId;

    @Column(name = "model_version", length = 64, nullable = false, updatable = false)
    private String modelVersion;

    @Column(name = "dataset_version", length = 64, nullable = false, updatable = false)
    private String datasetVersion;

    @Column(name = "test_set_version", length = 64, nullable = false, updatable = false)
    private String testSetVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "metrics", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String metrics;
}
