package com.riskshield.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.event.config.KafkaTopicConfig;
import com.riskshield.event.dto.RiskDecisionedEvent;
import com.riskshield.event.dto.RiskScoredEvent;
import com.riskshield.event.idempotency.IdempotencyService;
import com.riskshield.policy.entity.RiskDecision;
import com.riskshield.policy.entity.RiskPolicy;
import com.riskshield.policy.service.PolicyEvaluationService;
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
public class RiskDecisionConsumer {

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final TransactionRepository transactionRepository;
    private final PolicyEvaluationService policyEvaluationService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(
            topics = KafkaTopicConfig.TOPIC_RISK_SCORED,
            groupId = "${spring.kafka.consumer.group-id:riskshield-backend-group}"
    )
    public void consumeRiskScored(String message) {
        try {
            RiskScoredEvent event = objectMapper.readValue(message, RiskScoredEvent.class);
            if (event.getCorrelationId() != null) {
                MDC.put("correlationId", event.getCorrelationId());
            }

            log.info("Received event 'risk.scored' for tx: {}, score: {}", event.getTransactionId(), event.getRiskScore());

            // 1. Idempotency check: Ignore duplicate events
            boolean isNew = idempotencyService.tryAcquire(
                    event.getEventId(),
                    RiskScoredEvent.EVENT_TYPE,
                    event.getTransactionId(),
                    event.getCorrelationId()
            );
            if (!isNew) {
                log.info("Event {} for tx {} already processed. Skipping.", event.getEventId(), event.getTransactionId());
                return;
            }

            // 2. Load or build Transaction
            Transaction tx = transactionRepository.findByIdWithDetails(event.getTransactionId())
                    .orElseGet(() -> Transaction.builder()
                            .id(event.getTransactionId())
                            .amountInPaise(100000L)
                            .currency("INR")
                            .paymentMethod("card")
                            .createdAt(Instant.now())
                            .build());

            // 3. Resolve Policy & Evaluate Deterministic Decision
            RiskPolicy policy = policyEvaluationService.resolvePolicyForMerchant(event.getMerchantId());
            RiskDecision decision = policyEvaluationService.evaluateWithPolicy(policy, tx, event.getRiskScore());

            // 4. Emit RiskDecisionedEvent to 'risk.decisioned'
            RiskDecisionedEvent decisionedEvent = RiskDecisionedEvent.builder()
                    .eventId("evt_" + UUID.randomUUID().toString().replace("-", ""))
                    .eventType(RiskDecisionedEvent.EVENT_TYPE)
                    .eventVersion("v1.0.0")
                    .occurredAt(Instant.now())
                    .transactionId(event.getTransactionId())
                    .merchantId(event.getMerchantId())
                    .correlationId(event.getCorrelationId())
                    .riskScore(decision.getRiskScore())
                    .decision(decision.getDecision())
                    .policyId(decision.getPolicyId())
                    .policyVersion(decision.getPolicyVersion())
                    .decisionReason(decision.getDecisionReason())
                    .build();

            String decisionJson = objectMapper.writeValueAsString(decisionedEvent);
            kafkaTemplate.send(KafkaTopicConfig.TOPIC_RISK_DECISIONED, decisionedEvent.getTransactionId(), decisionJson);
            log.info("Emitted 'risk.decisioned' for tx {}: decision={}", event.getTransactionId(), decision.getDecision());

        } catch (Exception e) {
            log.error("Failed to process risk.scored message: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing risk.scored", e);
        } finally {
            MDC.clear();
        }
    }
}
