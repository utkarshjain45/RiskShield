package com.riskshield.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "device_type", length = 64, nullable = false)
    @Builder.Default
    private String deviceType = "mobile_android";

    @Column(name = "is_emulator", nullable = false)
    @Builder.Default
    private boolean isEmulator = false;

    @Column(name = "first_seen_at", nullable = false)
    @Builder.Default
    private Instant firstSeenAt = Instant.now();
}
