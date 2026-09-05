package com.riskshield.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiting filter defending all external endpoints from API abuse,
 * credential stuffing, and Denial-of-Service attacks.
 */
@Slf4j
@RequiredArgsConstructor
public class ApiRateLimitingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    // Per-client request timestamps window
    private final Map<String, Deque<Long>> requestWindows = new ConcurrentHashMap<>();

    private static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 120;
    private static final int ASSISTANT_MAX_REQUESTS_PER_MINUTE = 30;
    private static final long WINDOW_MS = 60_000L;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String uri = request.getRequestURI();

        // Skip rate limiting on static assets, documentation, and health check probes
        if (uri.startsWith("/actuator/health") || uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = resolveClientKey(request);
        int maxLimit = uri.contains("/assistant/chat")
                ? ASSISTANT_MAX_REQUESTS_PER_MINUTE
                : DEFAULT_MAX_REQUESTS_PER_MINUTE;

        long now = System.currentTimeMillis();

        Deque<Long> timestamps = requestWindows.computeIfAbsent(clientKey, k -> new ArrayDeque<>());

        boolean allowed;
        synchronized (timestamps) {
            // Evict timestamps outside the 1-minute window
            while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > WINDOW_MS) {
                timestamps.pollFirst();
            }

            if (timestamps.size() < maxLimit) {
                timestamps.addLast(now);
                allowed = true;
            } else {
                allowed = false;
            }
        }

        if (!allowed) {
            log.warn("Rate limit exceeded for client {}: {} req/min on {}", clientKey, maxLimit, uri);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "status", "RATE_LIMIT_EXCEEDED",
                    "message", String.format("Rate limit of %d requests per minute exceeded. Please retry later.", maxLimit),
                    "retry_after_seconds", 60,
                    "timestamp", Instant.now().toString()
            )));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        String apiKey = request.getHeader(ApiKeyAuthenticationFilter.HEADER_API_KEY);
        if (apiKey != null && !apiKey.isBlank()) {
            return "key:" + apiKey;
        }

        String userRole = request.getHeader(ApiKeyAuthenticationFilter.HEADER_ROLE);
        String userId = request.getHeader(ApiKeyAuthenticationFilter.HEADER_USER_ID);
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
        }

        // Fallback to client IP
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }

        return "ip:" + request.getRemoteAddr();
    }
}
