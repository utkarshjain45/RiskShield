package com.riskshield.event.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.event.config.KafkaTopicConfig;
import com.riskshield.event.dto.PaymentCreatedEvent;
import com.riskshield.event.dto.PaymentUpdatedEvent;
import com.riskshield.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publishes a PaymentCreatedEvent to Kafka topic 'payment.created'.
     */
    public void publishPaymentCreated(PaymentCreatedEvent event) {
        event.initDefaultsIfMissing(PaymentCreatedEvent.EVENT_TYPE);
        send(KafkaTopicConfig.TOPIC_PAYMENT_CREATED, event.getTransactionId(), event);
    }

    /**
     * Converts a Transaction entity to PaymentCreatedEvent and publishes to Kafka.
     */
    public void publishPaymentCreated(Transaction tx, String correlationId) {
        PaymentCreatedEvent event = PaymentCreatedEvent.builder()
                .eventId("evt_" + UUID.randomUUID().toString().replace("-", ""))
                .eventType(PaymentCreatedEvent.EVENT_TYPE)
                .eventVersion("v1.0.0")
                .occurredAt(tx.getCreatedAt())
                .transactionId(tx.getId())
                .merchantId(tx.getMerchant() != null ? tx.getMerchant().getId() : "unknown")
                .correlationId(correlationId)
                .customerId(tx.getCustomer() != null ? tx.getCustomer().getId() : null)
                .customerEmail(tx.getCustomer() != null ? tx.getCustomer().getEmail() : null)
                .deviceId(tx.getDevice() != null ? tx.getDevice().getId() : null)
                .ipAddress(tx.getIpAddress())
                .amountInPaise(tx.getAmountInPaise())
                .currency(tx.getCurrency())
                .paymentMethod(tx.getPaymentMethod())
                .customerAccountAgeDays(tx.getCustomerAccountAgeDays())
                .isNewDevice(tx.isNewDevice())
                .isNewIp(tx.isNewIp())
                .build();

        publishPaymentCreated(event);
    }

    /**
     * Publishes a PaymentUpdatedEvent to Kafka topic 'payment.updated'.
     */
    public void publishPaymentUpdated(PaymentUpdatedEvent event) {
        event.initDefaultsIfMissing(PaymentUpdatedEvent.EVENT_TYPE);
        send(KafkaTopicConfig.TOPIC_PAYMENT_UPDATED, event.getTransactionId(), event);
    }

    private void send(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            log.info("Publishing event to topic '{}' with key '{}'", topic, key);
            kafkaTemplate.send(topic, key, json);
        } catch (Exception e) {
            log.warn("Failed to publish event to Kafka topic {}: {}", topic, e.getMessage());
        }
    }
}
