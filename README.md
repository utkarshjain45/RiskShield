# 🛡️ RiskShield AI — Real-Time Payment Risk & Fraud Intelligence

[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Quickstart](https://img.shields.io/badge/Quickstart-Step--by--Step%20Guide-blue.svg)](QUICKSTART.md)
[![Frontend](https://img.shields.io/badge/Frontend-Tailwind%20CSS-indigo.svg)](frontend)
[![Backend](https://img.shields.io/badge/Backend-Spring%20Boot%203.3-orange.svg)](backend)
[![ML Engine](https://img.shields.io/badge/ML%20Engine-XGBoost%20%2B%20TreeSHAP-emerald.svg)](ml-service)

**RiskShield AI** is an intelligent, real-time payment fraud prevention platform designed for high-volume merchants and payment gateways (like Razorpay). 

It scores incoming checkout transactions with machine learning in **under 25 milliseconds**, blocks fraudulent payments, explains every risk decision using SHAP feature attribution, records an immutable audit trail, and provides a sleek light operations console for fraud risk analysts.

---

## 🚀 Quick Setup (Get Running in 3 Minutes)

Want to run the project right away? Read the complete step-by-step guide in **[QUICKSTART.md](QUICKSTART.md)** or run:

```bash
# 1. Clone the repository
git clone https://github.com/your-username/riskshield-ai.git
cd "riskshield-ai"

# 2. Copy the default environment file
cp .env.example .env    # On Windows: Copy-Item .env.example .env

# 3. Start everything with one command
docker compose up --build -d
```

### Access Your Services:
* 🌐 **Merchant Operations Console:** [http://localhost:3000](http://localhost:3000)
* 🔌 **Backend REST API:** [http://localhost:8080/api/v1](http://localhost:8080/api/v1)
* 📖 **Interactive Swagger Docs:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* 🧠 **Python ML Health Check:** [http://localhost:8000/health](http://localhost:8000/health)

---

## 💡 How RiskShield AI Works (In Simple Terms)

```
[ Customer Checkout ] ──(Razorpay Webhook)──▶ [ Spring Boot API Gateway ]
                                                          │
                    ┌─────────────────────────────────────┴─────────────────────────────────────┐
                    ▼                                                                           ▼
          [ Redis Sliding Window ]                                                    [ Python ML Service ]
      (Card velocity, IP reuse, bursts)                                            (XGBoost Model + TreeSHAP)
                    │                                                                           │
                    └─────────────────────────────────────┬─────────────────────────────────────┘
                                                          ▼
                                            [ Deterministic Policy Engine ]
                                               Score < 40  ──▶ ALLOW
                                               Score 40-75 ──▶ MANUAL REVIEW
                                               Score > 75  ──▶ BLOCK
                                                          │
                    ┌─────────────────────────────────────┴─────────────────────────────────────┐
                    ▼                                                                           ▼
       [ Immutable Audit Ledger ]                                                    [ Operations Console ]
    (PostgreSQL append-only records)                                              (React + Tailwind Dashboard)
```

1. **Transaction Arrives:** A payment event is received via Razorpay webhook or direct API.
2. **Instant Behavioral Profiling:** Redis calculates sliding-window velocity (e.g. how many cards this IP used in the last 5 minutes).
3. **Machine Learning Scoring:** The Python ML service evaluates 18+ behavioral features using an XGBoost model and calculates TreeSHAP feature contributions.
4. **Deterministic Policy:** The merchant's risk policy maps the probability score to an authoritative action (`ALLOW`, `REVIEW`, or `BLOCK`).
5. **Auditable Non-Repudiation:** The decision and raw payload are cryptographically hashed and permanently recorded in an immutable PostgreSQL ledger.
6. **Live Operations:** Analysts monitor live traffic, investigate alerts, and review AI-grounded insights on the modern web console.

---

## ✨ Key Capabilities

| Feature | Description |
| :--- | :--- |
| **⚡ Sub-25ms Scoring** | Low-latency inference pipeline capable of handling high-throughput flash sales and payment surges. |
| **🔍 Explainable AI (SHAP)** | Every blocked transaction explains *why* (e.g. `+27 velocity attack`, `+19 device reuse`, `+18 abnormal amount`). |
| **🚨 Fraud Spike Detector** | EWMA & Z-score statistical engine detects sudden distributed botnet attacks across merchants and triggers critical alerts. |
| **🔒 Tamper-Proof Audit Trail** | Append-only security ledger with cryptographic SHA-256 hashes ensuring complete regulatory compliance. |
| **🎮 Interactive Demo Simulator** | Built-in traffic generator with 6 realistic fraud attack scenarios to demonstrate real-time detection without real cards. |
| **🎨 Light Fintech Dashboard** | A calm, modern operations console built with Tailwind CSS, tabular figures, and responsive layout. |

---

## 📁 Repository Structure

```
RiskShield AI/
├── README.md               # You are here: high-level project overview
├── QUICKSTART.md           # Step-by-step beginner guide to clone and run
├── docker-compose.yml      # Orchestrates all 6 containers with healthchecks
├── start.sh / start.ps1    # Automated 1-command startup scripts
│
├── frontend/               # React 18 + Tailwind CSS Operations Console
├── backend/                # Spring Boot 3.3 core risk engine & API gateway
├── ml-service/             # Python FastAPI service with XGBoost & TreeSHAP
├── data-generator/         # Realistic transaction generation & simulation engine
│
└── docs/                   # 📚 Detailed technical documentation & references
    ├── ARCHITECTURE.md     # Deep-dive system architecture and event pipelines
    ├── API.md              # Complete REST API reference and cURL examples
    ├── MODEL_CARD.md       # ML model evaluation, precision/recall, and metrics
    ├── SECURITY.md         # RBAC, multi-tenant isolation, and webhook security
    ├── THREAT_MODEL.md     # STRIDE threat matrix covering 9 fraud vectors
    ├── DEMO_SCRIPT.md      # 5-minute evaluation walkthrough guide
    ├── JUDGING.md          # Hackathon track criteria mapping
    └── RAZORPAY_TEST_MODE_SETUP.md # Razorpay webhook configuration guide
```

---

## 📚 Technical Documentation Index

All in-depth technical specifications and deep-dive guides are organized in the [`docs/`](docs/) directory:

- 🏛️ **[Architecture Deep-Dive](docs/ARCHITECTURE.md)** — Architectural patterns, Kafka topic topologies, and failure resilience.
- 🔌 **[API Reference](docs/API.md)** — Detailed endpoint specifications, request/response JSON schemas, and authentication headers.
- 🧠 **[ML Model Card](docs/MODEL_CARD.md)** — XGBoost performance metrics (99.1% Precision, 100% Recall on test set) and confusion matrix.
- 🛡️ **[Security Architecture](docs/SECURITY.md)** — HMAC signature validation, PII redaction, rate limiting, and RBAC roles.
- 🎯 **[Threat Model](docs/THREAT_MODEL.md)** — STRIDE matrix detailing 9 payment attack vectors and mitigations.
- 📋 **[Demo Script](docs/DEMO_SCRIPT.md)** — Timed walkthrough script for demonstrations and evaluations.
- 💳 **[Razorpay Test Mode Setup](docs/RAZORPAY_TEST_MODE_SETUP.md)** — Guide for connecting live Razorpay test webhooks.

---

## ⚖️ License

RiskShield AI is licensed under the [MIT License](LICENSE).
