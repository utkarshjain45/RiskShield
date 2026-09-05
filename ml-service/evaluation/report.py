import json
import os
from typing import Dict, Any, List

def format_experiment_markdown(report: Dict[str, Any]) -> str:
    """Formats experiment evaluation comparison into GitHub markdown."""
    models_data = report.get("models", {})
    best_model_name = report.get("best_model", "XGBoost")
    summary = report.get("summary", "")

    md = []
    md.append("# 🧪 RiskShield AI — Model Experimentation & Evaluation Report\n")
    md.append(f"**Experiment Timestamp:** `{report.get('timestamp')}`  \n")
    md.append(f"**Selected Champion Model:** `{best_model_name}`  \n")
    md.append(f"**Business Selection Criteria:** Minimization of total financial loss (Uncaught Fraud Loss + False-Positive Operational Friction) with bounded FPR on held-out test set.\n")
    md.append("\n---\n")

    md.append("## 1. Held-Out Test Set Performance Comparison\n")
    md.append("| Model | Threshold | Precision | Recall | F1 Score | PR-AUC | ROC-AUC | FPR | FNR | Total Business Cost (INR) |")
    md.append("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |")

    for name, data in models_data.items():
        t_eval = data.get("test_evaluation", {})
        impact = t_eval.get("monetary_impact", {})
        md.append(
            f"| **{name}** | {t_eval.get('threshold', 0.5):.2f} | "
            f"{t_eval.get('precision', 0):.4f} | {t_eval.get('recall', 0):.4f} | "
            f"{t_eval.get('f1', 0):.4f} | {t_eval.get('pr_auc', 0):.4f} | "
            f"{t_eval.get('roc_auc', 0):.4f} | {t_eval.get('false_positive_rate', 0)*100:.2f}% | "
            f"{t_eval.get('false_negative_rate', 0)*100:.2f}% | "
            f"₹{impact.get('total_cost_inr', 0):,.2f} |"
        )

    md.append("\n---\n")
    md.append("## 2. Financial & Business Loss Breakdown (Test Set)\n")
    md.append("| Model | Missed Fraud (FN Count) | Uncaught Fraud Loss (INR) | False Alarms (FP Count) | False Positive Cost (INR) | Net Business Loss (INR) |")
    md.append("| :--- | :--- | :--- | :--- | :--- | :--- |")

    for name, data in models_data.items():
        t_eval = data.get("test_evaluation", {})
        impact = t_eval.get("monetary_impact", {})
        md.append(
            f"| **{name}** | {impact.get('fn_count', 0):,} | "
            f"₹{impact.get('fn_cost_inr', 0):,.2f} | {impact.get('fp_count', 0):,} | "
            f"₹{impact.get('fp_cost_inr', 0):,.2f} | **₹{impact.get('total_cost_inr', 0):,.2f}** |"
        )

    md.append("\n---\n")
    md.append("## 3. Confusion Matrix Breakdown (Test Set)\n")
    for name, data in models_data.items():
        cm = data.get("test_evaluation", {}).get("confusion_matrix", {})
        md.append(f"### {name}")
        md.append(f"- **True Negatives (Legitimate Allowed):** {cm.get('true_negatives', 0):,}")
        md.append(f"- **False Positives (Legitimate Flagged):** {cm.get('false_positives', 0):,}")
        md.append(f"- **False Negatives (Fraud Missed):** {cm.get('false_negatives', 0):,}")
        md.append(f"- **True Positives (Fraud Caught):** {cm.get('true_positives', 0):,}")
        md.append("")

    md.append("## 4. Architecture & Production Champion Rationale\n")
    md.append(summary)
    md.append("\n")

    return "\n".join(md)

def save_experiment_reports(report: Dict[str, Any], output_dir: str):
    """Saves both JSON and Markdown experiment reports."""
    os.makedirs(output_dir, exist_ok=True)
    
    json_path = os.path.join(output_dir, "experiment_report.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    md_content = format_experiment_markdown(report)
    md_path = os.path.join(output_dir, "experiment_report.md")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write(md_content)
