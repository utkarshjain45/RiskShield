from typing import Dict, Any, Tuple
import numpy as np
import pandas as pd
from sklearn.metrics import (
    precision_score,
    recall_score,
    f1_score,
    roc_auc_score,
    average_precision_score,
    confusion_matrix
)

# Realistic fintech cost parameters (Razorpay ecosystem standard)
DEFAULT_REVIEW_FRICTION_INR = 150.0   # Operational investigation / SMS step-up challenge cost
DEFAULT_LOST_MARGIN_RATE = 0.03       # Lost merchant margin on incorrectly blocked / churned sales

def compute_monetary_costs(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    amounts: np.ndarray,
    fixed_review_cost: float = DEFAULT_REVIEW_FRICTION_INR,
    lost_margin_rate: float = DEFAULT_LOST_MARGIN_RATE
) -> Dict[str, float]:
    """
    Computes rigorous financial loss metrics for fraud prediction:
    - FN Cost: 100% of missed fraudulent transaction amounts (chargeback loss)
    - FP Cost: Operational friction/review cost + lost merchant transaction margin
    """
    y_true = np.asarray(y_true, dtype=int)
    y_pred = np.asarray(y_pred, dtype=int)
    amounts = np.asarray(amounts, dtype=float)

    # False Negatives: True Fraud (1), predicted Legitimate (0)
    fn_mask = (y_true == 1) & (y_pred == 0)
    fn_count = int(np.sum(fn_mask))
    fn_cost = float(np.sum(amounts[fn_mask]))

    # False Positives: True Legit (0), predicted Fraud (1)
    fp_mask = (y_true == 0) & (y_pred == 1)
    fp_count = int(np.sum(fp_mask))
    fp_amount = float(np.sum(amounts[fp_mask]))
    fp_cost = float((fp_count * fixed_review_cost) + (fp_amount * lost_margin_rate))

    total_cost = fn_cost + fp_cost

    return {
        "fn_count": fn_count,
        "fn_cost_inr": round(fn_cost, 2),
        "fp_count": fp_count,
        "fp_cost_inr": round(fp_cost, 2),
        "total_cost_inr": round(total_cost, 2)
    }

def evaluate_predictions(
    y_true: np.ndarray,
    y_scores: np.ndarray,
    amounts: np.ndarray,
    threshold: float = 0.50
) -> Dict[str, Any]:
    """Computes precision, recall, F1, ROC-AUC, PR-AUC, FPR, FNR, confusion matrix & monetary costs."""
    
    y_true = np.asarray(y_true, dtype=int)
    y_scores = np.asarray(y_scores, dtype=float)
    y_pred = (y_scores >= threshold).astype(int)

    # Confusion matrix
    tn, fp, fn, tp = confusion_matrix(y_true, y_pred, labels=[0, 1]).ravel()

    # Rates
    fpr = fp / max(1, (fp + tn))
    fnr = fn / max(1, (fn + tp))
    
    # Classification metrics
    prec = precision_score(y_true, y_pred, zero_division=0)
    rec = recall_score(y_true, y_pred, zero_division=0)
    f1 = f1_score(y_true, y_pred, zero_division=0)
    
    try:
        roc_auc = roc_auc_score(y_true, y_scores)
    except Exception:
        roc_auc = 0.5
        
    try:
        pr_auc = average_precision_score(y_true, y_scores)
    except Exception:
        pr_auc = 0.0

    costs = compute_monetary_costs(y_true, y_pred, amounts)

    return {
        "threshold": round(float(threshold), 4),
        "precision": round(float(prec), 5),
        "recall": round(float(rec), 5),
        "f1": round(float(f1), 5),
        "roc_auc": round(float(roc_auc), 5),
        "pr_auc": round(float(pr_auc), 5),
        "false_positive_rate": round(float(fpr), 5),
        "false_negative_rate": round(float(fnr), 5),
        "confusion_matrix": {
            "true_negatives": int(tn),
            "false_positives": int(fp),
            "false_negatives": int(fn),
            "true_positives": int(tp)
        },
        "monetary_impact": costs
    }

def find_optimal_threshold(
    y_true: np.ndarray,
    y_scores: np.ndarray,
    amounts: np.ndarray,
    min_recall: float = 0.70
) -> Tuple[float, Dict[str, Any]]:
    """
    Finds the optimal decision threshold on validation set that minimizes
    Total Monetary Business Cost while guaranteeing minimum acceptable recall.
    """
    thresholds = np.linspace(0.05, 0.95, 91)
    best_thresh = 0.50
    best_cost = float("inf")
    best_eval = {}

    for t in thresholds:
        curr_eval = evaluate_predictions(y_true, y_scores, amounts, threshold=t)
        # Prioritize minimum acceptable recall for loss containment
        if curr_eval["recall"] >= min_recall or best_eval == {}:
            curr_cost = curr_eval["monetary_impact"]["total_cost_inr"]
            if curr_cost < best_cost:
                best_cost = curr_cost
                best_thresh = float(t)
                best_eval = curr_eval

    if not best_eval:
        best_eval = evaluate_predictions(y_true, y_scores, amounts, threshold=0.50)

    return best_thresh, best_eval
