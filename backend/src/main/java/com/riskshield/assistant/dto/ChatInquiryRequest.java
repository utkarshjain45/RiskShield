package com.riskshield.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatInquiryRequest {

    @Size(max = 64, message = "session_id cannot exceed 64 characters")
    @JsonProperty("session_id")
    private String sessionId;

    @NotBlank(message = "Inquiry message is mandatory")
    @Size(max = 2000, message = "Inquiry message cannot exceed 2000 characters")
    @JsonProperty("message")
    private String message;

    @Size(max = 64, message = "merchant_id cannot exceed 64 characters")
    @JsonProperty("merchant_id")
    private String merchantId;

    @Size(max = 64, message = "active_transaction_id cannot exceed 64 characters")
    @JsonProperty("active_transaction_id")
    private String activeTransactionId;
}
