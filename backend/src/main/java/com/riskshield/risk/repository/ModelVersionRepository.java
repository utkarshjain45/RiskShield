package com.riskshield.risk.repository;

import com.riskshield.risk.entity.ModelVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelVersionRepository extends JpaRepository<ModelVersion, String> {
    Optional<ModelVersion> findByActiveTrue();
    Optional<ModelVersion> findByVersionName(String versionName);
}
