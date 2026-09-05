package com.riskshield.risk.client;

import com.riskshield.common.exception.RiskEngineException;
import com.riskshield.risk.client.dto.MlRiskScoreRequest;
import com.riskshield.risk.client.dto.MlRiskScoreResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class MlServiceClient {

    private final RestClient mlRestClient;

    public MlRiskScoreResponse scoreTransaction(MlRiskScoreRequest request) {
        log.info("Dispatching risk evaluation to ML service for txId: {}", request.getTransactionId());
        try {
            MlRiskScoreResponse response = mlRestClient.post()
                    .uri("/api/v1/risk/score")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MlRiskScoreResponse.class);

            if (response == null) {
                throw new RiskEngineException("Received empty response from ML service", HttpStatus.BAD_GATEWAY);
            }

            log.info("Received ML risk response for txId: {} | Score: {} | Prob: {}",
                    response.getTransactionId(), response.getRiskScore(), response.getFraudProbability());
            return response;
        } catch (Exception ex) {
            log.error("ML service invocation failed for txId {}: {}", request.getTransactionId(), ex.getMessage(), ex);
            // Graceful fallback heuristics if ML service is unreachable
            return fallbackScore(request, ex.getMessage());
        }
    }

    private MlRiskScoreResponse fallbackScore(MlRiskScoreRequest req, String reason) {
        log.warn("Invoking deterministic fallback risk scorer for txId: {} due to: {}", req.getTransactionId(), reason);
        double fallbackScore = 15.0; // Default baseline

        // Basic heuristic fallback rules
        if (req.getTxCount5m() != null && req.getTxCount5m() > 5) {
            fallbackScore += 40.0;
        }
        if (req.getAmount() != null && req.getAmount() > 50000.0) {
            fallbackScore += 30.0;
        }
        if (req.getIsNewDevice() != null && req.getIsNewDevice() == 1) {
            fallbackScore += 10.0;
        }

        fallbackScore = Math.min(99.0, fallbackScore);

        return MlRiskScoreResponse.builder()
                .transactionId(req.getTransactionId())
                .fraudProbability(fallbackScore / 100.0)
                .riskScore(fallbackScore)
                .modelVersion("v1.0.0-fallback-heuristic")
                .topRiskSignals(Collections.emptyList())
                .inferenceLatencyMs(0.0)
                .build();
    }
}
