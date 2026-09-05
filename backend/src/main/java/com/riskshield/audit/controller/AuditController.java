package com.riskshield.audit.controller;

import com.riskshield.audit.dto.AuditEventResponse;
import com.riskshield.audit.dto.TransactionTimelineDto;
import com.riskshield.audit.service.AuditService;
import com.riskshield.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Trail", description = "Immutable audit event logging and ledger query endpoints")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/{transactionId}")
    @Operation(summary = "Retrieve complete immutable audit trail for a transaction")
    public ResponseEntity<ApiResponse<List<AuditEventResponse>>> getAuditTrail(
            @PathVariable("transactionId") String transactionId
    ) {
        List<AuditEventResponse> events = auditService.getEventsForTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.ok(events));
    }

    @GetMapping("/{transactionId}/timeline")
    @Operation(summary = "Retrieve chronological structured timeline for a transaction")
    public ResponseEntity<ApiResponse<TransactionTimelineDto>> getTransactionTimeline(
            @PathVariable("transactionId") String transactionId
    ) {
        TransactionTimelineDto timeline = auditService.getTransactionTimeline(transactionId);
        return ResponseEntity.ok(ApiResponse.ok(timeline));
    }

    @GetMapping("/incidents/{incidentId}")
    @Operation(summary = "Retrieve complete immutable audit trail for a fraud incident")
    public ResponseEntity<ApiResponse<List<AuditEventResponse>>> getIncidentAuditTrail(
            @PathVariable("incidentId") String incidentId
    ) {
        List<AuditEventResponse> events = auditService.getEventsForIncident(incidentId);
        return ResponseEntity.ok(ApiResponse.ok(events));
    }

    @GetMapping
    @Operation(summary = "Query paginated chronological audit event ledger with multi-field filtering")
    public ResponseEntity<ApiResponse<Page<AuditEventResponse>>> getAuditEvents(
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 25, sort = "createdAt") Pageable pageable
    ) {
        Page<AuditEventResponse> events = auditService.getAuditEvents(
                merchantId, eventType, actorId, service, entityType, action, from, to, pageable
        );
        return ResponseEntity.ok(ApiResponse.ok(events));
    }
}
