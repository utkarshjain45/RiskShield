package com.riskshield.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.alert.entity.Alert;
import com.riskshield.alert.service.AlertService;
import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.event.config.KafkaTopicConfig;
import com.riskshield.event.dto.FraudDetectedEvent;
import com.riskshield.event.dto.RiskAlertCreatedEvent;
import com.riskshield.event.dto.RiskDecisionedEvent;
import com.riskshield.event.idempotency.IdempotencyService;
import com.riskshield.merchant.entity.Merchant;
import com.riskshield.merchant.repository.MerchantRepository;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FraudAlertConsumer {

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final AlertService alertService;
    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_RISK_DECISIONED,
            groupId = "${spring.kafka.consumer.group-id:riskshield-backend-group}"
    )
    public void consumeRiskDecisioned(String message) {
        try {
            RiskDecisionedEvent event = objectMapper.readValue(message, RiskDecisionedEvent.class);
            if (event.getCorrelationId() != null) {
                MDC.put("correlationId", event.getCorrelationId());
            }

            log.info("Received event 'risk.decisioned' for tx: {}, decision: {}", event.getTransactionId(), event.getDecision());

            // 1. Idempotency check: Ignore duplicate events
            boolean isNew = idempotencyService.tryAcquire(
                    event.getEventId(),
                    RiskDecisionedEvent.EVENT_TYPE,
                    event.getTransactionId(),
                    event.getCorrelationId()
            );
            if (!isNew) {
                log.info("Event {} for tx {} already processed. Skipping.", event.getEventId(), event.getTransactionId());
                return;
            }

            // 2. Determine if fraud alarm threshold is breached
            boolean isCriticalFraud = (event.getDecision() == RiskDecisionType.BLOCK);
            boolean isElevatedFraud = (event.getRiskScore() != null && event.getRiskScore() >= 80.0);

            if (isCriticalFraud || isElevatedFraud) {
                String severity = isCriticalFraud ? "CRITICAL" : "HIGH";
                log.warn("Triggering fraud alert pipeline for tx {}: severity={}, decision={}, score={}",
                        event.getTransactionId(), severity, event.getDecision(), event.getRiskScore());

                Transaction tx = transactionRepository.findById(event.getTransactionId()).orElse(null);
                Merchant merchant = (event.getMerchantId() != null)
                        ? merchantRepository.findById(event.getMerchantId()).orElse(null)
                        : null;

                // Create persistent Alert record
                Alert alert = alertService.createAlert(
                        tx,
                        merchant,
                        "AUTOMATED_FRAUD_BLOCK_ALARM",
                        severity,
                        String.format("Fraud decision '%s' triggered with score %.2f. Reason: %s",
                                event.getDecision(), event.getRiskScore(), event.getDecisionReason())
                );

                // 3. Emit FraudDetectedEvent to 'fraud.detected'
                FraudDetectedEvent fraudEvent = FraudDetectedEvent.builder()
                        .eventId("evt_" + UUID.randomUUID().toString().replace("-", ""))
                        .eventType(FraudDetectedEvent.EVENT_TYPE)
                        .eventVersion("v1.0.0")
                        .occurredAt(Instant.now())
                        .transactionId(event.getTransactionId())
                        .merchantId(event.getMerchantId())
                        .correlationId(event.getCorrelationId())
                        .riskScore(event.getRiskScore())
                        .decision(event.getDecision())
                        .fraudSeverity(severity)
                        .triggerReason(event.getDecisionReason())
                        .build();

                kafkaTemplate.send(KafkaTopicConfig.TOPIC_FRAUD_DETECTED, event.getTransactionId(),
                        objectMapper.writeValueAsString(fraudEvent));

                // 4. Emit RiskAlertCreatedEvent to 'risk.alert.created'
                RiskAlertCreatedEvent alertCreatedEvent = RiskAlertCreatedEvent.builder()
                        .eventId("evt_" + UUID.randomUUID().toString().replace("-", ""))
                        .eventType(RiskAlertCreatedEvent.EVENT_TYPE)
                        .eventVersion("v1.0.0")
                        .occurredAt(Instant.now())
                        .transactionId(event.getTransactionId())
                        .merchantId(event.getMerchantId())
                        .correlationId(event.getCorrelationId())
                        .alertId(alert != null ? alert.getId() : "alt_gen_" + event.getTransactionId())
                        .alertType("AUTOMATED_FRAUD_BLOCK_ALARM")
                        .severity(severity)
                        .status("OPEN")
                        .details(alert != null ? alert.getDetails() : event.getDecisionReason())
                        .build();

                kafkaTemplate.send(KafkaTopicConfig.TOPIC_RISK_ALERT_CREATED, event.getTransactionId(),
                        objectMapper.writeValueAsString(alertCreatedEvent));

                log.info("Dispatched 'fraud.detected' and 'risk.alert.created' events for tx {}", event.getTransactionId());
            }

        } catch (Exception e) {
            log.error("Failed to process risk.decisioned message: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing risk.decisioned", e);
        } finally {
            MDC.clear();
        }
    }
}
