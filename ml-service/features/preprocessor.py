import joblib
from typing import Tuple, List, Dict, Any
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.pipeline import Pipeline
from sklearn.impute import SimpleImputer

NUMERICAL_FEATURES: List[str] = [
    "amount",
    "cust_hist_avg_amount",
    "amount_deviation",
    "tx_count_5m",
    "tx_count_30m",
    "tx_count_1h",
    "amount_spent_1h",
    "cust_tx_frequency",
    "cust_failed_rate",
    "device_tx_count",
    "device_account_count",
    "ip_tx_count",
    "ip_account_count",
    "is_new_device",
    "is_new_ip",
    "customer_account_age_days",
    "hour_of_day",
    "day_of_week"
]

CATEGORICAL_FEATURES: List[str] = [
    "payment_method",
    "merchant_category",
    "merchant_risk_tier"
]

def build_preprocessor() -> ColumnTransformer:
    """Builds a scikit-learn ColumnTransformer for numerical and categorical features."""
    
    numeric_transformer = Pipeline(steps=[
        ("imputer", SimpleImputer(strategy="median")),
        ("scaler", StandardScaler())
    ])

    categorical_transformer = Pipeline(steps=[
        ("imputer", SimpleImputer(strategy="constant", fill_value="unknown")),
        ("onehot", OneHotEncoder(handle_unknown="ignore", sparse_output=False))
    ])

    preprocessor = ColumnTransformer(
        transformers=[
            ("num", numeric_transformer, NUMERICAL_FEATURES),
            ("cat", categorical_transformer, CATEGORICAL_FEATURES)
        ],
        remainder="drop"
    )

    return preprocessor

def get_transformed_feature_names(preprocessor: ColumnTransformer) -> List[str]:
    """Retrieves all transformed feature names after fitting."""
    feature_names = []
    
    # Numeric features
    feature_names.extend(NUMERICAL_FEATURES)
    
    # Categorical one-hot features
    try:
        cat_encoder = preprocessor.named_transformers_["cat"].named_steps["onehot"]
        cat_names = cat_encoder.get_feature_names_out(CATEGORICAL_FEATURES).tolist()
        feature_names.extend(cat_names)
    except Exception:
        feature_names.extend(CATEGORICAL_FEATURES)
        
    return feature_names

def save_preprocessor(preprocessor: ColumnTransformer, filepath: str):
    """Saves fitted preprocessor to disk."""
    joblib.dump(preprocessor, filepath)

def load_preprocessor(filepath: str) -> ColumnTransformer:
    """Loads fitted preprocessor from disk."""
    return joblib.load(filepath)
