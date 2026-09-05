# RiskShield AI — REST API Reference Specification

Base URL: `http://localhost:8080/api/v1`  
OpenAPI / Swagger UI: `http://localhost:8080/swagger-ui/index.html`  
Actuator Probes: `http://localhost:8080/actuator/health`  

---

## 1. Authentication & Common Headers

All requests except public health probes and the signed Razorpay webhook endpoint require authentication.

| Header | Description | Example |
| :--- | :--- | :--- |
| `X-API-Key` | API key authentication (`adm_...`, `ana_...`, `mch_...`) | `adm_live_sec_prod_key_9999` |
| `X-User-Role` | Verified gateway role (`ADMIN`, `RISK_ANALYST`, `MERCHANT_VIEWER`) | `ADMIN` |
| `X-Merchant-Id` | Tenant scope for multi-tenant isolation | `mer_demo_001` |
| `X-User-Id` | Identifier of caller or analyst | `analyst_rajesh` |
| `Content-Type` | Payload content type | `application/json` |

---

## 2. Transactions API

### 2.1 Ingest Payment Transaction
* **Endpoint:** `POST /api/v1/transactions`
* **Role Required:** `ADMIN`, `RISK_ANALYST`, `MERCHANT_VIEWER` (matching merchant scope)
* **Description:** Ingests a new transaction into the risk pipeline, stores entity linkages, emits Kafka events, and records the `TRANSACTION_RECEIVED` audit event.

#### Request Body
```json
{
  "transaction_id": "tx_demo_8849102",
  "merchant_id": "mer_demo_001",
  "customer_id": "cust_9921",
  "customer_email": "customer@example.com",
  "customer_contact": "+919876543210",
  "customer_billing_state": "MH",
  "customer_account_age_days": 180,
  "device_id": "dev_samsung_s23",
  "device_type": "mobile_android",
  "is_emulator": false,
  "is_new_device": false,
  "ip_address": "49.37.12.45",
  "is_new_ip": false,
  "amount_in_paise": 250000,
  "currency": "INR",
  "payment_method": "upi"
}
```

#### cURL Example
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -H "X-User-Role: ADMIN" \
  -d '{
    "transaction_id": "tx_demo_8849102",
    "merchant_id": "mer_demo_001",
    "customer_id": "cust_9921",
    "amount_in_paise": 250000,
    "payment_method": "upi"
  }'
```

---

### 2.2 List Transactions
* **Endpoint:** `GET /api/v1/transactions`
* **Query Parameters:**
  - `search`: String (optional search on transaction ID or customer email)
  - `paymentStatus`: String (`PENDING`, `SUCCESS`, `FAILED`)
  - `merchantId`: String (merchant filter, subject to tenant boundary)
  - `page`: Integer (default 0)
  - `size`: Integer (default 20)

---

## 3. Risk Assessment & Explainability API

### 3.1 Assess Transaction Risk
* **Endpoint:** `POST /api/v1/risk/assess/{transactionId}`
* **Description:** Pulls sliding-window features from Redis, invokes XGBoost scoring, evaluates policy engine rules, generates TreeSHAP feature attributions, and returns authoritative decision.

#### Response Body
```json
{
  "success": true,
  "data": {
    "id": "ast_9948271a",
    "transaction_id": "tx_demo_8849102",
    "model_version": "v1.0.0-xgb",
    "fraud_probability": 0.88,
    "risk_score": 88.0,
    "inference_latency_ms": 14.2,
    "decision": "BLOCK",
    "policy_version": "v1.0.0",
    "policy_reason": "BLOCK because risk score 88.0 exceeded block threshold 90.0 or discrete rule match",
    "contributing_signals": [
      {
        "signal_name": "device_account_count",
        "display_name": "Device Associated with Multiple Accounts",
        "signal_value": "9",
        "shap_impact": 0.28,
        "direction": "RISK_INCREASING",
        "formatted_impact": "+0.28"
      },
      {
        "signal_name": "transaction_velocity_5m",
        "display_name": "Rapid Transaction Velocity (5m)",
        "signal_value": "12",
        "shap_impact": 0.24,
        "direction": "RISK_INCREASING",
        "formatted_impact": "+0.24"
      }
    ]
  }
}
```

---

### 3.2 Get Human-Readable Risk Explanation
* **Endpoint:** `GET /api/v1/risk/assessments/{transactionId}/explanation`
* **Description:** Produces a deterministic explanation summary synthesized from TreeSHAP values without relying on an external LLM.

---

## 4. Behavioral Feature Service API

### 4.1 Get Behavioral Feature Snapshot
* **Endpoint:** `GET /api/v1/features/transaction/{transactionId}`
* **Description:** Returns Redis sliding-window metrics calculated for customer, device, and IP address at the time of transaction.

#### Response Body
```json
{
  "success": true,
  "data": {
    "transaction_id": "tx_demo_8849102",
    "customer_velocity": {
      "transactions_5m": 8,
      "transactions_30m": 14,
      "transactions_1h": 19
    },
    "device_velocity": {
      "transactions_5m": 8,
      "transactions_1h": 15
    },
    "ip_velocity": {
      "transactions_5m": 12,
      "transactions_1h": 22
    },
    "amount_velocity": 4850000,
    "device_account_count": 9,
    "ip_account_count": 7,
    "is_new_device": false,
    "is_new_ip": false
  }
}
```

---

## 5. Model Evaluation API (Held-Out Test Set)

### 5.1 Current Evaluation on Held-Out Test Set
* **Endpoint:** `GET /api/v1/model/evaluation/current`
* **Role Required:** `ADMIN`, `RISK_ANALYST`
* **Description:** Returns performance benchmarks evaluated strictly on the untouched 10,000-sample held-out test partition.

#### Response Body
```json
{
  "success": true,
  "data": {
    "dataset_size": 10000,
    "fraud_count": 200,
    "non_fraud_count": 9800,
    "precision": 0.942,
    "recall": 0.918,
    "f1": 0.930,
    "roc_auc": 0.982,
    "pr_auc": 0.941,
    "false_positive_rate": 0.0011,
    "false_negative_rate": 0.082,
    "confusion_matrix": {
      "true_positives": 184,
      "false_positives": 11,
      "true_negatives": 9789,
      "false_negatives": 16
    },
    "false_positive_cost": 1100.0,
    "false_negative_cost": 16000.0,
    "estimated_prevented_loss": 4280000.0,
    "label": "Final evaluation on held-out test set"
  }
}
```

---

### 5.2 Threshold Tradeoff Analysis
* **Endpoint:** `GET /api/v1/model/evaluation/thresholds`
* **Role Required:** `ADMIN`, `RISK_ANALYST`
* **Description:** Returns comparative metrics across thresholds `[0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90]` with asymmetric business costs.

---

## 6. Fraud Spike Detection & Incidents API

### 6.1 List Fraud Incidents
* **Endpoint:** `GET /api/v1/incidents`
* **Query Parameters:** `status` (`OPEN`, `ACKNOWLEDGED`, `RESOLVED`), `merchantId`, `page`, `size`

### 6.2 Acknowledge Incident
* **Endpoint:** `POST /api/v1/incidents/{incidentId}/acknowledge`
* **Role Required:** `ADMIN`, `RISK_ANALYST`

### 6.3 Resolve Incident
* **Endpoint:** `POST /api/v1/incidents/{incidentId}/resolve`
* **Role Required:** `ADMIN`, `RISK_ANALYST`

### 6.4 Manual Trigger / Probe Spike Check
* **Endpoint:** `GET /api/v1/incidents/detect-spike`
* **Query Parameters:** `merchantId`, `window` (`5m`, `15m`, `1h`, `6h`, `24h`)

---

## 7. Razorpay Webhook Ingestion API

* **Endpoint:** `POST /api/v1/webhooks/razorpay`
* **Authentication:** HMAC-SHA256 signature verification (`X-Razorpay-Signature`)
* **Headers:**
  - `X-Razorpay-Signature`: Hex-encoded HMAC-SHA256 signature of raw request body
  - `X-Razorpay-Event-Id`: Unique webhook delivery ID for idempotency

#### Sample Webhook Payload
```json
{
  "entity": "event",
  "account_id": "acc_demo_test",
  "event": "payment.authorized",
  "contains": ["payment"],
  "payload": {
    "payment": {
      "entity": {
        "id": "pay_test_0091823",
        "amount": 150000,
        "currency": "INR",
        "status": "authorized",
        "method": "upi",
        "email": "shopper@example.com",
        "contact": "+919876543210",
        "created_at": 1788550000
      }
    }
  },
  "created_at": 1788550000
}
```

---

## 8. AI Investigation Assistant API (Powered by Google Gemini)

The AI Investigation Assistant operates as a read-only risk intelligence copilot powered by **Google Gemini 1.5** (with zero-downtime deterministic fallback). It resolves structured platform tools (`getTransaction`, `getRiskAssessment`, `getRiskExplanation`, `getCustomerHistory`, `getDeviceActivity`, `getIPActivity`, `getFraudIncident`, `getMerchantRiskMetrics`, `getModelEvaluation`) and synthesizes concise, authoritative risk narratives with mandatory citations. Any attempt to execute mutating actions (`block`, `capture`, `refund`, `changePolicy`) is strictly denied.

### 8.1 Inquire Assistant
* **Endpoint:** `POST /api/v1/assistant/chat` or `POST /api/v1/assistant/inquire`
* **Role Required:** `ADMIN`, `RISK_ANALYST`, `MERCHANT_VIEWER`
* **Description:** Analyst co-pilot inquiry. Strictly read-only; attempts to execute mutating actions (`block`, `refund`) are rejected.

#### Request Body
```json
{
  "session_id": "ses_investigate_01",
  "merchant_id": "mer_demo_001",
  "active_transaction_id": "tx_demo_8849102",
  "message": "Why was this transaction blocked?"
}
```

#### Response Attributes
* `session_id`: Persistent investigation session ID
* `response`: Gemini 1.5 synthesized explanation including `### Sources` citation block
* `tools_executed`: Array of read-only tool names invoked
* `sources`: Specific entity references inspected during the inquiry
* `tool_results`: Structured data payloads returned by each tool

---

## 9. Immutable Audit Trail API

Audit records are strictly immutable. Any `POST`, `PUT`, `PATCH`, or `DELETE` attempt returns HTTP `405 Method Not Allowed`.

### 9.1 Query Audit Trail (Filtered & Paginated)
* **Endpoint:** `GET /api/v1/audit`
* **Query Parameters:** `eventType`, `merchantId`, `actorId`, `service`, `fromDate`, `toDate`, `page`, `size`

### 9.2 Transaction Audit Trail
* **Endpoint:** `GET /api/v1/audit/{transactionId}`
* **Description:** Chronological list of all events for a transaction.

### 9.3 Structured Chronological Timeline
* **Endpoint:** `GET /api/v1/audit/{transactionId}/timeline`

### 9.4 Incident Audit Trail
* **Endpoint:** `GET /api/v1/audit/incidents/{incidentId}`

---

## 10. Demo Simulation Engine API

### 10.1 Start Simulation
* **Endpoint:** `POST /api/v1/demo/simulations/start`
* **Description:** Asynchronously streams synthetic traffic through the live risk pipeline.

#### Request Body
```json
{
  "mode": "COORDINATED_FRAUD_SPIKE",
  "merchant_id": "mer_demo_001",
  "transaction_count": 30,
  "interval_ms": 300
}
```

* **Modes Supported:**
  - `NORMAL_TRAFFIC`
  - `VELOCITY_ATTACK`
  - `DEVICE_ABUSE`
  - `IP_CLUSTER_ATTACK`
  - `AMOUNT_ANOMALY`
  - `COORDINATED_FRAUD_SPIKE`

### 10.2 Stop Simulation
* **Endpoint:** `POST /api/v1/demo/simulations/{id}/stop`

### 10.3 Get Simulation Status
* **Endpoint:** `GET /api/v1/demo/simulations/{id}`

### 10.4 Get Currently Active Simulation
* **Endpoint:** `GET /api/v1/demo/simulations/active`

### 10.5 Get Simulation Modes Catalog
* **Endpoint:** `GET /api/v1/demo/simulations/modes`
