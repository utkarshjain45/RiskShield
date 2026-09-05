package com.riskshield.spike;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.spike.controller.IncidentController;
import com.riskshield.spike.dto.*;
import com.riskshield.spike.entity.IncidentSeverity;
import com.riskshield.spike.entity.IncidentStatus;
import com.riskshield.spike.service.FraudSpikeDetectorService;
import com.riskshield.spike.service.IncidentManagementService;
import com.riskshield.spike.service.MerchantMetricsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IncidentController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IncidentManagementService incidentManagementService;

    @MockBean
    private FraudSpikeDetectorService fraudSpikeDetectorService;

    @MockBean
    private MerchantMetricsService merchantMetricsService;

    @Test
    @DisplayName("GET /api/v1/incidents returns paginated incident list")
    void testGetIncidentsEndpoint() throws Exception {
        FraudIncidentDto dto = FraudIncidentDto.builder()
                .incidentId("inc_test_100")
                .merchantId("mer_001")
                .severity(IncidentSeverity.CRITICAL)
                .status(IncidentStatus.OPEN)
                .baselineRate(0.018)
                .currentRate(0.124)
                .percentageIncrease(588.89)
                .affectedTransactions(326L)
                .estimatedExposure(274000000L)
                .explanationSummary("Critical spike active")
                .detectedAt(Instant.now())
                .build();

        Page<FraudIncidentDto> page = new PageImpl<>(List.of(dto));
        when(incidentManagementService.getIncidents(eq("mer_001"), eq(IncidentStatus.OPEN), any(PageRequest.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/incidents")
                        .param("merchantId", "mer_001")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].incident_id", is("inc_test_100")))
                .andExpect(jsonPath("$.data.content[0].severity", is("CRITICAL")))
                .andExpect(jsonPath("$.data.content[0].affected_transactions", is(326)));
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{id} returns single incident details")
    void testGetIncidentByIdEndpoint() throws Exception {
        FraudIncidentDto dto = FraudIncidentDto.builder()
                .incidentId("inc_test_100")
                .merchantId("mer_001")
                .severity(IncidentSeverity.CRITICAL)
                .status(IncidentStatus.OPEN)
                .baselineRate(0.018)
                .currentRate(0.124)
                .percentageIncrease(588.89)
                .affectedTransactions(326L)
                .estimatedExposure(274000000L)
                .detectedAt(Instant.now())
                .build();

        when(incidentManagementService.getIncidentById("inc_test_100")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/incidents/inc_test_100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.incident_id", is("inc_test_100")))
                .andExpect(jsonPath("$.data.merchant_id", is("mer_001")));
    }

    @Test
    @DisplayName("POST /api/v1/incidents/{id}/acknowledge transitions status")
    void testAcknowledgeIncidentEndpoint() throws Exception {
        FraudIncidentDto updated = FraudIncidentDto.builder()
                .incidentId("inc_test_100")
                .status(IncidentStatus.ACKNOWLEDGED)
                .acknowledgedAt(Instant.now())
                .build();

        when(incidentManagementService.acknowledgeIncident(eq("inc_test_100"), eq("investigator_007")))
                .thenReturn(updated);

        mockMvc.perform(post("/api/v1/incidents/inc_test_100/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("actor_id", "investigator_007"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("ACKNOWLEDGED")))
                .andExpect(jsonPath("$.message", containsString("acknowledged successfully")));
    }

    @Test
    @DisplayName("POST /api/v1/incidents/{id}/resolve marks incident resolved")
    void testResolveIncidentEndpoint() throws Exception {
        FraudIncidentDto updated = FraudIncidentDto.builder()
                .incidentId("inc_test_100")
                .status(IncidentStatus.RESOLVED)
                .resolvedAt(Instant.now())
                .explanationSummary("Resolved: Merchant notified and attacker IP range blacklisted")
                .build();

        when(incidentManagementService.resolveIncident(eq("inc_test_100"), eq("investigator_007"), eq("IP block applied")))
                .thenReturn(updated);

        mockMvc.perform(post("/api/v1/incidents/inc_test_100/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "actor_id", "investigator_007",
                                "notes", "IP block applied"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("RESOLVED")))
                .andExpect(jsonPath("$.message", containsString("resolved successfully")));
    }

    @Test
    @DisplayName("GET /api/v1/incidents/metrics/{merchantId} returns multi-window metrics")
    void testGetMerchantMetricsEndpoint() throws Exception {
        TimeWindowMetrics m5m = TimeWindowMetrics.builder()
                .window("5m")
                .totalTransactions(40)
                .suspiciousTransactions(5)
                .fraudRate(0.125)
                .fraudRatePercentage("12.5%")
                .fraudExposureFormatted("₹45,000")
                .build();

        MerchantMultiWindowMetricsDto metricsDto = MerchantMultiWindowMetricsDto.builder()
                .merchantId("mer_001")
                .calculatedAt(Instant.now())
                .metrics5m(m5m)
                .allWindows(Map.of("5m", m5m))
                .build();

        when(merchantMetricsService.getMultiWindowMetrics(eq("mer_001"), any())).thenReturn(metricsDto);

        mockMvc.perform(get("/api/v1/incidents/metrics/mer_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.merchant_id", is("mer_001")))
                .andExpect(jsonPath("$.data.metrics_5m.fraud_rate_percentage", is("12.5%")));
    }

    @Test
    @DisplayName("POST /api/v1/incidents/scan/{merchantId} triggers on-demand statistical spike detection")
    void testScanForSpikesEndpoint() throws Exception {
        SpikeDetectionResultDto resultDto = SpikeDetectionResultDto.builder()
                .merchantId("mer_001")
                .severity(IncidentSeverity.CRITICAL)
                .timeWindow("15m")
                .baselineRate(0.018)
                .currentRate(0.124)
                .percentageIncrease(588.89)
                .affectedTransactions(326)
                .estimatedExposureFormatted("₹27,40,000")
                .zScore(8.83)
                .explanationNarrative("Normal fraud rate: 1.8%. Current: 12.4%. Increase: 589%. Affected: 326. Exposure: ₹27,40,000.")
                .incidentId("inc_scan_001")
                .build();

        when(fraudSpikeDetectorService.detectSpike(eq("mer_001"), eq("15m"), any())).thenReturn(resultDto);

        mockMvc.perform(post("/api/v1/incidents/scan/mer_001").param("window", "15m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.severity", is("CRITICAL")))
                .andExpect(jsonPath("$.data.estimated_exposure_formatted", is("₹27,40,000")))
                .andExpect(jsonPath("$.data.affected_transactions", is(326)));
    }
}
