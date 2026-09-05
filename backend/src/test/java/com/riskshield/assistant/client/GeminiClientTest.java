package com.riskshield.assistant.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.assistant.tool.ToolExecutionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("isAvailable() returns false when API key is blank, null, or placeholder")
    void testIsAvailable_FalseConditions() {
        GeminiClient client1 = new GeminiClient("", "gemini-1.5-flash", "https://generativelanguage.googleapis.com", 2000, objectMapper);
        assertThat(client1.isAvailable()).isFalse();

        GeminiClient client2 = new GeminiClient("placeholder", "gemini-1.5-flash", "https://generativelanguage.googleapis.com", 2000, objectMapper);
        assertThat(client2.isAvailable()).isFalse();

        GeminiClient client3 = new GeminiClient("your_gemini_api_key_here", "gemini-1.5-flash", "https://generativelanguage.googleapis.com", 2000, objectMapper);
        assertThat(client3.isAvailable()).isFalse();

        GeminiClient client4 = new GeminiClient(null, "gemini-1.5-flash", "https://generativelanguage.googleapis.com", 2000, objectMapper);
        assertThat(client4.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("isAvailable() returns true when valid API key is present")
    void testIsAvailable_TrueCondition() {
        GeminiClient client = new GeminiClient("AIzaSyFakeTestKey1234567890", "gemini-1.5-flash", "https://generativelanguage.googleapis.com", 2000, objectMapper);
        assertThat(client.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("generateInvestigationExplanation() returns Optional.empty() when client is not available")
    void testGenerateInvestigationExplanation_WhenNotAvailable() {
        GeminiClient client = new GeminiClient("", "gemini-1.5-flash", "https://generativelanguage.googleapis.com", 2000, objectMapper);
        Optional<String> result = client.generateInvestigationExplanation("Why was tx_123 blocked?", List.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("generateInvestigationExplanation() gracefully catches connection error and returns empty Optional")
    void testGenerateInvestigationExplanation_GracefulFallbackOnError() {
        // Point to non-routable dummy host with 100ms timeout
        GeminiClient client = new GeminiClient("AIzaSyFakeTestKey", "gemini-1.5-flash", "http://127.0.0.1:54321", 100, objectMapper);
        ToolExecutionResult mockTool = ToolExecutionResult.builder()
                .toolName("getTransaction")
                .success(true)
                .sourceEntity("Transaction[tx_123]")
                .data(Map.of("amount_inr", 1500.0, "status", "BLOCKED"))
                .build();

        Optional<String> result = client.generateInvestigationExplanation("Why was tx_123 blocked?", List.of(mockTool));
        // Must never throw an exception, must return empty Optional for seamless fallback
        assertThat(result).isEmpty();
    }
}
