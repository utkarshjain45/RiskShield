package com.riskshield.audit.repository;

import com.riskshield.audit.entity.AuditEvent;
import com.riskshield.audit.entity.AuditEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {

    Optional<AuditEvent> findByAuditId(String auditId);

    @Query("SELECT a FROM AuditEvent a WHERE a.transaction.id = :transactionId ORDER BY a.createdAt ASC")
    List<AuditEvent> findByTransactionIdOrderByCreatedAtAsc(@Param("transactionId") String transactionId);

    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(a.entityId = :incidentId OR a.entityType = 'FraudIncident' AND a.entityId = :incidentId) " +
           "ORDER BY a.createdAt ASC")
    List<AuditEvent> findByIncidentIdOrderByCreatedAtAsc(@Param("incidentId") String incidentId);

    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(:merchantId IS NULL OR :merchantId = '' OR a.merchantId = :merchantId) AND " +
           "(:eventType IS NULL OR a.eventType = :eventType) AND " +
           "(:actorId IS NULL OR :actorId = '' OR a.actorId = :actorId) AND " +
           "(:service IS NULL OR :service = '' OR a.service = :service) AND " +
           "(:entityType IS NULL OR :entityType = '' OR a.entityType = :entityType) AND " +
           "(:action IS NULL OR :action = '' OR a.action = :action) AND " +
           "(:fromDate IS NULL OR a.createdAt >= :fromDate) AND " +
           "(:toDate IS NULL OR a.createdAt <= :toDate) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditEvent> findByComprehensiveFilters(
            @Param("merchantId") String merchantId,
            @Param("eventType") AuditEventType eventType,
            @Param("actorId") String actorId,
            @Param("service") String service,
            @Param("entityType") String entityType,
            @Param("action") String action,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable
    );

    @Query("SELECT a FROM AuditEvent a " +
           "WHERE (:entityType IS NULL OR :entityType = '' OR a.entityType = :entityType) AND " +
           "(:action IS NULL OR :action = '' OR a.action = :action) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditEvent> findByFilters(
            @Param("entityType") String entityType,
            @Param("action") String action,
            Pageable pageable
    );
}
