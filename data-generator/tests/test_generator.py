import os
import sys
import shutil
import pytest
import pandas as pd
from datetime import datetime

# Ensure data-generator directory is in sys.path
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from generator.config import GeneratorConfig
from generator.engine import GeneratorEngine

TEST_OUTPUT_DIR = "test_data_artifacts"

@pytest.fixture(scope="module")
def run_simulation():
    """Runs a smaller deterministic simulation fixture for testing."""
    config = GeneratorConfig(
        transactions=2500,
        fraud_rate=0.04,
        random_seed=123,
        merchants=25,
        customers=200,
        devices=300,
        ips=350,
        start_date="2026-06-01T00:00:00",
        end_date="2026-06-30T23:59:59"
    )
    config.output_dirs.raw = os.path.join(TEST_OUTPUT_DIR, "raw")
    config.output_dirs.processed = os.path.join(TEST_OUTPUT_DIR, "processed")
    config.output_dirs.stats = os.path.join(TEST_OUTPUT_DIR, "summary_statistics.json")

    engine = GeneratorEngine(config)
    tx_df, train_df, val_df, test_df, stats = engine.run()
    
    yield tx_df, train_df, val_df, test_df, stats, config

    # Cleanup artifacts after tests
    if os.path.exists(TEST_OUTPUT_DIR):
        shutil.rmtree(TEST_OUTPUT_DIR)

def test_no_duplicate_transaction_ids(run_simulation):
    tx_df, _, _, _, _, _ = run_simulation
    assert len(tx_df) == 2500
    assert tx_df["transaction_id"].is_unique, "Duplicate transaction IDs detected!"

def test_valid_timestamps_chronological(run_simulation):
    tx_df, train_df, val_df, test_df, _, config = run_simulation
    
    # Check parseability
    timestamps = pd.to_datetime(tx_df["timestamp"])
    assert not timestamps.isna().any(), "Found invalid or unparseable timestamps"
    
    # Check sorted order
    assert timestamps.is_monotonic_increasing, "Transactions are not strictly sorted chronologically"
    
    # Check chronological boundaries without temporal leakage
    train_max = pd.to_datetime(train_df["timestamp"]).max()
    val_min = pd.to_datetime(val_df["timestamp"]).min()
    val_max = pd.to_datetime(val_df["timestamp"]).max()
    test_min = pd.to_datetime(test_df["timestamp"]).min()

    assert train_max <= val_min, "Temporal leakage: train data overlaps validation data"
    assert val_max <= test_min, "Temporal leakage: validation data overlaps test data"

def test_valid_relationships(run_simulation):
    tx_df, _, _, _, _, config = run_simulation
    
    raw_dir = config.output_dirs.raw
    merchants_df = pd.read_csv(os.path.join(raw_dir, "merchants.csv"))
    customers_df = pd.read_csv(os.path.join(raw_dir, "customers.csv"))
    devices_df = pd.read_csv(os.path.join(raw_dir, "devices.csv"))
    ips_df = pd.read_csv(os.path.join(raw_dir, "ip_addresses.csv"))

    # Merchant integrity
    assert set(tx_df["merchant_id"]).issubset(set(merchants_df["merchant_id"])), \
        "Orphaned merchant_id found in transactions"

    # Customer integrity
    assert set(tx_df["customer_id"]).issubset(set(customers_df["customer_id"])), \
        "Orphaned customer_id found in transactions"

    # Device integrity
    assert set(tx_df["device_id"]).issubset(set(devices_df["device_id"])), \
        "Orphaned device_id found in transactions"

    # IP address integrity (note: cluster spike generates subnet IPs, checking standard pool)
    non_spike_ips = tx_df[tx_df["fraud_scenario"] != "coordinated_cluster_spike"]["ip_address"]
    assert set(non_spike_ips).issubset(set(ips_df["ip_address"])), \
        "Orphaned IP address found in standard transactions"

def test_labels_are_binary(run_simulation):
    tx_df, _, _, _, _, _ = run_simulation
    unique_labels = set(tx_df["fraud_label"].unique())
    assert unique_labels.issubset({0, 1}), f"Non-binary labels detected: {unique_labels}"

def test_configurable_fraud_rate(run_simulation):
    tx_df, _, _, _, _, config = run_simulation
    expected_fraud_count = int(config.transactions * config.fraud_rate)
    actual_fraud_count = int(tx_df["fraud_label"].sum())
    
    assert actual_fraud_count == expected_fraud_count, \
        f"Expected {expected_fraud_count} fraud txs, but got {actual_fraud_count}"

def test_deterministic_output_with_same_seed():
    """Verifies that two runs with identical seeds produce identical transaction rows."""
    config_a = GeneratorConfig(
        transactions=500,
        fraud_rate=0.03,
        random_seed=999,
        merchants=20,
        customers=100,
        devices=150,
        ips=200
    )
    config_a.output_dirs.raw = os.path.join(TEST_OUTPUT_DIR, "run_a_raw")
    config_a.output_dirs.processed = os.path.join(TEST_OUTPUT_DIR, "run_a_proc")
    config_a.output_dirs.stats = os.path.join(TEST_OUTPUT_DIR, "run_a_stats.json")

    config_b = GeneratorConfig(
        transactions=500,
        fraud_rate=0.03,
        random_seed=999,
        merchants=20,
        customers=100,
        devices=150,
        ips=200
    )
    config_b.output_dirs.raw = os.path.join(TEST_OUTPUT_DIR, "run_b_raw")
    config_b.output_dirs.processed = os.path.join(TEST_OUTPUT_DIR, "run_b_proc")
    config_b.output_dirs.stats = os.path.join(TEST_OUTPUT_DIR, "run_b_stats.json")

    df_a, _, _, _, _ = GeneratorEngine(config_a).run()
    df_b, _, _, _, _ = GeneratorEngine(config_b).run()

    pd.testing.assert_frame_equal(df_a, df_b, check_exact=True)
