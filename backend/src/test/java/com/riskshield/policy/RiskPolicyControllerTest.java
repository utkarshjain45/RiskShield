package com.riskshield.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.common.filter.CorrelationIdFilter;
import com.riskshield.policy.dto.*;
import com.riskshield.policy.service.RiskPolicyService;
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

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class RiskPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RiskPolicyService riskPolicyService;

    @Test
    @DisplayName("GET /api/v1/policies should return list of policies")
    void testGetPolicies() throws Exception {
        PolicyResponse response = PolicyResponse.builder()
                .id("pol_test_001")
                .name("Global Baseline")
                .lowRiskThreshold(30.0)
                .reviewThreshold(70.0)
                .blockThreshold(90.0)
                .enabled(true)
                .build();

        when(riskPolicyService.getPolicies(any(), any())).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/policies")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-pol-test-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is("pol_test_001")))
                .andExpect(jsonPath("$.data[0].blockThreshold", is(90.0)));
    }

    @Test
    @DisplayName("POST /api/v1/policies should create policy with valid thresholds")
    void testCreatePolicySuccess() throws Exception {
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .name("Merchant Electronics Policy")
                .lowRiskThreshold(25.0)
                .reviewThreshold(60.0)
                .blockThreshold(85.0)
                .falsePositiveCostWeight(1.2)
                .enabled(true)
                .build();

        PolicyResponse created = PolicyResponse.builder()
                .id("pol_created_123")
                .name(request.getName())
                .lowRiskThreshold(25.0)
                .reviewThreshold(60.0)
                .blockThreshold(85.0)
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        when(riskPolicyService.createPolicy(any(CreatePolicyRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is("pol_created_123")))
                .andExpect(jsonPath("$.data.name", is("Merchant Electronics Policy")));
    }

    @Test
    @DisplayName("POST /api/v1/policies should fail with 400 when missing required fields or threshold bounds")
    void testCreatePolicyValidationFailure() throws Exception {
        CreatePolicyRequest invalidRequest = CreatePolicyRequest.builder()
                .name("") // Blank name
                .lowRiskThreshold(-10.0) // Negative
                .reviewThreshold(150.0) // > 100
                .blockThreshold(null) // Missing
                .build();

        mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @DisplayName("PUT /api/v1/policies/{id} should update policy successfully")
    void testUpdatePolicySuccess() throws Exception {
        UpdatePolicyRequest updateReq = UpdatePolicyRequest.builder()
                .name("Updated Enterprise Policy")
                .blockThreshold(92.0)
                .enabled(true)
                .build();

        PolicyResponse updated = PolicyResponse.builder()
                .id("pol_target_001")
                .name("Updated Enterprise Policy")
                .lowRiskThreshold(30.0)
                .reviewThreshold(70.0)
                .blockThreshold(92.0)
                .enabled(true)
                .build();

        when(riskPolicyService.updatePolicy(eq("pol_target_001"), any(UpdatePolicyRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/policies/pol_target_001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.blockThreshold", is(92.0)));
    }

    @Test
    @DisplayName("POST /api/v1/policies/{id}/evaluate should execute deterministic evaluation")
    void testEvaluatePolicyEndpoint() throws Exception {
        EvaluatePolicyRequest evalReq = EvaluatePolicyRequest.builder()
                .riskScore(94.0)
                .build();

        PolicyEvaluationResponse evalRes = PolicyEvaluationResponse.builder()
                .transactionId("tx_eval_12345")
                .policyId("pol_target_001")
                .policyVersion("v1.0.0")
                .riskScore(94.0)
                .decision(RiskDecisionType.BLOCK)
                .decisionReason("BLOCK because risk score 94 exceeded merchant block threshold 90.")
                .evaluatedAt(Instant.now())
                .build();

        when(riskPolicyService.evaluatePolicy(eq("pol_target_001"), any(EvaluatePolicyRequest.class)))
                .thenReturn(evalRes);

        mockMvc.perform(post("/api/v1/policies/pol_target_001/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.decision", is("BLOCK")))
                .andExpect(jsonPath("$.data.decisionReason", containsString("exceeded merchant block threshold")));
    }
}
