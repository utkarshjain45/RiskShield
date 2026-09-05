package com.riskshield.event.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class KafkaTopicConfig {

    public static final String TOPIC_PAYMENT_CREATED = "payment.created";
    public static final String TOPIC_PAYMENT_UPDATED = "payment.updated";
    public static final String TOPIC_RISK_SCORED = "risk.scored";
    public static final String TOPIC_RISK_DECISIONED = "risk.decisioned";
    public static final String TOPIC_FRAUD_DETECTED = "fraud.detected";
    public static final String TOPIC_RISK_ALERT_CREATED = "risk.alert.created";

    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    public NewTopic paymentCreatedTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_CREATED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentCreatedDltTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_CREATED + DLT_SUFFIX).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentUpdatedTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_UPDATED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentUpdatedDltTopic() {
        return TopicBuilder.name(TOPIC_PAYMENT_UPDATED + DLT_SUFFIX).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic riskScoredTopic() {
        return TopicBuilder.name(TOPIC_RISK_SCORED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic riskScoredDltTopic() {
        return TopicBuilder.name(TOPIC_RISK_SCORED + DLT_SUFFIX).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic riskDecisionedTopic() {
        return TopicBuilder.name(TOPIC_RISK_DECISIONED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic riskDecisionedDltTopic() {
        return TopicBuilder.name(TOPIC_RISK_DECISIONED + DLT_SUFFIX).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic fraudDetectedTopic() {
        return TopicBuilder.name(TOPIC_FRAUD_DETECTED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic fraudDetectedDltTopic() {
        return TopicBuilder.name(TOPIC_FRAUD_DETECTED + DLT_SUFFIX).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic riskAlertCreatedTopic() {
        return TopicBuilder.name(TOPIC_RISK_ALERT_CREATED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic riskAlertCreatedDltTopic() {
        return TopicBuilder.name(TOPIC_RISK_ALERT_CREATED + DLT_SUFFIX).partitions(3).replicas(1).build();
    }
}
