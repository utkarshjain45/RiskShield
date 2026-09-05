package com.riskshield.risk.repository;

import com.riskshield.risk.entity.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, String> {

    @Query("SELECT r FROM RiskAssessment r " +
           "LEFT JOIN FETCH r.signals " +
           "LEFT JOIN FETCH r.transaction " +
           "WHERE r.transaction.id = :transactionId")
    Optional<RiskAssessment> findByTransactionId(@Param("transactionId") String transactionId);

    long countByRiskScoreBetween(Double min, Double max);
}
