package com.riskshield.assistant.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ai_investigation_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiInvestigationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AiInvestigationSession session;

    @com.fasterxml.jackson.annotation.JsonProperty("session_id")
    public String getSessionId() {
        return session != null ? session.getId() : null;
    }

    @Column(length = 32, nullable = false)
    private String role; // USER, ASSISTANT, SYSTEM, TOOL

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "tool_calls", columnDefinition = "TEXT")
    private String toolCalls; // JSON array of tool calls

    @Column(name = "tool_call_id", length = 64)
    private String toolCallId;

    @Column(columnDefinition = "TEXT")
    private String sources; // JSON array of data sources

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
