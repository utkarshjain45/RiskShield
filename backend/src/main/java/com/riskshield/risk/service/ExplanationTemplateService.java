package com.riskshield.risk.service;

import com.riskshield.policy.entity.RiskDecision;
import com.riskshield.risk.dto.RiskExplanationResponse;
import com.riskshield.risk.entity.RiskAssessment;
import com.riskshield.risk.entity.RiskSignal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Produces deterministic, human-readable narrative explanations for fraud assessments
 * and policy decisions without using an LLM.
 */
@Service
@Slf4j
public class ExplanationTemplateService {

    private static final Map<String, String> CLAUSE_TEMPLATES = new LinkedHashMap<>();

    static {
        CLAUSE_TEMPLATES.put("transaction_velocity", "the transaction velocity is significantly higher than the customer's normal behavior");
        CLAUSE_TEMPLATES.put("device_account_count", "the device has recently been associated with multiple accounts");
        CLAUSE_TEMPLATES.put("amount_deviation", "the transaction amount deviates noticeably from the customer's historical average");
        CLAUSE_TEMPLATES.put("new_device", "the payment originated from an unrecognized hardware device");
        CLAUSE_TEMPLATES.put("new_ip", "the transaction was submitted from an unrecognized IP address");
        CLAUSE_TEMPLATES.put("account_age", "the customer account is brand new with limited transaction history");
        CLAUSE_TEMPLATES.put("ip_account_count", "the IP address has recently been associated with multiple accounts");
        CLAUSE_TEMPLATES.put("payment_failure_rate", "the customer has an elevated payment failure rate");
        CLAUSE_TEMPLATES.put("spending_velocity", "the hourly spending velocity is significantly elevated");
    }

    /**
     * Synthesizes a deterministic human-readable explanation from the assessment, decision, and risk signals.
     */
    public String generateNarrativeExplanation(
            Double riskScore,
            String decisionType,
            List<RiskSignal> signals
    ) {
        double score = riskScore != null ? riskScore : 0.0;
        String decision = decisionType != null ? decisionType.toUpperCase() : "REVIEW";

        // If low risk / ALLOW
        if ("ALLOW".equals(decision) || score < 31.0) {
            return "This transaction was approved as behavioral and velocity metrics are consistent with legitimate customer activity.";
        }

        // Filter to top positive risk drivers (sorted by shapImpact descending)
        List<RiskSignal> riskDrivers = signals != null
                ? signals.stream()
                .filter(s -> "INCREASES_RISK".equalsIgnoreCase(s.getDirection()) || (s.getShapImpact() != null && s.getShapImpact() > 0))
                .sorted(Comparator.comparing(
                        (RiskSignal s) -> s.getShapImpact() != null ? s.getShapImpact() : 0.0,
                        Comparator.reverseOrder()
                ).thenComparing(RiskSignal::getSignalName))
                .limit(3)
                .collect(Collectors.toList())
                : Collections.emptyList();

        if (riskDrivers.isEmpty()) {
            if ("BLOCK".equals(decision) || score >= 90.0) {
                return "This transaction was blocked because composite model risk indicators exceeded the critical safety threshold.";
            }
            return "This transaction was flagged because composite risk indicators exceeded standard review thresholds.";
        }

        List<String> clauses = new ArrayList<>();
        for (RiskSignal driver : riskDrivers) {
            String clause = mapSignalToClause(driver);
            if (!clauses.contains(clause)) {
                clauses.add(clause);
            }
        }

        String prefix = "BLOCK".equals(decision)
                ? "This transaction was blocked because "
                : "This transaction was flagged because ";

        if (clauses.size() == 1) {
            return prefix + clauses.get(0) + ".";
        } else if (clauses.size() == 2) {
            return prefix + clauses.get(0) + " and " + clauses.get(1) + ".";
        } else {
            return prefix + clauses.get(0) + ", " + clauses.get(1) + ", and " + clauses.get(2) + ".";
        }
    }

    /**
     * Builds complete RiskExplanationResponse from stored assessment and optional decision.
     */
    public RiskExplanationResponse buildExplanationResponse(
            RiskAssessment assessment,
            RiskDecision decision
    ) {
        String decisionStr = decision != null && decision.getDecision() != null
                ? decision.getDecision().name()
                : (assessment.getRiskScore() >= 90.0 ? "BLOCK" : (assessment.getRiskScore() >= 31.0 ? "REVIEW" : "ALLOW"));

        List<RiskSignal> signals = assessment.getSignals() != null
                ? new ArrayList<>(assessment.getSignals())
                : Collections.emptyList();

        // Sort signals deterministically by absolute impact descending, then by signal name
        signals.sort(Comparator.comparing(
                (RiskSignal s) -> s.getShapImpact() != null ? Math.abs(s.getShapImpact()) : 0.0,
                Comparator.reverseOrder()
        ).thenComparing(RiskSignal::getSignalName));

        List<RiskExplanationResponse.ContributingFeatureDto> featureDtos = signals.stream()
                .map(s -> {
                    Double impact = s.getShapImpact() != null ? s.getShapImpact() : 0.0;
                    String formattedImpact = s.getFormattedImpact();
                    if (formattedImpact == null || formattedImpact.isBlank()) {
                        formattedImpact = String.format(Locale.ROOT, impact >= 0 ? "+%.2f" : "%.2f", impact);
                    }
                    String displayName = s.getDisplayName();
                    if (displayName == null || displayName.isBlank()) {
                        displayName = formatDefaultDisplayName(s.getSignalName());
                    }

                    return RiskExplanationResponse.ContributingFeatureDto.builder()
                            .featureName(s.getSignalName())
                            .displayName(displayName)
                            .impact(impact)
                            .formattedImpact(formattedImpact)
                            .direction(s.getDirection())
                            .description(s.getDescription())
                            .build();
                })
                .collect(Collectors.toList());

        String narrative = generateNarrativeExplanation(
                assessment.getRiskScore(),
                decisionStr,
                signals
        );

        return RiskExplanationResponse.builder()
                .transactionId(assessment.getTransaction() != null ? assessment.getTransaction().getId() : null)
                .riskScore(assessment.getRiskScore())
                .fraudProbability(assessment.getFraudProbability())
                .decision(decisionStr)
                .modelVersion(assessment.getModelVersion())
                .topContributingFeatures(featureDtos)
                .humanReadableExplanation(narrative)
                .evaluatedAt(assessment.getPredictionTimestamp())
                .build();
    }

    private String mapSignalToClause(RiskSignal signal) {
        String name = signal.getSignalName() != null ? signal.getSignalName().toLowerCase() : "";
        for (Map.Entry<String, String> entry : CLAUSE_TEMPLATES.entrySet()) {
            if (name.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        String display = signal.getDisplayName() != null ? signal.getDisplayName() : signal.getSignalName();
        return "the " + display.toLowerCase() + " exhibited anomalous risk patterns";
    }

    private String formatDefaultDisplayName(String signalName) {
        if (signalName == null) return "Unknown Signal";
        return Arrays.stream(signalName.replace("_", " ").split("\\s+"))
                .map(w -> w.isEmpty() ? "" : Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}
