package com.riskshield.spike.repository;

import com.riskshield.spike.entity.FraudIncident;
import com.riskshield.spike.entity.IncidentSeverity;
import com.riskshield.spike.entity.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FraudIncidentRepository extends JpaRepository<FraudIncident, String> {

    @Query("SELECT i FROM FraudIncident i LEFT JOIN FETCH i.merchant WHERE i.incidentId = :incidentId")
    Optional<FraudIncident> findByIdWithMerchant(@Param("incidentId") String incidentId);

    Page<FraudIncident> findByMerchant_IdOrderByDetectedAtDesc(String merchantId, Pageable pageable);

    Page<FraudIncident> findByStatusOrderByDetectedAtDesc(IncidentStatus status, Pageable pageable);

    Page<FraudIncident> findByMerchant_IdAndStatusOrderByDetectedAtDesc(String merchantId, IncidentStatus status, Pageable pageable);

    Page<FraudIncident> findAllByOrderByDetectedAtDesc(Pageable pageable);

    Optional<FraudIncident> findFirstByMerchant_IdAndStatusAndSeverityOrderByDetectedAtDesc(
            String merchantId,
            IncidentStatus status,
            IncidentSeverity severity
    );

    long countByStatus(IncidentStatus status);

    long countBySeverityAndStatus(IncidentSeverity severity, IncidentStatus status);
}
