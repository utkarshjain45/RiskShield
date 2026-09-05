package com.riskshield.demo;

import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.demo.dto.SimulationModeDto;
import com.riskshield.demo.dto.SimulationRequest;
import com.riskshield.demo.dto.SimulationStatusDto;
import com.riskshield.demo.entity.DemoSimulation;
import com.riskshield.demo.entity.SimulationMode;
import com.riskshield.demo.entity.SimulationStatus;
import com.riskshield.demo.repository.DemoSimulationRepository;
import com.riskshield.demo.service.DemoSimulationService;
import com.riskshield.demo.service.DemoTrafficGenerator;
import com.riskshield.risk.dto.RiskAssessmentResponse;
import com.riskshield.risk.service.RiskAssessmentService;
import com.riskshield.spike.dto.SpikeDetectionResultDto;
import com.riskshield.spike.service.FraudSpikeDetectorService;
import com.riskshield.transaction.dto.CreateTransactionRequest;
import com.riskshield.transaction.dto.TransactionResponse;
import com.riskshield.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoSimulationServiceTest {

    @Mock
    private DemoSimulationRepository simulationRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private RiskAssessmentService riskAssessmentService;

    @Mock
    private FraudSpikeDetectorService fraudSpikeDetectorService;

    private DemoTrafficGenerator trafficGenerator;
    private DemoSimulationService simulationService;

    @BeforeEach
    void setUp() {
        trafficGenerator = new DemoTrafficGenerator();
        simulationService = new DemoSimulationService(
                simulationRepository,
                trafficGenerator,
                transactionService,
                riskAssessmentService,
                fraudSpikeDetectorService
        );
    }

    @Test
    @DisplayName("Traffic Generator produces valid requests across all 6 modes")
    void testTrafficGeneratorForAllModes() {
        for (SimulationMode mode : SimulationMode.values()) {
            CreateTransactionRequest req = trafficGenerator.generateRequest(mode, "mer_test_001", 1);
            assertThat(req).isNotNull();
            assertThat(req.getMerchantId()).isEqualTo("mer_test_001");
            assertThat(req.getAmountInPaise()).isGreaterThan(0L);
            assertThat(req.getCustomerId()).isNotBlank();
            assertThat(req.getDeviceId()).isNotBlank();
            assertThat(req.getIpAddress()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Coordinated Fraud Spike mode produces abnormal amounts and shared emulator devices")
    void testCoordinatedFraudSpikeCharacteristics() {
        CreateTransactionRequest req = trafficGenerator.generateRequest(SimulationMode.COORDINATED_FRAUD_SPIKE, "mer_test_001", 5);
        assertThat(req.getIsEmulator()).isTrue();
        assertThat(req.getAmountInPaise()).isGreaterThanOrEqualTo(7500000L); // >= ₹75,000
        assertThat(req.getCustomerAccountAgeDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("Velocity Attack mode targets same compromised customer repeatedly")
    void testVelocityAttackCharacteristics() {
        CreateTransactionRequest req1 = trafficGenerator.generateRequest(SimulationMode.VELOCITY_ATTACK, "mer_test_001", 1);
        CreateTransactionRequest req2 = trafficGenerator.generateRequest(SimulationMode.VELOCITY_ATTACK, "mer_test_001", 2);

        assertThat(req1.getCustomerId()).isEqualTo(req2.getCustomerId());
        assertThat(req1.getDeviceId()).isEqualTo(req2.getDeviceId());
    }

    @Test
    @DisplayName("Start simulation creates running session and returns DTO")
    void testStartSimulation() {
        when(simulationRepository.findFirstByStatusOrderByStartedAtDesc(SimulationStatus.RUNNING))
                .thenReturn(Optional.empty());

        when(simulationRepository.save(any(DemoSimulation.class))).thenAnswer(inv -> {
            DemoSimulation s = inv.getArgument(0);
            return s;
        });

        lenient().when(transactionService.createTransaction(any(CreateTransactionRequest.class)))
                .thenReturn(TransactionResponse.builder().id("tx_sim_001").build());
        lenient().when(riskAssessmentService.assessTransaction(anyString()))
                .thenReturn(RiskAssessmentResponse.builder().id("ast_001").decision(RiskDecisionType.BLOCK).build());

        SimulationRequest req = SimulationRequest.builder()
                .mode(SimulationMode.COORDINATED_FRAUD_SPIKE)
                .merchantId("mer_test_001")
                .transactionCount(10)
                .intervalMs(100)
                .build();

        SimulationStatusDto result = simulationService.startSimulation(req);

        assertThat(result).isNotNull();
        assertThat(result.getMode()).isEqualTo(SimulationMode.COORDINATED_FRAUD_SPIKE);
        assertThat(result.getStatus()).isEqualTo(SimulationStatus.RUNNING);
        assertThat(result.getTargetCount()).isEqualTo(10);
        assertThat(result.getMerchantId()).isEqualTo("mer_test_001");
    }

    @Test
    @DisplayName("Stop simulation transitions running session to STOPPED")
    void testStopSimulation() {
        DemoSimulation sim = DemoSimulation.builder()
                .id("sim_test_stop_001")
                .mode(SimulationMode.VELOCITY_ATTACK)
                .status(SimulationStatus.RUNNING)
                .merchantId("mer_test_001")
                .targetCount(30)
                .generatedCount(12)
                .allowedCount(2)
                .reviewCount(5)
                .blockedCount(5)
                .startedAt(Instant.now().minusSeconds(60))
                .build();

        when(simulationRepository.findById("sim_test_stop_001")).thenReturn(Optional.of(sim));
        when(simulationRepository.save(any(DemoSimulation.class))).thenAnswer(inv -> inv.getArgument(0));

        SimulationStatusDto stopped = simulationService.stopSimulation("sim_test_stop_001");

        assertThat(stopped.getStatus()).isEqualTo(SimulationStatus.STOPPED);
        assertThat(stopped.getGeneratedCount()).isEqualTo(12);
        assertThat(stopped.getStoppedAt()).isNotNull();
        assertThat(stopped.getSummary()).contains("Simulation stopped by user");
    }

    @Test
    @DisplayName("Get available modes lists all 6 modes with metadata")
    void testGetAvailableModes() {
        List<SimulationModeDto> modes = simulationService.getAvailableModes();
        assertThat(modes).hasSize(6);
        assertThat(modes).extracting(SimulationModeDto::getMode)
                .containsExactlyInAnyOrder(
                        "NORMAL_TRAFFIC",
                        "VELOCITY_ATTACK",
                        "DEVICE_ABUSE",
                        "IP_CLUSTER_ATTACK",
                        "AMOUNT_ANOMALY",
                        "COORDINATED_FRAUD_SPIKE"
                );
    }
}
