package com.riskshield.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.alert.entity.Alert;
import com.riskshield.alert.service.AlertService;
import com.riskshield.common.enums.RiskDecisionType;
import com.riskshield.event.config.KafkaTopicConfig;
import com.riskshield.event.consumer.FraudAlertConsumer;
import com.riskshield.event.consumer.RiskDecisionConsumer;
import com.riskshield.event.consumer.RiskEventConsumer;
import com.riskshield.event.dto.PaymentCreatedEvent;
import com.riskshield.event.dto.RiskDecisionedEvent;
import com.riskshield.event.dto.RiskScoredEvent;
import com.riskshield.event.idempotency.IdempotencyService;
import com.riskshield.feature.dto.CustomerVelocityDto;
import com.riskshield.feature.dto.DeviceVelocityDto;
import com.riskshield.feature.dto.FeatureSnapshot;
import com.riskshield.feature.dto.IpVelocityDto;
import com.riskshield.feature.service.BehavioralFeatureService;
import com.riskshield.merchant.entity.Merchant;
import com.riskshield.merchant.repository.MerchantRepository;
import com.riskshield.policy.entity.RiskDecision;
import com.riskshield.policy.entity.RiskPolicy;
import com.riskshield.policy.service.PolicyEvaluationService;
import com.riskshield.risk.client.MlServiceClient;
import com.riskshield.risk.client.dto.MlRiskScoreResponse;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaEventFlowTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private BehavioralFeatureService behavioralFeatureService;

    @Mock
    private MlServiceClient mlServiceClient;

    @Mock
    private PolicyEvaluationService policyEvaluationService;

    @Mock
    private AlertService alertService;

    private ObjectMapper objectMapper;

    private RiskEventConsumer riskEventConsumer;
    private RiskDecisionConsumer riskDecisionConsumer;
    private FraudAlertConsumer fraudAlertConsumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();

        riskEventConsumer = new RiskEventConsumer(
                objectMapper,
                idempotencyService,
                transactionRepository,
                behavioralFeatureService,
                mlServiceClient,
                kafkaTemplate
        );

        riskDecisionConsumer = new RiskDecisionConsumer(
                objectMapper,
                idempotencyService,
                transactionRepository,
                policyEvaluationService,
                kafkaTemplate
        );

        fraudAlertConsumer = new FraudAlertConsumer(
                objectMapper,
                idempotencyService,
                alertService,
                transactionRepository,
                merchantRepository,
                kafkaTemplate
        );
    }

    @Test
    @DisplayName("RiskEventConsumer: Ingests payment.created, enriches with Redis features, scores via ML, and emits risk.scored")
    void testRiskEventConsumerProcessPaymentCreated() throws Exception {
        PaymentCreatedEvent paymentEvent = PaymentCreatedEvent.builder()
                .eventId("evt_pay_001")
                .eventType(PaymentCreatedEvent.EVENT_TYPE)
                .transactionId("tx_event_100")
                .merchantId("mer_test_001")
                .amountInPaise(499900L)
                .currency("INR")
                .paymentMethod("card")
                .isNewDevice(true)
                .isNewIp(true)
                .correlationId("corr_event_100")
                .build();

        when(idempotencyService.tryAcquire(eq("evt_pay_001"), eq(PaymentCreatedEvent.EVENT_TYPE), eq("tx_event_100"), eq("corr_event_100")))
                .thenReturn(true);

        when(transactionRepository.findByIdWithDetails("tx_event_100"))
                .thenReturn(Optional.empty());

        MlRiskScoreResponse mlResponse = MlRiskScoreResponse.builder()
                .transactionId("tx_event_100")
                .fraudProbability(0.915)
                .riskScore(91.5)
                .modelVersion("v1.0.0-xgboost")
                .inferenceLatencyMs(3.2)
                .topRiskSignals(List.of(
                        MlRiskScoreResponse.MlRiskSignalDto.builder()
                                .featureName("tx_count_5m")
                                .featureValue(6)
                                .shapImpact(0.4)
                                .direction("INCREASES_RISK")
                                .description("Velocity spike")
                                .build()
                ))
                .build();

        when(mlServiceClient.scoreTransaction(any())).thenReturn(mlResponse);

        String messageJson = objectMapper.writeValueAsString(paymentEvent);
        riskEventConsumer.consumePaymentCreated(messageJson);

        // Verify published to risk.scored
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), eq("tx_event_100"), payloadCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopicConfig.TOPIC_RISK_SCORED);
        RiskScoredEvent scoredResult = objectMapper.readValue(payloadCaptor.getValue(), RiskScoredEvent.class);
        assertThat(scoredResult.getRiskScore()).isEqualTo(91.5);
        assertThat(scoredResult.getTransactionId()).isEqualTo("tx_event_100");
    }

    @Test
    @DisplayName("RiskDecisionConsumer: Ingests risk.scored, runs deterministic policy engine, and emits risk.decisioned")
    void testRiskDecisionConsumerProcessRiskScored() throws Exception {
        RiskScoredEvent scoredEvent = RiskScoredEvent.builder()
                .eventId("evt_score_002")
                .eventType(RiskScoredEvent.EVENT_TYPE)
                .transactionId("tx_event_200")
                .merchantId("mer_test_001")
                .riskScore(94.0)
                .fraudProbability(0.94)
                .modelVersion("v1.0.0")
                .correlationId("corr_event_200")
                .build();

        when(idempotencyService.tryAcquire(eq("evt_score_002"), eq(RiskScoredEvent.EVENT_TYPE), eq("tx_event_200"), eq("corr_event_200")))
                .thenReturn(true);

        RiskPolicy mockPolicy = RiskPolicy.builder()
                .id("pol_default_baseline")
                .lowRiskThreshold(30.0)
                .reviewThreshold(70.0)
                .blockThreshold(90.0)
                .build();

        when(policyEvaluationService.resolvePolicyForMerchant("mer_test_001")).thenReturn(mockPolicy);

        RiskDecision mockDecision = RiskDecision.builder()
                .id("dec_200")
                .riskScore(94.0)
                .decision(RiskDecisionType.BLOCK)
                .policyId("pol_default_baseline")
                .policyVersion("v1.0.0")
                .reason("BLOCK because risk score 94 exceeded merchant block threshold 90.")
                .decisionReason("BLOCK because risk score 94 exceeded merchant block threshold 90.")
                .evaluatedAt(Instant.now())
                .build();

        when(policyEvaluationService.evaluateWithPolicy(eq(mockPolicy), any(), eq(94.0))).thenReturn(mockDecision);

        String messageJson = objectMapper.writeValueAsString(scoredEvent);
        riskDecisionConsumer.consumeRiskScored(messageJson);

        // Verify published to risk.decisioned
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), eq("tx_event_200"), payloadCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopicConfig.TOPIC_RISK_DECISIONED);
        RiskDecisionedEvent decisionedResult = objectMapper.readValue(payloadCaptor.getValue(), RiskDecisionedEvent.class);
        assertThat(decisionedResult.getDecision()).isEqualTo(RiskDecisionType.BLOCK);
        assertThat(decisionedResult.getDecisionReason()).contains("exceeded merchant block threshold 90");
    }

    @Test
    @DisplayName("FraudAlertConsumer: Ingests BLOCK decision, creates Alert, and dispatches fraud.detected and risk.alert.created")
    void testFraudAlertConsumerOnBlock() throws Exception {
        RiskDecisionedEvent decisionedEvent = RiskDecisionedEvent.builder()
                .eventId("evt_dec_003")
                .eventType(RiskDecisionedEvent.EVENT_TYPE)
                .transactionId("tx_event_300")
                .merchantId("mer_test_001")
                .riskScore(95.0)
                .decision(RiskDecisionType.BLOCK)
                .decisionReason("BLOCK because risk score 95 exceeded block threshold 90.")
                .correlationId("corr_event_300")
                .build();

        when(idempotencyService.tryAcquire(eq("evt_dec_003"), eq(RiskDecisionedEvent.EVENT_TYPE), eq("tx_event_300"), eq("corr_event_300")))
                .thenReturn(true);

        Alert mockAlert = Alert.builder()
                .id("alt_300")
                .alertType("AUTOMATED_FRAUD_BLOCK_ALARM")
                .severity("CRITICAL")
                .status("OPEN")
                .details("Critical fraud breach")
                .build();

        when(alertService.createAlert(any(), any(), eq("AUTOMATED_FRAUD_BLOCK_ALARM"), eq("CRITICAL"), anyString()))
                .thenReturn(mockAlert);

        String messageJson = objectMapper.writeValueAsString(decisionedEvent);
        fraudAlertConsumer.consumeRiskDecisioned(messageJson);

        // Verify alert created
        verify(alertService).createAlert(any(), any(), eq("AUTOMATED_FRAUD_BLOCK_ALARM"), eq("CRITICAL"), anyString());

        // Verify events emitted to fraud.detected and risk.alert.created
        verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_FRAUD_DETECTED), eq("tx_event_300"), anyString());
        verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_RISK_ALERT_CREATED), eq("tx_event_300"), anyString());
    }

    @Test
    @DisplayName("FraudAlertConsumer: ALLOW decisions do not trigger alerts or fraud events")
    void testFraudAlertConsumerOnAllowNoAlert() throws Exception {
        RiskDecisionedEvent allowEvent = RiskDecisionedEvent.builder()
                .eventId("evt_dec_allow_004")
                .eventType(RiskDecisionedEvent.EVENT_TYPE)
                .transactionId("tx_event_400")
                .merchantId("mer_test_001")
                .riskScore(18.0)
                .decision(RiskDecisionType.ALLOW)
                .decisionReason("ALLOW because risk score 18 is within low risk threshold 30.")
                .correlationId("corr_event_400")
                .build();

        when(idempotencyService.tryAcquire(eq("evt_dec_allow_004"), any(), any(), any()))
                .thenReturn(true);

        String messageJson = objectMapper.writeValueAsString(allowEvent);
        fraudAlertConsumer.consumeRiskDecisioned(messageJson);

        verify(alertService, never()).createAlert(any(), any(), any(), any(), any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Idempotency: Duplicate event is rejected and processing is skipped cleanly")
    void testIdempotencyDuplicateSkipped() throws Exception {
        PaymentCreatedEvent duplicateEvent = PaymentCreatedEvent.builder()
                .eventId("evt_dup_999")
                .eventType(PaymentCreatedEvent.EVENT_TYPE)
                .transactionId("tx_event_999")
                .correlationId("corr_dup_999")
                .build();

        when(idempotencyService.tryAcquire(eq("evt_dup_999"), any(), any(), any()))
                .thenReturn(false); // Duplicate!

        String messageJson = objectMapper.writeValueAsString(duplicateEvent);
        riskEventConsumer.consumePaymentCreated(messageJson);

        // Verify ML service and Kafka template were NEVER invoked
        verify(mlServiceClient, never()).scoreTransaction(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}
