package com.riskshield.razorpay.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RazorpayParsedEvent {

    private String eventId;
    private String eventType;
    private String accountId;
    private String paymentId;
    private Long amountInPaise;
    private String currency;
    private String status; // authorized, captured, failed
    private String method; // card, upi, netbanking, wallet
    private String email;
    private String contact;
    private String orderId;
    private String merchantId;
    private Instant paymentCreatedAt;
    private JsonNode paymentNode;
    private String rawPayload;

    public boolean isPaymentEvent() {
        return eventType != null && eventType.startsWith("payment.");
    }
}
