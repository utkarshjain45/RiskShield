package com.riskshield.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.riskshield.assistant.tool.ToolExecutionResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatInquiryResponse {

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("message_id")
    private Long messageId;

    @JsonProperty("response")
    private String response;

    @JsonProperty("tools_executed")
    private List<String> toolsExecuted;

    @JsonProperty("sources")
    private List<String> sources;

    @JsonProperty("tool_results")
    private List<ToolExecutionResult> toolResults;

    @JsonProperty("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
