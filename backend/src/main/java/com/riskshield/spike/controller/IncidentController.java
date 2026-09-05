package com.riskshield.spike.controller;

import com.riskshield.common.dto.ApiResponse;
import com.riskshield.spike.dto.FraudIncidentDto;
import com.riskshield.spike.dto.MerchantMultiWindowMetricsDto;
import com.riskshield.spike.dto.SpikeDetectionResultDto;
import com.riskshield.spike.entity.IncidentStatus;
import com.riskshield.spike.service.FraudSpikeDetectorService;
import com.riskshield.spike.service.IncidentManagementService;
import com.riskshield.spike.service.MerchantMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
@Tag(name = "Fraud Spike Incidents", description = "Detection, metrics, and lifecycle management for fraud spike incidents")
public class IncidentController {

    private final IncidentManagementService incidentManagementService;
    private final FraudSpikeDetectorService fraudSpikeDetectorService;
    private final MerchantMetricsService merchantMetricsService;

    @GetMapping
    @Operation(summary = "List fraud incidents with optional merchant and status filters")
    public ResponseEntity<ApiResponse<Page<FraudIncidentDto>>> getIncidents(
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<FraudIncidentDto> incidents = incidentManagementService.getIncidents(
                merchantId, status, PageRequest.of(page, size)
        );
        return ResponseEntity.ok(ApiResponse.ok(incidents));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get fraud incident details by ID")
    public ResponseEntity<ApiResponse<FraudIncidentDto>> getIncidentById(@PathVariable String id) {
        FraudIncidentDto incident = incidentManagementService.getIncidentById(id);
        return ResponseEntity.ok(ApiResponse.ok(incident));
    }

    @PostMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge a fraud incident by an investigator")
    public ResponseEntity<ApiResponse<FraudIncidentDto>> acknowledgeIncident(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String actorId = (body != null && body.containsKey("actor_id"))
                ? body.get("actor_id")
                : "SECURITY_OPERATIONS";
        FraudIncidentDto updated = incidentManagementService.acknowledgeIncident(id, actorId);
        return ResponseEntity.ok(ApiResponse.ok("Incident acknowledged successfully", updated));
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve a fraud incident with resolution notes")
    public ResponseEntity<ApiResponse<FraudIncidentDto>> resolveIncident(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String actorId = (body != null && body.containsKey("actor_id"))
                ? body.get("actor_id")
                : "SECURITY_OPERATIONS";
        String notes = (body != null && body.containsKey("notes"))
                ? body.get("notes")
                : "Mitigation verified";
        FraudIncidentDto updated = incidentManagementService.resolveIncident(id, actorId, notes);
        return ResponseEntity.ok(ApiResponse.ok("Incident resolved successfully", updated));
    }

    @GetMapping("/metrics/{merchantId}")
    @Operation(summary = "Get multi-window metrics (5m, 15m, 1h, 6h, 24h) for a merchant")
    public ResponseEntity<ApiResponse<MerchantMultiWindowMetricsDto>> getMerchantMetrics(
            @PathVariable String merchantId
    ) {
        MerchantMultiWindowMetricsDto metrics = merchantMetricsService.getMultiWindowMetrics(merchantId, null);
        return ResponseEntity.ok(ApiResponse.ok(metrics));
    }

    @PostMapping("/scan/{merchantId}")
    @Operation(summary = "Run on-demand statistical fraud spike detection for a merchant")
    public ResponseEntity<ApiResponse<SpikeDetectionResultDto>> scanForSpikes(
            @PathVariable String merchantId,
            @RequestParam(defaultValue = "15m") String window
    ) {
        SpikeDetectionResultDto result = fraudSpikeDetectorService.detectSpike(merchantId, window, null);
        return ResponseEntity.ok(ApiResponse.ok("Fraud spike analysis completed", result));
    }
}
