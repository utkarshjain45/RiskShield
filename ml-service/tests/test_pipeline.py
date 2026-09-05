import os
import sys
import pytest
import numpy as np
import pandas as pd
import joblib

# Ensure ml-service root is in sys.path
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from features.engineer import LeakFreeFeatureEngineer, FEATURE_COLUMNS
from features.preprocessor import build_preprocessor, load_preprocessor
from evaluation.metrics import evaluate_predictions, compute_monetary_costs

def test_feature_engineering_columns():
    engineer = LeakFreeFeatureEngineer()
    
    sample_data = pd.DataFrame([
        {
            "transaction_id": "tx_1",
            "merchant_id": "mer_1",
            "customer_id": "cust_1",
            "device_id": "dev_1",
            "ip_address": "1.1.1.1",
            "timestamp": "2026-06-01T10:00:00",
            "amount": 500.0,
            "transaction_status": "SUCCESS",
            "customer_account_age_days": 30,
            "is_new_device": 0,
            "is_new_ip": 0,
            "hour_of_day": 10,
            "day_of_week": 0,
            "payment_method": "upi",
            "fraud_label": 0
        },
        {
            "transaction_id": "tx_2",
            "merchant_id": "mer_1",
            "customer_id": "cust_1",
            "device_id": "dev_1",
            "ip_address": "1.1.1.1",
            "timestamp": "2026-06-01T10:03:00",
            "amount": 1200.0,
            "transaction_status": "SUCCESS",
            "customer_account_age_days": 30,
            "is_new_device": 0,
            "is_new_ip": 0,
            "hour_of_day": 10,
            "day_of_week": 0,
            "payment_method": "upi",
            "fraud_label": 0
        }
    ])

    enriched = engineer.compute_features(sample_data)
    
    for col in FEATURE_COLUMNS:
        assert col in enriched.columns, f"Missing feature column: {col}"
    
    # Check sliding window count for tx_2 (should be 1 because tx_1 occurred 3 mins earlier)
    assert enriched.loc[1, "tx_count_5m"] == 1
    # Check prior avg for tx_2 should equal tx_1 amount (500.0)
    assert enriched.loc[1, "cust_hist_avg_amount"] == 500.0

def test_no_future_leakage():
    """
    Verifies that altering a future transaction does NOT change the feature
    values calculated for earlier transactions.
    """
    engineer_1 = LeakFreeFeatureEngineer()
    engineer_2 = LeakFreeFeatureEngineer()

    base_txs = [
        {
            "transaction_id": "tx_1",
            "merchant_id": "mer_1",
            "customer_id": "cust_A",
            "device_id": "dev_1",
            "ip_address": "1.1.1.1",
            "timestamp": "2026-06-01T10:00:00",
            "amount": 500.0,
            "transaction_status": "SUCCESS",
            "customer_account_age_days": 10,
            "is_new_device": 0,
            "is_new_ip": 0,
            "hour_of_day": 10,
            "day_of_week": 0,
            "payment_method": "upi",
            "fraud_label": 0
        }
    ]

    # Future transaction in scenario 1
    run_1_df = pd.DataFrame(base_txs + [
        {
            "transaction_id": "tx_2",
            "merchant_id": "mer_1",
            "customer_id": "cust_A",
            "device_id": "dev_1",
            "ip_address": "1.1.1.1",
            "timestamp": "2026-06-01T10:05:00",
            "amount": 1000.0,
            "transaction_status": "SUCCESS",
            "customer_account_age_days": 10,
            "is_new_device": 0,
            "is_new_ip": 0,
            "hour_of_day": 10,
            "day_of_week": 0,
            "payment_method": "upi",
            "fraud_label": 0
        }
    ])

    # Future transaction in scenario 2 with drastically different amount and failure
    run_2_df = pd.DataFrame(base_txs + [
        {
            "transaction_id": "tx_2",
            "merchant_id": "mer_1",
            "customer_id": "cust_A",
            "device_id": "dev_1",
            "ip_address": "1.1.1.1",
            "timestamp": "2026-06-01T10:05:00",
            "amount": 999999.0,
            "transaction_status": "FAILED",
            "customer_account_age_days": 10,
            "is_new_device": 1,
            "is_new_ip": 1,
            "hour_of_day": 10,
            "day_of_week": 0,
            "payment_method": "card",
            "fraud_label": 1
        }
    ])

    res_1 = engineer_1.compute_features(run_1_df)
    res_2 = engineer_2.compute_features(run_2_df)

    # All features for tx_1 must be strictly identical across both runs!
    for col in FEATURE_COLUMNS:
        val_1 = res_1.loc[0, col]
        val_2 = res_2.loc[0, col]
        assert val_1 == val_2, f"Target leakage detected on tx_1 feature '{col}'! ({val_1} != {val_2})"

def test_preprocessor_transformation():
    preprocessor = build_preprocessor()
    
    sample_df = pd.DataFrame({
        "amount": [100.0, 5000.0],
        "cust_hist_avg_amount": [100.0, 4000.0],
        "amount_deviation": [1.0, 1.25],
        "tx_count_5m": [0, 2],
        "tx_count_30m": [0, 3],
        "tx_count_1h": [0, 4],
        "amount_spent_1h": [0.0, 12000.0],
        "cust_tx_frequency": [0.0, 0.1],
        "cust_failed_rate": [0.0, 0.05],
        "device_tx_count": [0, 1],
        "device_account_count": [1, 1],
        "ip_tx_count": [0, 1],
        "ip_account_count": [1, 1],
        "is_new_device": [0, 1],
        "is_new_ip": [0, 1],
        "customer_account_age_days": [50, 120],
        "hour_of_day": [14, 22],
        "day_of_week": [1, 5],
        "payment_method": ["upi", "card"],
        "merchant_category": ["grocery_supermarket", "electronics"],
        "merchant_risk_tier": ["LOW", "HIGH"]
    })

    transformed = preprocessor.fit_transform(sample_df)
    assert transformed.shape[0] == 2
    assert transformed.shape[1] > 18 # Includes one-hot encodings

def test_monetary_cost_calculation():
    y_true = np.array([1, 1, 0, 0])
    y_pred = np.array([1, 0, 1, 0]) # 1 TP, 1 FN, 1 FP, 1 TN
    amounts = np.array([1000.0, 5000.0, 2000.0, 300.0])

    # FN: 2nd item (amount 5000)
    # FP: 3rd item (amount 2000) -> 150 + 2000 * 0.03 = 150 + 60 = 210
    costs = compute_monetary_costs(y_true, y_pred, amounts, fixed_review_cost=150.0, lost_margin_rate=0.03)

    assert costs["fn_count"] == 1
    assert costs["fn_cost_inr"] == 5000.0
    assert costs["fp_count"] == 1
    assert costs["fp_cost_inr"] == 210.0
    assert costs["total_cost_inr"] == 5210.0

def test_model_artifact_loading():
    models_dir = os.path.join(parent_dir, "models")
    model_path = os.path.join(models_dir, "best_model.joblib")
    preproc_path = os.path.join(models_dir, "preprocessor.joblib")

    if os.path.exists(model_path) and os.path.exists(preproc_path):
        model = joblib.load(model_path)
        preproc = load_preprocessor(preproc_path)
        assert hasattr(model, "predict_proba"), "Loaded model missing predict_proba method"
        assert hasattr(preproc, "transform"), "Loaded preprocessor missing transform method"
