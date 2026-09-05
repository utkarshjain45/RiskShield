from datetime import datetime, timedelta
from typing import Dict, List, Any, Optional
import numpy as np
import pandas as pd

# Hourly probability distribution for Indian payments (diurnal curve)
# Low from 2 AM - 5 AM (~0.005-0.015), peaks in evening 7 PM - 10 PM (~0.075-0.09)
HOURLY_WEIGHTS = np.array([
    0.012, 0.008, 0.005, 0.004, 0.006, 0.012,
    0.022, 0.035, 0.050, 0.062, 0.065, 0.060,
    0.055, 0.052, 0.050, 0.054, 0.060, 0.070,
    0.082, 0.092, 0.088, 0.072, 0.050, 0.030
])
HOURLY_WEIGHTS = HOURLY_WEIGHTS / HOURLY_WEIGHTS.sum()

CARD_BINS = ["411111", "424242", "510510", "520082", "607082", "652150"]

class PatternEngine:
    """
    Synthesizes authentic legitimate transaction behaviors and rich multi-factor
    fraud attack patterns (A through H).
    """

    def __init__(
        self,
        rng: np.random.Generator,
        merchants_df: pd.DataFrame,
        customers_df: pd.DataFrame,
        devices_df: pd.DataFrame,
        ips_df: pd.DataFrame,
        start_time: datetime,
        end_time: datetime
    ):
        self.rng = rng
        self.merchants = merchants_df.to_dict(orient="records")
        self.customers = customers_df.to_dict(orient="records")
        self.devices = devices_df.to_dict(orient="records")
        self.ips = ips_df.to_dict(orient="records")
        
        self.all_device_ids = devices_df["device_id"].values
        self.all_ips = ips_df["ip_address"].values
        self.vpn_ips = ips_df[ips_df["is_vpn_proxy"] == 1]["ip_address"].values
        if len(self.vpn_ips) == 0:
            self.vpn_ips = self.all_ips[:50]

        self.start_time = start_time
        self.end_time = end_time
        self.total_days = max(1, (end_time - start_time).days)

        # Fast lookup indices
        self.num_merchants = len(self.merchants)
        self.num_customers = len(self.customers)
        self.num_devices = len(self.devices)
        self.num_ips = len(self.ips)

        # Customer account creation map
        self.cust_created_map = {
            c["customer_id"]: datetime.fromisoformat(c["account_created_at"])
            for c in self.customers
        }

    def generate_random_timestamp(self, day_offset: Optional[int] = None, hour: Optional[int] = None) -> datetime:
        """Samples timestamp respecting Indian diurnal payment volume curve."""
        if day_offset is None:
            day_offset = int(self.rng.integers(0, self.total_days))
        if hour is None:
            hour = int(self.rng.choice(24, p=HOURLY_WEIGHTS))
        
        minute = int(self.rng.integers(0, 60))
        second = int(self.rng.integers(0, 60))
        
        tx_dt = self.start_time + timedelta(days=day_offset, hours=hour, minutes=minute, seconds=second)
        if tx_dt > self.end_time:
            tx_dt = self.end_time - timedelta(minutes=int(self.rng.integers(1, 120)))
        return tx_dt

    def generate_legitimate_tx(self, cust_idx: Optional[int] = None, timestamp: Optional[datetime] = None) -> Dict[str, Any]:
        """
        Synthesizes legitimate customer payment behavior.
        - Uses primary device ~94% of the time, secondary device ~5%, new device ~1%.
        - Uses primary IP ~92% of the time, mobile carrier alternate IP ~7%, new IP ~1%.
        - Amounts adhere to customer personal baseline blended with merchant category norm.
        """
        if cust_idx is None:
            cust_idx = int(self.rng.integers(0, self.num_customers))
        cust = self.customers[cust_idx]
        
        mer_idx = int(self.rng.integers(0, self.num_merchants))
        mer = self.merchants[mer_idx]

        if timestamp is None:
            timestamp = self.generate_random_timestamp()

        # Account age calculation
        acc_created = self.cust_created_map[cust["customer_id"]]
        age_days = max(1, (timestamp - acc_created).days)

        # Device assignment
        dev_roll = self.rng.random()
        if dev_roll < 0.94:
            device_id = cust["primary_device_id"]
            is_new_device = 0
        elif dev_roll < 0.99:
            # Secondary device (e.g. tablet or laptop)
            device_id = self.all_device_ids[(cust_idx * 7) % self.num_devices]
            is_new_device = 0
        else:
            # Rare legitimate phone upgrade
            device_id = self.all_device_ids[int(self.rng.integers(0, self.num_devices))]
            is_new_device = 1

        # IP assignment
        ip_roll = self.rng.random()
        if ip_roll < 0.92:
            ip_addr = cust["primary_ip"]
            is_new_ip = 0
        elif ip_roll < 0.98:
            # Mobile data network alternate IP
            ip_addr = self.all_ips[(cust_idx * 13) % self.num_ips]
            is_new_ip = 0
        else:
            # Traveling / new IP
            ip_addr = self.all_ips[int(self.rng.integers(0, self.num_ips))]
            is_new_ip = 1

        # Amount model: Blended log-normal around merchant & customer profile
        base_amount = 0.6 * cust["typical_amount_inr"] + 0.4 * mer["avg_ticket_inr"]
        multiplier = np.exp(self.rng.normal(0, 0.35)) # Most tx within 0.7x - 1.4x
        amount = round(float(np.clip(base_amount * multiplier, 20.0, 150000.0)), 2)

        # Payment method
        if self.rng.random() < 0.85:
            payment_method = cust["typical_payment_method"]
        else:
            payment_method = self.rng.choice(["upi", "card", "netbanking"])

        # Legitimate failure rate ~2.5% (OTP expiry, insufficient balance)
        status = "FAILED" if self.rng.random() < 0.025 else "SUCCESS"

        return {
            "merchant_id": mer["merchant_id"],
            "customer_id": cust["customer_id"],
            "device_id": device_id,
            "ip_address": ip_addr,
            "timestamp": timestamp,
            "amount": amount,
            "currency": "INR",
            "payment_method": payment_method,
            "transaction_status": status,
            "customer_account_age_days": age_days,
            "is_new_device": is_new_device,
            "is_new_ip": is_new_ip,
            "fraud_label": 0,
            "fraud_scenario": "legitimate"
        }

    # --------------------------------------------------------------------------
    # Fraud Scenarios A through H
    # --------------------------------------------------------------------------

    def generate_pattern_a_velocity(self) -> List[Dict[str, Any]]:
        """A. Velocity Fraud: Rapid bursts (6-12 tx in 5-15 mins) from single card/customer."""
        cust_idx = int(self.rng.integers(0, self.num_customers))
        cust = self.customers[cust_idx]
        mer = self.merchants[int(self.rng.integers(0, self.num_merchants))]
        base_time = self.generate_random_timestamp()
        
        burst_count = int(self.rng.integers(6, 13))
        results = []
        is_card_testing = self.rng.random() < 0.5
        
        device_id = self.all_device_ids[int(self.rng.integers(0, self.num_devices))]
        ip_addr = self.rng.choice(self.vpn_ips)

        for i in range(burst_count):
            tx_time = base_time + timedelta(seconds=int(self.rng.integers(15, 75) * (i + 1)))
            age_days = max(1, (tx_time - self.cust_created_map[cust["customer_id"]]).days)
            
            if is_card_testing:
                amount = round(float(self.rng.uniform(20.0, 150.0)), 2) # micro card auth
            else:
                amount = round(float(self.rng.uniform(2500.0, 18000.0)), 2) # rapid drain

            # Card testing has high failure rate due to guessed CVVs
            status = "FAILED" if (is_card_testing and self.rng.random() < 0.55) else "SUCCESS"

            results.append({
                "merchant_id": mer["merchant_id"],
                "customer_id": cust["customer_id"],
                "device_id": device_id,
                "ip_address": ip_addr,
                "timestamp": tx_time,
                "amount": amount,
                "currency": "INR",
                "payment_method": "card",
                "transaction_status": status,
                "customer_account_age_days": age_days,
                "is_new_device": 1,
                "is_new_ip": 1,
                "fraud_label": 1,
                "fraud_scenario": "velocity_fraud"
            })
        return results

    def generate_pattern_b_new_device_ato(self) -> Dict[str, Any]:
        """B. Account Takeover: Aged account attacked from brand new device + proxy IP with high ticket."""
        # Pick aged customer (age > 90 days)
        cust_idx = int(self.rng.integers(0, self.num_customers))
        cust = self.customers[cust_idx]
        
        # High value merchant (Electronics, Digital Gaming, Travel)
        high_val_mers = [m for m in self.merchants if m["category"] in ["electronics", "digital_gaming", "travel_airline"]]
        mer = high_val_mers[int(self.rng.integers(0, len(high_val_mers)))] if high_val_mers else self.merchants[0]

        timestamp = self.generate_random_timestamp()
        age_days = max(90, (timestamp - self.cust_created_map[cust["customer_id"]]).days)

        # High ticket: 3.5x to 7x typical spend
        amount = round(float(cust["typical_amount_inr"] * self.rng.uniform(3.5, 7.5)), 2)
        amount = max(4500.0, min(140000.0, amount))

        new_dev = self.all_device_ids[int(self.rng.integers(0, self.num_devices))]
        new_ip = self.rng.choice(self.vpn_ips)

        return {
            "merchant_id": mer["merchant_id"],
            "customer_id": cust["customer_id"],
            "device_id": new_dev,
            "ip_address": new_ip,
            "timestamp": timestamp,
            "amount": amount,
            "currency": "INR",
            "payment_method": "card",
            "transaction_status": "SUCCESS" if self.rng.random() < 0.85 else "FAILED",
            "customer_account_age_days": age_days,
            "is_new_device": 1,
            "is_new_ip": 1,
            "fraud_label": 1,
            "fraud_scenario": "new_device_abuse"
        }

    def generate_pattern_c_device_syndicate(self) -> List[Dict[str, Any]]:
        """C. Device Syndicate Farm: 1 device used across 15 to 30 distinct accounts."""
        syndicate_device = self.all_device_ids[int(self.rng.integers(0, self.num_devices))]
        base_time = self.generate_random_timestamp()
        mer = self.merchants[int(self.rng.integers(0, self.num_merchants))]
        
        account_count = int(self.rng.integers(15, 31))
        chosen_cust_indices = self.rng.choice(self.num_customers, size=account_count, replace=False)
        results = []

        for i, c_idx in enumerate(chosen_cust_indices):
            cust = self.customers[c_idx]
            tx_time = base_time + timedelta(minutes=int(self.rng.integers(2, 25) * (i + 1)))
            age_days = max(1, (tx_time - self.cust_created_map[cust["customer_id"]]).days)
            amount = round(float(self.rng.uniform(800.0, 9500.0)), 2)

            results.append({
                "merchant_id": mer["merchant_id"],
                "customer_id": cust["customer_id"],
                "device_id": syndicate_device, # Shared syndicate device
                "ip_address": self.all_ips[int(self.rng.integers(0, self.num_ips))],
                "timestamp": tx_time,
                "amount": amount,
                "currency": "INR",
                "payment_method": "card",
                "transaction_status": "SUCCESS" if self.rng.random() < 0.70 else "FAILED",
                "customer_account_age_days": age_days,
                "is_new_device": 1,
                "is_new_ip": 1,
                "fraud_label": 1,
                "fraud_scenario": "device_reuse_syndicate"
            })
        return results

    def generate_pattern_d_ip_pooling(self) -> List[Dict[str, Any]]:
        """D. IP Pooling / Proxy Abuse: 1 data-center/VPN IP originating hits for 20+ accounts."""
        pool_ip = self.rng.choice(self.vpn_ips)
        base_time = self.generate_random_timestamp()
        mer = self.merchants[int(self.rng.integers(0, self.num_merchants))]
        
        hit_count = int(self.rng.integers(16, 32))
        chosen_cust_indices = self.rng.choice(self.num_customers, size=hit_count, replace=False)
        results = []

        for i, c_idx in enumerate(chosen_cust_indices):
            cust = self.customers[c_idx]
            tx_time = base_time + timedelta(seconds=int(self.rng.integers(30, 180) * (i + 1)))
            age_days = max(1, (tx_time - self.cust_created_map[cust["customer_id"]]).days)
            amount = round(float(self.rng.uniform(500.0, 12000.0)), 2)

            results.append({
                "merchant_id": mer["merchant_id"],
                "customer_id": cust["customer_id"],
                "device_id": self.all_device_ids[int(self.rng.integers(0, self.num_devices))],
                "ip_address": pool_ip, # Single pooled VPN/proxy IP
                "timestamp": tx_time,
                "amount": amount,
                "currency": "INR",
                "payment_method": "upi" if self.rng.random() < 0.5 else "card",
                "transaction_status": "SUCCESS" if self.rng.random() < 0.75 else "FAILED",
                "customer_account_age_days": age_days,
                "is_new_device": 1,
                "is_new_ip": 1,
                "fraud_label": 1,
                "fraud_scenario": "ip_pooling_abuse"
            })
        return results

    def generate_pattern_e_amount_anomaly(self) -> Dict[str, Any]:
        """E. Contextual Amount Anomaly: 8x-15x customer typical spend on high risk merchant."""
        cust_idx = int(self.rng.integers(0, self.num_customers))
        cust = self.customers[cust_idx]
        
        high_risk_mers = [m for m in self.merchants if m["merchant_risk_tier"] == "HIGH"]
        mer = high_risk_mers[int(self.rng.integers(0, len(high_risk_mers)))] if high_risk_mers else self.merchants[0]

        timestamp = self.generate_random_timestamp()
        age_days = max(5, (timestamp - self.cust_created_map[cust["customer_id"]]).days)

        # Extreme multiple of customer's normal habit
        amount = round(float(cust["typical_amount_inr"] * self.rng.uniform(8.0, 16.0)), 2)
        amount = max(12000.0, min(180000.0, amount))

        return {
            "merchant_id": mer["merchant_id"],
            "customer_id": cust["customer_id"],
            "device_id": self.all_device_ids[int(self.rng.integers(0, self.num_devices))],
            "ip_address": self.all_ips[int(self.rng.integers(0, self.num_ips))],
            "timestamp": timestamp,
            "amount": amount,
            "currency": "INR",
            "payment_method": "netbanking" if self.rng.random() < 0.4 else "card",
            "transaction_status": "SUCCESS" if self.rng.random() < 0.80 else "FAILED",
            "customer_account_age_days": age_days,
            "is_new_device": 1,
            "is_new_ip": 1,
            "fraud_label": 1,
            "fraud_scenario": "amount_anomaly"
        }

    def generate_pattern_f_unusual_time(self) -> Dict[str, Any]:
        """F. Unusual Time Attack: Graveyard shift (3 AM - 5 AM) with high ticket and foreign IP."""
        cust_idx = int(self.rng.integers(0, self.num_customers))
        cust = self.customers[cust_idx]
        mer = self.merchants[int(self.rng.integers(0, self.num_merchants))]

        # Force off-peak graveyard hours (2 AM to 4 AM)
        hour = int(self.rng.integers(2, 5))
        timestamp = self.generate_random_timestamp(hour=hour)
        age_days = max(1, (timestamp - self.cust_created_map[cust["customer_id"]]).days)

        amount = round(float(self.rng.uniform(6000.0, 45000.0)), 2)

        return {
            "merchant_id": mer["merchant_id"],
            "customer_id": cust["customer_id"],
            "device_id": self.all_device_ids[int(self.rng.integers(0, self.num_devices))],
            "ip_address": self.rng.choice(self.vpn_ips),
            "timestamp": timestamp,
            "amount": amount,
            "currency": "INR",
            "payment_method": "card",
            "transaction_status": "SUCCESS" if self.rng.random() < 0.82 else "FAILED",
            "customer_account_age_days": age_days,
            "is_new_device": 1,
            "is_new_ip": 1,
            "fraud_label": 1,
            "fraud_scenario": "unusual_time_abuse"
        }

    def generate_pattern_g_abnormal_behavior(self) -> Dict[str, Any]:
        """G. Abnormal Customer Behavior: Sudden divergence in merchant category, method & amount."""
        cust_idx = int(self.rng.integers(0, self.num_customers))
        cust = self.customers[cust_idx]
        
        # Pick category opposite of customer typical spend
        digital_mers = [m for m in self.merchants if m["category"] in ["digital_gaming", "fintech_lending"]]
        mer = digital_mers[int(self.rng.integers(0, len(digital_mers)))] if digital_mers else self.merchants[0]

        timestamp = self.generate_random_timestamp()
        age_days = max(1, (timestamp - self.cust_created_map[cust["customer_id"]]).days)

        # Opposite payment channel
        unusual_pm = "card" if cust["typical_payment_method"] == "upi" else "upi"
        amount = round(float(cust["typical_amount_inr"] * self.rng.uniform(4.0, 9.0)), 2)

        return {
            "merchant_id": mer["merchant_id"],
            "customer_id": cust["customer_id"],
            "device_id": self.all_device_ids[int(self.rng.integers(0, self.num_devices))],
            "ip_address": self.all_ips[int(self.rng.integers(0, self.num_ips))],
            "timestamp": timestamp,
            "amount": amount,
            "currency": "INR",
            "payment_method": unusual_pm,
            "transaction_status": "SUCCESS" if self.rng.random() < 0.85 else "FAILED",
            "customer_account_age_days": age_days,
            "is_new_device": 1,
            "is_new_ip": 1,
            "fraud_label": 1,
            "fraud_scenario": "abnormal_behavior"
        }

    def generate_pattern_h_coordinated_spike(self) -> List[Dict[str, Any]]:
        """H. Coordinated Cluster / Spike: Synchronized 50-120 attack txs on single merchant within 2 hours."""
        target_mer = self.merchants[int(self.rng.integers(0, self.num_merchants))]
        base_time = self.generate_random_timestamp()
        spike_size = int(self.rng.integers(40, 90))
        
        shared_subnet_prefix = f"185.220.{self.rng.integers(100, 200)}"
        results = []

        for i in range(spike_size):
            tx_time = base_time + timedelta(seconds=int(self.rng.integers(10, 7200))) # within 2-hour window
            cust = self.customers[int(self.rng.integers(0, self.num_customers))]
            age_days = max(1, (tx_time - self.cust_created_map[cust["customer_id"]]).days)
            
            # Attacking proxy subnet
            cluster_ip = f"{shared_subnet_prefix}.{self.rng.integers(1, 254)}"
            amount = round(float(self.rng.uniform(1200.0, 8500.0)), 2)

            results.append({
                "merchant_id": target_mer["merchant_id"],
                "customer_id": cust["customer_id"],
                "device_id": self.all_device_ids[int(self.rng.integers(0, self.num_devices))],
                "ip_address": cluster_ip,
                "timestamp": tx_time,
                "amount": amount,
                "currency": "INR",
                "payment_method": "card" if self.rng.random() < 0.7 else "upi",
                "transaction_status": "SUCCESS" if self.rng.random() < 0.65 else "FAILED",
                "customer_account_age_days": age_days,
                "is_new_device": 1,
                "is_new_ip": 1,
                "fraud_label": 1,
                "fraud_scenario": "coordinated_cluster_spike"
            })
        return results
