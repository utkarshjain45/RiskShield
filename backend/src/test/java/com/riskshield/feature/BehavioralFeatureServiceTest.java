package com.riskshield.feature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.feature.dto.FeatureSnapshot;
import com.riskshield.feature.service.BehavioralFeatureService;
import com.riskshield.transaction.entity.Customer;
import com.riskshield.transaction.entity.Device;
import com.riskshield.transaction.entity.Transaction;
import com.riskshield.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BehavioralFeatureServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private com.riskshield.audit.service.AuditService auditService;

    private ObjectMapper objectMapper;
    private BehavioralFeatureService service;

    private Customer customer;
    private Device device;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        lenient().when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new BehavioralFeatureService(stringRedisTemplate, transactionRepository, objectMapper, auditService);

        customer = Customer.builder()
                .id("cust_101")
                .email("shopper@example.com")
                .build();

        device = Device.builder()
                .id("dev_404")
                .deviceType("mobile_ios")
                .build();

        transaction = Transaction.builder()
                .id("tx_test_999")
                .customer(customer)
                .device(device)
                .ipAddress("49.207.200.15")
                .amountInPaise(499900L) // ₹4,999
                .createdAt(Instant.now())
                .isNewDevice(false)
                .isNewIp(false)
                .build();
    }

    @Test
    @DisplayName("Counter Increments: Ingesting transaction populates customer, device, and IP sliding ZSETs with TTLs")
    void testCounterIncrements() {
        service.recordTransaction(transaction);

        // Verify customer transaction ZSET
        verify(zSetOperations).add(
                eq(String.format(BehavioralFeatureService.KEY_CUST_TXS, "cust_101")),
                eq("tx_test_999"),
                anyDouble()
        );

        // Verify customer amount ZSET
        verify(zSetOperations).add(
                eq(String.format(BehavioralFeatureService.KEY_CUST_AMOUNTS, "cust_101")),
                eq("499900:tx_test_999"),
                anyDouble()
        );

        // Verify device transaction and account sharing ZSETs
        verify(zSetOperations).add(
                eq(String.format(BehavioralFeatureService.KEY_DEV_TXS, "dev_404")),
                eq("tx_test_999"),
                anyDouble()
        );
        verify(zSetOperations).add(
                eq(String.format(BehavioralFeatureService.KEY_DEV_ACCOUNTS, "dev_404")),
                eq("cust_101"),
                anyDouble()
        );

        // Verify IP transaction and account sharing ZSETs
        verify(zSetOperations).add(
                eq(String.format(BehavioralFeatureService.KEY_IP_TXS, "49.207.200.15")),
                eq("tx_test_999"),
                anyDouble()
        );
        verify(zSetOperations).add(
                eq(String.format(BehavioralFeatureService.KEY_IP_ACCOUNTS, "49.207.200.15")),
                eq("cust_101"),
                anyDouble()
        );

        // Verify TTL is applied to sliding window keys
        verify(stringRedisTemplate, atLeastOnce()).expire(
                eq(String.format(BehavioralFeatureService.KEY_CUST_TXS, "cust_101")),
                eq(BehavioralFeatureService.VELOCITY_KEY_TTL)
        );
    }

    @Test
    @DisplayName("Window Expiry: Evicts elements older than 1 hour (WINDOW_1H_MS)")
    void testWindowExpiryEviction() {
        service.recordTransaction(transaction);

        // Verify removeRangeByScore was called to evict elements older than 1 hour
        ArgumentCaptor<Double> maxScoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(zSetOperations, atLeastOnce()).removeRangeByScore(
                eq(String.format(BehavioralFeatureService.KEY_CUST_TXS, "cust_101")),
                eq(0.0),
                maxScoreCaptor.capture()
        );

        long nowMs = transaction.getCreatedAt().toEpochMilli();
        long expectedMaxEvictedScore = nowMs - BehavioralFeatureService.WINDOW_1H_MS;
        assertThat(maxScoreCaptor.getValue().longValue()).isEqualTo(expectedMaxEvictedScore);
    }

    @Test
    @DisplayName("Sliding-Window Calculation: Reads 5m, 30m, 1h velocity, amount, and account multiplexing")
    void testComputeSnapshot() {
        String custTxKey = String.format(BehavioralFeatureService.KEY_CUST_TXS, "cust_101");
        String custAmountKey = String.format(BehavioralFeatureService.KEY_CUST_AMOUNTS, "cust_101");
        String devAccKey = String.format(BehavioralFeatureService.KEY_DEV_ACCOUNTS, "dev_404");
        String ipAccKey = String.format(BehavioralFeatureService.KEY_IP_ACCOUNTS, "49.207.200.15");

        // Mock 5m = 3, 30m = 7, 1h = 12
        when(zSetOperations.count(eq(custTxKey), anyDouble(), anyDouble()))
                .thenReturn(3L)
                .thenReturn(7L)
                .thenReturn(12L);

        // Mock amount entries in last 1 hour
        when(zSetOperations.rangeByScore(eq(custAmountKey), anyDouble(), anyDouble()))
                .thenReturn(Set.of("499900:tx_1", "150000:tx_2", "200000:tx_3"));

        // Mock distinct account sharing on device (3 accounts) and IP (5 accounts)
        when(zSetOperations.zCard(eq(devAccKey))).thenReturn(3L);
        when(zSetOperations.zCard(eq(ipAccKey))).thenReturn(5L);

        FeatureSnapshot snapshot = service.computeSnapshot(transaction);

        assertThat(snapshot.getTransactionId()).isEqualTo("tx_test_999");
        assertThat(snapshot.getCustomerVelocity().getTransactions5m()).isEqualTo(3L);
        assertThat(snapshot.getCustomerVelocity().getTransactions30m()).isEqualTo(7L);
        assertThat(snapshot.getCustomerVelocity().getTransactions1h()).isEqualTo(12L);
        // Total amount = 499900 + 150000 + 200000 = 849900 paise
        assertThat(snapshot.getAmountVelocity()).isEqualTo(849900L);
        assertThat(snapshot.getDeviceAccountCount()).isEqualTo(3L);
        assertThat(snapshot.getIpAccountCount()).isEqualTo(5L);

        // Verify snapshot cached in Redis
        verify(valueOperations).set(
                eq(String.format(BehavioralFeatureService.KEY_SNAPSHOT, "tx_test_999")),
                anyString(),
                eq(BehavioralFeatureService.SNAPSHOT_CACHE_TTL)
        );
    }

    @Test
    @DisplayName("Idempotency: Re-recording the same transaction updates score without duplicate counting")
    void testIdempotency() {
        service.recordTransaction(transaction);
        service.recordTransaction(transaction);

        // ZADD in Redis with the same member key simply updates score
        verify(zSetOperations, times(2)).add(
                eq(String.format(BehavioralFeatureService.KEY_CUST_TXS, "cust_101")),
                eq("tx_test_999"),
                anyDouble()
        );
    }

    @Test
    @DisplayName("New Device & IP Detection: Recognizes novel hardware/IPs and adds to persistent set")
    void testNewDeviceAndIpDetection() {
        String devSetKey = String.format(BehavioralFeatureService.KEY_CUST_DEVICES, "cust_101");
        String ipSetKey = String.format(BehavioralFeatureService.KEY_CUST_IPS, "cust_101");

        // First transaction: not yet in set -> isNewDevice = true, isNewIp = true
        when(setOperations.isMember(eq(devSetKey), eq("dev_404"))).thenReturn(false);
        when(setOperations.isMember(eq(ipSetKey), eq("49.207.200.15"))).thenReturn(false);

        FeatureSnapshot firstSnapshot = service.computeSnapshot(transaction);

        assertThat(firstSnapshot.getIsNewDevice()).isTrue();
        assertThat(firstSnapshot.getIsNewIp()).isTrue();
        verify(setOperations).add(eq(devSetKey), eq("dev_404"));
        verify(setOperations).add(eq(ipSetKey), eq("49.207.200.15"));

        // Subsequent transaction: already in set -> isNewDevice = false, isNewIp = false
        when(setOperations.isMember(eq(devSetKey), eq("dev_404"))).thenReturn(true);
        when(setOperations.isMember(eq(ipSetKey), eq("49.207.200.15"))).thenReturn(true);

        FeatureSnapshot secondSnapshot = service.computeSnapshot(transaction);

        assertThat(secondSnapshot.getIsNewDevice()).isFalse();
        assertThat(secondSnapshot.getIsNewIp()).isFalse();
    }

    @Test
    @DisplayName("Cached Snapshot Retrieval: Returns cached JSON from Redis before hitting PostgreSQL")
    void testGetSnapshotFromCache() throws Exception {
        FeatureSnapshot cached = FeatureSnapshot.builder()
                .transactionId("tx_cached_001")
                .amountVelocity(50000L)
                .deviceAccountCount(1L)
                .ipAccountCount(1L)
                .isNewDevice(false)
                .isNewIp(false)
                .build();

        String json = objectMapper.writeValueAsString(cached);
        when(valueOperations.get(eq(String.format(BehavioralFeatureService.KEY_SNAPSHOT, "tx_cached_001"))))
                .thenReturn(json);

        FeatureSnapshot result = service.getSnapshotByTransactionId("tx_cached_001");

        assertThat(result.getTransactionId()).isEqualTo("tx_cached_001");
        assertThat(result.getAmountVelocity()).isEqualTo(50000L);
        verify(transactionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("PostgreSQL Fallback: Loads durable record from database when cache misses")
    void testPostgresFallbackOnCacheMiss() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(transactionRepository.findById("tx_test_999")).thenReturn(Optional.of(transaction));

        FeatureSnapshot result = service.getSnapshotByTransactionId("tx_test_999");

        assertThat(result.getTransactionId()).isEqualTo("tx_test_999");
        verify(transactionRepository).findById("tx_test_999");
    }
}
