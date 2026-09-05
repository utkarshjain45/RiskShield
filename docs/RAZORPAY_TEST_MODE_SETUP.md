# Razorpay Test Mode Setup & Webhook Integration Guide

This guide explains how to connect Razorpay Test Mode with RiskShield AI's real-time risk assessment engine.

---

## 1. Safety & Test Mode Isolation Policy

> [!CAUTION]
> **Strict Test Mode Isolation**:
> - Never use live/production API keys (`rzp_live_...`) with RiskShield AI.
> - Only use Razorpay **Test Mode** API keys (`rzp_test_...`).
> - The webhook secret must be configured via environment variables and is **NEVER** logged or exposed in HTTP error responses.

---

## 2. Environment Variables Configuration

Set the following environment variables in your deployment environment or in a `.env` file:

```bash
# Razorpay Test Mode Credentials
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxxxxxx
RAZORPAY_KEY_SECRET=your_test_key_secret_here
RAZORPAY_WEBHOOK_SECRET=your_test_webhook_secret_here

# Kafka Brokers for asynchronous webhook processing
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

In `backend/src/main/resources/application.yml`:
```yaml
riskshield:
  razorpay:
    key-id: ${RAZORPAY_KEY_ID:rzp_test_placeholder}
    key-secret: ${RAZORPAY_KEY_SECRET:rzp_test_secret_placeholder}
    webhook-secret: ${RAZORPAY_WEBHOOK_SECRET:rzp_test_webhook_secret}
```

---

## 3. Configuring Webhooks in Razorpay Dashboard (Test Mode)

1. Log in to the [Razorpay Dashboard](https://dashboard.razorpay.com/).
2. Toggle the switch in the top header to **Test Mode**.
3. Navigate to **Settings** → **Webhooks**.
4. Click **+ Add New Webhook**.
5. Fill in the webhook configuration:
   - **Webhook URL**:
     - For production/staging: `https://your-domain.com/api/v1/webhooks/razorpay`
     - For local development with ngrok: `https://<your-subdomain>.ngrok-free.app/api/v1/webhooks/razorpay`
   - **Secret**: Enter a secure random string (e.g. `rzp_test_webhook_secret_secure_123`). Copy this value into `RAZORPAY_WEBHOOK_SECRET`.
   - **Alert Email**: Enter your developer or security alert email address.
   - **Active Events**: Select the payment lifecycle events:
     - `payment.authorized` (Triggers real-time ML inference & risk assessment)
     - `payment.captured` (Confirms payment settlement)
     - `payment.failed` (Updates transaction state and behavioral counters)
6. Click **Save Webhook**.

---

## 4. Webhook Ingestion Architecture

```
Razorpay Test Mode
        ↓ HTTP POST (raw JSON + X-Razorpay-Signature + X-Razorpay-Event-Id)
POST /api/v1/webhooks/razorpay
        ↓
1. Constant-time HMAC-SHA256 Signature Validation (Timing attack safe)
        ↓
2. Event-ID Idempotency Check (Duplicate drops return fast 200 OK)
        ↓
3. Receipt Metadata Persistence (Table: razorpay_webhook_receipts)
        ↓
4. Safe Out-of-Order Reconciliation (Preserves terminal CAPTURED/FAILED states)
        ↓
5. Return Fast 200 OK (< 50ms, prevents Razorpay webhook retries)
        ↓ (Asynchronous)
Kafka Topic: payment.created / payment.updated
        ↓
RiskEventConsumer (Feature Enrichment → TreeSHAP Scoring → Policy Engine)
```

---

## 5. Local Development Replay Mechanism

To test the ingestion pipeline without setting up ngrok or waiting for test cards:

### Using the Built-In Replay Endpoint
RiskShield AI includes a local development replay endpoint that automatically signs test payloads using your configured `RAZORPAY_WEBHOOK_SECRET`:

```bash
# Replay a test authorized payment
curl -X POST http://localhost:8080/api/v1/webhooks/razorpay/replay \
  -H "Content-Type: application/json" \
  -d '{
    "event_type": "payment.authorized",
    "amount_in_paise": 450000,
    "method": "card",
    "email": "customer.test@example.com",
    "contact": "+919876543210",
    "merchant_id": "mer_default_001"
  }'
```

Response:
```json
{
  "status": "SUCCESS",
  "message": "Payment event dispatched to risk assessment pipeline",
  "event_id": "evt_replay_a67ceab2a44644",
  "event_type": "payment.authorized",
  "entity_id": "pay_test_39e89acc77f546",
  "received_at": "2026-09-05T00:10:25.355Z"
}
```

### Direct Webhook Simulation with Custom HMAC Signature
```bash
PAYLOAD='{"entity":"event","account_id":"acc_test","event":"payment.authorized","contains":["payment"],"payload":{"payment":{"entity":{"id":"pay_test_999","amount":100000,"currency":"INR","status":"authorized","method":"upi","email":"user@test.com","contact":"+919999999999","notes":{"merchant_id":"mer_default_001"},"created_at":1600000000}}}}'

# Generate HMAC-SHA256 signature
SIG=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "rzp_test_webhook_secret" | sed 's/^.* //')

# Post directly to webhook endpoint
curl -X POST http://localhost:8080/api/v1/webhooks/razorpay \
  -H "Content-Type: application/json" \
  -H "X-Razorpay-Signature: $SIG" \
  -H "X-Razorpay-Event-Id: evt_manual_test_001" \
  -d "$PAYLOAD"
```

---

## 6. Verification Test Cases

The test suite in `RazorpayWebhookControllerTest` verifies:
- **Valid Signature**: Dispatches Kafka `payment.created` event and records `PROCESSED` receipt.
- **Invalid Signature**: Rejects with `401 Unauthorized`.
- **Duplicate Event**: Returns `200 OK` with `DUPLICATE` status, prevents duplicate Kafka dispatch.
- **Malformed Payload**: Returns `400 Bad Request`.
- **Unknown/Non-Payment Event**: Returns `200 OK` with `IGNORED` status.
- **Out-of-Order Delivery**: Late `payment.authorized` events do not overwrite already `CAPTURED` transactions.
