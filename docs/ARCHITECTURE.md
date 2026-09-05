# RiskShield AI — System Architecture Specification

## 1. Executive Summary & Track Alignment

* **Track:** Razorpay AI Buildathon — *Track 2: AI Risk Manager & Merchant Reliability*  
* **Loss Class Targeted:** Real-Time Payment Fraud, Automated Carding Velocity Attacks, Device Emulator Cycling, and Coordinated Proxy Syndicates.
* **Core Objective:** Deliver an event-driven, production-ready AI risk management platform for merchants that predicts fraud risk with sub-25ms latency, detects anomalous fraud spikes, applies deterministic policy-bound decisions (`ALLOW`, `REVIEW`, `BLOCK`), produces auditable SHAP model evidence, and provides an investigator copilot for fraud ops teams.

---

## 2. Core Architectural Invariants

1. **ML Predicts Risk Probability, Not Unilateral Action:** The machine learning model outputs calibrated risk scores $[0.0, 100.0]$ and confidence intervals. It never directly executes funds blocking without policy mediation.
2. **Statistical Anomaly Engine for Spikes:** Time-series and rolling-window statistical algorithms (rolling mean + standard deviation / Z-score) detect merchant-wide or route-wide fraud velocity spikes in real time without arbitrary static counts.
3. **Deterministic Policy Engine:** An auditable, rule-based policy engine maps risk scores, merchant rules, velocity flags, and Razorpay metadata into deterministic decisions: `ALLOW`, `REVIEW`, `BLOCK`.
4. **LLM as Explainer & Investigator Assistant Only:** Large Language Models summarize risk indicators, generate human-readable case briefs for fraud analysts, and power tool-calling investigative queries. The LLM has **zero direct authority** to execute payment allow/block decisions or mutate database records.
5. **Multi-Tier Immutable Audit Trail:** Every risk decision and lifecycle event records:
   - Unique `audit_id`, `event_type` (14 strongly typed categories), `actor_type`, `merchant_id`
   - `transaction_id`, `correlation_id`, `service`
   - Cryptographic `payload_hash` (SHA-256 for non-repudiation)
   - `@PreUpdate` and `@PreRemove` lifecycle interceptors throwing `UnsupportedOperationException`
6. **Measurable False-Positive Economics:** The system continuously calculates false-positive costs ($FP_{cost} = \text{₹100 customer friction}$ vs $FN_{cost} = \text{₹1,000 chargeback loss}$) alongside recall metrics.
7. **Strictly Held-Out Evaluation:** ML model evaluation enforces temporal train/val/test splits to eliminate data leakage and accurately benchmark Precision@Recall thresholds on 10,000 untouched test records.
8. **Razorpay Test Mode Isolation:** All webhook ingestion, verification, and API calls strictly use Razorpay Test Key IDs and Secrets (`rzp_test_*`). Secret keys are loaded solely via environment variables and never logged or committed.
9. **Graceful Degradation & Fail-Safe Defaults:** If ML inference or Redis times out (>50ms SLA breach), the system falls back to deterministic rule fallback without dropping customer transactions.

---

## 3. High-Level System Architecture

```mermaid
flowchart TD
    subgraph Ingestion["Ingestion & Client Layer"]
        RZP[Razorpay Webhook / Checkout Gateway]
        SIM[RiskShield Demo Traffic Simulator]
        UI[React 18 / TypeScript Merchant Console]
    end

    subgraph Security["API Security & Tenant Layer"]
        AUTH[ApiKeyAuthenticationFilter & RBAC]
        RL[ApiRateLimitingFilter (Token Bucket)]
        TENANT[SecurityUtils Multi-Tenant Boundary]
    end

    subgraph Core["Core Spring Boot 3.3 Gateway"]
        TX[TransactionService Ingest]
        AUDIT[AuditService (SHA-256 Immutable Ledger)]
        POLICY[PolicyEvaluationService (ALLOW/REVIEW/BLOCK)]
        SPIKE[FraudSpikeDetectorService (Rolling Z-Score)]
        ASSISTANT[AiInvestigationService (Tool-Bound Copilot)]
    end

    subgraph State["Behavioral & Event State"]
        REDIS[(Redis 7 Sliding-Window State)]
        KAFKA[[Apache Kafka Event Bus]]
        PG[(PostgreSQL 16 Relational Store)]
    end

    subgraph ML["Python ML Microservice (FastAPI)"]
        XGB[XGBoost Classifier v1.2.0]
        SHAP[TreeSHAP Attribution Explainer]
    end

    RZP -->|HMAC-SHA256 Signed| AUTH
    SIM -->|Synthetic Non-Bypassing| AUTH
    UI -->|REST / API Key| AUTH
    AUTH --> RL --> TENANT --> TX

    TX -->|Sliding-Window Counters| REDIS
    TX -->|Publish payment.created| KAFKA
    TX -->|Record Event| AUDIT
    TX -->|Score Transaction| XGB
    XGB -->|Feature Attributions| SHAP
    SHAP -->|Risk Score + SHAP| POLICY

    POLICY -->|Spike Metric Updates| SPIKE
    POLICY -->|Immutable Decisions| PG
    AUDIT -->|SHA-256 Non-Repudiation| PG
    SPIKE -->|Alert Events| KAFKA
    ASSISTANT -->|Read-Only Queries| PG
    ASSISTANT -->|Tool Invocations| Core
```

---

## 4. Component Breakdown & Responsibilities

### 4.1 Frontend Console (`frontend/`)
- **Technology:** React 18, TypeScript, Vite, TailwindCSS / CSS Design System, Lucide Icons.
- **Key Modules:**
  - **Overview Dashboard:** Today's volume, active fraud rate, prevented loss, and risk distribution charts.
  - **Demo Mode Banner & Launcher:** Top-level visual banner and modal with 6 selectable simulation scenarios.
  - **Transactions Page:** Real-time searchable transaction feed with instant status badges.
  - **Transaction Investigation:** Detailed drilldown with customer, device, IP linkages, and TreeSHAP attribution waterfall.
  - **Fraud Incidents:** Active and historical fraud spike incident lifecycle (`OPEN`, `ACKNOWLEDGED`, `RESOLVED`).
  - **Model Evaluation:** Held-out test set performance, confusion matrix, and threshold optimization curves.
  - **Audit Center:** Chronological immutable event timeline with SHA-256 verification badges.
  - **AI Investigation Assistant:** Interactive slide-out copilot drawer for natural language incident exploration.

### 4.2 Backend Gateway & Risk Engine (`backend/`)
- **Technology:** Java 17/21, Spring Boot 3.3+, Spring Data JPA, Spring Kafka, Spring Data Redis, Flyway Migrations (V1–V10).
- **Key Services:**
  - `TransactionService`: Ingestion, customer/device resolution, Kafka event emission.
  - `BehavioralFeatureService`: Redis sliding-window velocity aggregations (`5m`, `30m`, `1h`) and entity set cardinality.
  - `RiskAssessmentService`: Orchestrates ML inference, normalized SHAP explanation mapping, and alert dispatch.
  - `PolicyEvaluationService`: Deterministic threshold evaluation (`ALLOW < 30`, `REVIEW 30-90`, `BLOCK >= 90`) and custom merchant rules.
  - `FraudSpikeDetectorService`: Time-window baseline tracking (5m, 15m, 1h, 6h, 24h), rolling Z-score evaluation, exposure calculation.
  - `RazorpayWebhookService`: Raw body HMAC-SHA256 signature verification, event-id idempotency, and 15-minute replay attack defense.
  - `AuditService`: Cryptographic SHA-256 payload hashing and immutable event storage across 14 risk event types.
  - `DemoSimulationService`: Background execution of synthetic payment traffic through the live risk pipeline.
  - `AiInvestigationService` & `GeminiClient`: Read-only risk analyst assistant powered by Google Gemini 1.5 (`gemini-1.5-flash`), with structured tool execution (`InvestigationToolRegistry`) and graceful deterministic fallback.

### 4.3 Python ML Microservice (`ml-service/`)
- **Technology:** Python 3.11, FastAPI, XGBoost 2.0+, SHAP, NumPy, Pandas, Scikit-learn.
- **Key Capabilities:**
  - Calibrated probability inference under 25ms.
  - TreeSHAP explainer generating additive contributions for all 18 behavioral features.
  - Strict input validation and fallback risk heuristics if models are retraining.

### 4.4 Distributed Cache & Sliding-Window Store (Redis 7)
- **Data Structures:**
  - Sorted Sets (`ZSET`): Windowed counters (`tx:cust:<id>:5m`, `tx:dev:<id>:1h`, `amt:cust:<id>:1h`) with unix timestamp scores.
  - Sets (`SET`): Entity association tracking (`dev:accounts:<id>:1h`, `ip:accounts:<id>:1h`).
  - Key TTLs: Automatic expiration matching window duration (e.g. 300s for 5m, 3600s for 1h).

### 4.5 Relational Datastore & Migrations (PostgreSQL 16)
- **Flyway Migrations:**
  - `V1`: Core schema (merchants, customers, devices, transactions, risk_assessments, risk_signals).
  - `V2`: Policy engine enhancements (thresholds, discrete rules).
  - `V3`: Kafka event idempotency tracking.
  - `V4`: Fraud spike incident tables and rolling baselines.
  - `V5`: SHAP display names and formatted impact strings.
  - `V6`: Model evaluation run ledger.
  - `V7`: Razorpay webhook receipt storage.
  - `V8`: AI investigation sessions and message history.
  - `V9`: Enhanced immutable audit trail (audit_id, actor_type, payload_hash, metadata).
  - `V10`: Demo simulation sessions and execution metrics.

---

## 5. Security & Multi-Tenancy Architecture

1. **Stateless Authentication:** Every API request is verified via `X-API-Key` or secure gateway headers (`X-User-Role`, `X-Merchant-Id`, `X-User-Id`).
2. **Tenant Isolation:**
   - `MERCHANT_VIEWER` roles are bound to their assigned `merchantId`.
   - Access attempts to other merchants return HTTP `403 Forbidden`.
3. **Tamper-Proof Audit Ledger:**
   - No HTTP endpoints exist for `POST`, `PUT`, `PATCH`, or `DELETE` on `/api/v1/audit/**` (405 Method Not Allowed).
   - `@PreUpdate` and `@PreRemove` lifecycle callbacks reject tampering.
   - Column-level `updatable = false` constraints enforced by Hibernate and PostgreSQL.
4. **PII Masking:**
   - Automated `%maskPii` Logback converter redacts card numbers, emails, phone numbers, and authorization headers in logs.
