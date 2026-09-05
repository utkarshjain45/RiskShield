package com.riskshield.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.audit.entity.AuditEvent;
import com.riskshield.audit.repository.AuditEventRepository;
import com.riskshield.merchant.entity.Merchant;
import com.riskshield.merchant.repository.MerchantRepository;
import com.riskshield.policy.dto.CreatePolicyRequest;
import com.riskshield.policy.entity.RiskDecision;
import com.riskshield.policy.repository.RiskDecisionRepository;
import com.riskshield.razorpay.service.RazorpaySignatureValidator;
import com.riskshield.spike.entity.FraudIncident;
import com.riskshield.spike.entity.IncidentSeverity;
import com.riskshield.spike.entity.IncidentStatus;
import com.riskshield.spike.repository.FraudIncidentRepository;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private FraudIncidentRepository fraudIncidentRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private RiskDecisionRepository riskDecisionRepository;

    @Autowired
    private com.riskshield.policy.repository.RiskPolicyRepository riskPolicyRepository;

    @MockBean
    private RazorpaySignatureValidator signatureValidator;

    private static final String MERCHANT_1 = "mer_sec_001";
    private static final String MERCHANT_2 = "mer_sec_002";
    private static final String TX_MER_1 = "tx_sec_001";
    private static final String TX_MER_2 = "tx_sec_002";
    private static final String INCIDENT_ID = "inc_sec_001";

    @BeforeEach
    void setupTestData() {
        Merchant m1 = merchantRepository.findById(MERCHANT_1).orElseGet(() ->
                merchantRepository.save(Merchant.builder()
                        .id(MERCHANT_1)
                        .name("Secured Merchant 1")
                        .email("m1@riskshield.internal")
                        .riskTier("LOW")
                        .active(true)
                        .build()));

        Merchant m2 = merchantRepository.findById(MERCHANT_2).orElseGet(() ->
                merchantRepository.save(Merchant.builder()
                        .id(MERCHANT_2)
                        .name("Secured Merchant 2")
                        .email("m2@riskshield.internal")
                        .riskTier("MEDIUM")
                        .active(true)
                        .build()));

        if (transactionRepository.findById(TX_MER_1).isEmpty()) {
            transactionRepository.save(Transaction.builder()
                    .id(TX_MER_1)
                    .merchant(m1)
                    .amountInPaise(500000L)
                    .currency("INR")
                    .paymentMethod("card")
                    .paymentStatus("SUCCESS")
                    .createdAt(Instant.now())
                    .build());
        }

        if (transactionRepository.findById(TX_MER_2).isEmpty()) {
            transactionRepository.save(Transaction.builder()
                    .id(TX_MER_2)
                    .merchant(m2)
                    .amountInPaise(1200000L)
                    .currency("INR")
                    .paymentMethod("upi")
                    .paymentStatus("SUCCESS")
                    .createdAt(Instant.now())
                    .build());
        }

        if (fraudIncidentRepository.findById(INCIDENT_ID).isEmpty()) {
            fraudIncidentRepository.save(FraudIncident.builder()
                    .incidentId(INCIDENT_ID)
                    .merchant(m1)
                    .severity(IncidentSeverity.CRITICAL)
                    .status(IncidentStatus.OPEN)
                    .timeWindow("1h")
                    .baselineRate(0.015)
                    .currentRate(0.125)
                    .percentageIncrease(733.3)
                    .affectedTransactions(45L)
                    .estimatedExposure(45000000L)
                    .explanationSummary("Critical fraud surge detected")
                    .detectedAt(Instant.now())
                    .build());
        }

        if (riskPolicyRepository.findById("pol_default").isEmpty()) {
            riskPolicyRepository.save(com.riskshield.policy.entity.RiskPolicy.builder()
                    .id("pol_default")
                    .name("Default System Policy")
                    .policyVersion("v1.0.0")
                    .lowRiskThreshold(30.0)
                    .reviewThreshold(70.0)
                    .blockThreshold(90.0)
                    .enabled(true)
                    .build());
        }
    }

    @Test
    @DisplayName("1. Unauthenticated request without credentials is rejected with 401 Unauthorized")
    void testUnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("2. MERCHANT_VIEWER is permitted to access their assigned merchant's transactions")
    void testMerchantViewerAllowedOwnTransactions() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/" + TX_MER_1)
                        .header("X-User-Role", "MERCHANT_VIEWER")
                        .header("X-Merchant-Id", MERCHANT_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(TX_MER_1));
    }

    @Test
    @DisplayName("3. Cross-merchant data access: MERCHANT_VIEWER for mer_1 is denied access to mer_2 (403 Forbidden)")
    void testCrossMerchantDataAccessForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/" + TX_MER_2)
                        .header("X-User-Role", "MERCHANT_VIEWER")
                        .header("X-Merchant-Id", MERCHANT_1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cross-merchant data access denied")));
    }

    @Test
    @DisplayName("4. Role-based access: MERCHANT_VIEWER cannot create risk policies (403 Forbidden)")
    void testMerchantViewerCannotCreatePolicy() throws Exception {
        CreatePolicyRequest req = CreatePolicyRequest.builder()
                .name("Unauthorized Policy")
                .merchantId(MERCHANT_1)
                .policyVersion("v1.0")
                .lowRiskThreshold(30.0)
                .reviewThreshold(70.0)
                .blockThreshold(90.0)
                .enabled(true)
                .build();

        mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("X-User-Role", "MERCHANT_VIEWER")
                        .header("X-Merchant-Id", MERCHANT_1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. Role-based access: RISK_ANALYST can create risk policies (201 Created)")
    void testRiskAnalystCanCreatePolicy() throws Exception {
        CreatePolicyRequest req = CreatePolicyRequest.builder()
                .name("Analyst Custom Policy " + UUID.randomUUID().toString().substring(0, 8))
                .merchantId(MERCHANT_1)
                .policyVersion("v1.0")
                .lowRiskThreshold(30.0)
                .reviewThreshold(70.0)
                .blockThreshold(90.0)
                .enabled(true)
                .build();

        mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .header("X-User-Role", "RISK_ANALYST")
                        .header("X-User-Id", "analyst_sarah"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("6. MerchantViewer cannot access another merchant's fraud incident (403 Forbidden)")
    void testCrossMerchantIncidentAccessForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/" + INCIDENT_ID)
                        .header("X-User-Role", "MERCHANT_VIEWER")
                        .header("X-Merchant-Id", MERCHANT_2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cross-merchant data access denied")));
    }

    @Test
    @DisplayName("7. MerchantViewer cannot acknowledge or mutate fraud incidents (RBAC 403)")
    void testMerchantViewerCannotMutateIncident() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/" + INCIDENT_ID + "/acknowledge")
                        .header("X-User-Role", "MERCHANT_VIEWER")
                        .header("X-Merchant-Id", MERCHANT_1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8. MerchantViewer cannot access model evaluation held-out test data (RBAC 403)")
    void testMerchantViewerCannotViewModelEvaluation() throws Exception {
        mockMvc.perform(get("/api/v1/model/evaluation/current")
                        .header("X-User-Role", "MERCHANT_VIEWER")
                        .header("X-Merchant-Id", MERCHANT_1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8. Admin has full access to model evaluation and system configurations")
    void testAdminAccessToModelEvaluationAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/model/evaluation/current")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "admin_super"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("9. Immutability: AuditEvent entity cannot be updated or deleted once saved")
    void testImmutableAuditEvent() {
        AuditEvent event = AuditEvent.builder()
                .entityType("SecurityTest")
                .entityId("sec_123")
                .action("SECURITY_AUDIT")
                .actorId("SEC_SYSTEM")
                .details("Initial audit record")
                .build();

        AuditEvent saved = auditEventRepository.saveAndFlush(event);

        assertThrows(UnsupportedOperationException.class, () -> {
            saved.preventUpdate();
        });

        assertThrows(Exception.class, () -> {
            auditEventRepository.delete(saved);
            auditEventRepository.flush();
        });
    }

    @Test
    @DisplayName("10. Immutability: RiskDecision entity cannot be updated or deleted once saved")
    void testImmutableRiskDecision() {
        Merchant m1 = merchantRepository.findById(MERCHANT_1).orElseThrow();
        String uniqueTxId = "tx_sec_imm_" + UUID.randomUUID().toString().substring(0, 8);
        Transaction tx = transactionRepository.saveAndFlush(Transaction.builder()
                .id(uniqueTxId)
                .merchant(m1)
                .amountInPaise(100000L)
                .currency("INR")
                .paymentMethod("upi")
                .paymentStatus("SUCCESS")
                .createdAt(Instant.now())
                .build());

        RiskDecision decision = RiskDecision.builder()
                .id("dec_sec_imm_" + UUID.randomUUID().toString().substring(0, 8))
                .transaction(tx)
                .riskScore(45.0)
                .decision(com.riskshield.common.enums.RiskDecisionType.REVIEW)
                .policyId("pol_default")
                .policyVersion("v1.0")
                .reason("Deterministic review decision")
                .build();

        RiskDecision saved = riskDecisionRepository.saveAndFlush(decision);

        assertThrows(UnsupportedOperationException.class, () -> {
            saved.preventUpdate();
        });

        assertThrows(Exception.class, () -> {
            riskDecisionRepository.delete(saved);
            riskDecisionRepository.flush();
        });
    }

    @Test
    @DisplayName("11. Webhook security: Stale webhook timestamp (>15 mins old) is rejected as replay attack")
    void testWebhookReplayAttackRejected() throws Exception {
        when(signatureValidator.isValid(anyString(), anyString())).thenReturn(true);

        long staleEpochSec = Instant.now().minusSeconds(1200).getEpochSecond(); // 20 minutes ago
        String stalePayload = """
                {
                  "entity": "event",
                  "event": "payment.authorized",
                  "id": "evt_replay_test_001",
                  "account_id": "acc_sec_001",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_replay_001",
                        "amount": 250000,
                        "currency": "INR",
                        "status": "authorized",
                        "created_at": %d
                      }
                    }
                  }
                }
                """.formatted(staleEpochSec);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stalePayload)
                        .header("X-Razorpay-Signature", "valid_mock_signature")
                        .header("X-Razorpay-Event-Id", "evt_replay_test_001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("UNAUTHORIZED"));
    }
}
