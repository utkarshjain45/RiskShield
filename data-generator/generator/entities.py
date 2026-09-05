import random
from datetime import datetime, timedelta
from typing import Dict, List, Tuple
import numpy as np
import pandas as pd

INDIAN_STATES = [
    "Maharashtra", "Karnataka", "Delhi", "Tamil Nadu", "Telangana",
    "Gujarat", "Uttar Pradesh", "West Bengal", "Rajasthan", "Kerala"
]

MERCHANT_CATEGORIES = {
    "grocery_supermarket": {"weight": 0.28, "avg": 850.0, "std": 380.0, "risk_tier": "LOW"},
    "food_dining": {"weight": 0.22, "avg": 420.0, "std": 180.0, "risk_tier": "LOW"},
    "fashion_apparel": {"weight": 0.16, "avg": 2400.0, "std": 1100.0, "risk_tier": "MEDIUM"},
    "electronics": {"weight": 0.10, "avg": 14500.0, "std": 7500.0, "risk_tier": "HIGH"},
    "travel_airline": {"weight": 0.08, "avg": 8200.0, "std": 4200.0, "risk_tier": "MEDIUM"},
    "digital_gaming": {"weight": 0.06, "avg": 1250.0, "std": 1600.0, "risk_tier": "HIGH"},
    "fintech_lending": {"weight": 0.06, "avg": 4800.0, "std": 3200.0, "risk_tier": "MEDIUM"},
    "pharmacy_healthcare": {"weight": 0.04, "avg": 950.0, "std": 450.0, "risk_tier": "LOW"},
}

MERCHANT_BRAND_PREFIXES = [
    "QuickKart", "FreshBazaar", "UrbanMart", "ChaiPoint", "SwiggyEats", "ZomatoDirect",
    "MyntraTrends", "NykaaBeauty", "RelianceDigital", "CromaTech", "FlipkartExpress",
    "IndiGoAviation", "MakeMyTripDirect", "SteamGamesInd", "PlayArena", "CredPay",
    "ApolloPharmacy", "PharmEasyStore", "TataCliq", "AjioFashion"
]

DEVICE_TYPES = [
    ("mobile_android", 0.55),
    ("mobile_ios", 0.25),
    ("desktop_chrome", 0.12),
    ("desktop_windows", 0.05),
    ("tablet", 0.03)
]

ISPS = [
    ("Reliance Jio", 0.42, False, "IN"),
    ("Bharti Airtel", 0.35, False, "IN"),
    ("Vodafone Idea", 0.10, False, "IN"),
    ("ACT Fibernet", 0.07, False, "IN"),
    ("BSNL Broadband", 0.03, False, "IN"),
    ("DigitalOcean VPN", 0.015, True, "SG"),
    ("AWS Data Center Proxy", 0.015, True, "US")
]

class EntityGenerator:
    """
    Generates relational master entities: Merchants, Customers, Devices, IP Addresses.
    Uses vectorization and deterministic RNG for fast scaling up to millions of records.
    """

    def __init__(self, seed: int, start_time: datetime, end_time: datetime):
        self.rng = np.random.default_rng(seed)
        random.seed(seed)
        self.start_time = start_time
        self.end_time = end_time
        self.sim_span_seconds = max(1, int((end_time - start_time).total_seconds()))

    def generate_merchants(self, count: int) -> pd.DataFrame:
        cat_names = list(MERCHANT_CATEGORIES.keys())
        cat_weights = [MERCHANT_CATEGORIES[k]["weight"] for k in cat_names]
        chosen_cats = self.rng.choice(cat_names, size=count, p=cat_weights)

        merchant_ids = [f"mer_{i:04d}" for i in range(1, count + 1)]
        names = [
            f"{self.rng.choice(MERCHANT_BRAND_PREFIXES)}_{i:04d}"
            for i in range(1, count + 1)
        ]
        
        avg_tickets = [MERCHANT_CATEGORIES[c]["avg"] for c in chosen_cats]
        std_tickets = [MERCHANT_CATEGORIES[c]["std"] for c in chosen_cats]
        risk_tiers = [MERCHANT_CATEGORIES[c]["risk_tier"] for c in chosen_cats]
        
        # Onboarding timestamp: 300 to 30 days before simulation start
        offset_seconds = self.rng.integers(30 * 86400, 300 * 86400, size=count)
        created_ats = [
            (self.start_time - timedelta(seconds=int(sec))).isoformat()
            for sec in offset_seconds
        ]

        return pd.DataFrame({
            "merchant_id": merchant_ids,
            "merchant_name": names,
            "category": chosen_cats,
            "merchant_risk_tier": risk_tiers,
            "avg_ticket_inr": avg_tickets,
            "std_ticket_inr": std_tickets,
            "created_at": created_ats
        })

    def generate_devices(self, count: int) -> pd.DataFrame:
        dev_ids = [f"dev_{i:06d}" for i in range(1, count + 1)]
        d_types, d_weights = zip(*DEVICE_TYPES)
        chosen_types = self.rng.choice(d_types, size=count, p=d_weights)
        
        # 0.5% emulator rate in general pool
        is_emulator = self.rng.choice([0, 1], size=count, p=[0.995, 0.005])
        
        # First seen timestamps
        offsets = self.rng.integers(0, self.sim_span_seconds + (60 * 86400), size=count)
        first_seens = [
            (self.start_time - timedelta(days=60) + timedelta(seconds=int(sec))).isoformat()
            for sec in offsets
        ]

        return pd.DataFrame({
            "device_id": dev_ids,
            "device_type": chosen_types,
            "is_emulator": is_emulator,
            "first_seen_at": first_seens
        })

    def generate_ip_addresses(self, count: int) -> pd.DataFrame:
        isp_names, isp_weights, is_vpn_flags, countries = zip(*ISPS)
        chosen_indices = self.rng.choice(len(ISPS), size=count, p=isp_weights)

        chosen_isps = [isp_names[i] for i in chosen_indices]
        chosen_vpns = [int(is_vpn_flags[i]) for i in chosen_indices]
        chosen_countries = [countries[i] for i in chosen_indices]
        
        chosen_states = [
            self.rng.choice(INDIAN_STATES) if chosen_countries[i] == "IN" else "International"
            for i in range(count)
        ]

        # Generate realistic looking IP blocks
        octet1 = self.rng.choice([49, 103, 106, 117, 122, 157, 182, 223], size=count)
        octet2 = self.rng.integers(1, 255, size=count)
        octet3 = self.rng.integers(1, 255, size=count)
        octet4 = self.rng.integers(1, 254, size=count)
        ips = [f"{octet1[i]}.{octet2[i]}.{octet3[i]}.{octet4[i]}" for i in range(count)]

        offsets = self.rng.integers(0, self.sim_span_seconds + (90 * 86400), size=count)
        first_seens = [
            (self.start_time - timedelta(days=90) + timedelta(seconds=int(sec))).isoformat()
            for sec in offsets
        ]

        return pd.DataFrame({
            "ip_address": ips,
            "ip_country": chosen_countries,
            "ip_state": chosen_states,
            "isp": chosen_isps,
            "is_vpn_proxy": chosen_vpns,
            "first_seen_at": first_seens
        })

    def generate_customers(
        self,
        count: int,
        device_df: pd.DataFrame,
        ip_df: pd.DataFrame
    ) -> pd.DataFrame:
        customer_ids = [f"cust_{i:06d}" for i in range(1, count + 1)]
        
        # Account creation age: 10 to 400 days before simulation start
        account_ages_days = self.rng.integers(10, 400, size=count)
        account_created_ats = [
            (self.start_time - timedelta(days=int(age))).isoformat()
            for age in account_ages_days
        ]

        # Customer personal spending baseline (log-normal distribution)
        typical_amounts = np.round(np.exp(self.rng.normal(6.5, 0.75, size=count)), 2) # median ~₹665, tail to ₹10k
        
        pm_choices = ["upi", "card", "netbanking"]
        pm_weights = [0.55, 0.38, 0.07]
        typical_pms = self.rng.choice(pm_choices, size=count, p=pm_weights)

        # Primary device & primary IP binding
        primary_devs = self.rng.choice(device_df["device_id"].values, size=count)
        primary_ips = self.rng.choice(ip_df["ip_address"].values, size=count)
        billing_states = self.rng.choice(INDIAN_STATES, size=count)

        risk_segments = self.rng.choice(
            ["low_risk", "standard", "high_risk"],
            size=count,
            p=[0.82, 0.15, 0.03]
        )

        return pd.DataFrame({
            "customer_id": customer_ids,
            "account_created_at": account_created_ats,
            "typical_amount_inr": typical_amounts,
            "typical_payment_method": typical_pms,
            "primary_device_id": primary_devs,
            "primary_ip": primary_ips,
            "billing_state": billing_states,
            "risk_segment": risk_segments
        })
