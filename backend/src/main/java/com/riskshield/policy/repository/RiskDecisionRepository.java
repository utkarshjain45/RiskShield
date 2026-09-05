package com.riskshield.policy.repository;

import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.policy.entity.RiskDecision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiskDecisionRepository extends JpaRepository<RiskDecision, String> {

    @Query("SELECT d FROM RiskDecision d " +
           "LEFT JOIN FETCH d.transaction " +
           "WHERE d.transaction.id = :transactionId")
    Optional<RiskDecision> findByTransactionId(@Param("transactionId") String transactionId);

    Page<RiskDecision> findByDecisionOrderByCreatedAtDesc(RiskDecisionType decision, Pageable pageable);

    long countByDecision(RiskDecisionType decision);

    @Query("SELECT COALESCE(SUM(d.transaction.amountInPaise), 0L) FROM RiskDecision d WHERE d.decision = :decision")
    long sumAmountInPaiseByDecision(@Param("decision") RiskDecisionType decision);

    @Query("SELECT COALESCE(SUM(d.transaction.amountInPaise), 0L) FROM RiskDecision d WHERE d.decision IN (com.riskshield.common.enums.RiskDecisionType.REVIEW, com.riskshield.common.enums.RiskDecisionType.BLOCK)")
    long sumFraudExposurePaise();
}
