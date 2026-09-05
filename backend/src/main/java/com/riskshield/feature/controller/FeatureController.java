package com.riskshield.feature.controller;

import com.riskshield.common.dto.ApiResponse;
import com.riskshield.feature.dto.FeatureSnapshot;
import com.riskshield.feature.service.BehavioralFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/features")
@RequiredArgsConstructor
@Tag(name = "Behavioral Feature Engine", description = "Real-time behavioral state, sliding-window velocity counters, and device/IP context via Redis")
public class FeatureController {

    private final BehavioralFeatureService behavioralFeatureService;

    @GetMapping("/transaction/{transactionId}")
    @Operation(summary = "Get behavioral feature snapshot", description = "Retrieve sliding-window velocity and behavioral context for a transaction")
    public ResponseEntity<ApiResponse<FeatureSnapshot>> getTransactionFeatures(
            @PathVariable String transactionId) {
        FeatureSnapshot snapshot = behavioralFeatureService.getSnapshotByTransactionId(transactionId);
        return ResponseEntity.ok(ApiResponse.success(snapshot));
    }
}
