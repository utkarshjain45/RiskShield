package com.riskshield.demo.entity;

import lombok.Getter;

@Getter
public enum SimulationMode {
    NORMAL_TRAFFIC(
            "Normal Organic Traffic",
            "Simulates legitimate consumer purchases across verified devices, diverse residential IPs, and typical basket amounts (₹450 – ₹3,500).",
            "95%+ ALLOW decisions, low risk scores (< 25), zero alerts, stable baseline."
    ),
    VELOCITY_ATTACK(
            "Carding Velocity Burst",
            "Simulates rapid-fire automated payments from the same customer account within seconds to test sliding-window counters.",
            "Spikes Redis customer velocity (tx_5m, tx_1h), risk score > 75, policy REVIEW/BLOCK, high velocity alert."
    ),
    DEVICE_ABUSE(
            "Device Farm / Multi-Account Abuse",
            "Simulates a single rooted emulator cycling through 8–15 distinct customer accounts in quick succession.",
            "Spikes Redis device_account_count (> 8), emulator flag detection, risk score > 80, policy BLOCK."
    ),
    IP_CLUSTER_ATTACK(
            "Proxy / VPN Cluster Attack",
            "Simulates transactions from a suspicious datacenter IP cluster distributing attempts across varied cards and identities.",
            "Spikes Redis ip_account_count (> 6) and ip_velocity, risk score > 75, IP cluster risk alert."
    ),
    AMOUNT_ANOMALY(
            "High-Value Outlier Anomaly",
            "Simulates sudden massive purchase spikes (₹1,50,000 – ₹4,50,000) from accounts with low historical average spend.",
            "Massive amount_deviation (> 150x), violates policy single-transaction limits, risk score > 85, immediate BLOCK."
    ),
    COORDINATED_FRAUD_SPIKE(
            "Coordinated Botnet Syndicate",
            "Simulates multi-vector coordinated fraud combining account cycling, shared emulator devices, proxy IPs, and abnormal amounts.",
            "Fraud rate spikes above 30%, FraudSpikeDetectorService fires CRITICAL incident, financial exposure calculated, incident alert created."
    );

    private final String displayName;
    private final String description;
    private final String expectedOutcome;

    SimulationMode(String displayName, String description, String expectedOutcome) {
        this.displayName = displayName;
        this.description = description;
        this.expectedOutcome = expectedOutcome;
    }
}
