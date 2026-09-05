import os
from collections import defaultdict, deque
from datetime import datetime
from typing import Dict, List, Tuple, Optional, Any
import numpy as np
import pandas as pd

FEATURE_COLUMNS = [
    # Amounts and deviation
    "amount",
    "cust_hist_avg_amount",
    "amount_deviation",
    # Velocity sliding windows
    "tx_count_5m",
    "tx_count_30m",
    "tx_count_1h",
    "amount_spent_1h",
    # Customer historical aggregates
    "cust_tx_frequency",
    "cust_failed_rate",
    # Device and IP behavioral graphs
    "device_tx_count",
    "device_account_count",
    "ip_tx_count",
    "ip_account_count",
    # Context flags and metadata
    "is_new_device",
    "is_new_ip",
    "customer_account_age_days",
    "hour_of_day",
    "day_of_week",
    # Categoricals
    "payment_method",
    "merchant_category",
    "merchant_risk_tier"
]

class LeakFreeFeatureEngineer:
    """
    Computes streaming/point-in-time features strictly using historical events
    prior to each transaction's timestamp, completely preventing target and temporal leakage.
    """

    def __init__(self, merchants_df: Optional[pd.DataFrame] = None):
        self.merchant_meta: Dict[str, Dict[str, str]] = {}
        if merchants_df is not None and not merchants_df.empty:
            for _, row in merchants_df.iterrows():
                self.merchant_meta[str(row["merchant_id"])] = {
                    "category": str(row.get("category", "unknown")),
                    "merchant_risk_tier": str(row.get("merchant_risk_tier", "MEDIUM"))
                }

    def compute_features(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Takes a chronologically ordered DataFrame of transactions and computes
        all behavioral, velocity, and relational graph features sequentially.
        """
        # Ensure chronological ordering
        df = df.copy()
        if not pd.to_datetime(df["timestamp"]).is_monotonic_increasing:
            df.sort_values(by="timestamp", inplace=True)
            df.reset_index(drop=True, inplace=True)

        # Convert timestamps to epoch seconds for ultra-fast sliding window comparisons
        ts_series = pd.to_datetime(df["timestamp"])
        epochs = (ts_series - pd.Timestamp("1970-01-01")) // pd.Timedelta("1s")
        epoch_array = epochs.to_numpy()

        n = len(df)
        
        # Output feature arrays
        cust_hist_avg_amount = np.zeros(n, dtype=np.float64)
        amount_deviation = np.zeros(n, dtype=np.float64)
        tx_count_5m = np.zeros(n, dtype=np.int32)
        tx_count_30m = np.zeros(n, dtype=np.int32)
        tx_count_1h = np.zeros(n, dtype=np.int32)
        amount_spent_1h = np.zeros(n, dtype=np.float64)
        cust_tx_frequency = np.zeros(n, dtype=np.float64)
        cust_failed_rate = np.zeros(n, dtype=np.float64)
        device_tx_count = np.zeros(n, dtype=np.int32)
        device_account_count = np.zeros(n, dtype=np.int32)
        ip_tx_count = np.zeros(n, dtype=np.int32)
        ip_account_count = np.zeros(n, dtype=np.int32)
        merchant_category = ["unknown"] * n
        merchant_risk_tier = ["MEDIUM"] * n

        # Internal state tracking (point-in-time)
        # Customer: sum of amounts, count of txs, count of failures
        cust_tx_totals = defaultdict(int)
        cust_amount_sums = defaultdict(float)
        cust_failed_counts = defaultdict(int)
        
        # Sliding windows for customer transactions (epoch, amount)
        # We store deque of (timestamp, amount) per customer
        cust_sliding_windows: Dict[str, deque] = defaultdict(deque)
        cust_1h_amount_sums: Dict[str, float] = defaultdict(float)

        # Device state: tx count, set of distinct customer_ids
        dev_tx_counts = defaultdict(int)
        dev_customers = defaultdict(set)

        # IP state: tx count, set of distinct customer_ids
        ip_tx_counts = defaultdict(int)
        ip_customers = defaultdict(set)

        cust_ids = df["customer_id"].values
        amounts = df["amount"].values
        dev_ids = df["device_id"].values
        ips = df["ip_address"].values
        statuses = df["transaction_status"].values
        ages = df["customer_account_age_days"].values
        mer_ids = df["merchant_id"].values

        # Sequential point-in-time calculation (Single pass, strictly prior state)
        for i in range(n):
            curr_epoch = epoch_array[i]
            cid = cust_ids[i]
            amt = float(amounts[i])
            did = dev_ids[i]
            ip = ips[i]
            status = statuses[i]
            age = max(1, int(ages[i]))
            mid = str(mer_ids[i])

            # 1. Customer historical average & deviation
            prior_cust_count = cust_tx_totals[cid]
            if prior_cust_count > 0:
                prior_avg = cust_amount_sums[cid] / prior_cust_count
                cust_hist_avg_amount[i] = round(prior_avg, 2)
                amount_deviation[i] = round(amt / (prior_avg + 1e-4), 4)
                cust_failed_rate[i] = round(cust_failed_counts[cid] / prior_cust_count, 4)
            else:
                cust_hist_avg_amount[i] = amt
                amount_deviation[i] = 1.0
                cust_failed_rate[i] = 0.0

            cust_tx_frequency[i] = round(prior_cust_count / age, 4)

            # 2. Sliding window velocity (5m = 300s, 30m = 1800s, 1h = 3600s)
            c_win = cust_sliding_windows[cid]
            # Prune events older than 1 hour (3600s)
            while c_win and c_win[0][0] <= (curr_epoch - 3600):
                old_ts, old_amt = c_win.popleft()
                cust_1h_amount_sums[cid] -= old_amt

            # Count in 1 hour
            tx_count_1h[i] = len(c_win)
            amount_spent_1h[i] = round(max(0.0, cust_1h_amount_sums[cid]), 2)

            # Count in 30m and 5m by looking into the deque
            cutoff_30m = curr_epoch - 1800
            cutoff_5m = curr_epoch - 300
            c_30m = 0
            c_5m = 0
            for item_ts, _ in reversed(c_win):
                if item_ts > cutoff_30m:
                    c_30m += 1
                    if item_ts > cutoff_5m:
                        c_5m += 1
                else:
                    break
            tx_count_30m[i] = c_5m + (c_30m - c_5m) # c_30m
            tx_count_5m[i] = c_5m

            # 3. Device behavioral graph
            device_tx_count[i] = dev_tx_counts[did]
            device_account_count[i] = len(dev_customers[did])

            # 4. IP behavioral graph
            ip_tx_count[i] = ip_tx_counts[ip]
            ip_account_count[i] = len(ip_customers[ip])

            # 5. Merchant metadata join
            if mid in self.merchant_meta:
                merchant_category[i] = self.merchant_meta[mid]["category"]
                merchant_risk_tier[i] = self.merchant_meta[mid]["merchant_risk_tier"]

            # --- State Update (Strictly after extracting point-in-time features) ---
            cust_tx_totals[cid] += 1
            cust_amount_sums[cid] += amt
            if status == "FAILED":
                cust_failed_counts[cid] += 1

            c_win.append((curr_epoch, amt))
            cust_1h_amount_sums[cid] += amt

            dev_tx_counts[did] += 1
            dev_customers[did].add(cid)

            ip_tx_counts[ip] += 1
            ip_customers[ip].add(cid)

        # Attach computed features to DataFrame
        df["cust_hist_avg_amount"] = cust_hist_avg_amount
        df["amount_deviation"] = amount_deviation
        df["tx_count_5m"] = tx_count_5m
        df["tx_count_30m"] = tx_count_30m
        df["tx_count_1h"] = tx_count_1h
        df["amount_spent_1h"] = amount_spent_1h
        df["cust_tx_frequency"] = cust_tx_frequency
        df["cust_failed_rate"] = cust_failed_rate
        df["device_tx_count"] = device_tx_count
        df["device_account_count"] = device_account_count
        df["ip_tx_count"] = ip_tx_count
        df["ip_account_count"] = ip_account_count
        df["merchant_category"] = merchant_category
        df["merchant_risk_tier"] = merchant_risk_tier

        return df
