package com.riskshield.api.v1.controller;

import com.riskshield.api.v1.dto.PaymentEvaluationRequest;
import com.riskshield.api.v1.dto.RiskDecisionResponse;
import com.riskshield.common.dto.ApiResponse;
import com.riskshield.common.enums.RiskDecisionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Risk Evaluation", description = "Endpoints for real-time risk assessment of payment transactions")
public class PaymentEvaluationController {

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate payment transaction risk and return deterministic decision")
    public ResponseEntity<ApiResponse<RiskDecisionResponse>> evaluatePayment(
            @Valid @RequestBody PaymentEvaluationRequest request) {

        log.info("Received evaluation request for txId: {}, merchantId: {}, amountPaise: {}",
                request.getTransactionId(), request.getMerchantId(), request.getAmountInPaise());

        // Baseline skeleton response conforming to audit contract
        RiskDecisionResponse response = RiskDecisionResponse.builder()
                .decisionId("dec_" + UUID.randomUUID())
                .transactionId(request.getTransactionId())
                .decision(RiskDecisionType.ALLOW)
                .riskScore(0.05)
                .modelVersion("v1.0.0-xgb-prototype")
                .policyVersion("pol_default_v1")
                .policyRuleTriggered("RULE_DEFAULT_ALLOW")
                .contributingSignals(Collections.emptyList())
                .spikeDetected(false)
                .executionTimeMs(12L)
                .evaluatedAt(Instant.now())
                .build();

        return ResponseEntity.ok(ApiResponse.ok("Payment evaluated successfully", response));
    }
}
