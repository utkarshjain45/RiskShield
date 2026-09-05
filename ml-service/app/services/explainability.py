from dataclasses import dataclass
from typing import Dict, List, Any, Optional, Tuple
import numpy as np

@dataclass(frozen=True)
class FeatureConceptMeta:
    concept_key: str
    display_name: str
    description_template: str

FEATURE_CONCEPT_MAPPINGS: Dict[str, FeatureConceptMeta] = {
    "tx_count_5m": FeatureConceptMeta(
        "transaction_velocity",
        "Transaction Velocity",
        "Rapid surge in transaction velocity within recent minutes"
    ),
    "tx_count_30m": FeatureConceptMeta(
        "transaction_velocity",
        "Transaction Velocity",
        "Transaction frequency over the past 30 minutes"
    ),
    "tx_count_1h": FeatureConceptMeta(
        "transaction_velocity",
        "Transaction Velocity",
        "Elevated hourly transaction velocity for this customer"
    ),
    "device_account_count": FeatureConceptMeta(
        "device_account_count",
        "Device Account Sharing",
        "Hardware device shared across multiple customer accounts"
    ),
    "amount_deviation": FeatureConceptMeta(
        "amount_deviation",
        "Amount Deviation",
        "Transaction amount deviates noticeably from customer historical average"
    ),
    "is_new_device": FeatureConceptMeta(
        "new_device",
        "New Device Fingerprint",
        "Transaction originated from an unrecognized hardware device"
    ),
    "is_new_ip": FeatureConceptMeta(
        "new_ip",
        "New IP Address",
        "Transaction originated from an unrecognized IP network address"
    ),
    "customer_account_age_days": FeatureConceptMeta(
        "account_age",
        "Customer Account Age",
        "Customer account longevity and historical baseline tenure"
    ),
    "ip_account_count": FeatureConceptMeta(
        "ip_account_count",
        "IP Account Sharing",
        "IP address associated with multiple distinct user accounts"
    ),
    "cust_failed_rate": FeatureConceptMeta(
        "payment_failure_rate",
        "Payment Failure Rate",
        "Historical payment decline/failure pattern for customer"
    ),
    "amount_spent_1h": FeatureConceptMeta(
        "spending_velocity",
        "Spending Velocity",
        "Cumulative amount spent over the trailing 1-hour window"
    ),
    "device_tx_count": FeatureConceptMeta(
        "device_usage",
        "Device Transaction Volume",
        "Cumulative volume of transactions recorded on device"
    ),
    "ip_tx_count": FeatureConceptMeta(
        "ip_usage",
        "IP Transaction Volume",
        "Cumulative volume of transactions originating from IP"
    ),
    "cust_hist_avg_amount": FeatureConceptMeta(
        "customer_baseline",
        "Customer Spending Baseline",
        "Customer's historical average transaction value"
    ),
    "cust_tx_frequency": FeatureConceptMeta(
        "customer_frequency",
        "Customer Frequency",
        "Daily average transaction frequency baseline"
    ),
    "amount": FeatureConceptMeta(
        "amount",
        "Transaction Amount",
        "Absolute transaction value"
    ),
    "hour_of_day": FeatureConceptMeta(
        "transaction_timing",
        "Transaction Timing",
        "Time of day relative to customary customer hours"
    ),
    "day_of_week": FeatureConceptMeta(
        "transaction_timing",
        "Transaction Timing",
        "Day of week transaction pattern"
    ),
}

class ShapExplainabilityNormalizer:
    """
    Normalizes low-level model features and one-hot encodings into clean,
    merchant-friendly feature concepts with deterministic attributions.
    """

    @staticmethod
    def map_feature_to_concept(raw_feature_name: str) -> Tuple[str, str]:
        """
        Maps a transformed pipeline feature name to (concept_key, display_name).
        Handles one-hot prefixing (e.g., cat__payment_method_upi -> payment_channel).
        """
        clean_name = raw_feature_name
        for prefix in ("remainder__", "num__", "cat__"):
            if clean_name.startswith(prefix):
                clean_name = clean_name[len(prefix):]

        # Exact match in defined concepts
        if clean_name in FEATURE_CONCEPT_MAPPINGS:
            meta = FEATURE_CONCEPT_MAPPINGS[clean_name]
            return meta.concept_key, meta.display_name

        # Substring / pattern matches
        if "velocity" in clean_name or "tx_count" in clean_name:
            return "transaction_velocity", "Transaction Velocity"
        if "device_account" in clean_name:
            return "device_account_count", "Device Account Sharing"
        if "amount_deviation" in clean_name:
            return "amount_deviation", "Amount Deviation"
        if "new_device" in clean_name:
            return "new_device", "New Device Fingerprint"
        if "new_ip" in clean_name:
            return "new_ip", "New IP Address"
        if "account_age" in clean_name:
            return "account_age", "Customer Account Age"
        if "ip_account" in clean_name:
            return "ip_account_count", "IP Account Sharing"
        if "payment_method" in clean_name:
            return "payment_channel", "Payment Channel"
        if "merchant_category" in clean_name or "merchant_risk" in clean_name:
            return "merchant_risk_profile", "Merchant Risk Profile"

        formatted = clean_name.replace("_", " ").title()
        return clean_name, formatted

    @staticmethod
    def format_impact(impact: float) -> str:
        """Formats signed SHAP impact e.g. +0.27 or -0.14."""
        return f"+{impact:.2f}" if impact >= 0 else f"{impact:.2f}"

    @classmethod
    def aggregate_shap_contributions(
        cls,
        feature_names: List[str],
        shap_values: np.ndarray,
        raw_tx_values: Dict[str, Any]
    ) -> List[Dict[str, Any]]:
        """
        Aggregates raw SHAP values into normalized merchant-friendly concepts.
        Returns deterministically sorted list of top contributing concepts.
        """
        concept_impacts: Dict[str, float] = {}
        concept_display: Dict[str, str] = {}
        concept_raw_values: Dict[str, Any] = {}

        n_features = len(feature_names)
        for i in range(len(shap_values)):
            raw_feat = feature_names[i] if i < n_features else f"feat_{i}"
            impact = float(shap_values[i])

            concept_key, display_name = cls.map_feature_to_concept(raw_feat)

            concept_impacts[concept_key] = concept_impacts.get(concept_key, 0.0) + impact
            concept_display[concept_key] = display_name

            # Store most representative raw value
            if concept_key not in concept_raw_values:
                val = raw_tx_values.get(raw_feat)
                if val is None:
                    # Strip prefixes to lookup
                    stripped = raw_feat.split("__")[-1]
                    val = raw_tx_values.get(stripped, 0)
                concept_raw_values[concept_key] = val

        total_abs = max(1e-5, sum(abs(v) for v in concept_impacts.values()))

        # Deterministic sorting: sort by absolute impact descending, then by concept key alphabetically
        sorted_keys = sorted(
            concept_impacts.keys(),
            key=lambda k: (-abs(concept_impacts[k]), k)
        )

        results = []
        for key in sorted_keys:
            impact = concept_impacts[key]
            pct = round((abs(impact) / total_abs) * 100.0, 2)
            direction = "INCREASES_RISK" if impact > 0 else "DECREASES_RISK"
            formatted_impact = f"+{impact:.2f}" if impact >= 0 else f"{impact:.2f}"

            desc = cls._generate_concept_description(key, concept_raw_values.get(key), impact)

            results.append({
                "concept_key": key,
                "display_name": concept_display[key],
                "shap_impact": round(impact, 4),
                "formatted_impact": formatted_impact,
                "contribution_percentage": pct,
                "direction": direction,
                "raw_value": concept_raw_values.get(key),
                "description": desc
            })

        return results

    @staticmethod
    def _generate_concept_description(concept: str, raw_val: Any, impact: float) -> str:
        """Constructs human-readable business explanation for a normalized signal."""
        direction = "elevates" if impact > 0 else "reduces"
        if concept == "transaction_velocity":
            return f"Transaction velocity is significantly higher than customer baseline ({direction} risk)"
        elif concept == "device_account_count":
            return f"Hardware device shared across multiple distinct accounts ({direction} risk)"
        elif concept == "amount_deviation":
            return f"Transaction amount deviates noticeably from customer average ({direction} risk)"
        elif concept == "new_device":
            return "Transaction originates from an unrecognized hardware device"
        elif concept == "new_ip":
            return "Transaction originates from an unrecognized IP network address"
        elif concept == "account_age":
            return f"Customer account tenure baseline ({direction} risk)"
        elif concept == "ip_account_count":
            return f"IP address associated with multiple distinct customer accounts ({direction} risk)"
        elif concept == "payment_failure_rate":
            return f"Customer payment failure rate ({direction} risk)"
        elif concept == "spending_velocity":
            return f"Hourly spending velocity ({direction} risk)"
        return f"{concept.replace('_', ' ').title()} contribution: {impact:+.2f}"
