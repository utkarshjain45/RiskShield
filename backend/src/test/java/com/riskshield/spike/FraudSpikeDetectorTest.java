package com.riskshield.spike;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.alert.entity.Alert;
import com.riskshield.alert.service.AlertService;
import com.riskshield.audit.service.AuditService;
import com.riskshield.merchant.entity.Merchant;
import com.riskshield.merchant.repository.MerchantRepository;
import com.riskshield.spike.dto.FraudIncidentDto;
import com.riskshield.spike.dto.MerchantMultiWindowMetricsDto;
import com.riskshield.spike.dto.SpikeDetectionResultDto;
import com.riskshield.spike.dto.TimeWindowMetrics;
import com.riskshield.spike.entity.FraudIncident;
import com.riskshield.spike.entity.IncidentSeverity;
import com.riskshield.spike.entity.IncidentStatus;
import com.riskshield.spike.repository.FraudIncidentRepository;
import com.riskshield.spike.service.FraudSpikeDetectorService;
import com.riskshield.spike.service.IncidentManagementService;
import com.riskshield.spike.service.MerchantMetricsService;
import com.riskshield.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudSpikeDetectorTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private FraudIncidentRepository fraudIncidentRepository;

    @Mock
    private AlertService alertService;

    @Mock
    private AuditService auditService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private MerchantMetricsService merchantMetricsService;
    private FraudSpikeDetectorService fraudSpikeDetectorService;
    private IncidentManagementService incidentManagementService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private Merchant testMerchant;

    @BeforeEach
    void setUp() {
        merchantMetricsService = new MerchantMetricsService(transactionRepository);

        fraudSpikeDetectorService = new FraudSpikeDetectorService(
                merchantMetricsService,
                merchantRepository,
                fraudIncidentRepository,
                alertService,
                auditService,
                objectMapper
        );

        // Inject mock KafkaTemplate using reflection or setter
        try {
            var field = FraudSpikeDetectorService.class.getDeclaredField("kafkaTemplate");
            field.setAccessible(true);
            field.set(fraudSpikeDetectorService, kafkaTemplate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        incidentManagementService = new IncidentManagementService(fraudIncidentRepository, auditService);

        testMerchant = Merchant.builder()
                .id("mer_test_001")
                .name("Test Merchant Electronics")
                .email("ops@testmerchant.com")
                .build();
    }

    @Test
    @DisplayName("Currency formatting correctly handles Indian numbering system")
    void testIndianCurrencyFormatting() {
        assertThat(MerchantMetricsService.formatInrCurrency(0)).isEqualTo("₹0");
        assertThat(MerchantMetricsService.formatInrCurrency(500)).isEqualTo("₹500");
        assertThat(MerchantMetricsService.formatInrCurrency(1500)).isEqualTo("₹1,500");
        assertThat(MerchantMetricsService.formatInrCurrency(100000)).isEqualTo("₹1,00,000");
        assertThat(MerchantMetricsService.formatInrCurrency(2740000)).isEqualTo("₹27,40,000");
        assertThat(MerchantMetricsService.formatInrCurrency(10000000)).isEqualTo("₹1,00,00,000");
    }

    @Test
    @DisplayName("Statistical detector classifies normal fraud rate as NORMAL")
    void testNormalScenarioClassification() {
        // Baseline 1.8%, current 1.9%, stdDev 0.8%
        SpikeDetectionResultDto result = fraudSpikeDetectorService.evaluateAndRecordSpike(
                "mer_test_001",
                "15m",
                0.018,
                0.008,
                0.019,
                4,
                250000L, // ₹2,500
                Instant.now()
        );

        assertThat(result.getSeverity()).isEqualTo(IncidentSeverity.NORMAL);
        assertThat(result.getZScore()).isLessThan(1.0);
        assertThat(result.getExplanationNarrative()).contains("Activity within normal parameters");
        verifyNoInteractions(alertService);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("Statistical detector classifies moderate surge as ELEVATED")
    void testElevatedScenarioClassification() {
        when(merchantRepository.findById("mer_test_001")).thenReturn(Optional.of(testMerchant));
        when(fraudIncidentRepository.findFirstByMerchant_IdAndStatusAndSeverityOrderByDetectedAtDesc(
                eq("mer_test_001"), eq(IncidentStatus.OPEN), eq(IncidentSeverity.ELEVATED)))
                .thenReturn(Optional.empty());
        when(fraudIncidentRepository.save(any(FraudIncident.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Baseline 1.8%, current 5.4%, stdDev 1.2% -> z-score: 3.0, increase: 200%
        SpikeDetectionResultDto result = fraudSpikeDetectorService.evaluateAndRecordSpike(
                "mer_test_001",
                "15m",
                0.018,
                0.012,
                0.054,
                15,
                15000000L, // ₹1,50,000
                Instant.now()
        );

        assertThat(result.getSeverity()).isEqualTo(IncidentSeverity.ELEVATED);
        assertThat(result.getZScore()).isGreaterThanOrEqualTo(2.0);
        assertThat(result.getPercentageIncrease()).isGreaterThan(100.0);
        assertThat(result.getAffectedTransactions()).isEqualTo(15);
        assertThat(result.getEstimatedExposureFormatted()).isEqualTo("₹1,50,000");
        assertThat(result.getExplanationNarrative()).contains("Normal fraud rate: 1.8%");
        assertThat(result.getExplanationNarrative()).contains("Current: 5.4%");

        // Elevated incidents are recorded, but CRITICAL alert is not triggered
        verify(fraudIncidentRepository).save(any(FraudIncident.class));
        verifyNoInteractions(alertService);
    }

    @Test
    @DisplayName("Statistical detector flags sudden severe surge as CRITICAL and fires security alert")
    void testCriticalSpikeScenarioMatchingSpecification() {
        when(merchantRepository.findById("mer_test_001")).thenReturn(Optional.of(testMerchant));
        when(fraudIncidentRepository.findFirstByMerchant_IdAndStatusAndSeverityOrderByDetectedAtDesc(
                eq("mer_test_001"), eq(IncidentStatus.OPEN), eq(IncidentSeverity.CRITICAL)))
                .thenReturn(Optional.empty());
        when(fraudIncidentRepository.save(any(FraudIncident.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Alert mockAlert = Alert.builder()
                .id("alt_spike_123")
                .merchant(testMerchant)
                .alertType("FRAUD_SPIKE_CRITICAL")
                .severity("CRITICAL")
                .status("OPEN")
                .details("Critical spike")
                .build();
        when(alertService.createAlert(isNull(), eq(testMerchant), eq("FRAUD_SPIKE_CRITICAL"), eq("CRITICAL"), anyString()))
                .thenReturn(mockAlert);

        // Specification Example:
        // Normal fraud rate: 1.8%
        // Current: 12.4%
        // Increase: ~588%
        // Affected: 326
        // Exposure: ₹27,40,000 (274000000 paise)
        double baselineRate = 0.018;
        double currentRate = 0.124;
        double stdDev = 0.012;
        long affected = 326;
        long exposurePaise = 274000000L; // ₹27,40,000

        SpikeDetectionResultDto result = fraudSpikeDetectorService.evaluateAndRecordSpike(
                "mer_test_001",
                "15m",
                baselineRate,
                stdDev,
                currentRate,
                affected,
                exposurePaise,
                Instant.now()
        );

        assertThat(result.getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(result.getBaselineRateFormatted()).isEqualTo("1.8%");
        assertThat(result.getCurrentRateFormatted()).isEqualTo("12.4%");
        assertThat(result.getPercentageIncrease()).isBetween(588.0, 590.0);
        assertThat(result.getAffectedTransactions()).isEqualTo(326);
        assertThat(result.getEstimatedExposureFormatted()).isEqualTo("₹27,40,000");
        assertThat(result.getZScore()).isGreaterThan(3.5);

        // Verify narrative explanation
        String explanation = result.getExplanationNarrative();
        assertThat(explanation).contains("Normal fraud rate: 1.8%");
        assertThat(explanation).contains("Current: 12.4%");
        assertThat(explanation).contains("Affected: 326");
        assertThat(explanation).contains("Exposure: ₹27,40,000");

        // Verify database incident persisted
        ArgumentCaptor<FraudIncident> incidentCaptor = ArgumentCaptor.forClass(FraudIncident.class);
        verify(fraudIncidentRepository).save(incidentCaptor.capture());
        FraudIncident savedIncident = incidentCaptor.getValue();
        assertThat(savedIncident.getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(savedIncident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(savedIncident.getAffectedTransactions()).isEqualTo(326);
        assertThat(savedIncident.getEstimatedExposure()).isEqualTo(274000000L);

        // Verify AlertService triggered
        verify(alertService).createAlert(isNull(), eq(testMerchant), eq("FRAUD_SPIKE_CRITICAL"), eq("CRITICAL"), contains("Exposure: ₹27,40,000"));

        // Verify Kafka event published
        verify(kafkaTemplate).send(eq("risk.alert.created"), eq("mer_test_001"), anyString());

        // Verify Audit record
        verify(auditService).recordEvent(isNull(), eq("FraudIncident"), eq(savedIncident.getIncidentId()), eq("CRITICAL_SPIKE_DETECTED"), eq("SYSTEM"), anyString());
    }

    @Test
    @DisplayName("Incident management lifecycle: OPEN -> ACKNOWLEDGED -> RESOLVED")
    void testIncidentLifecycleTransitions() {
        FraudIncident incident = FraudIncident.builder()
                .incidentId("inc_test_lifecycle")
                .merchant(testMerchant)
                .severity(IncidentSeverity.CRITICAL)
                .status(IncidentStatus.OPEN)
                .detectedAt(Instant.now().minusSeconds(600))
                .baselineRate(0.018)
                .currentRate(0.124)
                .percentageIncrease(588.89)
                .affectedTransactions(326L)
                .estimatedExposure(274000000L)
                .explanationSummary("Critical spike active")
                .build();

        when(fraudIncidentRepository.findById("inc_test_lifecycle")).thenReturn(Optional.of(incident));
        when(fraudIncidentRepository.save(any(FraudIncident.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Acknowledge
        FraudIncidentDto acknowledged = incidentManagementService.acknowledgeIncident("inc_test_lifecycle", "analyst_alice");
        assertThat(acknowledged.getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
        assertThat(acknowledged.getAcknowledgedAt()).isNotNull();

        // 2. Resolve
        FraudIncidentDto resolved = incidentManagementService.resolveIncident("inc_test_lifecycle", "analyst_alice", "Blocked fraudulent IP ranges");
        assertThat(resolved.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolved.getResolvedAt()).isNotNull();
        assertThat(resolved.getExplanationSummary()).contains("Blocked fraudulent IP ranges");

        // 3. Attempting to acknowledge an already resolved incident throws IllegalStateException
        assertThatThrownBy(() -> incidentManagementService.acknowledgeIncident("inc_test_lifecycle", "analyst_bob"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot acknowledge an already RESOLVED incident");
    }

    @Test
    @DisplayName("Window metrics aggregation parses SQL projection into structured DTO")
    void testWindowMetricsCalculation() {
        // Mock SQL raw return: [total, suspicious, blocked, review, avgAmount, fraudExposure]
        Object[] rawRow = new Object[]{500L, 25L, 15L, 10L, 450000.0, 11250000L};
        when(transactionRepository.calculateMerchantWindowMetricsRaw(eq("mer_test_001"), any(), any()))
                .thenReturn(rawRow);

        TimeWindowMetrics metrics = merchantMetricsService.calculateWindowMetrics(
                "mer_test_001", "15m", Instant.now().minusSeconds(900), Instant.now()
        );

        assertThat(metrics.getWindow()).isEqualTo("15m");
        assertThat(metrics.getTotalTransactions()).isEqualTo(500L);
        assertThat(metrics.getSuspiciousTransactions()).isEqualTo(25L);
        assertThat(metrics.getBlockedTransactions()).isEqualTo(15L);
        assertThat(metrics.getReviewTransactions()).isEqualTo(10L);
        assertThat(metrics.getFraudRate()).isEqualTo(0.05); // 25 / 500 = 5%
        assertThat(metrics.getFraudRatePercentage()).isEqualTo("5.0%");
        assertThat(metrics.getAverageTransactionAmountPaise()).isEqualTo(450000L);
        assertThat(metrics.getAverageTransactionAmountInr()).isEqualTo(4500.0);
        assertThat(metrics.getFraudExposurePaise()).isEqualTo(11250000L);
        assertThat(metrics.getFraudExposureInr()).isEqualTo(112500.0);
        assertThat(metrics.getFraudExposureFormatted()).isEqualTo("₹1,12,500");
    }

    @Test
    @DisplayName("Multi-window query returns 5m, 15m, 1h, 6h, 24h metrics")
    void testMultiWindowMetrics() {
        Object[] rawRow = new Object[]{100L, 2L, 1L, 1L, 200000.0, 400000L};
        when(transactionRepository.calculateMerchantWindowMetricsRaw(eq("mer_test_001"), any(), any()))
                .thenReturn(rawRow);

        MerchantMultiWindowMetricsDto multi = merchantMetricsService.getMultiWindowMetrics("mer_test_001", Instant.now());

        assertThat(multi.getMerchantId()).isEqualTo("mer_test_001");
        assertThat(multi.getMetrics5m()).isNotNull();
        assertThat(multi.getMetrics15m()).isNotNull();
        assertThat(multi.getMetrics1h()).isNotNull();
        assertThat(multi.getMetrics6h()).isNotNull();
        assertThat(multi.getMetrics24h()).isNotNull();
        assertThat(multi.getAllWindows()).containsKeys("5m", "15m", "1h", "6h", "24h");
    }

    @Test
    @DisplayName("Incident listing with pagination and filters")
    void testGetIncidentsPagination() {
        FraudIncident inc1 = FraudIncident.builder()
                .incidentId("inc_001")
                .merchant(testMerchant)
                .severity(IncidentSeverity.CRITICAL)
                .status(IncidentStatus.OPEN)
                .detectedAt(Instant.now())
                .baselineRate(0.018)
                .currentRate(0.124)
                .percentageIncrease(588.0)
                .affectedTransactions(326L)
                .estimatedExposure(274000000L)
                .build();

        Page<FraudIncident> page = new PageImpl<>(List.of(inc1));
        when(fraudIncidentRepository.findByMerchant_IdAndStatusOrderByDetectedAtDesc(
                eq("mer_test_001"), eq(IncidentStatus.OPEN), any(PageRequest.class)))
                .thenReturn(page);

        Page<FraudIncidentDto> result = incidentManagementService.getIncidents(
                "mer_test_001", IncidentStatus.OPEN, PageRequest.of(0, 10)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getIncidentId()).isEqualTo("inc_001");
        assertThat(result.getContent().get(0).getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
    }
}
