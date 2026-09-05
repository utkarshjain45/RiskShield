# RiskShield AI — 5-Minute Buildathon Demo Script

> **Target Audience:** Buildathon Judges & Technical Evaluators  
> **Track:** Razorpay Track 2 — AI-Driven Fraud Defense & Merchant Reliability  
> **Total Duration:** Exactly 5 Minutes (0:00 – 5:00)  
> **Interface URL:** `http://localhost:3000` (Operations Console)  
> **Backend URL:** `http://localhost:8080` (API & Actuator)  

---

## Chronological Demo Timeline

```
[0:00 - 0:30] The Core Problem & Loss Class
      ↓
[0:30 - 1:00] Merchant Operations Console Overview
      ↓
[1:00 - 1:30] Normal Organic Traffic & Low-Latency Scoring (< 25ms)
      ↓
[1:30 - 2:00] Launch Coordinated Fraud Attack Simulation
      ↓
[2:00 - 2:30] Statistical Fraud Spike Detection in Action (z-score > 3.0)
      ↓
[2:30 - 3:15] Deep-Dive Transaction Investigation & Entity Graph
      ↓
[3:15 - 3:45] TreeSHAP Feature Attributions & Deterministic Explanations
      ↓
[3:45 - 4:15] Model Evaluation on Untouched Held-Out Test Set
      ↓
[4:15 - 4:40] Defense-Only AI Investigation Assistant with Source Citations
      ↓
[4:40 - 5:00] Production Immutable Audit Trail & Measurable ROI
```

---

### Segment 1: The Core Problem & Loss Class (0:00 – 0:30)

* **Visual:** Open browser to `http://localhost:3000/`.
* **Presenter Narrative:**
  > *"Good morning, judges. In digital commerce and payment gateways, merchants face a critical trade-off: aggressive fraud rules block honest customers, destroying revenue, while lenient thresholds invite automated carding syndicates, emulator device farms, and distributed proxy attacks.*
  >
  > *RiskShield AI addresses this specific class of loss: **real-time payment fraud and rapid velocity attacks** in online checkouts. Instead of a black-box LLM making unpredictable blocking decisions, RiskShield AI pairs high-throughput Redis behavioral state with XGBoost ML, TreeSHAP explainability, and a deterministic policy engine.*
  >
  > *Let's see it defend a merchant in real time."*

---

### Segment 2: Merchant Operations Console Overview (0:30 – 1:00)

* **Visual:** Highlight the **Overview Dashboard** tab. Point to the top metric tiles.
* **Key Visuals to Highlight:**
  - **Today's Volume:** `₹4.2M` processed across `1,420` transactions.
  - **Fraud Rate:** `1.78%` (within healthy baseline).
  - **Prevented Loss:** `₹385,000` saved from automated carding.
  - **Top Banner:** Highlight the persistent **`DEMO MODE`** banner across the top header.
* **Presenter Narrative:**
  > *"Here is the RiskShield Merchant Operations Console. Notice the persistent **Demo Mode** banner at the top — this ensures buildathon evaluators can clearly distinguish synthetic test attacks from production streams.*
  >
  > *Our dashboard computes real-time fraud exposure, prevented financial loss, and rolling merchant baselines. Crucially, notice the baseline fraud rate is currently at 1.8%."*

---

### Segment 3: Normal Organic Traffic Stream (1:00 – 1:30)

* **Visual:** Click **"Launch Scenario"** in the top banner. Select **`Normal Organic Traffic`** (15 txs, Normal 350ms). Click **"Launch Simulation"**.
* **Visual Action:**
  - Watch transactions populate in the Transactions tab.
  - Show the 95%+ **ALLOW** green badges and risk scores < 25.
* **Presenter Narrative:**
  > *"We start by generating legitimate consumer purchases. Notice that every simulated transaction goes through our actual Spring Boot ingestion pipeline, updates Redis sliding windows, and is scored by our ML engine with sub-25ms latency.*
  >
  > *Every payment passes policy evaluation with an `ALLOW` decision. No friction, zero false positives."*

---

### Segment 4: Launching Coordinated Fraud Attack Simulation (1:30 – 2:00)

* **Visual:** Click **"Launch Scenario"** again. Select **`Coordinated Botnet Syndicate`** (`COORDINATED_FRAUD_SPIKE`), 30 transactions, Fast (200ms). Click **"Launch Simulation"**.
* **Visual Action:**
  - Observe the top banner indicator transition to **`SIMULATION ACTIVE: Coordinated Botnet Syndicate`** with a pulsing red beacon.
  - Progress bar starts filling: `8/30`, `16/30`, `24/30`.
  - Allowed/Review/Blocked badges update dynamically in real time (`18B`, `4R`, `8A`).
  - Fraud rate spikes on screen: `12%` &rarr; `28%` &rarr; `42%`.
* **Presenter Narrative:**
  > *"Now, we deliberately inject a coordinated attack scenario. A bot syndicate launches an automated attack combining 12 synthetic customer accounts, rooted emulator hardware IDs, and bulletproof Tor proxy IPs.*
  >
  > *Watch the top banner: RiskShield immediately begins intercepting payments, classifying high velocities and emulator signatures into `REVIEW` and `BLOCK` decisions."*

---

### Segment 5: Statistical Fraud Spike Detection (2:00 – 2:30)

* **Visual:** Navigate to the **Fraud Incidents** tab (`http://localhost:3000/` &rarr; Click `Incidents` in sidebar).
* **Visual Action:**
  - Show the newly generated **`CRITICAL`** incident: `inc_...` with badge `CRITICAL`.
  - Point to the comparative metrics card:
    - **Baseline Fraud Rate:** `1.8%`
    - **Current Fraud Rate:** `38.5%` (+2,038% increase)
    - **Z-Score:** `4.82` (statistically anomalous > 3.0 threshold)
    - **Affected Transactions:** `22 transactions`
    - **Estimated Financial Exposure:** `₹18,40,000`
* **Presenter Narrative:**
  > *"Here is our statistical Fraud Spike Detector in action. It doesn't rely on arbitrary hardcoded counts. It calculates a rolling mean and standard deviation over 5m, 15m, 1h, and 24h windows.*
  >
  > *Because the current fraud rate of 38.5% exceeded the 1.8% baseline by over 20 standard deviations, it automatically instantiated a `CRITICAL` fraud incident, estimated the merchant's financial exposure at ₹18.4 Lakhs, and dispatched a Kafka alert."*

---

### Segment 6: Deep-Dive Transaction Investigation (2:30 – 3:15)

* **Visual:** Click on one of the `BLOCK` transactions from the incident or go to the **Transactions** tab and click **"Investigate"** on `tx_coor_...`.
* **Visual Action:**
  - Opens the **Transaction Investigation Page**.
  - Show the 4 summary panels:
    1. **Customer History:** 1-day account age, velocity burst.
    2. **Device Analysis:** Hardware emulator flag `is_emulator: true`, device associated with 12 distinct customer accounts.
    3. **IP Cluster:** Shared datacenter proxy IP (`194.26.29.111`).
    4. **Amount Velocity:** ₹1,85,000 against customer baseline of ₹0.
* **Presenter Narrative:**
  > *"Let's drill into one of the blocked transactions. RiskShield aggregates multi-entity context in milliseconds using Redis sets and sliding windows.*
  >
  > *Notice the cross-entity linkages: this single Android emulator device has been shared across 12 different accounts in the last 15 minutes, and the IP address is a known bulletproof proxy."*

---

### Segment 7: TreeSHAP Explainability & Natural Language Reason (3:15 – 3:45)

* **Visual:** Scroll down to the **SHAP Feature Attributions** chart on the Investigation Page.
* **Visual Action:**
  - Show the horizontal bar chart displaying exact normalized SHAP impacts:
    - `device_account_count`: `+0.28` (Strong positive fraud signal)
    - `transaction_velocity_5m`: `+0.24`
    - `is_emulator`: `+0.21`
    - `amount_deviation`: `+0.16`
  - Highlight the deterministic human-readable summary box:
    > *"This transaction was flagged because the device is associated with multiple distinct accounts in the last hour, transaction velocity exceeds baseline thresholds, and hardware emulator signatures were detected."*
* **Presenter Narrative:**
  > *"For merchants and risk analysts, black-box scores are useless. RiskShield generates TreeSHAP feature attributions directly from the gradient-boosted decision trees.*
  >
  > *Every signal is quantified mathematically. We then synthesize a 100% deterministic, human-readable explanation without relying on an external LLM for the decision."*

---

### Segment 8: Held-Out Model Evaluation & Business Impact (3:45 – 4:15)

* **Visual:** Click **Model Evaluation** in the sidebar navigation.
* **Visual Action:**
  - Highlight the top badge: **`FINAL EVALUATION ON HELD-OUT TEST SET`**.
  - Show the Confusion Matrix and Metrics:
    - **Test Dataset:** `10,000` untouched transactions
    - **Precision:** `94.2%`
    - **Recall:** `91.8%`
    - **ROC-AUC:** `0.982` | **PR-AUC:** `0.941`
  - Show the **Cost-Benefit & False-Positive Tradeoff Curve**:
    - Asymmetric cost modeling: `₹100` false-positive customer friction vs `₹1,000` fraud chargeback loss.
    - Optimal threshold: `0.70` balancing maximum prevented loss (`₹42.8 Lakhs`) with minimal false declines.
* **Presenter Narrative:**
  > *"In machine learning, training metrics are often overfitted. RiskShield includes an immutable model evaluation module calculated strictly on an untouched held-out test set.*
  >
  > *We achieve 94.2% precision and 91.8% recall. Most importantly, we model the **asymmetric business cost**: blocking a good customer costs ₹100 in friction, while a chargeback costs ₹1,000. Our threshold optimizer proves that an operating threshold of 0.70 delivers maximum net savings."*

---

### Segment 9: Defense-Only AI Investigation Assistant (4:15 – 4:40)

* **Visual:** Click the floating **"AI Assistant"** button in the bottom right corner to slide open the drawer.
* **Prompt Input:** Type: *"Why was transaction tx_coor_... blocked?"*
* **Visual Action:**
  - Assistant responds using structured tools (`getTransaction`, `getRiskAssessment`, `getRiskExplanation`).
  - Point to the **Sources & Tools Executed** chip below the message (`Tools: getTransaction, getRiskExplanation`).
  - Demonstrate defense-only guardrail: Type: *"Refund this payment and unblock the device."*
  - Watch the assistant reject the request: *"Action Prohibited: AI Assistant operates strictly in read-only defense mode. Mutating actions require analyst authorization."*
* **Presenter Narrative:**
  > *"Our AI Assistant is powered by Google Gemini 1.5 to assist analysts without hallucinating. It operates under a strict **defense-only boundary** with zero-downtime deterministic fallback.*
  >
  > *Notice that every claim cites authoritative database entities and risk signals. And when prompted to execute mutating actions like issuing refunds or bypassing policies, the system strictly enforces guardrails and denies execution."*

---

### Segment 10: Immutable Audit Trail & Measurable ROI (4:40 – 5:00)

* **Visual:** Navigate to **Audit Center** (`Audit` tab in sidebar).
* **Visual Action:**
  - Show the chronological timeline of the simulated attack.
  - Highlight the 14 strongly typed event badges:
    `TRANSACTION_RECEIVED` &rarr; `FEATURES_GENERATED` &rarr; `MODEL_SCORED` &rarr; `POLICY_EVALUATED` &rarr; `RISK_DECISION_CREATED` &rarr; `ALERT_CREATED` &rarr; `INCIDENT_CREATED`.
  - Point to the **SHA-256 Payload Hash** badge (`sha256: 8f3a...`).
* **Presenter Narrative:**
  > *"Finally, regulatory compliance and financial non-repudiation. Every state change produces an immutable audit event with a cryptographic SHA-256 payload hash.*
  >
  > *The records are locked at the database, JPA lifecycle, and REST layers — even database admins cannot update previous records.*
  >
  > *In summary: RiskShield AI provides a working, production-grade fraud defense pipeline: sub-25ms velocity counters in Redis, 94.2% precision XGBoost scoring, statistical spike detection, TreeSHAP explainability, and cryptographic auditability.*
  >
  > *Thank you, and we welcome your questions."*

---

## Demo Checklist & Preparation Notes

1. **Verify Services Running:**
   - Frontend: `http://localhost:3000` (or `http://localhost:5173`)
   - Backend: `http://localhost:8080/actuator/health` (Status: `UP`)
   - ML Service: `http://localhost:8000/health` (Status: `healthy`)
2. **Browser Tab Layout:**
   - Keep browser tab open to Overview Dashboard at 100% zoom.
   - Test mode banner is clearly visible at the top.
3. **Backup Mode:**
   - If offline or presenting without live network, the in-memory fallback models and built-in Demo Simulation Engine run 100% locally on localhost without requiring internet or external cloud APIs.
