package com.riskshield.policy.repository;

import com.riskshield.policy.entity.RiskPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RiskPolicyRepository extends JpaRepository<RiskPolicy, String> {
    Optional<RiskPolicy> findByMerchantIdAndEnabledTrue(String merchantId);
    Optional<RiskPolicy> findFirstByMerchantIsNullAndEnabledTrue();
    List<RiskPolicy> findByMerchantId(String merchantId);
    List<RiskPolicy> findByEnabled(boolean enabled);
    List<RiskPolicy> findByMerchantIdAndEnabled(String merchantId, boolean enabled);

    // Backward compatibility aliases
    default Optional<RiskPolicy> findByMerchantIdAndActiveTrue(String merchantId) {
        return findByMerchantIdAndEnabledTrue(merchantId);
    }
    default Optional<RiskPolicy> findFirstByMerchantIsNullAndActiveTrue() {
        return findFirstByMerchantIsNullAndEnabledTrue();
    }
}
