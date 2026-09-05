package com.riskshield.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.event.config.KafkaTopicConfig;
import com.riskshield.event.dto.PaymentCreatedEvent;
import com.riskshield.event.dto.RiskScoredEvent;
import com.riskshield.event.idempotency.IdempotencyService;
import com.riskshield.feature.dto.FeatureSnapshot;
import com.riskshield.feature.service.BehavioralFeatureService;
import com.riskshield.risk.client.MlServiceClient;
import com.riskshield.risk.client.dto.MlRiskScoreRequest;
import com.riskshield.risk.client.dto.MlRiskScoreResponse;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskEventConsumer {

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final TransactionRepository transactionRepository;
    private final BehavioralFeatureService behavioralFeatureService;
    private final MlServiceClient mlServiceClient;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_PAYMENT_CREATED,
            groupId = "${spring.kafka.consumer.group-id:riskshield-backend-group}"
    )
    public void consumePaymentCreated(String message) {
        try {
            PaymentCreatedEvent event = objectMapper.readValue(message, PaymentCreatedEvent.class);
            if (event.getCorrelationId() != null) {
                MDC.put("correlationId", event.getCorrelationId());
            }

            log.info("Received event 'payment.created' for tx: {}, eventId: {}", event.getTransactionId(), event.getEventId());

            // 1. Idempotency check: Ignore duplicate events
            boolean isNew = idempotencyService.tryAcquire(
                    event.getEventId(),
                    PaymentCreatedEvent.EVENT_TYPE,
                    event.getTransactionId(),
                    event.getCorrelationId()
            );
            if (!isNew) {
                log.info("Event {} for tx {} already processed. Skipping.", event.getEventId(), event.getTransactionId());
                return;
            }

            // 2. Load Transaction from durable storage or build from event
            Transaction tx = transactionRepository.findByIdWithDetails(event.getTransactionId())
                    .orElse(null);

            // 3. Feature Enrichment via Redis sliding-window counters
            FeatureSnapshot snapshot;
            if (tx != null) {
                behavioralFeatureService.recordTransaction(tx);
                snapshot = behavioralFeatureService.computeSnapshot(tx);
            } else {
                snapshot = FeatureSnapshot.builder()
                        .transactionId(event.getTransactionId())
                        .amountVelocity(event.getAmountInPaise() != null ? event.getAmountInPaise() : 0L)
                        .deviceAccountCount(1L)
                        .ipAccountCount(1L)
                        .isNewDevice(Boolean.TRUE.equals(event.getIsNewDevice()))
                        .isNewIp(Boolean.TRUE.equals(event.getIsNewIp()))
                        .build();
            }

            double amountInr = (event.getAmountInPaise() != null ? event.getAmountInPaise() : 0L) / 100.0;
            Instant occurredAt = event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now();

            // 4. Build ML inference request
            MlRiskScoreRequest mlRequest = MlRiskScoreRequest.builder()
                    .transactionId(event.getTransactionId())
                    .merchantId(event.getMerchantId() != null ? event.getMerchantId() : "unknown")
                    .customerId(event.getCustomerId() != null ? event.getCustomerId() : "guest")
                    .deviceId(event.getDeviceId() != null ? event.getDeviceId() : "none")
                    .ipAddress(event.getIpAddress() != null ? event.getIpAddress() : "127.0.0.1")
                    .amount(amountInr)
                    .custHistAvgAmount(amountInr)
                    .amountDeviation(1.0)
                    .txCount5m(snapshot.getCustomerVelocity() != null ? (int) snapshot.getCustomerVelocity().getTransactions5m() : 1)
                    .txCount30m(snapshot.getCustomerVelocity() != null ? (int) snapshot.getCustomerVelocity().getTransactions30m() : 1)
                    .txCount1h(snapshot.getCustomerVelocity() != null ? (int) snapshot.getCustomerVelocity().getTransactions1h() : 1)
                    .amountSpent1h(snapshot.getAmountVelocity() != null ? snapshot.getAmountVelocity() / 100.0 : amountInr)
                    .custTxFrequency(1.0)
                    .custFailedRate(0.0)
                    .deviceTxCount(snapshot.getDeviceVelocity() != null ? (int) snapshot.getDeviceVelocity().getTransactions1h() : 1)
                    .deviceAccountCount(snapshot.getDeviceAccountCount() != null ? snapshot.getDeviceAccountCount().intValue() : 1)
                    .ipTxCount(snapshot.getIpVelocity() != null ? (int) snapshot.getIpVelocity().getTransactions1h() : 1)
                    .ipAccountCount(snapshot.getIpAccountCount() != null ? snapshot.getIpAccountCount().intValue() : 1)
                    .isNewDevice(Boolean.TRUE.equals(snapshot.getIsNewDevice()) ? 1 : 0)
                    .isNewIp(Boolean.TRUE.equals(snapshot.getIsNewIp()) ? 1 : 0)
                    .customerAccountAgeDays(event.getCustomerAccountAgeDays() != null ? event.getCustomerAccountAgeDays() : 30)
                    .hourOfDay(occurredAt.atZone(java.time.ZoneOffset.UTC).getHour())
                    .dayOfWeek(occurredAt.atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue() - 1)
                    .paymentMethod(event.getPaymentMethod() != null ? event.getPaymentMethod() : "card")
                    .build();

            // 5. ML Scoring
            MlRiskScoreResponse mlResponse = mlServiceClient.scoreTransaction(mlRequest);

            // 6. Map signals
            List<RiskScoredEvent.SignalDto> signals = new ArrayList<>();
            if (mlResponse.getTopRiskSignals() != null) {
                for (MlRiskScoreResponse.MlRiskSignalDto sig : mlResponse.getTopRiskSignals()) {
                    signals.add(RiskScoredEvent.SignalDto.builder()
                            .featureName(sig.getFeatureName())
                            .featureValue(String.valueOf(sig.getFeatureValue()))
                            .shapImpact(sig.getShapImpact())
                            .direction(sig.getDirection())
                            .description(sig.getDescription())
                            .build());
                }
            }

            // 7. Publish RiskScoredEvent to 'risk.scored'
            RiskScoredEvent scoredEvent = RiskScoredEvent.builder()
                    .eventId("evt_" + UUID.randomUUID().toString().replace("-", ""))
                    .eventType(RiskScoredEvent.EVENT_TYPE)
                    .eventVersion("v1.0.0")
                    .occurredAt(Instant.now())
                    .transactionId(event.getTransactionId())
                    .merchantId(event.getMerchantId())
                    .correlationId(event.getCorrelationId())
                    .fraudProbability(mlResponse.getFraudProbability())
                    .riskScore(mlResponse.getRiskScore())
                    .modelVersion(mlResponse.getModelVersion())
                    .inferenceLatencyMs(mlResponse.getInferenceLatencyMs())
                    .topRiskSignals(signals)
                    .build();

            String scoredJson = objectMapper.writeValueAsString(scoredEvent);
            kafkaTemplate.send(KafkaTopicConfig.TOPIC_RISK_SCORED, scoredEvent.getTransactionId(), scoredJson);
            log.info("Emitted 'risk.scored' for tx {}: riskScore={}", event.getTransactionId(), scoredEvent.getRiskScore());

        } catch (Exception e) {
            log.error("Failed to process payment.created message: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing payment.created", e);
        } finally {
            MDC.clear();
        }
    }
}
