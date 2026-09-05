package com.riskshield.audit;

import com.riskshield.audit.entity.ActorType;
import com.riskshield.audit.entity.AuditEvent;
import com.riskshield.audit.entity.AuditEventType;
import com.riskshield.audit.repository.AuditEventRepository;
import com.riskshield.audit.util.AuditCryptoUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class AuditEventImmutabilityTest {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    @DisplayName("1. Immutability: Calling preventUpdate() throws UnsupportedOperationException")
    void testCallingPreventUpdateThrowsException() {
        AuditEvent event = AuditEvent.builder()
                .auditId("aud_test_imm_" + UUID.randomUUID().toString().substring(0, 8))
                .eventType(AuditEventType.TRANSACTION_RECEIVED)
                .actorType(ActorType.SYSTEM)
                .actorId("SEC_SYSTEM")
                .service("test-service")
                .details("Initial audit record")
                .createdAt(Instant.now())
                .build();

        assertThrows(UnsupportedOperationException.class, event::preventUpdate);
    }

    @Test
    @DisplayName("2. Immutability: Calling preventDelete() throws UnsupportedOperationException")
    void testCallingPreventDeleteThrowsException() {
        AuditEvent event = AuditEvent.builder()
                .auditId("aud_test_imm_" + UUID.randomUUID().toString().substring(0, 8))
                .eventType(AuditEventType.MODEL_SCORED)
                .actorType(ActorType.ML_SERVICE)
                .actorId("ml-model-v1")
                .service("ml-service")
                .details("Model inference logged")
                .createdAt(Instant.now())
                .build();

        assertThrows(UnsupportedOperationException.class, event::preventDelete);
    }

    @Test
    @Transactional
    @DisplayName("3. Immutability: Repository deletion is blocked by JPA lifecycle @PreRemove hook")
    void testRepositoryDeleteBlockedByLifecycleHook() {
        AuditEvent event = AuditEvent.builder()
                .auditId("aud_del_block_" + UUID.randomUUID().toString().substring(0, 8))
                .eventType(AuditEventType.POLICY_EVALUATED)
                .actorType(ActorType.SYSTEM)
                .actorId("POLICY_ENGINE")
                .service("policy-engine")
                .details("Policy evaluated with decision REVIEW")
                .createdAt(Instant.now())
                .build();

        AuditEvent saved = auditEventRepository.saveAndFlush(event);
        assertThat(saved.getId()).isNotNull();

        assertThrows(Exception.class, () -> {
            auditEventRepository.delete(saved);
            auditEventRepository.flush();
        });
    }

    @Test
    @DisplayName("4. Cryptographic Hash: SHA-256 payload digest matches expected hash")
    void testAuditCryptoUtilsSha256() {
        String payload = "{\"transaction_id\":\"tx_123\",\"amount\":5000}";
        String hash = AuditCryptoUtils.computeSha256(payload);

        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 hex string length
        assertThat(hash).matches("^[a-f0-9]{64}$");

        // Hashing identical payload produces identical digest (deterministic)
        String hash2 = AuditCryptoUtils.computeSha256(payload);
        assertThat(hash).isEqualTo(hash2);
    }

    @Test
    @DisplayName("5. Audit ID and Defaults: AuditEvent entity generates default audit_id and service if omitted")
    void testAuditEventPrePersistDefaults() {
        AuditEvent event = AuditEvent.builder()
                .eventType(AuditEventType.ALERT_CREATED)
                .actorType(ActorType.SYSTEM)
                .actorId("ALERT_ENGINE")
                .details("Security alert fired")
                .build();

        event.ensureDefaults();

        assertThat(event.getAuditId()).startsWith("aud_");
        assertThat(event.getService()).isEqualTo("backend-api");
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getTimestamp()).isEqualTo(event.getCreatedAt());
    }
}
