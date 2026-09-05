package com.riskshield.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.policy.entity.RiskDecision;
import com.riskshield.risk.client.MlServiceClient;
import com.riskshield.risk.client.dto.MlRiskScoreResponse;
import com.riskshield.risk.dto.RiskExplanationResponse;
import com.riskshield.risk.entity.RiskAssessment;
import com.riskshield.risk.entity.RiskSignal;
import com.riskshield.risk.service.ExplanationTemplateService;
import com.riskshield.transaction.dto.CreateTransactionRequest;
import com.riskshield.transaction.entity.Transaction;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class RiskExplanationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExplanationTemplateService explanationTemplateService;

    @MockBean
    private MlServiceClient mlServiceClient;

    @MockBean
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    @Test
    @DisplayName("ExplanationTemplateService should produce deterministic explanations for multiple features")
    void testExplanationTemplateDeterminism() {
        RiskSignal sig1 = RiskSignal.builder()
                .signalName("transaction_velocity")
                .displayName("Transaction Velocity")
                .shapImpact(0.27)
                .formattedImpact("+0.27")
                .direction("INCREASES_RISK")
                .build();

        RiskSignal sig2 = RiskSignal.builder()
                .signalName("device_account_count")
                .displayName("Device Account Sharing")
                .shapImpact(0.19)
                .formattedImpact("+0.19")
                .direction("INCREASES_RISK")
                .build();

        List<RiskSignal> signals = List.of(sig1, sig2);

        // Run multiple iterations to verify 100% determinism
        String narrative1 = explanationTemplateService.generateNarrativeExplanation(78.0, "REVIEW", signals);
        String narrative2 = explanationTemplateService.generateNarrativeExplanation(78.0, "REVIEW", signals);
        String narrative3 = explanationTemplateService.generateNarrativeExplanation(78.0, "REVIEW", signals);

        String expected = "This transaction was flagged because the transaction velocity is significantly higher than the customer's normal behavior and the device has recently been associated with multiple accounts.";

        assertThat(narrative1).isEqualTo(expected);
        assertThat(narrative2).isEqualTo(expected);
        assertThat(narrative3).isEqualTo(expected);
    }

    @Test
    @DisplayName("ExplanationTemplateService should handle low-risk approval and single risk signal")
    void testExplanationTemplateLowRiskAndSingleSignal() {
        // Low risk approval
        String approvedNarrative = explanationTemplateService.generateNarrativeExplanation(12.0, "ALLOW", List.of());
        assertThat(approvedNarrative).contains("approved as behavioral and velocity metrics are consistent");

        // Single risk signal
        RiskSignal sig = RiskSignal.builder()
                .signalName("new_device")
                .displayName("New Device Fingerprint")
                .shapImpact(0.14)
                .formattedImpact("+0.14")
                .direction("INCREASES_RISK")
                .build();

        String singleNarrative = explanationTemplateService.generateNarrativeExplanation(65.0, "REVIEW", List.of(sig));
        assertThat(singleNarrative).isEqualTo("This transaction was flagged because the payment originated from an unrecognized hardware device.");
    }

    @Test
    @DisplayName("GET /api/v1/risk/assessments/{transactionId}/explanation should return structured explanation with narrative")
    void testGetAssessmentExplanationEndpoint() throws Exception {
        // 1. Create Transaction
        CreateTransactionRequest createReq = CreateTransactionRequest.builder()
                .merchantId("mer_test_001")
                .customerId("cust_explain_001")
                .customerEmail("customer@test.com")
                .deviceId("dev_explain_001")
                .ipAddress("103.25.120.1")
                .amountInPaise(1500000L) // ₹15,000
                .currency("INR")
                .paymentMethod("card")
                .customerAccountAgeDays(5)
                .isNewDevice(true)
                .isNewIp(true)
                .build();

        String txJson = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String txId = objectMapper.readTree(txJson).get("data").get("id").asText();

        // 2. Mock ML Service Response with normalized signals
        MlRiskScoreResponse mlResponse = MlRiskScoreResponse.builder()
                .transactionId(txId)
                .fraudProbability(0.85)
                .riskScore(85.0)
                .modelVersion("xgb-v1.0.0")
                .inferenceLatencyMs(12.5)
                .topRiskSignals(List.of(
                        MlRiskScoreResponse.MlRiskSignalDto.builder()
                                .featureName("transaction_velocity")
                                .displayName("Transaction Velocity")
                                .featureValue(5)
                                .shapImpact(0.27)
                                .formattedImpact("+0.27")
                                .direction("INCREASES_RISK")
                                .description("Transaction velocity is significantly higher than customer baseline")
                                .build(),
                        MlRiskScoreResponse.MlRiskSignalDto.builder()
                                .featureName("device_account_count")
                                .displayName("Device Account Sharing")
                                .featureValue(4)
                                .shapImpact(0.19)
                                .formattedImpact("+0.19")
                                .direction("INCREASES_RISK")
                                .description("Hardware device shared across multiple distinct accounts")
                                .build(),
                        MlRiskScoreResponse.MlRiskSignalDto.builder()
                                .featureName("amount_deviation")
                                .displayName("Amount Deviation")
                                .featureValue(12.5)
                                .shapImpact(0.18)
                                .formattedImpact("+0.18")
                                .direction("INCREASES_RISK")
                                .description("Transaction amount deviates noticeably from customer average")
                                .build()
                ))
                .build();

        when(mlServiceClient.scoreTransaction(any())).thenReturn(mlResponse);

        // 3. Assess Transaction (POST /api/v1/risk/assess/{transactionId})
        mockMvc.perform(post("/api/v1/risk/assess/" + txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskScore", is(85.0)))
                .andExpect(jsonPath("$.data.contributingSignals", hasSize(3)))
                .andExpect(jsonPath("$.data.contributingSignals[0].displayName", is("Transaction Velocity")))
                .andExpect(jsonPath("$.data.contributingSignals[0].formattedImpact", is("+0.27")));

        // 4. Query Explanation (GET /api/v1/risk/assessments/{transactionId}/explanation)
        mockMvc.perform(get("/api/v1/risk/assessments/" + txId + "/explanation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.transaction_id", is(txId)))
                .andExpect(jsonPath("$.data.risk_score", is(85.0)))
                .andExpect(jsonPath("$.data.fraud_probability", is(0.85)))
                .andExpect(jsonPath("$.data.model_version", is("xgb-v1.0.0")))
                .andExpect(jsonPath("$.data.top_contributing_features", hasSize(3)))
                .andExpect(jsonPath("$.data.top_contributing_features[0].feature_name", is("transaction_velocity")))
                .andExpect(jsonPath("$.data.top_contributing_features[0].display_name", is("Transaction Velocity")))
                .andExpect(jsonPath("$.data.top_contributing_features[0].formatted_impact", is("+0.27")))
                .andExpect(jsonPath("$.data.human_readable_explanation", containsString("This transaction was flagged because")))
                .andExpect(jsonPath("$.data.human_readable_explanation", containsString("transaction velocity is significantly higher")))
                .andExpect(jsonPath("$.data.human_readable_explanation", containsString("device has recently been associated with multiple accounts")));
    }
}
