package com.riskshield.policy.controller;

import com.riskshield.common.dto.ApiResponse;
import com.riskshield.policy.dto.*;
import com.riskshield.policy.service.RiskPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
@Tag(name = "Policy Management", description = "Deterministic risk policy engine and merchant threshold configurations")
public class RiskPolicyController {

    private final RiskPolicyService riskPolicyService;

    @GetMapping
    @Operation(summary = "List risk policies", description = "Retrieve configured risk policies filtered by merchant or active state")
    public ResponseEntity<ApiResponse<List<PolicyResponse>>> getPolicies(
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) Boolean enabled) {
        List<PolicyResponse> policies = riskPolicyService.getPolicies(merchantId, enabled);
        return ResponseEntity.ok(ApiResponse.success(policies));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get policy by ID", description = "Retrieve detailed configuration and rules for a specific risk policy")
    public ResponseEntity<ApiResponse<PolicyResponse>> getPolicyById(@PathVariable String id) {
        PolicyResponse policy = riskPolicyService.getPolicyById(id);
        return ResponseEntity.ok(ApiResponse.success(policy));
    }

    @PostMapping
    @Operation(summary = "Create risk policy", description = "Define a new deterministic risk policy with thresholds and discrete rules")
    public ResponseEntity<ApiResponse<PolicyResponse>> createPolicy(
            @Valid @RequestBody CreatePolicyRequest request) {
        PolicyResponse policy = riskPolicyService.createPolicy(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(policy));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update risk policy", description = "Update risk thresholds, false positive weights, and rule sets")
    public ResponseEntity<ApiResponse<PolicyResponse>> updatePolicy(
            @PathVariable String id,
            @Valid @RequestBody UpdatePolicyRequest request) {
        PolicyResponse policy = riskPolicyService.updatePolicy(id, request);
        return ResponseEntity.ok(ApiResponse.success(policy));
    }

    @PostMapping("/{id}/evaluate")
    @Operation(summary = "Evaluate policy directly", description = "Run deterministic policy evaluation against a risk score and transaction")
    public ResponseEntity<ApiResponse<PolicyEvaluationResponse>> evaluatePolicy(
            @PathVariable String id,
            @Valid @RequestBody EvaluatePolicyRequest request) {
        PolicyEvaluationResponse response = riskPolicyService.evaluatePolicy(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
