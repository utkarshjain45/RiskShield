package com.riskshield.razorpay.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "razorpay_webhook_receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayWebhookReceipt {

    @Id
    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "amount_in_paise")
    private Long amountInPaise;

    @Column(length = 16)
    @Builder.Default
    private String currency = "INR";

    @Column(length = 32, nullable = false)
    @Builder.Default
    private String status = "RECEIVED";

    @Column(name = "signature_verified", nullable = false)
    @Builder.Default
    private boolean signatureVerified = true;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
