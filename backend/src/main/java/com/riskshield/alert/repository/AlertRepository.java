package com.riskshield.alert.repository;

import com.riskshield.alert.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, String> {
    Page<Alert> findByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable pageable);
    List<Alert> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
}
