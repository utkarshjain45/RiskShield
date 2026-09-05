package com.riskshield.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.riskshield.audit.entity.ActorType;
import com.riskshield.audit.entity.AuditEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventResponse {

    private Long id;

    @JsonProperty("audit_id")
    private String auditId;

    @JsonProperty("event_type")
    private AuditEventType eventType;

    @JsonProperty("actor_type")
    private ActorType actorType;

    @JsonProperty("actor_id")
    private String actorId;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("correlation_id")
    private String correlationId;

    private String service;

    @JsonProperty("payload_hash")
    private String payloadHash;

    private String metadata;

    private String details;

    @JsonProperty("entity_type")
    private String entityType;

    @JsonProperty("entity_id")
    private String entityId;

    private String action;

    private Instant timestamp;

    @JsonProperty("created_at")
    private Instant createdAt;
}
