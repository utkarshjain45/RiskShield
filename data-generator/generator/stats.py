import json
import os
from typing import Dict, Any
import pandas as pd
import numpy as np

def compute_summary_statistics(
    df: pd.DataFrame,
    train_df: pd.DataFrame,
    val_df: pd.DataFrame,
    test_df: pd.DataFrame,
    elapsed_time: float,
    config: Any
) -> Dict[str, Any]:
    """Computes comprehensive summary statistics across raw and split datasets."""
    
    total_tx = len(df)
    fraud_count = int(df["fraud_label"].sum())
    actual_fraud_rate = float(fraud_count / max(1, total_tx))

    # Amounts
    legit_amounts = df[df["fraud_label"] == 0]["amount"]
    fraud_amounts = df[df["fraud_label"] == 1]["amount"]

    stats = {
        "dataset_metrics": {
            "total_transactions": total_tx,
            "target_fraud_rate": config.fraud_rate,
            "actual_fraud_rate": round(actual_fraud_rate, 5),
            "total_fraud_count": fraud_count,
            "total_legit_count": total_tx - fraud_count,
            "unique_merchants": int(df["merchant_id"].nunique()),
            "unique_customers": int(df["customer_id"].nunique()),
            "unique_devices": int(df["device_id"].nunique()),
            "unique_ips": int(df["ip_address"].nunique()),
            "date_start": str(df["timestamp"].min()),
            "date_end": str(df["timestamp"].max())
        },
        "amount_inr_distributions": {
            "legitimate": {
                "mean": round(float(legit_amounts.mean()), 2) if len(legit_amounts) else 0.0,
                "median": round(float(legit_amounts.median()), 2) if len(legit_amounts) else 0.0,
                "std": round(float(legit_amounts.std()), 2) if len(legit_amounts) else 0.0,
                "min": round(float(legit_amounts.min()), 2) if len(legit_amounts) else 0.0,
                "max": round(float(legit_amounts.max()), 2) if len(legit_amounts) else 0.0
            },
            "fraudulent": {
                "mean": round(float(fraud_amounts.mean()), 2) if len(fraud_amounts) else 0.0,
                "median": round(float(fraud_amounts.median()), 2) if len(fraud_amounts) else 0.0,
                "std": round(float(fraud_amounts.std()), 2) if len(fraud_amounts) else 0.0,
                "min": round(float(fraud_amounts.min()), 2) if len(fraud_amounts) else 0.0,
                "max": round(float(fraud_amounts.max()), 2) if len(fraud_amounts) else 0.0
            }
        },
        "chronological_splits": {
            "train": {
                "count": len(train_df),
                "ratio": round(len(train_df) / total_tx, 4),
                "fraud_count": int(train_df["fraud_label"].sum()),
                "fraud_rate": round(float(train_df["fraud_label"].mean()), 5),
                "start": str(train_df["timestamp"].min()),
                "end": str(train_df["timestamp"].max())
            },
            "validation": {
                "count": len(val_df),
                "ratio": round(len(val_df) / total_tx, 4),
                "fraud_count": int(val_df["fraud_label"].sum()),
                "fraud_rate": round(float(val_df["fraud_label"].mean()), 5),
                "start": str(val_df["timestamp"].min()),
                "end": str(val_df["timestamp"].max())
            },
            "test": {
                "count": len(test_df),
                "ratio": round(len(test_df) / total_tx, 4),
                "fraud_count": int(test_df["fraud_label"].sum()),
                "fraud_rate": round(float(test_df["fraud_label"].mean()), 5),
                "start": str(test_df["timestamp"].min()),
                "end": str(test_df["timestamp"].max())
            }
        },
        "breakdowns": {
            "fraud_scenarios": df["fraud_scenario"].value_counts().to_dict(),
            "payment_methods": df["payment_method"].value_counts().to_dict(),
            "transaction_status": df["transaction_status"].value_counts().to_dict()
        },
        "performance": {
            "elapsed_seconds": round(elapsed_time, 2),
            "throughput_tx_per_sec": round(total_tx / max(0.001, elapsed_time), 1)
        }
    }

    return stats

def print_summary_report(stats: Dict[str, Any]):
    """Prints a clean, ASCII-safe formatted report to console."""
    dm = stats["dataset_metrics"]
    perf = stats["performance"]
    splits = stats["chronological_splits"]
    amt = stats["amount_inr_distributions"]

    print("\n" + "=" * 76)
    print(" [RISKSHIELD AI] - SYNTHETIC DATA GENERATOR SUMMARY REPORT")
    print("=" * 76)
    print(f" Total Transactions : {dm['total_transactions']:,}")
    print(f" Target Fraud Rate  : {dm['target_fraud_rate'] * 100:.2f}% | Actual: {dm['actual_fraud_rate'] * 100:.2f}% ({dm['total_fraud_count']:,} fraud txs)")
    print(f" Date Coverage      : {dm['date_start']}  -->  {dm['date_end']}")
    print(f" Entities Generated : {dm['unique_merchants']:,} Merchants | {dm['unique_customers']:,} Customers | {dm['unique_devices']:,} Devices | {dm['unique_ips']:,} IPs")
    print(f" Runtime Performance: {perf['elapsed_seconds']}s ({perf['throughput_tx_per_sec']:,} tx/s)")
    print("-" * 76)
    print(" CHRONOLOGICAL SPLITS (Zero Future-Data Leakage):")
    for name, s in splits.items():
        print(f"  * {name.upper():<11}: {s['count']:,} tx ({s['ratio']*100:.1f}%) | Fraud: {s['fraud_rate']*100:.2f}% ({s['fraud_count']:,} tx) | Range: {s['start'][:10]} to {s['end'][:10]}")
    print("-" * 76)
    print(" AMOUNT (INR) DISTRIBUTIONS:")
    print(f"  * Legitimate : Mean: INR {amt['legitimate']['mean']:.2f} | Median: INR {amt['legitimate']['median']:.2f} | Std: INR {amt['legitimate']['std']:.2f} | Min: INR {amt['legitimate']['min']:.2f} | Max: INR {amt['legitimate']['max']:.2f}")
    print(f"  * Fraudulent : Mean: INR {amt['fraudulent']['mean']:.2f} | Median: INR {amt['fraudulent']['median']:.2f} | Std: INR {amt['fraudulent']['std']:.2f} | Min: INR {amt['fraudulent']['min']:.2f} | Max: INR {amt['fraudulent']['max']:.2f}")
    print("-" * 76)
    print(" FRAUD SCENARIO BREAKDOWN:")
    for scenario, count in stats["breakdowns"]["fraud_scenarios"].items():
        pct = (count / dm["total_transactions"]) * 100
        print(f"  * {scenario:<28}: {count:>6,} ({pct:>5.2f}%)")
    print("=" * 76 + "\n")

def save_summary_statistics(stats: Dict[str, Any], filepath: str):
    """Saves the summary dictionary as formatted JSON."""
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(stats, f, indent=2)
