package com.riskshield.transaction.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransactionRequest {

    @Size(max = 64, message = "transactionId cannot exceed 64 characters")
    private String transactionId; // Optional; auto-generated if omitted

    @NotBlank(message = "merchantId is mandatory")
    @Size(max = 64, message = "merchantId cannot exceed 64 characters")
    private String merchantId;

    @Size(max = 64, message = "customerId cannot exceed 64 characters")
    private String customerId;

    @Email(message = "customerEmail must be a valid email format")
    @Size(max = 255, message = "customerEmail cannot exceed 255 characters")
    private String customerEmail;

    @Size(max = 20, message = "customerContact cannot exceed 20 characters")
    private String customerContact;

    @Size(max = 64, message = "customerBillingState cannot exceed 64 characters")
    private String customerBillingState;

    @Size(max = 64, message = "deviceId cannot exceed 64 characters")
    private String deviceId;

    @Size(max = 64, message = "deviceType cannot exceed 64 characters")
    private String deviceType;

    private Boolean isEmulator;

    @Size(max = 45, message = "ipAddress cannot exceed 45 characters")
    private String ipAddress;

    @NotNull(message = "amountInPaise is mandatory")
    @Min(value = 1, message = "amountInPaise must be greater than 0")
    @Max(value = 10000000000L, message = "amountInPaise exceeds platform upper transaction limit")
    private Long amountInPaise;

    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
    @Builder.Default
    private String currency = "INR";

    @NotBlank(message = "paymentMethod is mandatory (e.g. card, upi, netbanking)")
    @Size(max = 32, message = "paymentMethod cannot exceed 32 characters")
    private String paymentMethod;

    @Builder.Default
    private Integer customerAccountAgeDays = 30;

    @Builder.Default
    private Boolean isNewDevice = false;

    @Builder.Default
    private Boolean isNewIp = false;
}
