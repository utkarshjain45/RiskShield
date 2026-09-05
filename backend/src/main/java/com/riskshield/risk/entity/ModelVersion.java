package com.riskshield.risk.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "model_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelVersion {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "version_name", length = 64, unique = true, nullable = false)
    private String versionName;

    @Column(length = 32, nullable = false)
    @Builder.Default
    private String framework = "xgboost";

    @Column(name = "optimal_threshold", nullable = false)
    @Builder.Default
    private Double optimalThreshold = 0.0500;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
