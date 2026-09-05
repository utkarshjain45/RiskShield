package com.riskshield.api.v1.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvaluationRequest {

    @NotBlank(message = "transactionId is required")
    private String transactionId;

    private String razorpayPaymentId;
    private String razorpayOrderId;

    @NotBlank(message = "merchantId is required")
    private String merchantId;

    @NotNull(message = "amountInPaise is required")
    @Min(value = 1, message = "amountInPaise must be greater than 0")
    private Long amountInPaise;

    @NotBlank(message = "currency is required")
    private String currency;

    @NotBlank(message = "paymentMethod is required")
    private String paymentMethod; // card, upi, netbanking

    private String customerId;
    private String customerEmail;
    private String customerContact;
    private String ipAddress;
    private String deviceFingerprint;

    // Optional metadata & card attributes
    private Map<String, Object> paymentDetails;
    private Map<String, Object> billingAddress;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
