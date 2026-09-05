package com.riskshield.audit;

import com.riskshield.audit.entity.ActorType;
import com.riskshield.audit.entity.AuditEvent;
import com.riskshield.audit.entity.AuditEventType;
import com.riskshield.audit.repository.AuditEventRepository;
import com.riskshield.merchant.entity.Merchant;
import com.riskshield.merchant.repository.MerchantRepository;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private static final String MERCHANT_A = "mer_aud_001";
    private static final String MERCHANT_B = "mer_aud_002";
    private static final String TX_A = "tx_aud_001";
    private static final String INCIDENT_A = "inc_aud_001";

    @BeforeEach
    void setupTestData() {
        Merchant mA = merchantRepository.findById(MERCHANT_A).orElseGet(() ->
                merchantRepository.save(Merchant.builder()
                        .id(MERCHANT_A)
                        .name("Audit Merchant Alpha")
                        .email("alpha@riskshield.internal")
                        .riskTier("LOW")
                        .active(true)
                        .build()));

        Merchant mB = merchantRepository.findById(MERCHANT_B).orElseGet(() ->
                merchantRepository.save(Merchant.builder()
                        .id(MERCHANT_B)
                        .name("Audit Merchant Beta")
                        .email("beta@riskshield.internal")
                        .riskTier("MEDIUM")
                        .active(true)
                        .build()));

        Transaction tx = transactionRepository.findById(TX_A).orElseGet(() ->
                transactionRepository.save(Transaction.builder()
                        .id(TX_A)
                        .merchant(mA)
                        .amountInPaise(250000L)
                        .currency("INR")
                        .paymentMethod("card")
                        .paymentStatus("SUCCESS")
                        .createdAt(Instant.now().minusSeconds(60))
                        .build()));

        // Seed chronological audit trail for TX_A
        auditEventRepository.save(AuditEvent.builder()
                .auditId("aud_tx_01_" + UUID.randomUUID().toString().substring(0, 8))
                .eventType(AuditEventType.TRANSACTION_RECEIVED)
                .actorType(ActorType.MERCHANT)
                .actorId(MERCHANT_A)
                .merchantId(MERCHANT_A)
                .transaction(tx)
                .service("transaction-service")
                .details("Transaction received via payment gateway")
                .createdAt(Instant.now().minusSeconds(50))
                .build());

        auditEventRepository.save(AuditEvent.builder()
                .auditId("aud_tx_02_" + UUID.randomUUID().toString().substring(0, 8))
                .eventType(AuditEventType.FEATURES_GENERATED)
                .actorType(ActorType.SYSTEM)
                .actorId("FEATURE_ENGINE")
                .merchantId(MERCHANT_A)
                .transaction(tx)
                .service("feature-service")
                .details("Generated velocity features")
                .createdAt(Instant.now().minusSeconds(40))
                .build());

        auditEventRepository.save(AuditEvent.builder()
                .auditId("aud_tx_03_" + UUID.randomUUID().toString().substring(0, 8))
                .eventType(AuditEventType.MODEL_SCORED)
                .actorType(ActorType.ML_SERVICE)
                .actorId("v1.0.0-xgboost")
                .merchantId(MERCHANT_A)
                .transaction(tx)
                .service("ml-service")
                .details("Model scored with risk 42.0")
                .createdAt(Instant.now().minusSeconds(30))
                .build());

        auditEventRepository.save(AuditEvent.builder()
                .auditId("aud_inc_01_" + UUID.randomUUID().toString().substring(0, 8))
                .eventType(AuditEventType.INCIDENT_CREATED)
                .actorType(ActorType.SYSTEM)
                .actorId("SPIKE_DETECTOR")
                .merchantId(MERCHANT_A)
                .entityType("FraudIncident")
                .entityId(INCIDENT_A)
                .service("spike-detector-service")
                .details("Critical fraud incident triggered")
                .createdAt(Instant.now().minusSeconds(20))
                .build());
    }

    @Test
    @DisplayName("1. MerchantViewer can query chronological audit trail for own transaction")
    void testMerchantViewerCanQueryOwnTransactionAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/audit/" + TX_A)
                        .header("X-User-Role", "MERCHANT_VIEWER")
                        .header("X-Merchant-Id", MERCHANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$.data[0].event_type").value("TRANSACTION_RECEIVED"))
                .andExpect(jsonPath("$.data[1].event_type").value("FEATURES_GENERATED"))
                .andExpect(jsonPath("$.data[2].event_type").value("MODEL_SCORED"));
    }

    @Test
    @DisplayName("2. MerchantViewer cannot query audit trail for another merchant's transaction (403 Forbidden)")
    void testMerchantViewerCrossMerchantAuditForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/audit/" + TX_A)
                        .header("X-User-Role", "MERCHANT_VIEWER")
                        .header("X-Merchant-Id", MERCHANT_B))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("3. Structured chronological transaction timeline endpoint returns sorted events")
    void testGetTransactionTimeline() throws Exception {
        mockMvc.perform(get("/api/v1/audit/" + TX_A + "/timeline")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "admin_lead"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transaction_id").value(TX_A))
                .andExpect(jsonPath("$.data.merchant_id").value(MERCHANT_A))
                .andExpect(jsonPath("$.data.event_count", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.events", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @DisplayName("4. Query incident audit trail returns chronological incident events")
    void testGetIncidentAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/audit/incidents/" + INCIDENT_A)
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "admin_lead"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].event_type").value("INCIDENT_CREATED"));
    }

    @Test
    @DisplayName("5. Cross-merchant incident audit trail access is rejected (403 Forbidden)")
    void testCrossMerchantIncidentAuditForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/audit/incidents/" + INCIDENT_A)
                        .header("X-User-Role", "MERCHANT_VIEWER")
                        .header("X-Merchant-Id", MERCHANT_B))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("6. Ordinary users cannot create, update, or delete audit records through normal APIs")
    void testAuditEndpointsAreImmutableFromNormalUserApis() throws Exception {
        // Attempting to POST /api/v1/audit -> 405 Method Not Allowed
        mockMvc.perform(post("/api/v1/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"malicious modification\"}")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "admin_malicious"))
                .andExpect(status().isMethodNotAllowed());

        // Attempting to PUT /api/v1/audit/tx_123 -> 405 Method Not Allowed
        mockMvc.perform(put("/api/v1/audit/tx_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"tampered audit trail\"}")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "admin_malicious"))
                .andExpect(status().isMethodNotAllowed());

        // Attempting to DELETE /api/v1/audit/tx_123 -> 405 Method Not Allowed
        mockMvc.perform(delete("/api/v1/audit/tx_123")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "admin_malicious"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("7. Audit query supports multi-field filtering by eventType, merchantId, service")
    void testAuditQueryMultiFieldFiltering() throws Exception {
        mockMvc.perform(get("/api/v1/audit")
                        .param("eventType", "FEATURES_GENERATED")
                        .param("merchantId", MERCHANT_A)
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "admin_super"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.content[0].event_type").value("FEATURES_GENERATED"))
                .andExpect(jsonPath("$.data.content[0].merchant_id").value(MERCHANT_A));
    }
}
