package com.riskshield.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.demo.dto.SimulationModeDto;
import com.riskshield.demo.dto.SimulationRequest;
import com.riskshield.demo.dto.SimulationStatusDto;
import com.riskshield.demo.entity.SimulationMode;
import com.riskshield.demo.entity.SimulationStatus;
import com.riskshield.demo.service.DemoSimulationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoSimulationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DemoSimulationService simulationService;

    @Test
    @DisplayName("POST /api/v1/demo/simulations/start returns 201 Created")
    void testStartSimulationEndpoint() throws Exception {
        SimulationRequest request = SimulationRequest.builder()
                .mode(SimulationMode.COORDINATED_FRAUD_SPIKE)
                .merchantId("mer_demo_001")
                .transactionCount(20)
                .intervalMs(300)
                .build();

        SimulationStatusDto statusDto = SimulationStatusDto.builder()
                .id("sim_demo_123")
                .mode(SimulationMode.COORDINATED_FRAUD_SPIKE)
                .modeDisplayName(SimulationMode.COORDINATED_FRAUD_SPIKE.getDisplayName())
                .status(SimulationStatus.RUNNING)
                .merchantId("mer_demo_001")
                .targetCount(20)
                .generatedCount(0)
                .allowedCount(0)
                .reviewCount(0)
                .blockedCount(0)
                .fraudRate(0.0)
                .intervalMs(300)
                .startedAt(Instant.now())
                .build();

        when(simulationService.startSimulation(any(SimulationRequest.class))).thenReturn(statusDto);

        mockMvc.perform(post("/api/v1/demo/simulations/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("sim_demo_123"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.mode").value("COORDINATED_FRAUD_SPIKE"));
    }

    @Test
    @DisplayName("POST /api/v1/demo/simulations/{id}/stop returns 200 OK")
    void testStopSimulationEndpoint() throws Exception {
        SimulationStatusDto statusDto = SimulationStatusDto.builder()
                .id("sim_demo_123")
                .mode(SimulationMode.VELOCITY_ATTACK)
                .status(SimulationStatus.STOPPED)
                .merchantId("mer_demo_001")
                .targetCount(30)
                .generatedCount(15)
                .stoppedAt(Instant.now())
                .build();

        when(simulationService.stopSimulation(eq("sim_demo_123"))).thenReturn(statusDto);

        mockMvc.perform(post("/api/v1/demo/simulations/sim_demo_123/stop")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("STOPPED"))
                .andExpect(jsonPath("$.data.generated_count").value(15));
    }

    @Test
    @DisplayName("GET /api/v1/demo/simulations/{id} returns simulation details")
    void testGetSimulationEndpoint() throws Exception {
        SimulationStatusDto statusDto = SimulationStatusDto.builder()
                .id("sim_demo_123")
                .mode(SimulationMode.DEVICE_ABUSE)
                .status(SimulationStatus.RUNNING)
                .merchantId("mer_demo_001")
                .targetCount(25)
                .generatedCount(10)
                .allowedCount(1)
                .reviewCount(3)
                .blockedCount(6)
                .fraudRate(0.9)
                .startedAt(Instant.now())
                .build();

        when(simulationService.getSimulation(eq("sim_demo_123"))).thenReturn(statusDto);

        mockMvc.perform(get("/api/v1/demo/simulations/sim_demo_123")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("sim_demo_123"))
                .andExpect(jsonPath("$.data.blocked_count").value(6));
    }

    @Test
    @DisplayName("GET /api/v1/demo/simulations/modes returns all 6 simulation modes")
    void testGetModesEndpoint() throws Exception {
        List<SimulationModeDto> modes = List.of(
                SimulationModeDto.builder().mode("NORMAL_TRAFFIC").displayName("Normal Traffic").build(),
                SimulationModeDto.builder().mode("VELOCITY_ATTACK").displayName("Velocity Attack").build(),
                SimulationModeDto.builder().mode("DEVICE_ABUSE").displayName("Device Abuse").build(),
                SimulationModeDto.builder().mode("IP_CLUSTER_ATTACK").displayName("IP Cluster Attack").build(),
                SimulationModeDto.builder().mode("AMOUNT_ANOMALY").displayName("Amount Anomaly").build(),
                SimulationModeDto.builder().mode("COORDINATED_FRAUD_SPIKE").displayName("Coordinated Fraud Spike").build()
        );

        when(simulationService.getAvailableModes()).thenReturn(modes);

        mockMvc.perform(get("/api/v1/demo/simulations/modes")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(6));
    }
}
