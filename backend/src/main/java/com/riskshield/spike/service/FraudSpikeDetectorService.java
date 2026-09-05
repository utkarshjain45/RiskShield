package com.riskshield.spike.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.alert.entity.Alert;
import com.riskshield.alert.service.AlertService;
import com.riskshield.audit.service.AuditService;
import com.riskshield.event.config.KafkaTopicConfig;
import com.riskshield.event.dto.RiskAlertCreatedEvent;
import com.riskshield.merchant.entity.Merchant;
import com.riskshield.merchant.repository.MerchantRepository;
import com.riskshield.spike.dto.SpikeDetectionResultDto;
import com.riskshield.spike.dto.TimeWindowMetrics;
import com.riskshield.spike.entity.FraudIncident;
import com.riskshield.spike.entity.IncidentSeverity;
import com.riskshield.spike.entity.IncidentStatus;
import com.riskshield.spike.repository.FraudIncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudSpikeDetectorService {

    public static final double DEFAULT_BASELINE_RATE = 0.018; // 1.8% standard default baseline
    public static final double MIN_VARIANCE_FLOOR = 0.005;     // 0.5% standard deviation floor to avoid zero-division

    private final MerchantMetricsService merchantMetricsService;
    private final MerchantRepository merchantRepository;
    private final FraudIncidentRepository fraudIncidentRepository;
    private final AlertService alertService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public SpikeDetectionResultDto detectSpike(
            String merchantId,
            String currentWindowLabel,
            Instant referenceTime
    ) {
        Instant now = referenceTime != null ? referenceTime : Instant.now();
        String windowLabel = (currentWindowLabel != null && !currentWindowLabel.isBlank())
                ? currentWindowLabel
                : "15m";

        Duration currentDuration = parseDurationFromLabel(windowLabel);
        Instant currentWindowStart = now.minus(currentDuration);

        // 1. Calculate current window metrics
        TimeWindowMetrics currentMetrics = merchantMetricsService.calculateWindowMetrics(
                merchantId, windowLabel, currentWindowStart, now
        );

        // 2. Compute rolling historical baseline (4 sequential slices over the preceding 24 hours)
        BaselineStats baseline = calculateRollingBaseline(merchantId, currentWindowStart);

        // 3. Statistical comparison
        return evaluateAndRecordSpike(
                merchantId,
                windowLabel,
                baseline.meanRate,
                baseline.stdDev,
                currentMetrics.getFraudRate(),
                currentMetrics.getSuspiciousTransactions(),
                currentMetrics.getFraudExposurePaise(),
                now
        );
    }

    @Transactional
    public SpikeDetectionResultDto evaluateAndRecordSpike(
            String merchantId,
            String windowLabel,
            double baselineRate,
            double stdDev,
            double currentRate,
            long affectedTransactions,
            long estimatedExposurePaise,
            Instant detectedAt
    ) {
        Instant timestamp = detectedAt != null ? detectedAt : Instant.now();
        double effectiveStdDev = Math.max(stdDev, MIN_VARIANCE_FLOOR);

        // Calculate percentage increase: (current - baseline) / baseline * 100
        double percentageIncrease;
        if (baselineRate > 0.0001) {
            percentageIncrease = ((currentRate - baselineRate) / baselineRate) * 100.0;
        } else {
            percentageIncrease = currentRate * 10000.0; // Scaled representation if baseline is near zero
        }

        // Calculate Z-Score: (current - baseline) / stdDev
        double zScore = (currentRate - baselineRate) / effectiveStdDev;

        // Classify severity statistically
        IncidentSeverity severity;
        if (zScore >= 3.5 && percentageIncrease >= 100.0 && affectedTransactions >= 5) {
            severity = IncidentSeverity.CRITICAL;
        } else if (zScore >= 2.0 && percentageIncrease >= 50.0 && affectedTransactions >= 3) {
            severity = IncidentSeverity.ELEVATED;
        } else {
            severity = IncidentSeverity.NORMAL;
        }

        double exposureInr = estimatedExposurePaise / 100.0;
        String exposureFormatted = MerchantMetricsService.formatInrCurrency(exposureInr);
        String baselinePctStr = String.format(Locale.US, "%.1f%%", baselineRate * 100.0);
        String currentPctStr = String.format(Locale.US, "%.1f%%", currentRate * 100.0);
        String increasePctStr = String.format(Locale.US, "%.0f%%", percentageIncrease);

        // Generate narrative explanation
        String narrativeExplanation = buildExplanation(
                severity,
                currentPctStr,
                baselinePctStr,
                increasePctStr,
                affectedTransactions,
                exposureFormatted,
                zScore
        );

        String incidentId = null;

        // Persist incident and handle alerts if elevated or critical
        if (severity == IncidentSeverity.CRITICAL || severity == IncidentSeverity.ELEVATED) {
            Merchant merchant = merchantRepository.findById(merchantId).orElse(null);
            if (merchant != null) {
                // Deduplication check: check if an OPEN incident already exists for this merchant and severity
                Optional<FraudIncident> existingOpenOpt = fraudIncidentRepository
                        .findFirstByMerchant_IdAndStatusAndSeverityOrderByDetectedAtDesc(
                                merchantId, IncidentStatus.OPEN, severity
                        );

                FraudIncident incident;
                if (existingOpenOpt.isPresent()) {
                    incident = existingOpenOpt.get();
                    incident.setCurrentRate(roundTo4Decimals(currentRate));
                    incident.setBaselineRate(roundTo4Decimals(baselineRate));
                    incident.setPercentageIncrease(roundTo2Decimals(percentageIncrease));
                    incident.setAffectedTransactions(affectedTransactions);
                    incident.setEstimatedExposure(estimatedExposurePaise);
                    incident.setZScore(roundTo2Decimals(zScore));
                    incident.setExplanationSummary(narrativeExplanation);
                    incident.setTimeWindow(windowLabel);
                    log.info("Updated existing OPEN fraud incident {} for merchant {}", incident.getIncidentId(), merchantId);
                } else {
                    incident = FraudIncident.builder()
                            .incidentId("inc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                            .merchant(merchant)
                            .severity(severity)
                            .detectedAt(timestamp)
                            .baselineRate(roundTo4Decimals(baselineRate))
                            .currentRate(roundTo4Decimals(currentRate))
                            .percentageIncrease(roundTo2Decimals(percentageIncrease))
                            .affectedTransactions(affectedTransactions)
                            .estimatedExposure(estimatedExposurePaise)
                            .status(IncidentStatus.OPEN)
                            .timeWindow(windowLabel)
                            .zScore(roundTo2Decimals(zScore))
                            .explanationSummary(narrativeExplanation)
                            .build();
                    log.warn("Created new {} fraud incident {} for merchant {}", severity, incident.getIncidentId(), merchantId);
                }

                incident = fraudIncidentRepository.save(incident);
                incidentId = incident.getIncidentId();

                // Record Audit Event: INCIDENT_CREATED
                try {
                    auditService.recordRiskEvent(
                            com.riskshield.audit.entity.AuditEventType.INCIDENT_CREATED,
                            com.riskshield.audit.entity.ActorType.SYSTEM,
                            "FRAUD_SPIKE_DETECTOR",
                            merchantId,
                            null,
                            "FraudIncident",
                            incident.getIncidentId(),
                            "spike-detector-service",
                            String.format("Created %s fraud incident %s for merchant %s: %s",
                                    severity, incident.getIncidentId(), merchantId, narrativeExplanation),
                            incident
                    );
                } catch (Exception e) {
                    log.warn("Failed to record INCIDENT_CREATED audit event: {}", e.getMessage());
                }

                // If CRITICAL, trigger high-priority security Alert and Kafka event
                if (severity == IncidentSeverity.CRITICAL) {
                    dispatchCriticalAlert(merchant, incident, narrativeExplanation);
                }
            }
        }

        return SpikeDetectionResultDto.builder()
                .merchantId(merchantId)
                .severity(severity)
                .detectedAt(timestamp)
                .timeWindow(windowLabel)
                .baselineRate(roundTo4Decimals(baselineRate))
                .baselineRateFormatted(baselinePctStr)
                .currentRate(roundTo4Decimals(currentRate))
                .currentRateFormatted(currentPctStr)
                .percentageIncrease(roundTo2Decimals(percentageIncrease))
                .percentageIncreaseFormatted(increasePctStr)
                .affectedTransactions(affectedTransactions)
                .estimatedExposurePaise(estimatedExposurePaise)
                .estimatedExposureInr(exposureInr)
                .estimatedExposureFormatted(exposureFormatted)
                .zScore(roundTo2Decimals(zScore))
                .explanationNarrative(narrativeExplanation)
                .incidentId(incidentId)
                .build();
    }

    private void dispatchCriticalAlert(Merchant merchant, FraudIncident incident, String details) {
        try {
            // 1. Create Alert in DB
            Alert alert = alertService.createAlert(
                    null,
                    merchant,
                    "FRAUD_SPIKE_CRITICAL",
                    "CRITICAL",
                    details
            );

            // 2. Publish RiskAlertCreatedEvent to Kafka topic risk.alert.created
            if (kafkaTemplate != null) {
                RiskAlertCreatedEvent event = RiskAlertCreatedEvent.builder()
                        .eventId("evt_" + UUID.randomUUID().toString().replace("-", ""))
                        .eventType(RiskAlertCreatedEvent.EVENT_TYPE)
                        .eventVersion("v1.0.0")
                        .occurredAt(Instant.now())
                        .transactionId(null)
                        .merchantId(merchant.getId())
                        .correlationId("spike-" + incident.getIncidentId())
                        .alertId(alert.getId())
                        .alertType("FRAUD_SPIKE_CRITICAL")
                        .severity("CRITICAL")
                        .status("OPEN")
                        .details(details)
                        .build();

                kafkaTemplate.send(
                        KafkaTopicConfig.TOPIC_RISK_ALERT_CREATED,
                        merchant.getId(),
                        objectMapper.writeValueAsString(event)
                );
                log.info("Dispatched Kafka alert event for CRITICAL fraud spike: incidentId={}", incident.getIncidentId());
            }

            // 3. Record Audit event
            auditService.recordEvent(
                    null,
                    "FraudIncident",
                    incident.getIncidentId(),
                    "CRITICAL_SPIKE_DETECTED",
                    "SYSTEM",
                    details
            );
        } catch (Exception e) {
            log.error("Failed to dispatch critical alert for incident {}: {}", incident.getIncidentId(), e.getMessage(), e);
        }
    }

    private BaselineStats calculateRollingBaseline(String merchantId, Instant baselineEnd) {
        // Divide prior 24 hours into four 6-hour historical slices
        Duration sliceDuration = Duration.ofHours(6);
        List<Double> historicalRates = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            Instant sliceEnd = baselineEnd.minus(sliceDuration.multipliedBy(i));
            Instant sliceStart = sliceEnd.minus(sliceDuration);

            TimeWindowMetrics sliceMetrics = merchantMetricsService.calculateWindowMetrics(
                    merchantId, "hist_" + (i + 1), sliceStart, sliceEnd
            );

            if (sliceMetrics.getTotalTransactions() > 0) {
                historicalRates.add(sliceMetrics.getFraudRate());
            }
        }

        if (historicalRates.isEmpty()) {
            return new BaselineStats(DEFAULT_BASELINE_RATE, MIN_VARIANCE_FLOOR);
        }

        // Calculate mean
        double sum = 0.0;
        for (double r : historicalRates) {
            sum += r;
        }
        double mean = sum / historicalRates.size();

        // Calculate sample standard deviation
        double varianceSum = 0.0;
        for (double r : historicalRates) {
            varianceSum += Math.pow(r - mean, 2);
        }
        double stdDev = historicalRates.size() > 1
                ? Math.sqrt(varianceSum / (historicalRates.size() - 1))
                : MIN_VARIANCE_FLOOR;

        return new BaselineStats(
                mean > 0.0001 ? mean : DEFAULT_BASELINE_RATE,
                Math.max(stdDev, MIN_VARIANCE_FLOOR)
        );
    }

    private String buildExplanation(
            IncidentSeverity severity,
            String currentRate,
            String baselineRate,
            String increasePct,
            long affected,
            String exposureFormatted,
            double zScore
    ) {
        if (severity == IncidentSeverity.NORMAL) {
            return String.format(
                    "Activity within normal parameters. Current fraud rate: %s (Historical baseline: %s, z-score: %.2f).",
                    currentRate, baselineRate, zScore
            );
        }

        return String.format(
                "Normal fraud rate: %s. Current: %s. Increase: %s. Affected: %d. Exposure: %s. (Statistical z-score: %.2f, Severity: %s)",
                baselineRate, currentRate, increasePct, affected, exposureFormatted, zScore, severity
        );
    }

    private Duration parseDurationFromLabel(String label) {
        if (label == null) return Duration.ofMinutes(15);
        return switch (label.toLowerCase()) {
            case "5m" -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "1h" -> Duration.ofHours(1);
            case "6h" -> Duration.ofHours(6);
            case "24h" -> Duration.ofHours(24);
            default -> Duration.ofMinutes(15);
        };
    }

    private double roundTo4Decimals(double val) {
        return Math.round(val * 10000.0) / 10000.0;
    }

    private double roundTo2Decimals(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    public static class BaselineStats {
        public final double meanRate;
        public final double stdDev;

        public BaselineStats(double meanRate, double stdDev) {
            this.meanRate = meanRate;
            this.stdDev = stdDev;
        }
    }
}
