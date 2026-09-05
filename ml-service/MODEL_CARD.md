# 🛡️ RiskShield AI — Model Card: Fraud Classification Engine (v1.0.0-xgboost)

[![Model Version](https://img.shields.io/badge/Model%20Version-v1.0.0--xgboost-blue.svg)](#model-details)
[![Track](https://img.shields.io/badge/Razorpay%20Buildathon-AI%20Risk%20Manager-indigo.svg)](#intended-use)
[![Evaluation](https://img.shields.io/badge/Test%20PR--AUC-0.9990-success.svg)](#quantitative-metrics)

---

## 1. Model Details

- **Model Identifier:** `RiskShield-XGB-Fraud-v1`
- **Architecture:** Extreme Gradient Boosting (`XGBClassifier`) with exact TreeSHAP feature attribution support.
- **Model Type:** Binary Supervised Classifier with calibrated probability output $P(\text{Fraud} \mid \mathbf{x}) \in [0.0, 1.0]$.
- **Release Date:** September 2026
- **License:** Proprietary (Razorpay AI Buildathon Submission)
- **Champion Selection Rationale:** While Logistic Regression and Random Forest performed competitively on synthetic data, **XGBoost** was selected as the production champion because:
  1. It provides exact, fast **TreeSHAP local feature attribution** ($\mathcal{O}(TLD^2)$), directly satisfying RiskShield AI's mandatory audit requirement to return contributing signals on every transaction.
  2. It naturally handles non-linear interactions across velocity windows, IP/device multiplexing, and contextual amount spikes.
  3. Optimized with `scale_pos_weight = 48.5` to counteract class imbalance directly in the tree split objective.

---

## 2. Intended Use & Invariant Boundary

### Intended Application
- Real-time fraud scoring for online payment transactions (UPI, Cards, Netbanking) in merchant payment gateways.
- Asynchronous fraud spike detection and behavioral feature attribution.
- Inputs to a deterministic downstream Policy Engine (`ALLOW` / `REVIEW` / `BLOCK`).

### Out-of-Scope & Invariant Violations
- **NO Direct Blocking by Model Alone:** The model outputs a continuous calibrated probability score. Authoritative fund-blocking or allow actions must strictly be executed by the deterministic Policy Engine.
- **NO Unrestricted LLM Autonomy:** LLMs must never predict risk probabilities or bypass policy rules; they are strictly consumers of this model's SHAP attributions for human investigator explanations.
- **NO Future-Data Leakage:** Point-in-time calculation must be strictly preserved during real-time feature streaming.

---

## 3. Training & Validation Datasets

The model was trained and evaluated on 100,000 synthetic transactions split **strictly chronologically** to eliminate temporal leakage:

| Dataset Split | Transaction Count | Temporal Range | Fraud Proportion | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **Train Set** | 70,000 | `2026-06-01` to `2026-08-03` | 2.02% (1,415 txs) | Model parameter estimation & preprocessing fitting |
| **Validation Set** | 15,000 | `2026-08-03` to `2026-08-17` | 0.94% (141 txs) | Hyperparameter tuning & optimal threshold selection |
| **Held-Out Test Set** | 15,000 | `2026-08-17` to `2026-08-30` | 2.96% (444 txs) | **Strictly held-out** benchmark evaluation |

---

## 4. Feature Architecture & Leak-Free Engineering

The model ingests 21 raw engineered features transformed into a 32-dimensional matrix via a scikit-learn `ColumnTransformer`:

### Numerical Features (Standardized & Imputed)
- `amount`: Current transaction amount in INR.
- `cust_hist_avg_amount`: Running mean of past amounts for this customer strictly prior to $t$.
- `amount_deviation`: Ratio $\frac{\text{amount}}{\text{cust\_hist\_avg\_amount} + 10^{-4}}$.
- `tx_count_5m`: Number of customer transactions in $(t - 5\text{m}, t)$.
- `tx_count_30m`: Number of customer transactions in $(t - 30\text{m}, t)$.
- `tx_count_1h`: Number of customer transactions in $(t - 1\text{h}, t)$.
- `amount_spent_1h`: Total customer spend in $(t - 1\text{h}, t)$.
- `cust_tx_frequency`: Cumulative customer transaction count normalized by account age.
- `cust_failed_rate`: Historical payment failure ratio strictly prior to $t$.
- `device_tx_count`: Cumulative transaction count observed on this device.
- `device_account_count`: Unique customer IDs observed on this hardware device up to $t$.
- `ip_tx_count`: Cumulative transaction count observed on this IP address.
- `ip_account_count`: Unique customer IDs observed on this IP address up to $t$.
- `is_new_device`: Binary indicator whether hardware device is novel for this customer.
- `is_new_ip`: Binary indicator whether IP address is novel for this customer.
- `customer_account_age_days`: Account age in days at transaction time.
- `hour_of_day`: UTC transaction hour ($0 \dots 23$).
- `day_of_week`: Day of week ($0 \dots 6$).

### Categorical Features (One-Hot Encoded)
- `payment_method`: `upi`, `card`, `netbanking`
- `merchant_category`: Merchant industry vertical (8 categories)
- `merchant_risk_tier`: `LOW`, `MEDIUM`, `HIGH`

---

## 5. Quantitative Metrics on Held-Out Test Set

Evaluated at the validation-selected decision threshold $\tau^* = 0.05$:

| Metric | Measured Value | Target SLA | Status |
| :--- | :--- | :--- | :--- |
| **Precision** | **99.11%** | $\ge 90.0\%$ | Exceeded |
| **Recall** | **100.00%** | $\ge 95.0\%$ | Exceeded (444 / 444 caught) |
| **F1 Score** | **0.9955** | $\ge 0.90$ | Exceeded |
| **PR-AUC (Average Precision)** | **0.9990** | $\ge 0.95$ | Exceeded |
| **ROC-AUC** | **1.0000** | $\ge 0.98$ | Exceeded |
| **False Positive Rate (FPR)** | **0.03%** | $\le 0.50\%$ | Exceptional (4 false alarms in 14,556 legit) |
| **False Negative Rate (FNR)** | **0.00%** | $\le 5.0\%$ | Zero missed fraud |

### Confusion Matrix (Test Set: 15,000 Transactions)
```
                    Predicted Legitimate    Predicted Fraud
Actual Legitimate          14,552                   4  (FP)
Actual Fraud                    0                 444  (TP)
```

---

## 6. Threshold Selection & Business Economics

### Objective Cost Function
Unlike traditional classifiers optimized for maximum accuracy (which is misleading under imbalanced fraud), RiskShield AI tunes thresholds to minimize **Total Financial Business Loss**:

$$\text{Loss}_{\text{Total}} = \text{Loss}(FN) + \text{Cost}(FP)$$

Where:
- $\text{Loss}(FN) = \sum_{i \in FN} \text{Amount}_i$ (Direct unrecoverable chargeback loss).
- $\text{Cost}(FP) = \sum_{j \in FP} \left( ₹150 \text{ (Review friction)} + 0.03 \times \text{Amount}_j \text{ (Lost margin / customer churn)} \right)$.

### Financial Loss Breakdown (Held-Out Test Set)
- **Direct Fraud Prevented:** ₹2,504,568.48 (100% of test fraud captured)
- **Uncaught Fraud Loss (FN):** ₹0.00 (0 missed transactions)
- **False Positive Friction Cost (FP):** ₹776.48 (4 false alarms)
- **Net Business Loss:** **₹776.48**

---

## 7. Assumptions & Limitations

1. **Synthetic Feature Assumptions:** Real-world merchant data may exhibit feature corruption (e.g., private relay IP masking, rotating device fingerprints). The model assumes device fingerprints and IPs are consistently hashed.
2. **Cold-Start Latency:** For brand-new customers with 0 historical transactions, `cust_hist_avg_amount` defaults to the transaction amount ($\text{deviation} = 1.0$), and the model relies more heavily on merchant category baseline, device novelty, and network IP reputation.
3. **Concept Drift & Retraining:** As fraud syndicates alter velocity burst cadences or rotate VPN proxies, feature distributions may drift. Retraining must occur periodically (recommended weekly) on rolling chronological windows.
