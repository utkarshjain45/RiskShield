package com.riskshield.event.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "consumer_group", length = 64, nullable = false)
    @Builder.Default
    private String consumerGroup = "riskshield-backend-group";

    @Column(name = "processed_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant processedAt = Instant.now();
}
