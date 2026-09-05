package com.riskshield.policy.repository;

import com.riskshield.policy.entity.RiskPolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskPolicyRuleRepository extends JpaRepository<RiskPolicyRule, String> {
    List<RiskPolicyRule> findByPolicyIdOrderByPriorityAsc(String policyId);
    List<RiskPolicyRule> findByPolicyIdAndEnabledTrueOrderByPriorityAsc(String policyId);
}
