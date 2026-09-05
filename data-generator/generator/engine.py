import os
import time
from datetime import datetime
from typing import Dict, List, Any, Tuple
import numpy as np
import pandas as pd

from generator.config import GeneratorConfig
from generator.entities import EntityGenerator
from generator.patterns import PatternEngine
from generator.stats import (
    compute_summary_statistics,
    print_summary_report,
    save_summary_statistics
)

class GeneratorEngine:
    """
    High-performance pipeline engine orchestrating master entity generation,
    behavioral transaction synthesis, chronological splitting, and statistics persistence.
    """

    def __init__(self, config: GeneratorConfig):
        self.config = config
        self.rng = np.random.default_rng(config.random_seed)
        self.start_time = datetime.fromisoformat(config.start_date)
        self.end_time = datetime.fromisoformat(config.end_date)

    def run(self) -> Tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, pd.DataFrame, Dict[str, Any]]:
        start_clock = time.perf_counter()
        print(f"[*] Initializing RiskShield AI Data Generator (Seed: {self.config.random_seed})")
        print(f"[*] Target: {self.config.transactions:,} transactions with {self.config.fraud_rate * 100:.2f}% target fraud rate")

        # 1. Master Entities Generation
        print(f"[*] Generating master entities...")
        entity_gen = EntityGenerator(
            seed=self.config.random_seed,
            start_time=self.start_time,
            end_time=self.end_time
        )
        
        merchants_df = entity_gen.generate_merchants(self.config.merchants)
        devices_df = entity_gen.generate_devices(self.config.devices)
        ips_df = entity_gen.generate_ip_addresses(self.config.ips)
        customers_df = entity_gen.generate_customers(
            count=self.config.customers,
            device_df=devices_df,
            ip_df=ips_df
        )

        print(f"    -> {len(merchants_df):,} merchants, {len(customers_df):,} customers, "
              f"{len(devices_df):,} devices, {len(ips_df):,} IPs")

        # 2. Pattern Engine Setup
        pattern_engine = PatternEngine(
            rng=self.rng,
            merchants_df=merchants_df,
            customers_df=customers_df,
            devices_df=devices_df,
            ips_df=ips_df,
            start_time=self.start_time,
            end_time=self.end_time
        )

        target_total = self.config.transactions
        target_fraud_count = int(target_total * self.config.fraud_rate)
        target_legit_count = target_total - target_fraud_count

        print(f"[*] Synthesizing {target_fraud_count:,} fraud events across scenarios A-H...")
        fraud_transactions: List[Dict[str, Any]] = []

        # Weights normalization for fraud scenarios
        weights_dict = self.config.fraud_pattern_weights.model_dump()
        scenarios = list(weights_dict.keys())
        w_vals = np.array([weights_dict[s] for s in scenarios], dtype=float)
        w_vals = w_vals / w_vals.sum()

        while len(fraud_transactions) < target_fraud_count:
            chosen_scenario = self.rng.choice(scenarios, p=w_vals)

            if chosen_scenario == "velocity_fraud":
                burst = pattern_engine.generate_pattern_a_velocity()
                fraud_transactions.extend(burst)
            elif chosen_scenario == "new_device_abuse":
                fraud_transactions.append(pattern_engine.generate_pattern_b_new_device_ato())
            elif chosen_scenario == "device_reuse_syndicate":
                burst = pattern_engine.generate_pattern_c_device_syndicate()
                fraud_transactions.extend(burst)
            elif chosen_scenario == "ip_pooling_abuse":
                burst = pattern_engine.generate_pattern_d_ip_pooling()
                fraud_transactions.extend(burst)
            elif chosen_scenario == "amount_anomaly":
                fraud_transactions.append(pattern_engine.generate_pattern_e_amount_anomaly())
            elif chosen_scenario == "unusual_time_abuse":
                fraud_transactions.append(pattern_engine.generate_pattern_f_unusual_time())
            elif chosen_scenario == "abnormal_behavior":
                fraud_transactions.append(pattern_engine.generate_pattern_g_abnormal_behavior())
            elif chosen_scenario == "coordinated_cluster_spike":
                burst = pattern_engine.generate_pattern_h_coordinated_spike()
                fraud_transactions.extend(burst)

        # Trim exact target fraud count
        fraud_transactions = fraud_transactions[:target_fraud_count]

        # 3. Generate Legitimate Transactions
        print(f"[*] Synthesizing {target_legit_count:,} legitimate transactions...")
        legit_transactions: List[Dict[str, Any]] = []
        
        # Batch generation for high throughput
        for _ in range(target_legit_count):
            legit_transactions.append(pattern_engine.generate_legitimate_tx())

        # 4. Merge, Enrich & Sort Chronologically
        print(f"[*] Merging, indexing, and sorting transactions chronologically...")
        all_txs = fraud_transactions + legit_transactions
        tx_df = pd.DataFrame(all_txs)

        # Sort strictly chronologically
        tx_df.sort_values(by="timestamp", inplace=True)
        tx_df.reset_index(drop=True, inplace=True)

        # Generate unique sequential transaction IDs
        tx_df["transaction_id"] = [f"tx_{i:08d}" for i in range(1, len(tx_df) + 1)]
        tx_df["amount_in_paise"] = (tx_df["amount"] * 100).astype(int)
        
        # Feature columns for convenience
        tx_dt = pd.to_datetime(tx_df["timestamp"])
        tx_df["hour_of_day"] = tx_dt.dt.hour
        tx_df["day_of_week"] = tx_dt.dt.dayofweek
        tx_df["timestamp"] = tx_dt.dt.strftime("%Y-%m-%dT%H:%M:%S")

        # Organize column ordering
        ordered_cols = [
            "transaction_id", "merchant_id", "customer_id", "device_id", "ip_address",
            "timestamp", "amount", "amount_in_paise", "currency", "payment_method",
            "transaction_status", "customer_account_age_days", "is_new_device", "is_new_ip",
            "hour_of_day", "day_of_week", "fraud_label", "fraud_scenario"
        ]
        tx_df = tx_df[ordered_cols]

        # 5. Chronological Train / Validation / Test Splits (Zero Leakage)
        print(f"[*] Partitioning chronological splits (Train: {self.config.train_ratio*100:.0f}%, "
              f"Val: {self.config.val_ratio*100:.0f}%, Test: {self.config.test_ratio*100:.0f}%)...")
        
        n_total = len(tx_df)
        train_end_idx = int(n_total * self.config.train_ratio)
        val_end_idx = int(n_total * (self.config.train_ratio + self.config.val_ratio))

        train_df = tx_df.iloc[:train_end_idx].copy()
        val_df = tx_df.iloc[train_end_idx:val_end_idx].copy()
        test_df = tx_df.iloc[val_end_idx:].copy()

        # 6. Save Data to Disk
        raw_dir = self.config.output_dirs.raw
        proc_dir = self.config.output_dirs.processed
        os.makedirs(raw_dir, exist_ok=True)
        os.makedirs(proc_dir, exist_ok=True)

        print(f"[*] Writing CSV artifacts to {raw_dir} and {proc_dir}...")
        merchants_df.to_csv(os.path.join(raw_dir, "merchants.csv"), index=False)
        customers_df.to_csv(os.path.join(raw_dir, "customers.csv"), index=False)
        devices_df.to_csv(os.path.join(raw_dir, "devices.csv"), index=False)
        ips_df.to_csv(os.path.join(raw_dir, "ip_addresses.csv"), index=False)
        tx_df.to_csv(os.path.join(raw_dir, "transactions.csv"), index=False)

        train_df.to_csv(os.path.join(proc_dir, "train.csv"), index=False)
        val_df.to_csv(os.path.join(proc_dir, "validation.csv"), index=False)
        test_df.to_csv(os.path.join(proc_dir, "test.csv"), index=False)

        # 7. Compute & Print Summary Statistics
        elapsed = time.perf_counter() - start_clock
        stats = compute_summary_statistics(
            df=tx_df,
            train_df=train_df,
            val_df=val_df,
            test_df=test_df,
            elapsed_time=elapsed,
            config=self.config
        )

        save_summary_statistics(stats, self.config.output_dirs.stats)
        print_summary_report(stats)

        return tx_df, train_df, val_df, test_df, stats
