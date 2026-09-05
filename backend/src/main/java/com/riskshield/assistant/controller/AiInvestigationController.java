package com.riskshield.assistant.controller;

import com.riskshield.assistant.dto.ChatInquiryRequest;
import com.riskshield.assistant.dto.ChatInquiryResponse;
import com.riskshield.assistant.dto.SessionSummaryDto;
import com.riskshield.assistant.entity.AiInvestigationMessage;
import com.riskshield.assistant.entity.AiInvestigationSession;
import com.riskshield.assistant.service.AiInvestigationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
@Tag(name = "AI Investigation Assistant", description = "Interactive risk analysis assistant using structured read-only tools")
public class AiInvestigationController {

    private final AiInvestigationService assistantService;

    @PostMapping("/chat")
    @Operation(summary = "Send an inquiry to the AI investigation assistant")
    public ResponseEntity<?> sendInquiry(
            @jakarta.validation.Valid @RequestBody ChatInquiryRequest request,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "ANALYST_DEFAULT") String userId
    ) {
        try {
            ChatInquiryResponse response = assistantService.handleInquiry(request, userId);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            log.warn("Assistant rate limit or state error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "status", "RATE_LIMIT_EXCEEDED",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Assistant inquiry failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "message", "Investigation inquiry error: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/sessions")
    @Operation(summary = "List investigation sessions")
    public ResponseEntity<List<SessionSummaryDto>> listSessions(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        return ResponseEntity.ok(assistantService.listSessions(userId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "Retrieve message history for a session")
    public ResponseEntity<List<AiInvestigationMessage>> getSessionMessages(
            @PathVariable String sessionId
    ) {
        return ResponseEntity.ok(assistantService.getSessionMessages(sessionId));
    }

    @PostMapping("/sessions")
    @Operation(summary = "Start a new investigation session")
    public ResponseEntity<SessionSummaryDto> createSession(
            @RequestBody(required = false) Map<String, String> payload,
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "ANALYST_DEFAULT") String userId
    ) {
        String merchantId = payload != null ? payload.get("merchant_id") : null;
        String title = payload != null ? payload.get("title") : "New Investigation";

        AiInvestigationSession session = assistantService.getOrCreateSession(null, merchantId, title, userId);
        return ResponseEntity.ok(SessionSummaryDto.builder()
                .id(session.getId())
                .merchantId(session.getMerchantId())
                .title(session.getTitle())
                .userId(session.getUserId())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build());
    }
}
