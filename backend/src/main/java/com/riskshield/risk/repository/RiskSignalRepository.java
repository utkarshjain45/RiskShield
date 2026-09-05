package com.riskshield.risk.repository;

import com.riskshield.risk.entity.RiskSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskSignalRepository extends JpaRepository<RiskSignal, Long> {
    List<RiskSignal> findByAssessmentIdOrderByShapImpactDesc(String assessmentId);
}
