package com.riskshield.audit.entity;

import com.riskshield.transaction.entity.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_id", length = 64, nullable = false, unique = true, updatable = false)
    private String auditId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 64, nullable = false, updatable = false)
    @Builder.Default
    private AuditEventType eventType = AuditEventType.TRANSACTION_RECEIVED;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 32, nullable = false, updatable = false)
    @Builder.Default
    private ActorType actorType = ActorType.SYSTEM;

    @Column(name = "actor_id", length = 64, nullable = false, updatable = false)
    @Builder.Default
    private String actorId = "SYSTEM";

    @Column(name = "merchant_id", length = 64, updatable = false)
    private String merchantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", updatable = false)
    private Transaction transaction;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(length = 64, nullable = false, updatable = false)
    @Builder.Default
    private String service = "backend-api";

    @Column(name = "payload_hash", length = 64, updatable = false)
    private String payloadHash;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String metadata;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String details;

    @Column(name = "entity_type", length = 64, nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", length = 64, nullable = false, updatable = false)
    private String entityId;

    @Column(length = 64, nullable = false, updatable = false)
    private String action;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    public void ensureDefaults() {
        if (this.auditId == null || this.auditId.isBlank()) {
            this.auditId = "aud_" + UUID.randomUUID().toString().replace("-", "");
        }
        if (this.eventType == null) {
            this.eventType = AuditEventType.TRANSACTION_RECEIVED;
        }
        if (this.actorType == null) {
            this.actorType = ActorType.SYSTEM;
        }
        if (this.service == null || this.service.isBlank()) {
            this.service = "backend-api";
        }
        if (this.action == null && this.eventType != null) {
            this.action = this.eventType.name();
        }
        if (this.entityType == null) {
            this.entityType = "RiskEvent";
        }
        if (this.entityId == null) {
            this.entityId = this.auditId;
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.merchantId == null && this.transaction != null && this.transaction.getMerchant() != null) {
            this.merchantId = this.transaction.getMerchant().getId();
        }
    }

    public Instant getTimestamp() {
        return this.createdAt;
    }

    public String getTransactionId() {
        return this.transaction != null ? this.transaction.getId() : null;
    }

    @PreUpdate
    public void preventUpdate() {
        throw new UnsupportedOperationException("Audit events are immutable and cannot be updated once recorded");
    }

    @PreRemove
    public void preventDelete() {
        throw new UnsupportedOperationException("Audit events are immutable and cannot be deleted");
    }
}
