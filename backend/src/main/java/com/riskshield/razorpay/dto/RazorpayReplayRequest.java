package com.riskshield.razorpay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RazorpayReplayRequest {

    @JsonProperty("event_type")
    @Builder.Default
    private String eventType = "payment.authorized";

    @JsonProperty("payment_id")
    private String paymentId;

    @JsonProperty("amount_in_paise")
    @Builder.Default
    private Long amountInPaise = 250000L; // ₹2,500.00

    @JsonProperty("currency")
    @Builder.Default
    private String currency = "INR";

    @JsonProperty("method")
    @Builder.Default
    private String method = "card";

    @JsonProperty("email")
    @Builder.Default
    private String email = "customer@example.com";

    @JsonProperty("contact")
    @Builder.Default
    private String contact = "+919876543210";

    @JsonProperty("merchant_id")
    @Builder.Default
    private String merchantId = "mer_default_001";

    @JsonProperty("custom_raw_payload")
    private String customRawPayload;
}
