package com.riskshield.feature.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.common.exception.ResourceNotFoundException;
import com.riskshield.feature.dto.CustomerVelocityDto;
import com.riskshield.feature.dto.DeviceVelocityDto;
import com.riskshield.feature.dto.FeatureSnapshot;
import com.riskshield.feature.dto.IpVelocityDto;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Real-time Behavioral Feature Service backed by Redis.
 *
 * Maintains sliding-window counters and distinct account trackers using Redis Sorted Sets (ZSET),
 * enabling sub-millisecond velocity calculation without scanning PostgreSQL for every transaction.
 *
 * PostgreSQL remains the durable source of record.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehavioralFeatureService {

    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    private final com.riskshield.audit.service.AuditService auditService;

    // Redis Key Templates
    public static final String KEY_CUST_TXS = "rs:feat:cust:%s:txs";          // ZSET: epoch_ms -> txId
    public static final String KEY_CUST_AMOUNTS = "rs:feat:cust:%s:amounts";  // ZSET: epoch_ms -> amountPaise:txId
    public static final String KEY_CUST_DEVICES = "rs:feat:cust:%s:devices";  // SET: deviceId
    public static final String KEY_CUST_IPS = "rs:feat:cust:%s:ips";          // SET: ipAddress

    public static final String KEY_DEV_TXS = "rs:feat:dev:%s:txs";            // ZSET: epoch_ms -> txId
    public static final String KEY_DEV_ACCOUNTS = "rs:feat:dev:%s:accounts";   // ZSET: epoch_ms -> customerId

    public static final String KEY_IP_TXS = "rs:feat:ip:%s:txs";              // ZSET: epoch_ms -> txId
    public static final String KEY_IP_ACCOUNTS = "rs:feat:ip:%s:accounts";     // ZSET: epoch_ms -> customerId

    public static final String KEY_SNAPSHOT = "rs:feat:snapshot:%s";           // STRING: Serialized FeatureSnapshot

    // Window Durations (Milliseconds)
    public static final long WINDOW_5M_MS = 5 * 60 * 1000L;
    public static final long WINDOW_30M_MS = 30 * 60 * 1000L;
    public static final long WINDOW_1H_MS = 60 * 60 * 1000L;

    // Key TTLs
    public static final Duration VELOCITY_KEY_TTL = Duration.ofSeconds(3700); // 1h + 100s buffer
    public static final Duration DEVICE_IP_SET_TTL = Duration.ofDays(90);
    public static final Duration SNAPSHOT_CACHE_TTL = Duration.ofHours(24);

    /**
     * Ingests a transaction into Redis sliding-window structures.
     * Idempotent: repeated ingestion of the same transactionId updates timestamp score
     * without creating duplicate count members in the ZSET.
     */
    public void recordTransaction(Transaction transaction) {
        if (transaction == null) {
            return;
        }

        try {
            long nowMs = (transaction.getCreatedAt() != null)
                    ? transaction.getCreatedAt().toEpochMilli()
                    : System.currentTimeMillis();

            String txId = transaction.getId();
            String customerId = (transaction.getCustomer() != null) ? transaction.getCustomer().getId() : null;
            String deviceId = (transaction.getDevice() != null) ? transaction.getDevice().getId() : null;
            String ipAddress = transaction.getIpAddress();
            long amountInPaise = (transaction.getAmountInPaise() != null) ? transaction.getAmountInPaise() : 0L;

            // 1. Customer Sliding Windows
            if (customerId != null && !customerId.isBlank()) {
                String custTxKey = String.format(KEY_CUST_TXS, customerId);
                stringRedisTemplate.opsForZSet().add(custTxKey, txId, nowMs);
                stringRedisTemplate.expire(custTxKey, VELOCITY_KEY_TTL);

                String custAmountKey = String.format(KEY_CUST_AMOUNTS, customerId);
                stringRedisTemplate.opsForZSet().add(custAmountKey, amountInPaise + ":" + txId, nowMs);
                stringRedisTemplate.expire(custAmountKey, VELOCITY_KEY_TTL);

                // Evict expired entries older than 1 hour
                stringRedisTemplate.opsForZSet().removeRangeByScore(custTxKey, 0, nowMs - WINDOW_1H_MS);
                stringRedisTemplate.opsForZSet().removeRangeByScore(custAmountKey, 0, nowMs - WINDOW_1H_MS);
            }

            // 2. Device Sliding Windows
            if (deviceId != null && !deviceId.isBlank()) {
                String devTxKey = String.format(KEY_DEV_TXS, deviceId);
                stringRedisTemplate.opsForZSet().add(devTxKey, txId, nowMs);
                stringRedisTemplate.expire(devTxKey, VELOCITY_KEY_TTL);

                if (customerId != null && !customerId.isBlank()) {
                    String devAccKey = String.format(KEY_DEV_ACCOUNTS, deviceId);
                    stringRedisTemplate.opsForZSet().add(devAccKey, customerId, nowMs);
                    stringRedisTemplate.expire(devAccKey, VELOCITY_KEY_TTL);
                    stringRedisTemplate.opsForZSet().removeRangeByScore(devAccKey, 0, nowMs - WINDOW_1H_MS);
                }

                stringRedisTemplate.opsForZSet().removeRangeByScore(devTxKey, 0, nowMs - WINDOW_1H_MS);
            }

            // 3. IP Sliding Windows
            if (ipAddress != null && !ipAddress.isBlank()) {
                String ipTxKey = String.format(KEY_IP_TXS, ipAddress);
                stringRedisTemplate.opsForZSet().add(ipTxKey, txId, nowMs);
                stringRedisTemplate.expire(ipTxKey, VELOCITY_KEY_TTL);

                if (customerId != null && !customerId.isBlank()) {
                    String ipAccKey = String.format(KEY_IP_ACCOUNTS, ipAddress);
                    stringRedisTemplate.opsForZSet().add(ipAccKey, customerId, nowMs);
                    stringRedisTemplate.expire(ipAccKey, VELOCITY_KEY_TTL);
                    stringRedisTemplate.opsForZSet().removeRangeByScore(ipAccKey, 0, nowMs - WINDOW_1H_MS);
                }

                stringRedisTemplate.opsForZSet().removeRangeByScore(ipTxKey, 0, nowMs - WINDOW_1H_MS);
            }
        } catch (Exception e) {
            log.warn("Failed to record transaction {} in Redis feature store: {}", transaction.getId(), e.getMessage());
        }
    }

    /**
     * Computes the real-time FeatureSnapshot for a transaction.
     */
    public FeatureSnapshot computeSnapshot(Transaction transaction) {
        long nowMs = (transaction.getCreatedAt() != null)
                ? transaction.getCreatedAt().toEpochMilli()
                : System.currentTimeMillis();

        String customerId = (transaction.getCustomer() != null) ? transaction.getCustomer().getId() : null;
        String deviceId = (transaction.getDevice() != null) ? transaction.getDevice().getId() : null;
        String ipAddress = transaction.getIpAddress();

        // 1. Compute Customer Velocity
        long cust5m = 1L;
        long cust30m = 1L;
        long cust1h = 1L;
        long amount1h = (transaction.getAmountInPaise() != null) ? transaction.getAmountInPaise() : 0L;
        boolean isNewDevice = transaction.isNewDevice();
        boolean isNewIp = transaction.isNewIp();

        try {
            if (customerId != null && !customerId.isBlank()) {
                String custTxKey = String.format(KEY_CUST_TXS, customerId);
                Long count5m = stringRedisTemplate.opsForZSet().count(custTxKey, nowMs - WINDOW_5M_MS, nowMs);
                Long count30m = stringRedisTemplate.opsForZSet().count(custTxKey, nowMs - WINDOW_30M_MS, nowMs);
                Long count1h = stringRedisTemplate.opsForZSet().count(custTxKey, nowMs - WINDOW_1H_MS, nowMs);

                cust5m = (count5m != null && count5m > 0) ? count5m : 1L;
                cust30m = (count30m != null && count30m > 0) ? count30m : 1L;
                cust1h = (count1h != null && count1h > 0) ? count1h : 1L;

                // Amount velocity
                String custAmountKey = String.format(KEY_CUST_AMOUNTS, customerId);
                Set<String> amountEntries = stringRedisTemplate.opsForZSet().rangeByScore(custAmountKey, nowMs - WINDOW_1H_MS, nowMs);
                if (amountEntries != null && !amountEntries.isEmpty()) {
                    long totalAmount = 0L;
                    for (String entry : amountEntries) {
                        int colonIdx = entry.indexOf(':');
                        if (colonIdx > 0) {
                            try {
                                totalAmount += Long.parseLong(entry.substring(0, colonIdx));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    amount1h = Math.max(amount1h, totalAmount);
                }

                // Known Device Check
                if (deviceId != null && !deviceId.isBlank()) {
                    String custDevKey = String.format(KEY_CUST_DEVICES, customerId);
                    Boolean known = stringRedisTemplate.opsForSet().isMember(custDevKey, deviceId);
                    if (Boolean.TRUE.equals(known)) {
                        isNewDevice = false;
                    } else {
                        isNewDevice = true;
                        stringRedisTemplate.opsForSet().add(custDevKey, deviceId);
                        stringRedisTemplate.expire(custDevKey, DEVICE_IP_SET_TTL);
                    }
                }

                // Known IP Check
                if (ipAddress != null && !ipAddress.isBlank()) {
                    String custIpKey = String.format(KEY_CUST_IPS, customerId);
                    Boolean known = stringRedisTemplate.opsForSet().isMember(custIpKey, ipAddress);
                    if (Boolean.TRUE.equals(known)) {
                        isNewIp = false;
                    } else {
                        isNewIp = true;
                        stringRedisTemplate.opsForSet().add(custIpKey, ipAddress);
                        stringRedisTemplate.expire(custIpKey, DEVICE_IP_SET_TTL);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Redis error reading customer velocity for {}: {}", customerId, e.getMessage());
        }

        // 2. Compute Device Velocity & Account Sharing
        long dev5m = 1L;
        long dev1h = 1L;
        long devAccountCount = 1L;

        try {
            if (deviceId != null && !deviceId.isBlank()) {
                String devTxKey = String.format(KEY_DEV_TXS, deviceId);
                Long count5m = stringRedisTemplate.opsForZSet().count(devTxKey, nowMs - WINDOW_5M_MS, nowMs);
                Long count1h = stringRedisTemplate.opsForZSet().count(devTxKey, nowMs - WINDOW_1H_MS, nowMs);
                dev5m = (count5m != null && count5m > 0) ? count5m : 1L;
                dev1h = (count1h != null && count1h > 0) ? count1h : 1L;

                String devAccKey = String.format(KEY_DEV_ACCOUNTS, deviceId);
                stringRedisTemplate.opsForZSet().removeRangeByScore(devAccKey, 0, nowMs - WINDOW_1H_MS);
                Long distinctAccounts = stringRedisTemplate.opsForZSet().zCard(devAccKey);
                devAccountCount = (distinctAccounts != null && distinctAccounts > 0) ? distinctAccounts : 1L;
            }
        } catch (Exception e) {
            log.warn("Redis error reading device velocity for {}: {}", deviceId, e.getMessage());
        }

        // 3. Compute IP Velocity & Account Sharing
        long ip5m = 1L;
        long ip1h = 1L;
        long ipAccountCount = 1L;

        try {
            if (ipAddress != null && !ipAddress.isBlank()) {
                String ipTxKey = String.format(KEY_IP_TXS, ipAddress);
                Long count5m = stringRedisTemplate.opsForZSet().count(ipTxKey, nowMs - WINDOW_5M_MS, nowMs);
                Long count1h = stringRedisTemplate.opsForZSet().count(ipTxKey, nowMs - WINDOW_1H_MS, nowMs);
                ip5m = (count5m != null && count5m > 0) ? count5m : 1L;
                ip1h = (count1h != null && count1h > 0) ? count1h : 1L;

                String ipAccKey = String.format(KEY_IP_ACCOUNTS, ipAddress);
                stringRedisTemplate.opsForZSet().removeRangeByScore(ipAccKey, 0, nowMs - WINDOW_1H_MS);
                Long distinctAccounts = stringRedisTemplate.opsForZSet().zCard(ipAccKey);
                ipAccountCount = (distinctAccounts != null && distinctAccounts > 0) ? distinctAccounts : 1L;
            }
        } catch (Exception e) {
            log.warn("Redis error reading IP velocity for {}: {}", ipAddress, e.getMessage());
        }

        FeatureSnapshot snapshot = FeatureSnapshot.builder()
                .transactionId(transaction.getId())
                .customerVelocity(CustomerVelocityDto.builder()
                        .transactions5m(cust5m)
                        .transactions30m(cust30m)
                        .transactions1h(cust1h)
                        .amount1h(amount1h)
                        .build())
                .deviceVelocity(DeviceVelocityDto.builder()
                        .transactions5m(dev5m)
                        .transactions1h(dev1h)
                        .accountCount1h(devAccountCount)
                        .build())
                .ipVelocity(IpVelocityDto.builder()
                        .transactions5m(ip5m)
                        .transactions1h(ip1h)
                        .accountCount1h(ipAccountCount)
                        .build())
                .amountVelocity(amount1h)
                .deviceAccountCount(devAccountCount)
                .ipAccountCount(ipAccountCount)
                .isNewDevice(isNewDevice)
                .isNewIp(isNewIp)
                .capturedAt(Instant.ofEpochMilli(nowMs))
                .build();

        // 4. Cache Point-in-Time Snapshot
        cacheSnapshot(snapshot);

        // 5. Record Audit Event: FEATURES_GENERATED
        try {
            String merchantId = transaction.getMerchant() != null ? transaction.getMerchant().getId() : null;
            auditService.recordRiskEvent(
                    com.riskshield.audit.entity.AuditEventType.FEATURES_GENERATED,
                    com.riskshield.audit.entity.ActorType.SYSTEM,
                    "BEHAVIORAL_FEATURE_ENGINE",
                    merchantId,
                    transaction,
                    "FeatureSnapshot",
                    transaction.getId(),
                    "feature-service",
                    "Generated real-time behavioral velocity and entity sharing features",
                    snapshot
            );
        } catch (Exception e) {
            log.warn("Failed to record FEATURES_GENERATED audit event for {}: {}", transaction.getId(), e.getMessage());
        }

        return snapshot;
    }

    /**
     * Retrieves the feature snapshot for a transaction.
     * Looks up Redis cache first; falls back to loading transaction from Postgres and computing.
     */
    public FeatureSnapshot getSnapshotByTransactionId(String transactionId) {
        String cacheKey = String.format(KEY_SNAPSHOT, transactionId);

        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isBlank()) {
                return objectMapper.readValue(cachedJson, FeatureSnapshot.class);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve cached feature snapshot for {}: {}", transactionId, e.getMessage());
        }

        // Fallback to PostgreSQL durable record
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        if (tx.getMerchant() != null) {
            com.riskshield.security.util.SecurityUtils.assertMerchantAccess(tx.getMerchant().getId());
        }

        return computeSnapshot(tx);
    }

    private void cacheSnapshot(FeatureSnapshot snapshot) {
        if (snapshot == null || snapshot.getTransactionId() == null) {
            return;
        }
        try {
            String cacheKey = String.format(KEY_SNAPSHOT, snapshot.getTransactionId());
            String json = objectMapper.writeValueAsString(snapshot);
            stringRedisTemplate.opsForValue().set(cacheKey, json, SNAPSHOT_CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache feature snapshot for {}: {}", snapshot.getTransactionId(), e.getMessage());
        }
    }
}
