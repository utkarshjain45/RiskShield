package com.riskshield.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.audit.dto.AuditEventResponse;
import com.riskshield.audit.dto.TransactionTimelineDto;
import com.riskshield.audit.entity.ActorType;
import com.riskshield.audit.entity.AuditEvent;
import com.riskshield.audit.entity.AuditEventType;
import com.riskshield.audit.repository.AuditEventRepository;
import com.riskshield.audit.util.AuditCryptoUtils;
import com.riskshield.common.exception.ResourceNotFoundException;
import com.riskshield.common.filter.CorrelationIdFilter;
import com.riskshield.security.util.SecurityUtils;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Primary API for capturing immutable risk audit events with cryptographic integrity hashing.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditEvent recordRiskEvent(
            AuditEventType eventType,
            ActorType actorType,
            String actorId,
            String merchantId,
            Transaction transaction,
            String entityType,
            String entityId,
            String service,
            String details,
            Object payloadOrMetadata
    ) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String resolvedMerchantId = merchantId;
        if (resolvedMerchantId == null && transaction != null && transaction.getMerchant() != null) {
            resolvedMerchantId = transaction.getMerchant().getId();
        }

        String metadataJson = null;
        if (payloadOrMetadata != null) {
            try {
                if (payloadOrMetadata instanceof String str) {
                    metadataJson = str;
                } else {
                    metadataJson = objectMapper.writeValueAsString(payloadOrMetadata);
                }
            } catch (Exception e) {
                log.warn("Failed to serialize audit event metadata: {}", e.getMessage());
                metadataJson = String.valueOf(payloadOrMetadata);
            }
        }

        String payloadHash = AuditCryptoUtils.computeSha256(metadataJson != null ? metadataJson : details);
        String auditId = "aud_" + UUID.randomUUID().toString().replace("-", "");

        log.info("[{}] Recording immutable audit event [{}]: type={}, actor={}:{}, merchant={}, service={}",
                correlationId, auditId, eventType, actorType, actorId, resolvedMerchantId, service);

        AuditEvent event = AuditEvent.builder()
                .auditId(auditId)
                .eventType(eventType != null ? eventType : AuditEventType.TRANSACTION_RECEIVED)
                .actorType(actorType != null ? actorType : ActorType.SYSTEM)
                .actorId(actorId != null ? actorId : "SYSTEM")
                .merchantId(resolvedMerchantId)
                .transaction(transaction)
                .correlationId(correlationId)
                .service(service != null ? service : "backend-api")
                .payloadHash(payloadHash)
                .metadata(metadataJson)
                .details(details)
                .entityType(entityType != null ? entityType : "RiskEvent")
                .entityId(entityId != null ? entityId : auditId)
                .action(eventType != null ? eventType.name() : "AUDIT_LOG")
                .createdAt(Instant.now())
                .build();

        return auditEventRepository.save(event);
    }

    /**
     * Backward-compatible audit event recording method.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordEvent(
            Transaction transaction,
            String entityType,
            String entityId,
            String action,
            String actorId,
            String details
    ) {
        AuditEventType eventType;
        try {
            eventType = AuditEventType.valueOf(action);
        } catch (Exception e) {
            eventType = AuditEventType.TRANSACTION_RECEIVED;
        }

        String merchantId = transaction != null && transaction.getMerchant() != null
                ? transaction.getMerchant().getId()
                : null;

        recordRiskEvent(
                eventType,
                ActorType.SYSTEM,
                actorId != null ? actorId : "SYSTEM",
                merchantId,
                transaction,
                entityType,
                entityId,
                "backend-api",
                details,
                null
        );
    }

    /**
     * Retrieves chronological audit trail for a transaction, strictly enforcing merchant tenancy.
     */
    @Transactional(readOnly = true)
    public List<AuditEventResponse> getEventsForTransaction(String transactionId) {
        Transaction tx = transactionRepository.findById(transactionId).orElse(null);
        if (tx != null && tx.getMerchant() != null) {
            SecurityUtils.assertMerchantAccess(tx.getMerchant().getId());
        }

        return auditEventRepository.findByTransactionIdOrderByCreatedAtAsc(transactionId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Generates a structured chronological transaction timeline DTO.
     */
    @Transactional(readOnly = true)
    public TransactionTimelineDto getTransactionTimeline(String transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        if (tx.getMerchant() != null) {
            SecurityUtils.assertMerchantAccess(tx.getMerchant().getId());
        }

        List<AuditEventResponse> events = auditEventRepository.findByTransactionIdOrderByCreatedAtAsc(transactionId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        Instant startedAt = !events.isEmpty() ? events.get(0).getCreatedAt() : tx.getCreatedAt();
        Instant lastEventAt = !events.isEmpty() ? events.get(events.size() - 1).getCreatedAt() : tx.getCreatedAt();

        return TransactionTimelineDto.builder()
                .transactionId(transactionId)
                .merchantId(tx.getMerchant() != null ? tx.getMerchant().getId() : null)
                .eventCount(events.size())
                .startedAt(startedAt)
                .lastEventAt(lastEventAt)
                .events(events)
                .build();
    }

    /**
     * Retrieves chronological audit trail for a specific fraud incident.
     */
    @Transactional(readOnly = true)
    public List<AuditEventResponse> getEventsForIncident(String incidentId) {
        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
        if (!events.isEmpty()) {
            for (AuditEvent e : events) {
                if (e.getMerchantId() != null) {
                    SecurityUtils.assertMerchantAccess(e.getMerchantId());
                    break;
                }
            }
        }

        return events.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Queries paginated chronological audit event ledger with multi-field filtering and tenant isolation.
     */
    @Transactional(readOnly = true)
    public Page<AuditEventResponse> getAuditEvents(
            String merchantId,
            String eventTypeStr,
            String actorId,
            String service,
            String entityType,
            String action,
            Instant fromDate,
            Instant toDate,
            Pageable pageable
    ) {
        String effectiveMerchantId = SecurityUtils.resolveMerchantScope(merchantId);

        AuditEventType eventType = null;
        if (eventTypeStr != null && !eventTypeStr.isBlank() && !eventTypeStr.equalsIgnoreCase("ALL")) {
            try {
                eventType = AuditEventType.valueOf(eventTypeStr.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        final AuditEventType finalEventType = eventType;
        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (effectiveMerchantId != null && !effectiveMerchantId.isBlank()) {
                predicates.add(cb.equal(root.get("merchantId"), effectiveMerchantId));
            }
            if (finalEventType != null) {
                predicates.add(cb.equal(root.get("eventType"), finalEventType));
            }
            if (actorId != null && !actorId.isBlank()) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (service != null && !service.isBlank()) {
                predicates.add(cb.equal(root.get("service"), service));
            }
            if (entityType != null && !entityType.isBlank() && !entityType.equalsIgnoreCase("ALL")) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<AuditEvent> page = auditEventRepository.findAll(spec, sortedPageable);
        return page.map(this::mapToResponse);
    }

    public AuditEventResponse mapToResponse(AuditEvent e) {
        return AuditEventResponse.builder()
                .id(e.getId())
                .auditId(e.getAuditId())
                .eventType(e.getEventType())
                .actorType(e.getActorType())
                .actorId(e.getActorId())
                .merchantId(e.getMerchantId())
                .transactionId(e.getTransaction() != null ? e.getTransaction().getId() : null)
                .correlationId(e.getCorrelationId())
                .service(e.getService())
                .payloadHash(e.getPayloadHash())
                .metadata(e.getMetadata())
                .details(e.getDetails())
                .entityType(e.getEntityType())
                .entityId(e.getEntityId())
                .action(e.getAction())
                .timestamp(e.getCreatedAt())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
