import json
import logging
import os
import time
from datetime import datetime, timezone
from typing import Dict, List, Any, Optional, Tuple
import joblib
import numpy as np
import pandas as pd
import shap

from app.core.config import settings
from app.schemas.risk import (
    TransactionRiskRequest,
    RiskScoreResponse,
    RiskSignal,
    BatchRiskScoreRequest,
    BatchRiskScoreResponse,
    ExplainResponse,
    FeatureContribution,
    ModelInfoResponse
)
from features.preprocessor import NUMERICAL_FEATURES, CATEGORICAL_FEATURES
from app.services.explainability import ShapExplainabilityNormalizer

logger = logging.getLogger("riskshield.ml.service")

class ModelService:
    """
    Singleton service managing model lifecycle, in-memory feature transformation,
    real-time XGBoost inference, and exact TreeSHAP feature attributions.
    """

    def __init__(self):
        self.model = None
        self.preprocessor = None
        self.explainer = None
        self.feature_schema = {}
        self.metadata = {}
        self.transformed_feature_names: List[str] = []
        self.is_loaded = False
        self.start_time = time.time()

    def load_artifacts(self):
        """Loads model, preprocessor, schema, and metadata once on startup."""
        models_dir = settings.MODELS_DIR
        model_path = os.path.join(models_dir, settings.MODEL_FILE)
        preproc_path = os.path.join(models_dir, settings.PREPROCESSOR_FILE)
        schema_path = os.path.join(models_dir, settings.SCHEMA_FILE)
        metadata_path = os.path.join(models_dir, settings.METADATA_FILE)

        logger.info("Loading model artifacts from %s...", models_dir)

        if not os.path.exists(model_path) or not os.path.exists(preproc_path):
            raise FileNotFoundError(
                f"Model artifacts not found in {models_dir}. Please run 'python -m training.train' first."
            )

        # Load model and preprocessor
        self.model = joblib.load(model_path)
        self.preprocessor = joblib.load(preproc_path)

        # Load feature schema and metadata
        if os.path.exists(schema_path):
            with open(schema_path, "r", encoding="utf-8") as f:
                self.feature_schema = json.load(f)
                self.transformed_feature_names = self.feature_schema.get("transformed_feature_names", [])

        if os.path.exists(metadata_path):
            with open(metadata_path, "r", encoding="utf-8") as f:
                self.metadata = json.load(f)

        # Initialize TreeSHAP explainer once on startup
        logger.info("Initializing TreeSHAP explainer...")
        self.explainer = shap.TreeExplainer(self.model)
        self.is_loaded = True
        logger.info("Model service loaded successfully (Version: %s)", self.get_version())

    def get_version(self) -> str:
        return self.metadata.get("model_version", settings.DEFAULT_MODEL_VERSION)

    def _prepare_dataframe(self, transactions: List[TransactionRiskRequest]) -> pd.DataFrame:
        """Converts request Pydantic models into an ordered pandas DataFrame."""
        records = []
        for tx in transactions:
            row = {
                # Numerics
                "amount": tx.amount,
                "cust_hist_avg_amount": tx.cust_hist_avg_amount,
                "amount_deviation": tx.amount_deviation,
                "tx_count_5m": tx.tx_count_5m,
                "tx_count_30m": tx.tx_count_30m,
                "tx_count_1h": tx.tx_count_1h,
                "amount_spent_1h": tx.amount_spent_1h,
                "cust_tx_frequency": tx.cust_tx_frequency,
                "cust_failed_rate": tx.cust_failed_rate,
                "device_tx_count": tx.device_tx_count,
                "device_account_count": tx.device_account_count,
                "ip_tx_count": tx.ip_tx_count,
                "ip_account_count": tx.ip_account_count,
                "is_new_device": tx.is_new_device,
                "is_new_ip": tx.is_new_ip,
                "customer_account_age_days": tx.customer_account_age_days,
                "hour_of_day": tx.hour_of_day,
                "day_of_week": tx.day_of_week,
                # Categoricals
                "payment_method": tx.payment_method,
                "merchant_category": tx.merchant_category,
                "merchant_risk_tier": tx.merchant_risk_tier
            }
            records.append(row)
        return pd.DataFrame(records)

    def _generate_signal_description(self, name: str, val: Any, impact: float) -> str:
        """Constructs human-readable rationale for a contributing risk signal."""
        direction = "elevates" if impact > 0 else "reduces"
        if "velocity" in name or "count" in name:
            return f"Observed velocity: {val} events ({direction} risk)"
        elif "amount_deviation" in name:
            return f"Transaction amount is {val:.1f}x of customer average ({direction} risk)"
        elif "is_new_device" in name and val == 1:
            return "Transaction originates from an unrecognized hardware device"
        elif "is_new_ip" in name and val == 1:
            return "Transaction originates from an unrecognized IP network address"
        elif "cust_failed_rate" in name:
            return f"Customer historical payment failure rate is {float(val)*100:.1f}%"
        elif "device_account_count" in name and val > 2:
            return f"Hardware device shared across {val} distinct customer accounts (syndicate indicator)"
        elif "ip_account_count" in name and val > 3:
            return f"IP address shared across {val} distinct customer accounts (proxy/bot indicator)"
        return f"{name} = {val} ({direction} fraud risk)"

    def score_single(self, tx: TransactionRiskRequest) -> RiskScoreResponse:
        """Scores a single transaction returning probability, normalized risk (0-100), and top SHAP signals."""
        t0 = time.perf_counter()
        
        df = self._prepare_dataframe([tx])
        X_trans = self.preprocessor.transform(df)

        # Raw fraud probability [0.0, 1.0]
        prob = float(self.model.predict_proba(X_trans)[0, 1])
        risk_score = round(prob * 100.0, 2) # Normalized 0-100

        # SHAP feature contributions
        shap_values = self.explainer.shap_values(X_trans)[0]
        raw_dict = tx.model_dump()
        aggregated = ShapExplainabilityNormalizer.aggregate_shap_contributions(
            self.transformed_feature_names, shap_values, raw_dict
        )

        signals: List[RiskSignal] = []
        for item in aggregated[:5]:
            signals.append(
                RiskSignal(
                    feature_name=item["concept_key"],
                    display_name=item["display_name"],
                    feature_value=item["raw_value"],
                    shap_impact=item["shap_impact"],
                    formatted_impact=item["formatted_impact"],
                    direction=item["direction"],
                    description=item["description"]
                )
            )

        latency_ms = round((time.perf_counter() - t0) * 1000, 2)

        return RiskScoreResponse(
            transaction_id=tx.transaction_id,
            fraud_probability=round(prob, 5),
            risk_score=risk_score,
            model_version=self.get_version(),
            top_risk_signals=signals,
            timestamp=datetime.now(timezone.utc).isoformat(),
            inference_latency_ms=latency_ms
        )

    def score_batch(self, req: BatchRiskScoreRequest) -> BatchRiskScoreResponse:
        """Scores a batch of transactions in a single vectorized pass."""
        t0 = time.perf_counter()
        
        df = self._prepare_dataframe(req.transactions)
        X_trans = self.preprocessor.transform(df)
        probs = self.model.predict_proba(X_trans)[:, 1]
        shap_matrix = self.explainer.shap_values(X_trans)

        results = []
        for i, tx in enumerate(req.transactions):
            prob = float(probs[i])
            risk_score = round(prob * 100.0, 2)
            shap_row = shap_matrix[i]
            raw_dict = tx.model_dump()
            aggregated = ShapExplainabilityNormalizer.aggregate_shap_contributions(
                self.transformed_feature_names, shap_row, raw_dict
            )
            signals = []
            for item in aggregated[:5]:
                signals.append(
                    RiskSignal(
                        feature_name=item["concept_key"],
                        display_name=item["display_name"],
                        feature_value=item["raw_value"],
                        shap_impact=item["shap_impact"],
                        formatted_impact=item["formatted_impact"],
                        direction=item["direction"],
                        description=item["description"]
                    )
                )

            results.append(
                RiskScoreResponse(
                    transaction_id=tx.transaction_id,
                    fraud_probability=round(prob, 5),
                    risk_score=risk_score,
                    model_version=self.get_version(),
                    top_risk_signals=signals,
                    timestamp=datetime.now(timezone.utc).isoformat(),
                    inference_latency_ms=0.0 # Bounded in batch latency
                )
            )

        batch_latency_ms = round((time.perf_counter() - t0) * 1000, 2)
        return BatchRiskScoreResponse(
            total_evaluated=len(results),
            model_version=self.get_version(),
            results=results,
            batch_latency_ms=batch_latency_ms
        )

    def explain_transaction(self, tx: TransactionRiskRequest) -> ExplainResponse:
        """Computes complete structured model feature contributions via TreeSHAP."""
        df = self._prepare_dataframe([tx])
        X_trans = self.preprocessor.transform(df)
        prob = float(self.model.predict_proba(X_trans)[0, 1])
        risk_score = round(prob * 100.0, 2)

        shap_values = self.explainer.shap_values(X_trans)[0]
        base_val = float(self.explainer.expected_value) if hasattr(self.explainer, "expected_value") else 0.0

        raw_dict = tx.model_dump()
        aggregated = ShapExplainabilityNormalizer.aggregate_shap_contributions(
            self.transformed_feature_names, shap_values, raw_dict
        )

        contributions: List[FeatureContribution] = []
        high_risk_drivers = []

        for item in aggregated:
            contributions.append(
                FeatureContribution(
                    feature_name=item["concept_key"],
                    display_name=item["display_name"],
                    raw_value=item["raw_value"],
                    shap_value=item["shap_impact"],
                    formatted_impact=item["formatted_impact"],
                    contribution_percentage=item["contribution_percentage"],
                    direction=item["direction"]
                )
            )
            if item["shap_impact"] > 0 and len(high_risk_drivers) < 3:
                high_risk_drivers.append(f"{item['concept_key']} ({item['formatted_impact']})")

        if high_risk_drivers:
            summary = f"Risk score ({risk_score}/100) primarily driven by: {', '.join(high_risk_drivers)}."
        else:
            summary = f"Transaction displays nominal legitimate baseline characteristics (Score: {risk_score}/100)."

        return ExplainResponse(
            transaction_id=tx.transaction_id,
            fraud_probability=round(prob, 5),
            risk_score=risk_score,
            model_version=self.get_version(),
            base_value=round(base_val, 4),
            feature_contributions=contributions,
            summary_explanation=summary,
            timestamp=datetime.now(timezone.utc).isoformat()
        )

    def get_model_info(self) -> ModelInfoResponse:
        """Returns metadata about the active production model and test set metrics."""
        test_eval = self.metadata.get("metrics_summary", {})
        return ModelInfoResponse(
            model_name=self.metadata.get("model_name", "XGBoost"),
            model_version=self.get_version(),
            framework="xgboost",
            optimal_threshold=float(self.metadata.get("optimal_threshold", 0.05)),
            feature_count=len(self.transformed_feature_names),
            input_features=self.transformed_feature_names,
            created_at=self.metadata.get("created_at", datetime.now(timezone.utc).isoformat()),
            test_metrics=test_eval
        )

model_service = ModelService()
