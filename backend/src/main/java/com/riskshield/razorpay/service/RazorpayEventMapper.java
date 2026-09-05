package com.riskshield.razorpay.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.riskshield.event.dto.PaymentCreatedEvent;
import com.riskshield.event.dto.PaymentUpdatedEvent;
import com.riskshield.razorpay.dto.RazorpayParsedEvent;
import com.riskshield.razorpay.dto.RazorpayReplayRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayEventMapper {

    private final ObjectMapper objectMapper;

    /**
     * Parses and extracts metadata from an official Razorpay webhook payload.
     */
    public RazorpayParsedEvent parse(String rawJson, String headerEventId) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("Razorpay webhook payload cannot be null or empty");
        }

        try {
            JsonNode root = objectMapper.readTree(rawJson);

            // Razorpay webhooks root validation
            String entity = root.path("entity").asText(null);
            String eventType = root.path("event").asText(null);

            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("Invalid Razorpay webhook payload: missing 'event' field");
            }

            // Read eventId from header first, fallback to root.id, fallback to synthetic ID
            String resolvedEventId = headerEventId;
            if (resolvedEventId == null || resolvedEventId.isBlank()) {
                resolvedEventId = root.path("id").asText(null);
            }
            if (resolvedEventId == null || resolvedEventId.isBlank()) {
                resolvedEventId = "evt_rzp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }

            String accountId = root.path("account_id").asText(null);

            // Extract payment entity if available
            JsonNode payloadNode = root.path("payload");
            JsonNode paymentNode = payloadNode.path("payment").path("entity");

            String paymentId = paymentNode.path("id").asText(null);
            Long amount = paymentNode.has("amount") ? paymentNode.path("amount").asLong() : null;
            String currency = paymentNode.path("currency").asText("INR");
            String status = paymentNode.path("status").asText(null);
            String method = paymentNode.path("method").asText("card");
            String email = paymentNode.path("email").asText(null);
            String contact = paymentNode.path("contact").asText(null);
            String orderId = paymentNode.path("order_id").asText(null);

            // Check notes for custom metadata
            JsonNode notesNode = paymentNode.path("notes");
            String merchantId = notesNode.path("merchant_id").asText(accountId != null ? accountId : "mer_default_001");

            long createdAtSec = paymentNode.path("created_at").asLong(0);
            Instant paymentCreatedAt = createdAtSec > 0 ? Instant.ofEpochSecond(createdAtSec) : Instant.now();

            return RazorpayParsedEvent.builder()
                    .eventId(resolvedEventId)
                    .eventType(eventType)
                    .accountId(accountId)
                    .paymentId(paymentId)
                    .amountInPaise(amount)
                    .currency(currency)
                    .status(status)
                    .method(method)
                    .email(email)
                    .contact(contact)
                    .orderId(orderId)
                    .merchantId(merchantId)
                    .paymentCreatedAt(paymentCreatedAt)
                    .paymentNode(paymentNode)
                    .rawPayload(rawJson)
                    .build();

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed Razorpay webhook JSON payload: " + e.getMessage(), e);
        }
    }

    /**
     * Maps a parsed Razorpay payment event to RiskShield's PaymentCreatedEvent.
     */
    public PaymentCreatedEvent toPaymentCreatedEvent(RazorpayParsedEvent event, String correlationId) {
        String txId = event.getPaymentId() != null ? event.getPaymentId() : "tx_rzp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        return PaymentCreatedEvent.builder()
                .eventId(event.getEventId())
                .eventType(PaymentCreatedEvent.EVENT_TYPE)
                .eventVersion("v1.0.0")
                .occurredAt(event.getPaymentCreatedAt())
                .transactionId(txId)
                .merchantId(event.getMerchantId())
                .correlationId(correlationId)
                .customerEmail(event.getEmail())
                .customerId(event.getContact() != null ? "cust_" + Math.abs(event.getContact().hashCode()) : null)
                .amountInPaise(event.getAmountInPaise() != null ? event.getAmountInPaise() : 0L)
                .currency(event.getCurrency())
                .paymentMethod(event.getMethod())
                .isNewDevice(false)
                .isNewIp(false)
                .customerAccountAgeDays(90)
                .build();
    }

    /**
     * Maps a parsed Razorpay payment update event to PaymentUpdatedEvent.
     */
    public PaymentUpdatedEvent toPaymentUpdatedEvent(RazorpayParsedEvent event, String previousStatus, String correlationId) {
        return PaymentUpdatedEvent.builder()
                .eventId(event.getEventId())
                .eventType(PaymentUpdatedEvent.EVENT_TYPE)
                .eventVersion("v1.0.0")
                .occurredAt(Instant.now())
                .transactionId(event.getPaymentId())
                .merchantId(event.getMerchantId())
                .correlationId(correlationId)
                .previousStatus(previousStatus)
                .newStatus(event.getStatus() != null ? event.getStatus().toUpperCase() : "UPDATED")
                .updateReason("Razorpay Webhook: " + event.getEventType())
                .build();
    }

    /**
     * Generates a realistic, sanitized Razorpay Test Mode webhook payload for local replay and testing.
     */
    public String buildReplayPayload(RazorpayReplayRequest request, String eventId, String accountId) {
        if (request.getCustomRawPayload() != null && !request.getCustomRawPayload().isBlank()) {
            return request.getCustomRawPayload();
        }

        String paymentId = request.getPaymentId() != null && !request.getPaymentId().isBlank()
                ? request.getPaymentId()
                : "pay_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        long nowSec = Instant.now().getEpochSecond();

        ObjectNode root = objectMapper.createObjectNode();
        root.put("entity", "event");
        root.put("account_id", accountId != null ? accountId : "acc_test_riskshield");
        root.put("event", request.getEventType());
        root.putArray("contains").add("payment");

        ObjectNode payload = root.putObject("payload");
        ObjectNode payment = payload.putObject("payment");
        ObjectNode entity = payment.putObject("entity");

        entity.put("id", paymentId);
        entity.put("entity", "payment");
        entity.put("amount", request.getAmountInPaise() != null ? request.getAmountInPaise() : 250000L);
        entity.put("currency", request.getCurrency() != null ? request.getCurrency() : "INR");
        entity.put("status", request.getEventType().replace("payment.", ""));
        entity.put("order_id", "order_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        entity.put("method", request.getMethod() != null ? request.getMethod() : "card");
        entity.put("captured", "payment.captured".equals(request.getEventType()));
        entity.put("email", request.getEmail());
        entity.put("contact", request.getContact());
        entity.put("created_at", nowSec);

        ObjectNode notes = entity.putObject("notes");
        notes.put("merchant_id", request.getMerchantId() != null ? request.getMerchantId() : "mer_default_001");

        root.put("created_at", nowSec);

        return root.toPrettyString();
    }
}
