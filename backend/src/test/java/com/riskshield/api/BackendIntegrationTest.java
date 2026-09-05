package com.riskshield.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.common.filter.CorrelationIdFilter;
import com.riskshield.risk.client.MlServiceClient;
import com.riskshield.risk.client.dto.MlRiskScoreResponse;
import com.riskshield.transaction.dto.CreateTransactionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MlServiceClient mlServiceClient;

    @MockBean
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("Should ingest transaction, assess risk via ML client, evaluate policy, and retrieve audit trail")
    void testEndToEndRiskWorkflow() throws Exception {
        // 1. Create Transaction (POST /api/v1/transactions)
        CreateTransactionRequest createReq = CreateTransactionRequest.builder()
                .merchantId("mer_test_001")
                .customerId("cust_test_999")
                .customerEmail("victim@example.com")
                .deviceId("dev_test_404")
                .ipAddress("185.220.101.5")
                .amountInPaise(499900L) // ₹4,999.00
                .currency("INR")
                .paymentMethod("card")
                .customerAccountAgeDays(45)
                .isNewDevice(true)
                .isNewIp(true)
                .build();

        String createResJson = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-test-12345")
                        .header("X-User-Role", "ADMIN")
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-test-12345"))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.merchantId", is("mer_test_001")))
                .andExpect(jsonPath("$.data.amountInPaise", is(499900)))
                .andReturn().getResponse().getContentAsString();

        String txId = objectMapper.readTree(createResJson).get("data").get("id").asText();

        // 2. Query Transaction by ID (GET /api/v1/transactions/{id})
        mockMvc.perform(get("/api/v1/transactions/" + txId)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(txId)));

        // 3. Mock ML Service Response
        MlRiskScoreResponse mockMlResponse = MlRiskScoreResponse.builder()
                .transactionId(txId)
                .fraudProbability(0.9240)
                .riskScore(92.40)
                .modelVersion("v1.0.0-xgboost")
                .inferenceLatencyMs(2.85)
                .topRiskSignals(List.of(
                        MlRiskScoreResponse.MlRiskSignalDto.builder()
                                .featureName("tx_count_5m")
                                .featureValue(7)
                                .shapImpact(0.42)
                                .direction("INCREASES_RISK")
                                .description("Observed velocity: 7 events (elevates risk)")
                                .build(),
                        MlRiskScoreResponse.MlRiskSignalDto.builder()
                                .featureName("is_new_device")
                                .featureValue(1)
                                .shapImpact(0.28)
                                .direction("INCREASES_RISK")
                                .description("Transaction originates from an unrecognized hardware device")
                                .build()
                ))
                .build();

        when(mlServiceClient.scoreTransaction(any())).thenReturn(mockMlResponse);

        // 4. Assess Risk (POST /api/v1/risk/assess/{transactionId})
        mockMvc.perform(post("/api/v1/risk/assess/" + txId)
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-test-12345")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.transactionId", is(txId)))
                .andExpect(jsonPath("$.data.riskScore", is(92.40)))
                .andExpect(jsonPath("$.data.decision", is("BLOCK"))) // > 85 threshold -> BLOCK
                .andExpect(jsonPath("$.data.modelVersion", is("v1.0.0-xgboost")))
                .andExpect(jsonPath("$.data.contributingSignals", hasSize(2)));

        // 5. Query Risk Assessment (GET /api/v1/risk/assessments/{transactionId})
        mockMvc.perform(get("/api/v1/risk/assessments/" + txId)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.transactionId", is(txId)))
                .andExpect(jsonPath("$.data.decision", is("BLOCK")))
                .andExpect(jsonPath("$.data.contributingSignals[0].signalName", is("tx_count_5m")));

        // 6. Query Immutable Audit Trail (GET /api/v1/audit/{transactionId})
        mockMvc.perform(get("/api/v1/audit/" + txId)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.data[*].action", hasItems("TRANSACTION_RECEIVED", "POLICY_EVALUATED", "MODEL_SCORED")));

        // 7. Query Analytics Summary (GET /api/v1/analytics/summary)
        mockMvc.perform(get("/api/v1/analytics/summary")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalTransactions", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.decisionBreakdown.BLOCK", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Should return 400 when invalid transaction payload is submitted")
    void testValidationFailure() throws Exception {
        CreateTransactionRequest invalidReq = CreateTransactionRequest.builder()
                .merchantId("") // Blank
                .amountInPaise(0L) // Invalid min
                .paymentMethod("")
                .build();

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Role", "ADMIN")
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("Should return 404 for non-existent transaction")
    void testTransactionNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/tx_non_existent_99999")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Transaction not found")));
    }
}
