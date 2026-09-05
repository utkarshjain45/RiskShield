package com.riskshield.event.idempotency;

import com.riskshield.event.entity.ProcessedEvent;
import com.riskshield.event.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * Attempts to acquire an execution lock for the given eventId.
     * Returns true if this is the first time the event is observed.
     * Returns false if the event was already processed (idempotent skip).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(String eventId, String eventType, String transactionId, String correlationId) {
        if (eventId == null || eventId.isBlank()) {
            return true; // Cannot deduplicate events without eventId
        }

        if (processedEventRepository.existsByEventId(eventId)) {
            log.warn("Duplicate event detected: eventId={}, eventType={}, txId={}. Skipping processing.",
                    eventId, eventType, transactionId);
            return false;
        }

        try {
            ProcessedEvent record = ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .transactionId(transactionId)
                    .correlationId(correlationId)
                    .processedAt(Instant.now())
                    .build();

            processedEventRepository.saveAndFlush(record);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate event caught by primary key constraint: eventId={}", eventId);
            return false;
        }
    }
}
