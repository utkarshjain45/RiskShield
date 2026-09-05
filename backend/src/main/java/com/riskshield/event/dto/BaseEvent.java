package com.riskshield.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("event_version")
    private String eventVersion;

    @JsonProperty("occurred_at")
    private Instant occurredAt;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("correlation_id")
    private String correlationId;

    public void initDefaultsIfMissing(String defaultEventType) {
        if (this.eventId == null || this.eventId.isBlank()) {
            this.eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        }
        if (this.eventType == null || this.eventType.isBlank()) {
            this.eventType = defaultEventType;
        }
        if (this.eventVersion == null || this.eventVersion.isBlank()) {
            this.eventVersion = "v1.0.0";
        }
        if (this.occurredAt == null) {
            this.occurredAt = Instant.now();
        }
        if (this.correlationId == null || this.correlationId.isBlank()) {
            this.correlationId = "corr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
    }
}
