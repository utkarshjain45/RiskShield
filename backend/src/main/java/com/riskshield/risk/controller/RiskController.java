package com.riskshield.risk.controller;

import com.riskshield.common.dto.ApiResponse;
import com.riskshield.risk.dto.RiskAssessmentResponse;
import com.riskshield.risk.dto.RiskExplanationResponse;
import com.riskshield.risk.service.RiskAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
@Tag(name = "Risk Assessment & Policy Decision", description = "Endpoints for real-time risk scoring, SHAP attributions, and policy decisions")
public class RiskController {

    private final RiskAssessmentService riskAssessmentService;

    @PostMapping("/assess/{transactionId}")
    @Operation(summary = "Assess transaction risk via ML service and execute deterministic policy engine")
    public ResponseEntity<ApiResponse<RiskAssessmentResponse>> assessTransaction(
            @PathVariable("transactionId") String transactionId
    ) {
        RiskAssessmentResponse response = riskAssessmentService.assessTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.ok("Risk assessment completed successfully", response));
    }

    @GetMapping("/assessments/{transactionId}")
    @Operation(summary = "Retrieve risk assessment and contributing signals by transaction ID")
    public ResponseEntity<ApiResponse<RiskAssessmentResponse>> getAssessment(
            @PathVariable("transactionId") String transactionId
    ) {
        RiskAssessmentResponse response = riskAssessmentService.getAssessment(transactionId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/assessments/{transactionId}/explanation")
    @Operation(summary = "Retrieve normalized model explanation with top risk signals and human-readable narrative")
    public ResponseEntity<ApiResponse<RiskExplanationResponse>> getExplanation(
            @PathVariable("transactionId") String transactionId
    ) {
        RiskExplanationResponse response = riskAssessmentService.getTransactionExplanation(transactionId);
        return ResponseEntity.ok(ApiResponse.ok("Model explanation retrieved successfully", response));
    }
}
