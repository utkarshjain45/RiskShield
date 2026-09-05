# 🚀 RiskShield AI — Quickstart & Setup Guide

Welcome to **RiskShield AI**! This step-by-step guide will walk you through setting up and running the complete RiskShield AI platform on your local computer from scratch.

---

## 📋 What is RiskShield AI in Simple Terms?

**RiskShield AI** is an intelligent fraud prevention system for online payments (such as Razorpay). Whenever a customer makes a purchase:
1. The transaction is instantly evaluated by a Machine Learning model in under **25 milliseconds**.
2. If suspicious patterns are detected (like stolen cards, repeated device spoofing, or rapid transactions), the system automatically **blocks** or flags it for manual review.
3. Every decision includes an **explanation** (showing exactly why the transaction was blocked) and an immutable **audit trail**.
4. An interactive **Operations Dashboard** lets fraud analysts monitor live traffic, investigate alerts, and run controlled fraud simulations.

---

## 🛠️ Prerequisites

Before you begin, ensure you have the following installed on your machine:

1. **[Git](https://git-scm.com/)** — To clone the repository.
2. **[Docker Desktop](https://www.docker.com/products/docker-desktop/)** (version 24.0 or higher) — Docker runs all application services (Database, Redis, Kafka, Backend, Frontend, and ML engine) automatically without needing manual software installation.
   - *Ensure Docker Desktop is running before starting the setup.*

---

## ⚡ Step-by-Step Setup Instructions

### Step 1: Clone the Repository

Open your terminal (PowerShell, Command Prompt, or Bash) and clone the repository:

```bash
git clone https://github.com/your-username/riskshield-ai.git
cd "riskshield-ai"
```

---

### Step 2: Set Up the Environment Configuration

RiskShield AI includes a pre-configured template file with safe local defaults:

**On Linux / macOS:**
```bash
cp .env.example .env
```

**On Windows (PowerShell):**
```powershell
Copy-Item .env.example .env
```

**On Windows (CMD):**
```cmd
copy .env.example .env
```

> **Note:** The default `.env` works out-of-the-box for local testing. If you wish to enable the optional AI Assistant Copilot, add your free [Google Gemini API Key](https://aistudio.google.com/) to `GEMINI_API_KEY` inside `.env`.

---

### Step 3: Start the Platform (1 Single Command)

Start all services (PostgreSQL database, Redis cache, Kafka messaging, Python ML service, Spring Boot core engine, and React frontend console) using Docker Compose:

```bash
docker compose up --build -d
```

Docker will download the images, build the containers, and launch all services in the background. This typically takes 2–3 minutes on the first run.


---

### Step 4: Verify the Application is Running

Once the startup script completes, open your web browser and navigate to:

| Service | URL | Description |
| :--- | :--- | :--- |
| **Merchant Operations Console** | [http://localhost:3000](http://localhost:3000) | Main frontend dashboard (Transactions, Incidents, Policies, Demo) |
| **Backend REST API** | [http://localhost:8080/api/v1](http://localhost:8080/api/v1) | Core risk engine API endpoints |
| **Interactive Swagger API Docs** | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) | OpenAPI interactive documentation and test runner |
| **Python ML Engine** | [http://localhost:8000/health](http://localhost:8000/health) | Real-time XGBoost & TreeSHAP inference service |
| **Backend Health Check** | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) | System health status and dependency checks |

---

### Step 5: Test the Interactive Demo Simulation

You can immediately see the fraud detection engine in action without needing live payment credentials:

1. Open the **Operations Console** at [http://localhost:3000](http://localhost:3000).
2. Look at the top banner and click **`Launch Scenario`**.
3. Choose any simulated attack scenario:
   - **Velocity Attack:** High-frequency script testing stolen card credentials.
   - **Coordinated Fraud Spike:** Botnet cluster cycling rooted emulators and proxies.
   - **Amount Anomaly:** Sudden abnormal high-value purchases.
4. Click **`Start Simulation`**.
5. Watch the live counters update:
   - Suspicious transactions are flagged in real-time.
   - The **Fraud Spike Detector** triggers an operational incident.
   - Navigate to **Transactions** or **Risk Analysis** to see detailed SHAP feature attributions.
6. When finished, click the high-contrast **`Stop Demo`** button in the top banner.

---

## 🛑 Stopping and Restarting

### To Stop the Application:
```bash
docker compose down
```

### To Restart After Stopping:
```bash
docker compose up -d
```

### To Reset Everything (Fresh Clean Database):
```bash
docker compose down -v
docker compose up --build -d
```

---

## ❓ Troubleshooting & FAQs

### 1. "Docker is not running or not recognized"
- Make sure Docker Desktop is installed and opened. Check that the Docker whale icon in your system tray shows "Engine running".

### 2. "Port already in use" error (e.g. 5432, 6379, 3000, 8080)
- If you have local PostgreSQL or Redis installed, they might be using ports 5432 or 6379.
- You can either stop your local service or change the port mapping in `.env` (e.g., set `POSTGRES_PORT=5433`).

### 3. How do I inspect service logs?
To see live logs from any component:
```bash
# View all logs
docker compose logs -f

# View backend logs only
docker compose logs -f backend

# View ML service logs only
docker compose logs -f ml-service
```

---

## 📖 Deep-Dive Documentation

For advanced details, architecture specifications, API references, and security models, refer to the [`docs/`](docs/) directory:

- 🏛️ **[System Architecture](docs/ARCHITECTURE.md)** — Architectural diagrams, data pipelines, and component breakdown.
- 🔌 **[REST API Reference](docs/API.md)** — Endpoints, request schemas, and cURL examples.
- 🧠 **[Model Card](docs/MODEL_CARD.md)** — XGBoost held-out evaluation, confusion matrix, and TreeSHAP explainability.
- 🛡️ **[Security Architecture](docs/SECURITY.md)** — RBAC, webhook HMAC verification, and replay attack defense.
- 🎯 **[Threat Model](docs/THREAT_MODEL.md)** — STRIDE security matrix for 9 payment fraud vectors.
- 💳 **[Razorpay Test Mode Setup](docs/RAZORPAY_TEST_MODE_SETUP.md)** — Connecting live Razorpay webhooks.
