package com.riskshield.razorpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.event.producer.PaymentEventProducer;
import com.riskshield.razorpay.dto.RazorpayReplayRequest;
import com.riskshield.razorpay.entity.RazorpayWebhookReceipt;
import com.riskshield.razorpay.repository.RazorpayWebhookReceiptRepository;
import com.riskshield.razorpay.service.RazorpaySignatureValidator;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RazorpayWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RazorpaySignatureValidator signatureValidator;

    @Autowired
    private RazorpayWebhookReceiptRepository receiptRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private com.riskshield.merchant.repository.MerchantRepository merchantRepository;

    @MockBean
    private PaymentEventProducer paymentEventProducer;

    private static final String TEST_SECRET = "rzp_test_mock_webhook_secret";

    @BeforeEach
    void setUp() {
        receiptRepository.deleteAll();
    }

    private String createRazorpayPayload(String eventId, String eventType, String paymentId, long amountPaise) {
        long currentEpochSec = Instant.now().getEpochSecond();
        return """
                {
                  "entity": "event",
                  "account_id": "acc_test_merchant_001",
                  "event": "%s",
                  "contains": ["payment"],
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "%s",
                        "entity": "payment",
                        "amount": %d,
                        "currency": "INR",
                        "status": "authorized",
                        "order_id": "order_test_12345",
                        "method": "card",
                        "captured": false,
                        "email": "customer@example.com",
                        "contact": "+919876543210",
                        "notes": {
                          "merchant_id": "mer_default_001"
                        },
                        "created_at": %d
                      }
                    }
                  },
                  "created_at": %d
                }
                """.formatted(eventType, paymentId, amountPaise, currentEpochSec, currentEpochSec);
    }

    @Test
    @DisplayName("1. Valid signature processes webhook successfully and dispatches to Kafka")
    void testValidSignatureWebhook() throws Exception {
        String eventId = "evt_test_" + UUID.randomUUID();
        String paymentId = "pay_test_" + UUID.randomUUID().toString().substring(0, 10);
        String payload = createRazorpayPayload(eventId, "payment.authorized", paymentId, 150000L);
        String signature = signatureValidator.computeHmacSha256(payload, TEST_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.event_id", is(eventId)))
                .andExpect(jsonPath("$.event_type", is("payment.authorized")))
                .andExpect(jsonPath("$.entity_id", is(paymentId)));

        // Verify receipt persisted
        assertThat(receiptRepository.existsById(eventId)).isTrue();
        RazorpayWebhookReceipt receipt = receiptRepository.findById(eventId).orElseThrow();
        assertThat(receipt.getStatus()).isEqualTo("PROCESSED");
        assertThat(receipt.getAmountInPaise()).isEqualTo(150000L);
        assertThat(receipt.isSignatureVerified()).isTrue();

        // Verify Kafka event published
        verify(paymentEventProducer, times(1)).publishPaymentCreated(any());
    }

    @Test
    @DisplayName("2. Invalid signature returns 401 Unauthorized without dispatching to Kafka")
    void testInvalidSignatureWebhook() throws Exception {
        String eventId = "evt_invalid_" + UUID.randomUUID();
        String payload = createRazorpayPayload(eventId, "payment.authorized", "pay_test_inv", 100000L);
        String bogusSignature = "bad_signature_deadbeef1234567890abcdef";

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", bogusSignature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is("UNAUTHORIZED")));

        // Verify no receipt marked PROCESSED and no Kafka dispatch
        assertThat(receiptRepository.existsById(eventId)).isFalse();
        verify(paymentEventProducer, never()).publishPaymentCreated(any());
    }

    @Test
    @DisplayName("3. Duplicate event returns fast 200 OK with DUPLICATE status and no duplicate Kafka dispatch")
    void testDuplicateEventWebhook() throws Exception {
        String eventId = "evt_dup_" + UUID.randomUUID();
        String paymentId = "pay_test_dup";
        String payload = createRazorpayPayload(eventId, "payment.authorized", paymentId, 200000L);
        String signature = signatureValidator.computeHmacSha256(payload, TEST_SECRET);

        // First delivery
        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")));

        // Second duplicate delivery
        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DUPLICATE")))
                .andExpect(jsonPath("$.event_id", is(eventId)));

        // Kafka should have been invoked exactly once, not twice
        verify(paymentEventProducer, times(1)).publishPaymentCreated(any());
    }

    @Test
    @DisplayName("4. Malformed payload returns 400 Bad Request")
    void testMalformedPayloadWebhook() throws Exception {
        String eventId = "evt_malformed_" + UUID.randomUUID();
        String malformedJson = "{ \"entity\": \"event\", \"broken\": true ";
        String signature = signatureValidator.computeHmacSha256(malformedJson, TEST_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("BAD_REQUEST")));

        verify(paymentEventProducer, never()).publishPaymentCreated(any());
    }

    @Test
    @DisplayName("5. Unknown or non-payment event returns 200 OK and marks receipt IGNORED")
    void testUnknownEventWebhook() throws Exception {
        String eventId = "evt_unknown_" + UUID.randomUUID();
        String unknownPayload = """
                {
                  "entity": "event",
                  "account_id": "acc_test_merchant_001",
                  "event": "subscription.charged",
                  "contains": ["subscription"],
                  "payload": {
                    "subscription": {
                      "entity": {
                        "id": "sub_12345"
                      }
                    }
                  },
                  "created_at": 1600000000
                }
                """;
        String signature = signatureValidator.computeHmacSha256(unknownPayload, TEST_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(unknownPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IGNORED")))
                .andExpect(jsonPath("$.event_id", is(eventId)));

        RazorpayWebhookReceipt receipt = receiptRepository.findById(eventId).orElseThrow();
        assertThat(receipt.getStatus()).isEqualTo("IGNORED");
        verify(paymentEventProducer, never()).publishPaymentCreated(any());
    }

    @Test
    @DisplayName("6. Out-of-order event delivery preserves terminal CAPTURED transaction state")
    void testOutOfOrderEventWebhook() throws Exception {
        String paymentId = "pay_out_of_order_" + UUID.randomUUID().toString().substring(0, 8);

        // Pre-seed an already CAPTURED transaction in the database
        com.riskshield.merchant.entity.Merchant merchant = merchantRepository.findById("mer_default_001")
                .orElseGet(() -> merchantRepository.save(com.riskshield.merchant.entity.Merchant.builder()
                        .id("mer_default_001")
                        .name("Test Merchant")
                        .email("merchant@example.com")
                        .build()));

        Transaction existingTx = Transaction.builder()
                .id(paymentId)
                .merchant(merchant)
                .amountInPaise(350000L)
                .currency("INR")
                .paymentStatus("CAPTURED")
                .paymentMethod("card")
                .createdAt(Instant.now().minusSeconds(120))
                .build();
        transactionRepository.save(existingTx);

        // Incoming out-of-order payment.authorized event arrives late
        String eventId = "evt_late_" + UUID.randomUUID();
        String payload = createRazorpayPayload(eventId, "payment.authorized", paymentId, 350000L);
        String signature = signatureValidator.computeHmacSha256(payload, TEST_SECRET);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .header("X-Razorpay-Event-Id", eventId)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OUT_OF_ORDER")))
                .andExpect(jsonPath("$.entity_id", is(paymentId)));

        // Verify existing transaction status was NOT regressed to AUTHORIZED
        Transaction reloadedTx = transactionRepository.findById(paymentId).orElseThrow();
        assertThat(reloadedTx.getPaymentStatus()).isEqualTo("CAPTURED");

        // Verify receipt recorded with OUT_OF_ORDER status
        RazorpayWebhookReceipt receipt = receiptRepository.findById(eventId).orElseThrow();
        assertThat(receipt.getStatus()).isEqualTo("OUT_OF_ORDER");
    }

    @Test
    @DisplayName("7. Replay endpoint generates valid HMAC signature and processes payload")
    void testReplayEndpoint() throws Exception {
        RazorpayReplayRequest replayReq = RazorpayReplayRequest.builder()
                .eventType("payment.authorized")
                .amountInPaise(499900L)
                .method("upi")
                .email("replay.user@example.com")
                .merchantId("mer_test_replay")
                .build();

        mockMvc.perform(post("/api/v1/webhooks/razorpay/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replayReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.event_type", is("payment.authorized")));

        verify(paymentEventProducer, times(1)).publishPaymentCreated(any());
    }
}
