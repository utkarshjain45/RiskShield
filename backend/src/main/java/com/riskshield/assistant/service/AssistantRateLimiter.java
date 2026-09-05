package com.riskshield.assistant.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding window rate limiter for the AI Investigation Assistant.
 * Protects system resources and prevents automated spamming or brute-force queries.
 */
@Component
public class AssistantRateLimiter {

    private static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 30;
    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<String, Deque<Long>> clientTimestamps = new ConcurrentHashMap<>();

    /**
     * Attempts to acquire an inquiry permit for the given client key.
     *
     * @param clientKey identifier for the client (e.g. sessionId or IP address)
     * @return true if permit is granted, false if rate limit is exceeded
     */
    public boolean tryAcquire(String clientKey) {
        return tryAcquire(clientKey, DEFAULT_MAX_REQUESTS_PER_MINUTE);
    }

    public synchronized boolean tryAcquire(String clientKey, int maxRequestsPerMinute) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - WINDOW_MILLIS;

        Deque<Long> timestamps = clientTimestamps.computeIfAbsent(clientKey, k -> new ArrayDeque<>());

        // Purge expired timestamps outside the rolling 60s window
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequestsPerMinute) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    public void reset(String clientKey) {
        clientTimestamps.remove(clientKey);
    }
}
