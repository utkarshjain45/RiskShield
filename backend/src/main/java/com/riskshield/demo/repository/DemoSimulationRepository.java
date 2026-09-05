package com.riskshield.demo.repository;

import com.riskshield.demo.entity.DemoSimulation;
import com.riskshield.demo.entity.SimulationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemoSimulationRepository extends JpaRepository<DemoSimulation, String> {

    Optional<DemoSimulation> findFirstByStatusOrderByStartedAtDesc(SimulationStatus status);

    List<DemoSimulation> findByMerchantIdOrderByCreatedAtDesc(String merchantId);

    List<DemoSimulation> findTop10ByOrderByCreatedAtDesc();
}
