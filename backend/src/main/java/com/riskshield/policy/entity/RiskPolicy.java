package com.riskshield.policy.entity;

import com.riskshield.merchant.entity.Merchant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "risk_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskPolicy {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @Column(length = 128, nullable = false)
    @Builder.Default
    private String name = "Standard Risk Policy";

    @Column(name = "policy_version", length = 32, nullable = false)
    @Builder.Default
    private String policyVersion = "v1.0.0";

    @Column(name = "low_risk_threshold", nullable = false)
    @Builder.Default
    private Double lowRiskThreshold = 30.00;

    @Column(name = "review_threshold", nullable = false)
    @Builder.Default
    private Double reviewThreshold = 70.00;

    @Column(name = "block_threshold", nullable = false)
    @Builder.Default
    private Double blockThreshold = 90.00;

    @Column(name = "false_positive_cost_weight", nullable = false)
    @Builder.Default
    private Double falsePositiveCostWeight = 1.00;

    @Column(name = "max_single_tx_paise", nullable = false)
    @Builder.Default
    private Long maxSingleTxPaise = 10000000L; // 1 Lakh INR

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RiskPolicyRule> rules = new ArrayList<>();

    /**
     * Validates that thresholds obey mathematical consistency:
     * 0.0 <= lowRiskThreshold <= reviewThreshold <= blockThreshold <= 100.0
     */
    public void validateThresholds() {
        if (lowRiskThreshold == null || reviewThreshold == null || blockThreshold == null) {
            throw new IllegalArgumentException("Policy thresholds cannot be null");
        }
        if (lowRiskThreshold < 0.0 || blockThreshold > 100.0) {
            throw new IllegalArgumentException(String.format(
                    "Thresholds must be between 0.0 and 100.0 (received low: %.2f, block: %.2f)",
                    lowRiskThreshold, blockThreshold));
        }
        if (lowRiskThreshold > reviewThreshold) {
            throw new IllegalArgumentException(String.format(
                    "low_risk_threshold (%.2f) cannot exceed review_threshold (%.2f)",
                    lowRiskThreshold, reviewThreshold));
        }
        if (reviewThreshold > blockThreshold) {
            throw new IllegalArgumentException(String.format(
                    "review_threshold (%.2f) cannot exceed block_threshold (%.2f)",
                    reviewThreshold, blockThreshold));
        }
        if (falsePositiveCostWeight != null && falsePositiveCostWeight < 0.0) {
            throw new IllegalArgumentException("false_positive_cost_weight cannot be negative");
        }
    }
}
