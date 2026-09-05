package com.riskshield.assistant.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.assistant.tool.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

/**
 * Google Gemini 1.5 LLM Client for RiskShield AI Assistant.
 * Uses Google Generative Language API (gemini-1.5-flash / gemini-1.5-pro)
 * for synthesizing read-only fraud investigations, SHAP feature signals, and incident explanations.
 */
@Slf4j
@Component
public class GeminiClient {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int timeoutMs;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_INSTRUCTION = """
            You are RiskShield AI's Senior Risk & Fraud Intelligence Copilot.
            Your role is to explain fraud decisions, transaction risk assessments, TreeSHAP feature signals, and merchant fraud patterns to human risk analysts and merchants.
            
            CRITICAL CONSTRAINTS & SECURITY POLICIES:
            1. You are strictly read-only and consultative. You do NOT have the authority to execute payments, block transactions, capture, refund, or alter risk policies.
            2. The deterministic XGBoost machine learning model and policy rules are authoritative. Never contradict the recorded risk score or decision.
            3. Formulate clear, highly professional, analytical explanations synthesizing the exact tool outputs provided below.
            4. Format your output using clear markdown with headings, bold key figures, and concise bullet points.
            5. Do not invent or hallucinate data; ground all claims strictly in the provided tool outputs.
            """;

    public GeminiClient(
            @Value("${riskshield.gemini.api-key:}") String apiKey,
            @Value("${riskshield.gemini.model:gemini-1.5-flash}") String model,
            @Value("${riskshield.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${riskshield.gemini.timeout-ms:6000}") int timeoutMs,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = (model != null && !model.isBlank()) ? model.trim() : "gemini-1.5-flash";
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "https://generativelanguage.googleapis.com";
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 6000;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(this.timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(this.timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(requestFactory)
                .build();

        if (isAvailable()) {
            log.info("GeminiClient initialized successfully for model: {}", this.model);
        } else {
            log.info("GeminiClient initialized in standby mode (GEMINI_API_KEY not configured). Deterministic fallback active.");
        }
    }

    /**
     * Checks whether Google Gemini API is configured and ready to receive requests.
     */
    public boolean isAvailable() {
        return !apiKey.isEmpty()
                && !apiKey.equalsIgnoreCase("placeholder")
                && !apiKey.startsWith("your_");
    }

    /**
     * Synthesizes an authoritative investigation response using Gemini 1.5.
     *
     * @param userQuery The risk analyst inquiry
     * @param toolResults The outputs from the authoritative deterministic tools
     * @return Synthesized text narrative if successful, or Optional.empty() for fallback
     */
    public Optional<String> generateInvestigationExplanation(String userQuery, List<ToolExecutionResult> toolResults) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        try {
            String promptText = buildPrompt(userQuery, toolResults);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("text", promptText))
                            )
                    ),
                    "systemInstruction", Map.of(
                            "parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.2,
                            "maxOutputTokens", 1200
                    )
            );

            String uri = String.format("/v1beta/models/%s:generateContent?key=%s", model, apiKey);

            String responseBody = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    String generatedText = parts.get(0).path("text").asText("");
                    if (!generatedText.isBlank()) {
                        return Optional.of(generatedText.trim());
                    }
                }
            }

            log.warn("Gemini API returned unexpected response structure: {}", responseBody);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Gemini API call failed or timed out: {}. Gracefully falling back to deterministic response.", e.getMessage());
            return Optional.empty();
        }
    }

    private String buildPrompt(String query, List<ToolExecutionResult> toolResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("User Inquiry: \"").append(query).append("\"\n\n");
        sb.append("Authoritative Tool Outputs Retrieved From Risk Engine:\n");

        for (ToolExecutionResult tr : toolResults) {
            sb.append("\n--- TOOL: ").append(tr.getToolName()).append(" ---\n");
            if (tr.isSuccess()) {
                sb.append("Source: ").append(tr.getSourceEntity()).append("\n");
                try {
                    sb.append("Data: ").append(objectMapper.writeValueAsString(tr.getData())).append("\n");
                } catch (Exception ex) {
                    sb.append("Data: ").append(String.valueOf(tr.getData())).append("\n");
                }
            } else {
                sb.append("Status: FAILED - ").append(tr.getErrorMessage()).append("\n");
            }
        }

        sb.append("\nPlease synthesize a concise, structured, professional risk investigation report answering the user's inquiry based strictly on the above tool findings.");
        return sb.toString();
    }
}
