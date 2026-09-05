package com.riskshield.assistant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ai_investigation_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiInvestigationSession {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "merchant_id", length = 64)
    private String merchantId;

    @Column(nullable = false)
    private String title;

    @Column(name = "user_id", length = 64, nullable = false)
    @Builder.Default
    private String userId = "ANALYST_DEFAULT";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
