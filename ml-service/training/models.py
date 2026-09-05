from typing import Dict, Any
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier
from xgboost import XGBClassifier

def get_candidate_models(scale_pos_weight: float = 49.0, random_seed: int = 42) -> Dict[str, Any]:
    """
    Instantiates the three candidate fraud detection models:
    1. Logistic Regression (Linear baseline with balanced class weights)
    2. Random Forest (Non-linear bagging ensemble)
    3. XGBoost (Gradient-boosted decision trees with PR-AUC optimization)
    """
    models = {
        "Logistic_Regression": LogisticRegression(
            max_iter=1000,
            class_weight="balanced",
            C=1.0,
            solver="lbfgs",
            random_state=random_seed
        ),
        "Random_Forest": RandomForestClassifier(
            n_estimators=120,
            max_depth=12,
            min_samples_split=6,
            min_samples_leaf=2,
            class_weight="balanced_subsample",
            random_state=random_seed,
            n_jobs=-1
        ),
        "XGBoost": XGBClassifier(
            n_estimators=150,
            max_depth=6,
            learning_rate=0.08,
            subsample=0.85,
            colsample_bytree=0.85,
            scale_pos_weight=scale_pos_weight,
            eval_metric="aucpr",
            random_state=random_seed,
            n_jobs=-1
        )
    }
    return models
