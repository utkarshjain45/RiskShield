package com.riskshield.policy.service;

import com.riskshield.policy.entity.RiskDecision;
import com.riskshield.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade maintaining backward compatibility for existing callers (e.g. RiskAssessmentService)
 * while delegating to the deterministic PolicyEvaluationService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyEngineService {

    private final PolicyEvaluationService policyEvaluationService;

    @Transactional
    public RiskDecision evaluatePolicy(Transaction transaction, Double riskScore) {
        return policyEvaluationService.evaluate(transaction, riskScore);
    }
}
