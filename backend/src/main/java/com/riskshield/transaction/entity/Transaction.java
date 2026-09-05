package com.riskshield.transaction.entity;

import com.riskshield.merchant.entity.Merchant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "amount_in_paise", nullable = false)
    private Long amountInPaise;

    @Column(length = 8, nullable = false)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "payment_method", length = 32, nullable = false)
    private String paymentMethod;

    @Column(name = "payment_status", length = 32, nullable = false)
    @Builder.Default
    private String paymentStatus = "PENDING";

    @Column(name = "customer_account_age_days", nullable = false)
    @Builder.Default
    private Integer customerAccountAgeDays = 30;

    @Column(name = "is_new_device", nullable = false)
    @Builder.Default
    private boolean isNewDevice = false;

    @Column(name = "is_new_ip", nullable = false)
    @Builder.Default
    private boolean isNewIp = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
