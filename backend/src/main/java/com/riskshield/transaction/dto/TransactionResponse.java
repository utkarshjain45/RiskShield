package com.riskshield.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private String id;
    private String merchantId;
    private String merchantName;
    private String customerId;
    private String deviceId;
    private String ipAddress;
    private Long amountInPaise;
    private Double amountInInr;
    private String currency;
    private String paymentMethod;
    private String paymentStatus;
    private Integer customerAccountAgeDays;
    private boolean isNewDevice;
    private boolean isNewIp;
    private Double riskScore;
    private String decision;
    private Instant createdAt;
}
