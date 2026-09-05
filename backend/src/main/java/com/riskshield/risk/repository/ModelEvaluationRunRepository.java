package com.riskshield.risk.repository;

import com.riskshield.risk.entity.ModelEvaluationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelEvaluationRunRepository extends JpaRepository<ModelEvaluationRun, String> {

    Optional<ModelEvaluationRun> findTopByOrderByCreatedAtDesc();

    List<ModelEvaluationRun> findByModelVersionOrderByCreatedAtDesc(String modelVersion);
}
