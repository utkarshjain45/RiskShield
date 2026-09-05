package com.riskshield.api.v1.controller;

import com.riskshield.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Service Liveness and Readiness Probe")
public class HealthController {

    @GetMapping
    @Operation(summary = "Get service health status")
    public ResponseEntity<ApiResponse<Map<String, String>>> checkHealth() {
        Map<String, String> status = Map.of(
                "status", "UP",
                "service", "riskshield-backend",
                "version", "0.1.0-SNAPSHOT"
        );
        return ResponseEntity.ok(ApiResponse.ok(status));
    }
}
