package com.riskshield.razorpay.service;

import com.riskshield.event.dto.PaymentCreatedEvent;
import com.riskshield.event.dto.PaymentUpdatedEvent;
import com.riskshield.event.producer.PaymentEventProducer;
import com.riskshield.razorpay.config.RazorpayProperties;
import com.riskshield.razorpay.dto.RazorpayParsedEvent;
import com.riskshield.razorpay.dto.RazorpayReplayRequest;
import com.riskshield.razorpay.dto.RazorpayWebhookResponse;
import com.riskshield.razorpay.entity.RazorpayWebhookReceipt;
import com.riskshield.razorpay.repository.RazorpayWebhookReceiptRepository;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates incoming Razorpay Test Mode Webhooks:
 * 1. Signature validation (HMAC-SHA256)
 * 2. Event-id idempotency
 * 3. Webhook receipt metadata persistence
 * 4. Asynchronous Kafka risk assessment dispatch
 * 5. Out-of-order event safe reconciliation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayWebhookService {

    private final RazorpayProperties razorpayProperties;
    private final RazorpaySignatureValidator signatureValidator;
    private final RazorpayEventMapper eventMapper;
    private final RazorpayWebhookReceiptRepository receiptRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final TransactionRepository transactionRepository;
    private final com.riskshield.audit.service.AuditService auditService;

    /**
     * Processes an incoming Razorpay webhook payload.
     * Enforces signature verification, event-id idempotency, and asynchronous Kafka publishing.
     */
    @Transactional
    public RazorpayWebhookResponse processWebhook(String rawPayload, String signature, String eventIdHeader) {
        // 1. Signature Verification
        if (!signatureValidator.isValid(rawPayload, signature)) {
            try {
                auditService.recordRiskEvent(
                        com.riskshield.audit.entity.AuditEventType.WEBHOOK_REJECTED,
                        com.riskshield.audit.entity.ActorType.SYSTEM,
                        "RAZORPAY_GATEWAY",
                        null,
                        null,
                        "WebhookPayload",
                        eventIdHeader != null ? eventIdHeader : "unknown_event",
                        "razorpay-webhook-service",
                        "Rejected webhook: Invalid HMAC-SHA256 signature",
                        rawPayload
                );
            } catch (Exception ignored) {}
            throw new SecurityException("Invalid Razorpay webhook signature");
        }

        // 2. Parse Payload
        RazorpayParsedEvent parsed = eventMapper.parse(rawPayload, eventIdHeader);
        String eventId = parsed.getEventId();
        String eventType = parsed.getEventType();
        String paymentId = parsed.getPaymentId();

        // 2b. Replay Attack Defense: Verify payload timestamp is within a 15-minute tolerance window
        if (parsed.getPaymentCreatedAt() != null) {
            long ageSeconds = java.time.Duration.between(parsed.getPaymentCreatedAt(), Instant.now()).abs().toSeconds();
            if (ageSeconds > 900) { // 15 minutes window
                log.warn("Webhook replay attack rejected: Event timestamp {} exceeds 15-minute tolerance (age: {}s)",
                        parsed.getPaymentCreatedAt(), ageSeconds);
                try {
                    auditService.recordRiskEvent(
                            com.riskshield.audit.entity.AuditEventType.WEBHOOK_REJECTED,
                            com.riskshield.audit.entity.ActorType.SYSTEM,
                            "RAZORPAY_GATEWAY",
                            null,
                            null,
                            "WebhookPayload",
                            eventId,
                            "razorpay-webhook-service",
                            "Rejected webhook: Stale timestamp exceeds 15-minute replay window (age=" + ageSeconds + "s)",
                            rawPayload
                    );
                } catch (Exception ignored) {}
                throw new SecurityException("Webhook rejected: Stale timestamp indicates potential replay attack");
            }
        }

        log.info("Processing Razorpay webhook: eventId={}, eventType={}, paymentId={}", eventId, eventType, paymentId);

        // 3. Idempotency Check: Drop duplicate webhook deliveries immediately
        if (receiptRepository.existsById(eventId)) {
            log.info("Duplicate Razorpay webhook received: eventId={}. Acknowledging with fast 200 OK without re-processing.", eventId);
            return RazorpayWebhookResponse.builder()
                    .status("DUPLICATE")
                    .message("Event already recorded and processed")
                    .eventId(eventId)
                    .eventType(eventType)
                    .entityId(paymentId)
                    .receivedAt(Instant.now())
                    .build();
        }

        // 4. Record Webhook Receipt
        RazorpayWebhookReceipt receipt = RazorpayWebhookReceipt.builder()
                .eventId(eventId)
                .eventType(eventType)
                .accountId(parsed.getAccountId())
                .entityId(paymentId)
                .amountInPaise(parsed.getAmountInPaise())
                .currency(parsed.getCurrency() != null ? parsed.getCurrency() : "INR")
                .status("RECEIVED")
                .signatureVerified(true)
                .payload(rawPayload)
                .receivedAt(Instant.now())
                .build();

        receiptRepository.save(receipt);

        // Record Audit Event: WEBHOOK_RECEIVED
        try {
            auditService.recordRiskEvent(
                    com.riskshield.audit.entity.AuditEventType.WEBHOOK_RECEIVED,
                    com.riskshield.audit.entity.ActorType.MERCHANT,
                    parsed.getAccountId() != null ? parsed.getAccountId() : "RAZORPAY_WEBHOOK",
                    null,
                    null,
                    "WebhookReceipt",
                    eventId,
                    "razorpay-webhook-service",
                    String.format("Webhook %s received for event %s (entity %s)", eventId, eventType, paymentId),
                    receipt
            );
        } catch (Exception e) {
            log.warn("Failed to record WEBHOOK_RECEIVED audit event: {}", e.getMessage());
        }

        // 5. Handle Non-Payment / Unknown Events
        if (!parsed.isPaymentEvent()) {
            log.info("Razorpay event {} is not a payment lifecycle event; marking receipt as IGNORED", eventType);
            receipt.setStatus("IGNORED");
            receipt.setProcessedAt(Instant.now());
            receiptRepository.save(receipt);

            return RazorpayWebhookResponse.builder()
                    .status("IGNORED")
                    .message("Event type ignored by risk assessment pipeline")
                    .eventId(eventId)
                    .eventType(eventType)
                    .entityId(paymentId)
                    .receivedAt(receipt.getReceivedAt())
                    .build();
        }

        // 6. Safe Out-of-Order Handling & Kafka Event Generation
        String correlationId = "rzp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        boolean isOutOfOrder = false;

        if (paymentId != null) {
            Optional<Transaction> existingTxOpt = transactionRepository.findById(paymentId);
            if (existingTxOpt.isPresent()) {
                Transaction existingTx = existingTxOpt.get();
                String existingStatus = existingTx.getPaymentStatus();

                // If already captured/settled and an older authorized event arrives, do not regress state
                if ("CAPTURED".equalsIgnoreCase(existingStatus) && "payment.authorized".equalsIgnoreCase(eventType)) {
                    log.warn("Out-of-order event detected for tx {}: current={}, incoming={}. Preserving terminal state.",
                            paymentId, existingStatus, eventType);
                    isOutOfOrder = true;
                    receipt.setStatus("OUT_OF_ORDER");
                    receipt.setProcessedAt(Instant.now());
                    receiptRepository.save(receipt);

                    return RazorpayWebhookResponse.builder()
                            .status("OUT_OF_ORDER")
                            .message("Event acknowledged safely without regressing current transaction state")
                            .eventId(eventId)
                            .eventType(eventType)
                            .entityId(paymentId)
                            .receivedAt(receipt.getReceivedAt())
                            .build();
                }

                // If transaction exists and this is an update (e.g. payment.captured, payment.failed)
                PaymentUpdatedEvent updateEvent = eventMapper.toPaymentUpdatedEvent(parsed, existingStatus, correlationId);
                paymentEventProducer.publishPaymentUpdated(updateEvent);

                receipt.setStatus("PROCESSED");
                receipt.setProcessedAt(Instant.now());
                receiptRepository.save(receipt);

                return RazorpayWebhookResponse.builder()
                        .status("SUCCESS")
                        .message("Payment update dispatched to risk pipeline")
                        .eventId(eventId)
                        .eventType(eventType)
                        .entityId(paymentId)
                        .receivedAt(receipt.getReceivedAt())
                        .build();
            }
        }

        // 7. Fresh Payment Event (payment.authorized or new payment.captured)
        PaymentCreatedEvent createdEvent = eventMapper.toPaymentCreatedEvent(parsed, correlationId);
        paymentEventProducer.publishPaymentCreated(createdEvent);

        receipt.setStatus("PROCESSED");
        receipt.setProcessedAt(Instant.now());
        receiptRepository.save(receipt);

        return RazorpayWebhookResponse.builder()
                .status("SUCCESS")
                .message("Payment event dispatched to risk assessment pipeline")
                .eventId(eventId)
                .eventType(eventType)
                .entityId(paymentId)
                .receivedAt(receipt.getReceivedAt())
                .build();
    }

    /**
     * Local development replay mechanism:
     * Generates a validly signed Razorpay webhook payload and runs it through the ingestion pipeline.
     */
    public RazorpayWebhookResponse replayWebhook(RazorpayReplayRequest request) {
        String eventId = "evt_replay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String accountId = "acc_test_" + (request.getMerchantId() != null ? request.getMerchantId() : "riskshield");

        String payload = eventMapper.buildReplayPayload(request, eventId, accountId);

        String signature;
        try {
            signature = signatureValidator.computeHmacSha256(payload, razorpayProperties.getWebhookSecret());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test HMAC signature for replay: " + e.getMessage(), e);
        }

        return processWebhook(payload, signature, eventId);
    }
}
