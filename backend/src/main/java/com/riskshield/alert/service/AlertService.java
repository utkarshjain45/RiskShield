package com.riskshield.alert.service;

import com.riskshield.alert.entity.Alert;
import com.riskshield.alert.repository.AlertRepository;
import com.riskshield.merchant.entity.Merchant;
import com.riskshield.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final com.riskshield.audit.service.AuditService auditService;

    @Transactional
    public Alert createAlert(
            Transaction transaction,
            Merchant merchant,
            String alertType,
            String severity,
            String details
    ) {
        log.warn("Generating security alert for merchant {}: type={}, severity={}", merchant.getId(), alertType, severity);

        Alert alert = Alert.builder()
                .id("alt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .transaction(transaction)
                .merchant(merchant)
                .alertType(alertType)
                .severity(severity)
                .status("OPEN")
                .details(details)
                .createdAt(Instant.now())
                .build();

        Alert savedAlert = alertRepository.save(alert);

        try {
            auditService.recordRiskEvent(
                    com.riskshield.audit.entity.AuditEventType.ALERT_CREATED,
                    com.riskshield.audit.entity.ActorType.SYSTEM,
                    "ALERT_ENGINE",
                    merchant != null ? merchant.getId() : null,
                    transaction,
                    "Alert",
                    savedAlert.getId(),
                    "alert-service",
                    String.format("Alert %s created with severity %s: %s", alertType, severity, details),
                    savedAlert
            );
        } catch (Exception e) {
            log.warn("Failed to record ALERT_CREATED audit event for {}: {}", savedAlert.getId(), e.getMessage());
        }

        return savedAlert;
    }
}
