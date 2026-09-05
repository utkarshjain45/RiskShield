package com.riskshield.spike.service;

import com.riskshield.spike.dto.MerchantMultiWindowMetricsDto;
import com.riskshield.spike.dto.TimeWindowMetrics;
import com.riskshield.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantMetricsService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public TimeWindowMetrics calculateWindowMetrics(
            String merchantId,
            String windowLabel,
            Instant windowStart,
            Instant windowEnd
    ) {
        Object raw = transactionRepository.calculateMerchantWindowMetricsRaw(merchantId, windowStart, windowEnd);

        long total = 0L;
        long suspicious = 0L;
        long blocked = 0L;
        long review = 0L;
        double avgAmount = 0.0;
        long fraudExposure = 0L;

        if (raw instanceof Object[]) {
            Object[] row = (Object[]) raw;
            total = row[0] != null ? ((Number) row[0]).longValue() : 0L;
            suspicious = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            blocked = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            review = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            avgAmount = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
            fraudExposure = row[5] != null ? ((Number) row[5]).longValue() : 0L;
        }

        double fraudRate = total > 0 ? (double) suspicious / total : 0.0;
        long avgAmountPaise = Math.round(avgAmount);
        double avgAmountInr = avgAmountPaise / 100.0;
        double fraudExposureInr = fraudExposure / 100.0;

        return TimeWindowMetrics.builder()
                .window(windowLabel)
                .totalTransactions(total)
                .suspiciousTransactions(suspicious)
                .blockedTransactions(blocked)
                .reviewTransactions(review)
                .fraudRate(roundTo4Decimals(fraudRate))
                .fraudRatePercentage(String.format(Locale.US, "%.1f%%", fraudRate * 100.0))
                .averageTransactionAmountPaise(avgAmountPaise)
                .averageTransactionAmountInr(avgAmountInr)
                .fraudExposurePaise(fraudExposure)
                .fraudExposureInr(fraudExposureInr)
                .fraudExposureFormatted(formatInrCurrency(fraudExposureInr))
                .build();
    }

    @Transactional(readOnly = true)
    public MerchantMultiWindowMetricsDto getMultiWindowMetrics(String merchantId, Instant referenceTime) {
        Instant now = referenceTime != null ? referenceTime : Instant.now();

        TimeWindowMetrics m5m = calculateWindowMetrics(merchantId, "5m", now.minus(Duration.ofMinutes(5)), now);
        TimeWindowMetrics m15m = calculateWindowMetrics(merchantId, "15m", now.minus(Duration.ofMinutes(15)), now);
        TimeWindowMetrics m1h = calculateWindowMetrics(merchantId, "1h", now.minus(Duration.ofHours(1)), now);
        TimeWindowMetrics m6h = calculateWindowMetrics(merchantId, "6h", now.minus(Duration.ofHours(6)), now);
        TimeWindowMetrics m24h = calculateWindowMetrics(merchantId, "24h", now.minus(Duration.ofHours(24)), now);

        Map<String, TimeWindowMetrics> allWindows = new LinkedHashMap<>();
        allWindows.put("5m", m5m);
        allWindows.put("15m", m15m);
        allWindows.put("1h", m1h);
        allWindows.put("6h", m6h);
        allWindows.put("24h", m24h);

        return MerchantMultiWindowMetricsDto.builder()
                .merchantId(merchantId)
                .calculatedAt(now)
                .metrics5m(m5m)
                .metrics15m(m15m)
                .metrics1h(m1h)
                .metrics6h(m6h)
                .metrics24h(m24h)
                .allWindows(allWindows)
                .build();
    }

    public static String formatInrCurrency(double amountInr) {
        long rounded = Math.round(amountInr);
        if (rounded == 0) {
            return "₹0";
        }
        boolean negative = rounded < 0;
        rounded = Math.abs(rounded);
        String s = Long.toString(rounded);
        if (s.length() <= 3) {
            return (negative ? "-₹" : "₹") + s;
        }
        String lastThree = s.substring(s.length() - 3);
        String rest = s.substring(0, s.length() - 3);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            if (i > 0 && (rest.length() - i) % 2 == 0) {
                sb.append(",");
            }
            sb.append(rest.charAt(i));
        }
        sb.append(",").append(lastThree);
        return (negative ? "-₹" : "₹") + sb.toString();
    }

    private double roundTo4Decimals(double val) {
        return Math.round(val * 10000.0) / 10000.0;
    }
}
