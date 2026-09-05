package com.riskshield.event.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.listener.auto-startup:true}")
    private boolean autoStartup;

    /**
     * Configures consumer container factory with automated retry policy (3 retries with 1s backoff)
     * and Dead-Letter Topic (DLT) routing for permanently failed messages.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setAutoStartup(autoStartup);

        // Dead-Letter Topic Recoverer routes to <topic>.DLT
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    log.error("Event failed after all retry attempts. Routing record from topic {} to {}.DLT. Error: {}",
                            record.topic(), record.topic(), ex.getMessage());
                    return new TopicPartition(record.topic() + KafkaTopicConfig.DLT_SUFFIX, record.partition());
                }
        );

        // Retry 3 times with 1-second interval
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
