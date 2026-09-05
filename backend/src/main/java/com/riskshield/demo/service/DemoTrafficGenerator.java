package com.riskshield.demo.service;

import com.riskshield.demo.entity.SimulationMode;
import com.riskshield.transaction.dto.CreateTransactionRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@Component
public class DemoTrafficGenerator {

    private final Random random = new Random();

    private static final List<String> NORMAL_NAMES = List.of(
            "Aarav Sharma", "Priya Patel", "Rohit Verma", "Ananya Iyer",
            "Vikram Nair", "Neha Gupta", "Kunal Deshmukh", "Pooja Reddy",
            "Aditya Joshi", "Sneha Rao", "Arjun Sen", "Kavita Malhotra"
    );

    private static final List<String> CITIES = List.of(
            "MH", "KA", "DL", "TN", "TS", "GJ", "WB", "UP"
    );

    private static final List<String> NORMAL_PAYMENT_METHODS = List.of(
            "upi", "upi", "upi", "credit_card", "debit_card", "netbanking"
    );

    private static final List<String> RESIDENTIAL_IPS = List.of(
            "49.37.12.45", "103.21.144.60", "122.161.45.22", "117.201.88.9",
            "106.51.72.33", "157.48.19.120", "27.59.180.14", "182.72.105.66"
    );

    public CreateTransactionRequest generateRequest(SimulationMode mode, String merchantId, int stepIndex) {
        String effectiveMerchant = (merchantId != null && !merchantId.isBlank()) ? merchantId : "mer_demo_001";

        switch (mode) {
            case VELOCITY_ATTACK:
                return generateVelocityAttack(effectiveMerchant, stepIndex);
            case DEVICE_ABUSE:
                return generateDeviceAbuse(effectiveMerchant, stepIndex);
            case IP_CLUSTER_ATTACK:
                return generateIpClusterAttack(effectiveMerchant, stepIndex);
            case AMOUNT_ANOMALY:
                return generateAmountAnomaly(effectiveMerchant, stepIndex);
            case COORDINATED_FRAUD_SPIKE:
                return generateCoordinatedSpike(effectiveMerchant, stepIndex);
            case NORMAL_TRAFFIC:
            default:
                return generateNormalTraffic(effectiveMerchant, stepIndex);
        }
    }

    private CreateTransactionRequest generateNormalTraffic(String merchantId, int stepIndex) {
        int idx = random.nextInt(NORMAL_NAMES.size());
        String name = NORMAL_NAMES.get(idx);
        String emailSlug = name.toLowerCase().replace(" ", ".");
        String customerId = "cust_norm_" + Math.abs((name.hashCode() % 1000));
        String deviceId = "dev_legit_" + Math.abs((name.hashCode() % 500));
        String ip = RESIDENTIAL_IPS.get(random.nextInt(RESIDENTIAL_IPS.size()));
        String state = CITIES.get(random.nextInt(CITIES.size()));

        // Natural basket size: ₹450 to ₹3,500 (45,000 to 350,000 paise)
        long amountPaise = (450 + random.nextInt(3050)) * 100L;

        return CreateTransactionRequest.builder()
                .transactionId("tx_demo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .merchantId(merchantId)
                .customerId(customerId)
                .customerEmail(emailSlug + "@gmail.com")
                .customerContact("+9198" + (10000000 + random.nextInt(89999999)))
                .customerBillingState(state)
                .customerAccountAgeDays(60 + random.nextInt(300))
                .deviceId(deviceId)
                .deviceType(random.nextBoolean() ? "mobile_android" : "mobile_ios")
                .isEmulator(false)
                .isNewDevice(false)
                .ipAddress(ip)
                .isNewIp(false)
                .amountInPaise(amountPaise)
                .currency("INR")
                .paymentMethod(NORMAL_PAYMENT_METHODS.get(random.nextInt(NORMAL_PAYMENT_METHODS.size())))
                .build();
    }

    private CreateTransactionRequest generateVelocityAttack(String merchantId, int stepIndex) {
        // High velocity attack: Single card / single customer account rapidly repeating transactions
        String customerId = "cust_carding_target_44";
        String deviceId = "dev_rapid_burst_01";
        String ip = "103.241.200.12";

        // Moderate amounts repeated rapidly: ₹3,500 to ₹7,500
        long amountPaise = (3500 + random.nextInt(4000)) * 100L;

        return CreateTransactionRequest.builder()
                .transactionId("tx_vel_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .merchantId(merchantId)
                .customerId(customerId)
                .customerEmail("victim.account44@gmail.com")
                .customerContact("+919876543210")
                .customerBillingState("DL")
                .customerAccountAgeDays(45)
                .deviceId(deviceId)
                .deviceType("mobile_android")
                .isEmulator(false)
                .isNewDevice(false)
                .ipAddress(ip)
                .isNewIp(false)
                .amountInPaise(amountPaise)
                .currency("INR")
                .paymentMethod("credit_card")
                .build();
    }

    private CreateTransactionRequest generateDeviceAbuse(String merchantId, int stepIndex) {
        // Device abuse: Single rooted emulator cycling through many fresh accounts
        String deviceId = "dev_nox_emulator_farm_09";
        String customerId = "cust_farm_bot_" + String.format("%03d", (stepIndex % 15) + 1);
        String ip = "185.160.17.45";

        long amountPaise = (1200 + random.nextInt(3500)) * 100L;

        return CreateTransactionRequest.builder()
                .transactionId("tx_dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .merchantId(merchantId)
                .customerId(customerId)
                .customerEmail("bot_" + customerId + "@proton.me")
                .customerContact("+91910000" + String.format("%04d", stepIndex))
                .customerBillingState("MH")
                .customerAccountAgeDays(1) // Brand new synthetic account
                .deviceId(deviceId)
                .deviceType("emulator_android")
                .isEmulator(true)
                .isNewDevice(false) // Same emulator device!
                .ipAddress(ip)
                .isNewIp(false)
                .amountInPaise(amountPaise)
                .currency("INR")
                .paymentMethod("credit_card")
                .build();
    }

    private CreateTransactionRequest generateIpClusterAttack(String merchantId, int stepIndex) {
        // IP cluster: Datacenter/Tor exit node distributing transactions across diverse identities
        String maliciousIp = "185.220.101.5"; // Known Tor/bulletproof proxy IP
        String customerId = "cust_tor_cluster_" + String.format("%03d", stepIndex);
        String deviceId = "dev_proxy_client_" + (stepIndex % 4);

        long amountPaise = (4500 + random.nextInt(8000)) * 100L;

        return CreateTransactionRequest.builder()
                .transactionId("tx_ip_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .merchantId(merchantId)
                .customerId(customerId)
                .customerEmail("anon_" + stepIndex + "@temp-mail.org")
                .customerContact("+9199" + (20000000 + random.nextInt(79999999)))
                .customerBillingState("KA")
                .customerAccountAgeDays(2)
                .deviceId(deviceId)
                .deviceType("desktop_windows")
                .isEmulator(false)
                .isNewDevice(true)
                .ipAddress(maliciousIp)
                .isNewIp(false) // Repeated IP
                .amountInPaise(amountPaise)
                .currency("INR")
                .paymentMethod("credit_card")
                .build();
    }

    private CreateTransactionRequest generateAmountAnomaly(String merchantId, int stepIndex) {
        // Amount anomaly: User with low typical spend attempts sudden massive luxury/gold purchases
        String customerId = "cust_student_modest_77";
        String deviceId = "dev_unrecognized_chrome_" + stepIndex;
        String ip = "103.115.196.88";

        // Abnormal spike amounts: ₹1,50,000 to ₹4,50,000 (15,000,000 to 45,000,000 paise)
        long amountPaise = (150000 + random.nextInt(300000)) * 100L;

        return CreateTransactionRequest.builder()
                .transactionId("tx_amt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .merchantId(merchantId)
                .customerId(customerId)
                .customerEmail("neeraj.kumar77@gmail.com")
                .customerContact("+919811223344")
                .customerBillingState("UP")
                .customerAccountAgeDays(12)
                .deviceId(deviceId)
                .deviceType("desktop_mac")
                .isEmulator(false)
                .isNewDevice(true)
                .ipAddress(ip)
                .isNewIp(true)
                .amountInPaise(amountPaise)
                .currency("INR")
                .paymentMethod("netbanking")
                .build();
    }

    private CreateTransactionRequest generateCoordinatedSpike(String merchantId, int stepIndex) {
        // Coordinated syndicate: Combines account cycling, emulator farm, shared proxy IPs, high amounts, high velocity
        List<String> proxyIps = List.of("194.26.29.111", "194.26.29.112");
        List<String> emulatorPool = List.of("dev_syndicate_nox_01", "dev_syndicate_nox_02");

        String customerId = "cust_syndicate_bot_" + String.format("%02d", (stepIndex % 12) + 1);
        String deviceId = emulatorPool.get(stepIndex % emulatorPool.size());
        String ip = proxyIps.get(stepIndex % proxyIps.size());

        // Extreme spike amounts: ₹75,000 to ₹2,50,000
        long amountPaise = (75000 + random.nextInt(175000)) * 100L;

        return CreateTransactionRequest.builder()
                .transactionId("tx_coor_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .merchantId(merchantId)
                .customerId(customerId)
                .customerEmail(customerId + "@shadow-network.ru")
                .customerContact("+919000" + String.format("%06d", stepIndex + 1000))
                .customerBillingState("MH")
                .customerAccountAgeDays(1)
                .deviceId(deviceId)
                .deviceType("emulator_android")
                .isEmulator(true)
                .isNewDevice(false)
                .ipAddress(ip)
                .isNewIp(false)
                .amountInPaise(amountPaise)
                .currency("INR")
                .paymentMethod("credit_card")
                .build();
    }
}
