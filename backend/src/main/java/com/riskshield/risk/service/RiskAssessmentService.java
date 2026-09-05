package com.riskshield.risk.service;

import com.riskshield.alert.service.AlertService;
import com.riskshield.audit.service.AuditService;
import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.common.exception.ResourceNotFoundException;
import com.riskshield.feature.dto.FeatureSnapshot;
import com.riskshield.feature.service.BehavioralFeatureService;
import com.riskshield.policy.entity.RiskDecision;
import com.riskshield.policy.repository.RiskDecisionRepository;
import com.riskshield.policy.service.PolicyEngineService;
import com.riskshield.risk.client.MlServiceClient;
import com.riskshield.risk.client.dto.MlRiskScoreRequest;
import com.riskshield.risk.client.dto.MlRiskScoreResponse;
import com.riskshield.risk.dto.RiskAssessmentResponse;
import com.riskshield.risk.dto.RiskExplanationResponse;
import com.riskshield.risk.entity.RiskAssessment;
import com.riskshield.risk.entity.RiskSignal;
import com.riskshield.risk.repository.RiskAssessmentRepository;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentService {

    private final TransactionRepository transactionRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final RiskDecisionRepository riskDecisionRepository;
    private final MlServiceClient mlServiceClient;
    private final PolicyEngineService policyEngineService;
    private final AlertService alertService;
    private final AuditService auditService;
    private final BehavioralFeatureService behavioralFeatureService;
    private final ExplanationTemplateService explanationTemplateService;

    @Transactional
    public RiskAssessmentResponse assessTransaction(String transactionId) {
        log.info("Starting end-to-end risk evaluation for transaction: {}", transactionId);

        Transaction tx = transactionRepository.findByIdWithDetails(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + transactionId));

        Instant now = Instant.now();

        // 1. Ingest into Redis and compute real-time sliding window behavioral features
        behavioralFeatureService.recordTransaction(tx);
        FeatureSnapshot snapshot = behavioralFeatureService.computeSnapshot(tx);

        int tx5m = (int) snapshot.getCustomerVelocity().getTransactions5m();
        int tx30m = (int) snapshot.getCustomerVelocity().getTransactions30m();
        int tx1h = (int) snapshot.getCustomerVelocity().getTransactions1h();
        double currentInr = (tx.getAmountInPaise() != null ? tx.getAmountInPaise() : 0L) / 100.0;
        double amountSpent1hInr = snapshot.getAmountVelocity() / 100.0;

        double custAvgAmt = currentInr;
        if (tx.getCustomer() != null) {
            Double dbAvg = transactionRepository.calculateAvgAmountInPaiseByCustomer(tx.getCustomer());
            if (dbAvg != null && dbAvg > 0) {
                custAvgAmt = dbAvg / 100.0;
            }
        }

        int devTxCount = (int) snapshot.getDeviceVelocity().getTransactions1h();
        int devAccountCount = Math.max(1, snapshot.getDeviceAccountCount().intValue());
        int ipTxCount = (int) snapshot.getIpVelocity().getTransactions1h();
        int ipAccountCount = Math.max(1, snapshot.getIpAccountCount().intValue());
        boolean isNewDevice = Boolean.TRUE.equals(snapshot.getIsNewDevice());
        boolean isNewIp = Boolean.TRUE.equals(snapshot.getIsNewIp());

        double amountDeviation = (custAvgAmt > 0) ? (currentInr / custAvgAmt) : 1.0;

        // 2. Build ML inference request
        MlRiskScoreRequest mlRequest = MlRiskScoreRequest.builder()
                .transactionId(tx.getId())
                .merchantId(tx.getMerchant() != null ? tx.getMerchant().getId() : "unknown")
                .customerId(tx.getCustomer() != null ? tx.getCustomer().getId() : "guest")
                .deviceId(tx.getDevice() != null ? tx.getDevice().getId() : "none")
                .ipAddress(tx.getIpAddress() != null ? tx.getIpAddress() : "127.0.0.1")
                .amount(currentInr)
                .custHistAvgAmount(custAvgAmt)
                .amountDeviation(amountDeviation)
                .txCount5m(tx5m)
                .txCount30m(tx30m)
                .txCount1h(tx1h)
                .amountSpent1h(amountSpent1hInr)
                .custTxFrequency((double) tx1h)
                .custFailedRate(0.0)
                .deviceTxCount(devTxCount)
                .deviceAccountCount(devAccountCount)
                .ipTxCount(ipTxCount)
                .ipAccountCount(ipAccountCount)
                .isNewDevice(isNewDevice ? 1 : 0)
                .isNewIp(isNewIp ? 1 : 0)
                .customerAccountAgeDays(tx.getCustomerAccountAgeDays())
                .hourOfDay(now.atZone(java.time.ZoneOffset.UTC).getHour())
                .dayOfWeek(now.atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue() - 1)
                .paymentMethod(tx.getPaymentMethod())
                .merchantCategory(tx.getMerchant() != null ? tx.getMerchant().getCategory() : "grocery_supermarket")
                .merchantRiskTier(tx.getMerchant() != null ? tx.getMerchant().getRiskTier() : "MEDIUM")
                .build();

        // 3. Call ML Service via typed client
        MlRiskScoreResponse mlResponse = mlServiceClient.scoreTransaction(mlRequest);

        // 4. Save Risk Assessment entity & signals
        RiskAssessment assessment = RiskAssessment.builder()
                .id("ast_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .transaction(tx)
                .modelVersion(mlResponse.getModelVersion())
                .fraudProbability(mlResponse.getFraudProbability())
                .riskScore(mlResponse.getRiskScore())
                .inferenceLatencyMs(mlResponse.getInferenceLatencyMs())
                .predictionTimestamp(Instant.now())
                .signals(new ArrayList<>())
                .build();

        if (mlResponse.getTopRiskSignals() != null) {
            for (MlRiskScoreResponse.MlRiskSignalDto sig : mlResponse.getTopRiskSignals()) {
                Double impact = sig.getShapImpact();
                String formatted = sig.getFormattedImpact();
                if (formatted == null && impact != null) {
                    formatted = String.format(Locale.ROOT, impact >= 0 ? "+%.2f" : "%.2f", impact);
                }
                RiskSignal riskSignal = RiskSignal.builder()
                        .signalName(sig.getFeatureName())
                        .displayName(sig.getDisplayName())
                        .formattedImpact(formatted)
                        .signalValue(String.valueOf(sig.getFeatureValue()))
                        .shapImpact(sig.getShapImpact())
                        .direction(sig.getDirection())
                        .description(sig.getDescription())
                        .createdAt(Instant.now())
                        .build();
                assessment.addSignal(riskSignal);
            }
        }

        RiskAssessment savedAssessment = riskAssessmentRepository.save(assessment);

        // 5. Evaluate deterministic Policy Engine
        RiskDecision decision = policyEngineService.evaluatePolicy(tx, savedAssessment.getRiskScore());

        // 6. Check for Alerts
        if (decision.getDecision() == RiskDecisionType.BLOCK || savedAssessment.getRiskScore() >= 80.0) {
            alertService.createAlert(
                    tx,
                    tx.getMerchant(),
                    "HIGH_FRAUD_RISK_DETECTED",
                    (decision.getDecision() == RiskDecisionType.BLOCK) ? "CRITICAL" : "HIGH",
                    String.format("Fraud score %.2f triggered %s decision (%s)",
                            savedAssessment.getRiskScore(), decision.getDecision(), decision.getReason())
            );
        }

        // 7. Record Audit Trail: MODEL_SCORED
        auditService.recordRiskEvent(
                com.riskshield.audit.entity.AuditEventType.MODEL_SCORED,
                com.riskshield.audit.entity.ActorType.ML_SERVICE,
                savedAssessment.getModelVersion(),
                tx.getMerchant().getId(),
                tx,
                "RiskAssessment",
                savedAssessment.getId(),
                "ml-scoring-service",
                String.format("Risk assessed by model %s with score %.2f -> Decision: %s",
                        savedAssessment.getModelVersion(), savedAssessment.getRiskScore(), decision.getDecision()),
                mlResponse
        );

        return mapToResponse(savedAssessment, decision);
    }

    @Transactional(readOnly = true)
    public RiskAssessmentResponse getAssessment(String transactionId) {
        RiskAssessment assessment = riskAssessmentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Risk assessment not found for transaction: " + transactionId));

        RiskDecision decision = riskDecisionRepository.findByTransactionId(transactionId).orElse(null);

        return mapToResponse(assessment, decision);
    }

    @Transactional(readOnly = true)
    public RiskExplanationResponse getTransactionExplanation(String transactionId) {
        RiskAssessment assessment = riskAssessmentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Risk assessment not found for transaction: " + transactionId));

        RiskDecision decision = riskDecisionRepository.findByTransactionId(transactionId).orElse(null);

        return explanationTemplateService.buildExplanationResponse(assessment, decision);
    }

    private RiskAssessmentResponse mapToResponse(RiskAssessment assessment, RiskDecision decision) {
        List<RiskAssessmentResponse.RiskSignalDto> signals = assessment.getSignals().stream()
                .map(s -> RiskAssessmentResponse.RiskSignalDto.builder()
                        .signalName(s.getSignalName())
                        .displayName(s.getDisplayName())
                        .formattedImpact(s.getFormattedImpact())
                        .signalValue(s.getSignalValue())
                        .shapImpact(s.getShapImpact())
                        .direction(s.getDirection())
                        .description(s.getDescription())
                        .build())
                .collect(Collectors.toList());

        return RiskAssessmentResponse.builder()
                .id(assessment.getId())
                .transactionId(assessment.getTransaction().getId())
                .modelVersion(assessment.getModelVersion())
                .fraudProbability(assessment.getFraudProbability())
                .riskScore(assessment.getRiskScore())
                .inferenceLatencyMs(assessment.getInferenceLatencyMs())
                .predictionTimestamp(assessment.getPredictionTimestamp())
                .contributingSignals(signals)
                .decision(decision != null ? decision.getDecision() : RiskDecisionType.ALLOW)
                .policyVersion(decision != null ? decision.getPolicyVersion() : "N/A")
                .policyReason(decision != null ? decision.getReason() : "Awaiting policy evaluation")
                .build();
    }
}
