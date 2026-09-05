package com.riskshield.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @Column(length = 64)
    private String id;

    private String email;

    @Column(length = 32)
    private String contact;

    @Column(name = "billing_state", length = 64)
    private String billingState;

    @Column(name = "risk_segment", length = 32, nullable = false)
    @Builder.Default
    private String riskSegment = "standard";

    @Column(name = "account_created_at", nullable = false)
    @Builder.Default
    private Instant accountCreatedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
