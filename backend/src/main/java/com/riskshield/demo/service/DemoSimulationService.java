package com.riskshield.demo.service;

import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.common.exception.ResourceNotFoundException;
import com.riskshield.demo.dto.SimulationModeDto;
import com.riskshield.demo.dto.SimulationRequest;
import com.riskshield.demo.dto.SimulationStatusDto;
import com.riskshield.demo.entity.DemoSimulation;
import com.riskshield.demo.entity.SimulationMode;
import com.riskshield.demo.entity.SimulationStatus;
import com.riskshield.demo.repository.DemoSimulationRepository;
import com.riskshield.risk.dto.RiskAssessmentResponse;
import com.riskshield.risk.service.RiskAssessmentService;
import com.riskshield.spike.dto.SpikeDetectionResultDto;
import com.riskshield.spike.service.FraudSpikeDetectorService;
import com.riskshield.transaction.dto.CreateTransactionRequest;
import com.riskshield.transaction.dto.TransactionResponse;
import com.riskshield.transaction.service.TransactionService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoSimulationService {

    private final DemoSimulationRepository simulationRepository;
    private final DemoTrafficGenerator trafficGenerator;
    private final TransactionService transactionService;
    private final RiskAssessmentService riskAssessmentService;
    private final FraudSpikeDetectorService fraudSpikeDetectorService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, Future<?>> activeTasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

    @Transactional
    public SimulationStatusDto startSimulation(SimulationRequest request) {
        String merchantId = (request.getMerchantId() != null && !request.getMerchantId().isBlank())
                ? request.getMerchantId()
                : "mer_demo_001";

        // Stop any currently running simulation
        simulationRepository.findFirstByStatusOrderByStartedAtDesc(SimulationStatus.RUNNING)
                .ifPresent(active -> stopSimulationInternal(active.getId()));

        String simId = "sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        int targetCount = (request.getTransactionCount() != null && request.getTransactionCount() > 0)
                ? request.getTransactionCount()
                : 30;
        int intervalMs = (request.getIntervalMs() != null && request.getIntervalMs() >= 50)
                ? request.getIntervalMs()
                : 350;

        DemoSimulation simulation = DemoSimulation.builder()
                .id(simId)
                .mode(request.getMode())
                .status(SimulationStatus.RUNNING)
                .merchantId(merchantId)
                .targetCount(targetCount)
                .generatedCount(0)
                .allowedCount(0)
                .reviewCount(0)
                .blockedCount(0)
                .intervalMs(intervalMs)
                .startedAt(Instant.now())
                .summary("Demo simulation started: " + request.getMode().getDisplayName())
                .build();

        DemoSimulation saved = simulationRepository.save(simulation);

        AtomicBoolean stopFlag = new AtomicBoolean(false);
        stopFlags.put(simId, stopFlag);

        // Submit background runner
        Future<?> future = scheduler.submit(() -> runSimulationLoop(simId, request.getMode(), merchantId, targetCount, intervalMs, stopFlag));
        activeTasks.put(simId, future);

        log.info("Started demo simulation [{}] mode: {}, target: {}, merchant: {}",
                simId, request.getMode(), targetCount, merchantId);

        return mapToDto(saved);
    }

    public SimulationStatusDto stopSimulation(String simulationId) {
        DemoSimulation sim = stopSimulationInternal(simulationId);
        return mapToDto(sim);
    }

    private DemoSimulation stopSimulationInternal(String simulationId) {
        AtomicBoolean flag = stopFlags.get(simulationId);
        if (flag != null) {
            flag.set(true);
        }

        Future<?> future = activeTasks.remove(simulationId);
        if (future != null) {
            future.cancel(true);
        }

        DemoSimulation sim = simulationRepository.findById(simulationId)
                .orElseThrow(() -> new ResourceNotFoundException("Simulation not found: " + simulationId));

        if (sim.getStatus() == SimulationStatus.RUNNING) {
            sim.setStatus(SimulationStatus.STOPPED);
            sim.setStoppedAt(Instant.now());
            sim.setSummary(String.format("Simulation stopped by user after %d/%d transactions. Fraud rate: %.1f%%",
                    sim.getGeneratedCount(), sim.getTargetCount(), sim.calculateFraudRate() * 100.0));
            sim = simulationRepository.save(sim);
        }

        stopFlags.remove(simulationId);
        return sim;
    }

    @Transactional(readOnly = true)
    public SimulationStatusDto getSimulation(String simulationId) {
        DemoSimulation sim = simulationRepository.findById(simulationId)
                .orElseThrow(() -> new ResourceNotFoundException("Simulation not found: " + simulationId));
        return mapToDto(sim);
    }

    @Transactional(readOnly = true)
    public Optional<SimulationStatusDto> getActiveSimulation() {
        return simulationRepository.findFirstByStatusOrderByStartedAtDesc(SimulationStatus.RUNNING)
                .map(this::mapToDto);
    }

    public List<SimulationModeDto> getAvailableModes() {
        List<SimulationModeDto> list = new ArrayList<>();
        for (SimulationMode mode : SimulationMode.values()) {
            list.add(SimulationModeDto.builder()
                    .mode(mode.name())
                    .displayName(mode.getDisplayName())
                    .description(mode.getDescription())
                    .expectedOutcome(mode.getExpectedOutcome())
                    .build());
        }
        return list;
    }

    private void runSimulationLoop(
            String simId,
            SimulationMode mode,
            String merchantId,
            int targetCount,
            int intervalMs,
            AtomicBoolean stopFlag
    ) {
        int allowed = 0;
        int review = 0;
        int blocked = 0;
        String incidentId = null;

        for (int step = 1; step <= targetCount; step++) {
            if (stopFlag.get() || Thread.currentThread().isInterrupted()) {
                log.info("Simulation [{}] cancelled at step {}/{}", simId, step, targetCount);
                return;
            }

            try {
                // 1. Generate realistic synthetic transaction request
                CreateTransactionRequest req = trafficGenerator.generateRequest(mode, merchantId, step);

                // 2. Ingest transaction through standard pipeline
                TransactionResponse tx = transactionService.createTransaction(req);
                if (tx == null || tx.getId() == null) {
                    continue;
                }

                // 3. Process through authoritative risk engine (Redis features -> ML score -> Policy evaluation -> Audit trail)
                RiskAssessmentResponse assessment = riskAssessmentService.assessTransaction(tx.getId());

                // 4. Update metrics
                if (assessment != null) {
                    RiskDecisionType dec = assessment.getDecision();
                    if (dec == RiskDecisionType.ALLOW) {
                        allowed++;
                    } else if (dec == RiskDecisionType.REVIEW) {
                        review++;
                    } else if (dec == RiskDecisionType.BLOCK) {
                        blocked++;
                    }
                }

                // 5. Trigger statistical fraud spike detector for attack scenarios
                if (mode == SimulationMode.COORDINATED_FRAUD_SPIKE || mode == SimulationMode.VELOCITY_ATTACK) {
                    if (step >= 5 && step % 5 == 0) {
                        try {
                            SpikeDetectionResultDto spike = fraudSpikeDetectorService.detectSpike(merchantId, "15m", Instant.now());
                            if (spike != null && spike.getIncidentId() != null) {
                                incidentId = spike.getIncidentId();
                            }
                        } catch (Exception e) {
                            log.warn("Spike detection check in simulation [{}] had error: {}", simId, e.getMessage());
                        }
                    }
                }

                // 6. Update persistent simulation progress
                final int currentGen = step;
                final int curAllowed = allowed;
                final int curReview = review;
                final int curBlocked = blocked;
                final String curInc = incidentId;

                simulationRepository.findById(simId).ifPresent(s -> {
                    s.setGeneratedCount(currentGen);
                    s.setAllowedCount(curAllowed);
                    s.setReviewCount(curReview);
                    s.setBlockedCount(curBlocked);
                    if (curInc != null) {
                        s.setActiveIncidentId(curInc);
                    }
                    simulationRepository.save(s);
                });

                // Delay between transactions
                Thread.sleep(intervalMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Simulation [{}] interrupted.", simId);
                return;
            } catch (Exception e) {
                log.error("Error generating simulated transaction step {} in [{}]: {}", step, simId, e.getMessage(), e);
            }
        }

        // Finalize completed simulation
        final int finalAllowed = allowed;
        final int finalReview = review;
        final int finalBlocked = blocked;
        final String finalIncident = incidentId;

        simulationRepository.findById(simId).ifPresent(s -> {
            s.setStatus(SimulationStatus.COMPLETED);
            s.setStoppedAt(Instant.now());
            s.setGeneratedCount(targetCount);
            s.setAllowedCount(finalAllowed);
            s.setReviewCount(finalReview);
            s.setBlockedCount(finalBlocked);
            if (finalIncident != null) {
                s.setActiveIncidentId(finalIncident);
            }
            s.setSummary(String.format("Completed %s scenario (%d txs): %d ALLOW, %d REVIEW, %d BLOCK. Fraud Rate: %.1f%%",
                    mode.getDisplayName(), targetCount, finalAllowed, finalReview, finalBlocked, s.calculateFraudRate() * 100.0));
            simulationRepository.save(s);
            log.info("Simulation [{}] COMPLETED. Result: {}", simId, s.getSummary());
        });

        activeTasks.remove(simId);
        stopFlags.remove(simId);
    }

    private SimulationStatusDto mapToDto(DemoSimulation sim) {
        return SimulationStatusDto.builder()
                .id(sim.getId())
                .mode(sim.getMode())
                .modeDisplayName(sim.getMode() != null ? sim.getMode().getDisplayName() : "")
                .modeDescription(sim.getMode() != null ? sim.getMode().getDescription() : "")
                .modeExpectedOutcome(sim.getMode() != null ? sim.getMode().getExpectedOutcome() : "")
                .status(sim.getStatus())
                .merchantId(sim.getMerchantId())
                .targetCount(sim.getTargetCount())
                .generatedCount(sim.getGeneratedCount())
                .allowedCount(sim.getAllowedCount())
                .reviewCount(sim.getReviewCount())
                .blockedCount(sim.getBlockedCount())
                .fraudRate(sim.calculateFraudRate())
                .intervalMs(sim.getIntervalMs())
                .activeIncidentId(sim.getActiveIncidentId())
                .summary(sim.getSummary())
                .startedAt(sim.getStartedAt())
                .stoppedAt(sim.getStoppedAt())
                .build();
    }

    @PreDestroy
    public void cleanup() {
        scheduler.shutdownNow();
    }
}
