# RiskShield AI — Security Architecture & Policy

## 1. Security Overview

RiskShield AI is an enterprise-grade fraud detection and real-time risk intelligence platform engineered with a strict **defense-in-depth**, **zero-trust**, and **tenant-isolated** security model. 

The security architecture guarantees:
- **Zero Secrets Exposure**: Zero hardcoded secrets in source control or container images.
- **Strict Tenant Isolation**: Cryptographic or role-enforced boundary isolation preventing cross-merchant data leakage.
- **Role-Based Access Control (RBAC)**: Fine-grained permissions across administrative, analyst, and merchant viewer domains.
- **Tamper-Proof Auditability**: Immutable risk decisioning and security audit ledgers.
- **Automated PII Masking**: In-flight redaction of payment cards, phone numbers, emails, and credentials across all system logs.
- **Cryptographic Webhook Integrity**: Raw payload HMAC-SHA256 signature verification, replay attack mitigation, and event idempotency.
- **Defensive AI Engineering**: Sandboxed investigation assistant with read-only tool access and strict prompt injection defenses.

---

## 2. Authentication & Role-Based Access Control (RBAC)

RiskShield AI enforces stateless authentication via pre-shared API keys or verified gateway identity headers (`X-User-Role`, `X-Merchant-Id`, `X-User-Id`).

### Roles and Permissions Matrix

| Resource / Endpoint | ADMIN | RISK_ANALYST | MERCHANT_VIEWER | Notes |
|:---|:---:|:---:|:---:|:---|
| **GET /api/v1/transactions** | Full access | Full access | Own Merchant only | Filtered strictly by merchant scope |
| **GET /api/v1/transactions/{id}/investigate** | Full access | Full access | Own Merchant only | Cross-merchant returns `403 Forbidden` |
| **POST /api/v1/transactions** | Allowed | Denied (`403`) | Denied (`403`) | Transaction ingestion restricted |
| **GET /api/v1/incidents** | Full access | Full access | Own Merchant only | Scoped to merchant tenant |
| **POST /api/v1/incidents/{id}/acknowledge** | Allowed | Allowed | Denied (`403`) | Viewer cannot alter incident status |
| **POST /api/v1/incidents/{id}/resolve** | Allowed | Allowed | Denied (`403`) | Viewer cannot alter incident status |
| **GET /api/v1/policies** | Full access | Read-only | Own Merchant only | Viewers cannot see competitor policies |
| **POST / PUT /api/v1/policies/** | Full access | Denied (`403`) | Denied (`403`) | Only Admins mutate decision thresholds |
| **GET /api/v1/model/evaluation/** | Full access | Full access | Denied (`403`) | Held-out test metrics restricted |
| **POST /api/v1/assistant/chat** | Full access | Full access | Own Merchant only | Tool queries pinned to user's merchant |

### API Key Prefix Conventions
- `adm_...` &rarr; Authenticates as `ADMIN`
- `ana_...` &rarr; Authenticates as `RISK_ANALYST`
- `mch_...` &rarr; Authenticates as `MERCHANT_VIEWER`

---

## 3. Multi-Tenant Merchant Isolation

In multi-tenant financial environments, merchant cross-visibility is a critical vulnerability. RiskShield AI implements strict isolation at the service and data layer:

1. **Identity Pinning**: Every authenticated `MERCHANT_VIEWER` session is irrevocably bound to their specific `merchantId`.
2. **Access Control Assertions**: Before returning any transaction, feature snapshot, incident report, or policy, services execute:
   ```java
   SecurityUtils.assertMerchantAccess(transaction.getMerchant().getId());
   ```
3. **Automated Query Scoping**: Collection endpoints (`/transactions`, `/incidents`, `/policies`) automatically enforce filtering on `merchantId` if the caller has `MERCHANT_VIEWER` authority.
4. **Investigation AI Guard**: The AI Investigation Assistant extracts the principal's merchant ID from the security context and forcibly constrains all tool arguments (`getTransaction`, `getCustomerHistory`, `getDeviceActivity`, etc.) to the tenant's boundaries.

---

## 4. Immutable Decisioning & Audit Ledger

Financial regulations require cryptographic non-repudiation and complete traceability of automated payment decisions.

1. **Multi-Tier Immutability Architecture**:
   - **REST Layer**: `AuditController` exposes strictly `GET` endpoints. Any `POST`, `PUT`, `PATCH`, or `DELETE` attempt returns HTTP `405 Method Not Allowed`.
   - **JPA Layer**: `RiskDecision` and `AuditEvent` declare `@PreUpdate` and `@PreRemove` lifecycle hooks that immediately raise `UnsupportedOperationException`.
   - **Database Layer**: Persistent columns in `risk_decisions` and `audit_events` are locked with `@Column(updatable = false)`.
2. **Cryptographic SHA-256 Payload Hashing**:
   - Every `AuditEvent` records a 64-character SHA-256 hash (`payload_hash`) calculated over the raw event metadata JSON at insertion time via `AuditCryptoUtils.computeSha256(metadata)`.
   - Any external tampering with row content breaks mathematical hash verification.
3. **14 Mandatory Risk Event Types**:
   - Ingestion: `TRANSACTION_RECEIVED`, `FEATURES_GENERATED`
   - Evaluation: `MODEL_SCORED`, `POLICY_EVALUATED`, `RISK_DECISION_CREATED`
   - Incident & Alerts: `ALERT_CREATED`, `INCIDENT_CREATED`, `INCIDENT_ACKNOWLEDGED`, `INCIDENT_RESOLVED`
   - AI & Webhooks: `AI_INVESTIGATION_STARTED`, `AI_TOOL_CALLED`, `AI_RESPONSE_GENERATED`, `WEBHOOK_RECEIVED`, `WEBHOOK_REJECTED`

---

## 5. Webhook Security & Replay Attack Defense

The Razorpay integration endpoint (`POST /api/v1/webhooks/razorpay`) protects against forgery, replays, and race conditions:

1. **HMAC-SHA256 Signature Verification**:
   - Webhook signatures are verified against the raw HTTP request body using `Mac.getInstance("HmacSHA256")` and `MessageDigest.isEqual(...)` (constant-time comparison to eliminate timing side-channel attacks).
2. **Event Idempotency**:
   - Each webhook's `x-razorpay-event-id` is checked against the immutable database receipts table (`RazorpayWebhookReceipt`).
   - Duplicate delivery returns an immediate `200 OK` without triggering redundant scoring or Kafka events.
3. **Replay Window Enforcement**:
   - The payload payment timestamp (`payment.entity.created_at`) is validated against current server time:
   ```java
   long eventAgeSeconds = Math.abs(now - paymentCreatedAt);
   if (eventAgeSeconds > 900) { // 15-minute threshold
       throw new SecurityException("Webhook rejected: payload timestamp outside 15-minute replay window");
   }
   ```

---

## 6. PII Masking & Log Sanitization

Sensitive customer and financial data must never appear in plaintext logs.

RiskShield AI deploys a custom Logback conversion filter (`PiiMaskingPatternConverter`) that scans all log messages and redacts:
- **Payment Card PANs**: Replaced with `4111-****-****-1234`
- **Email Addresses**: Replaced with `j***@domain.com`
- **Phone Numbers**: Replaced with `+91******3210`
- **Authentication Secrets**: Bearer tokens, API keys, and authorization headers are scrubbed.

Configuration in `logback-spring.xml`:
```xml
<pattern>%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %clr(%5p) : %maskPii(%m)%n%wEx</pattern>
```

---

## 7. Rate Limiting & DoS Protection

RiskShield AI implements in-memory sliding-window token bucket rate limiting via `ApiRateLimitingFilter`:
- **General APIs**: 120 requests/minute per client IP / API key.
- **AI Investigation Assistant**: 30 requests/minute to mitigate resource exhaustion and expensive LLM queries.
- Exceeding the threshold triggers HTTP `429 Too Many Requests` with a `Retry-After: 60` response header and standardized JSON payload.

---

## 8. LLM Prompt Injection & Sandbox Guardrails

The AI Investigation Assistant is designed to assist human analysts without introducing LLM non-determinism into the enforcement loop:

1. **Authority Separation**:
   - The LLM **never** decides whether to block, allow, or review payments.
   - Classification and actioning are exclusively governed by the LightGBM ML model and deterministic policy engine.
2. **Read-Only Tools**:
   - All assistant tools (`getTransaction`, `getRiskAssessment`, `getCustomerHistory`, etc.) are strictly read-only. No mutation tools exist.
3. **Data Pre-Validation**:
   - User queries are limited to 2,000 characters and validated against shell/SQL injection patterns.
   - Assistant prompts treat user input as untrusted context and forbid tool execution outside the authenticated tenant boundary.

---

## 9. Zero-Secrets Policy & Configuration

1. **No Credentials in Code or Images**:
   - Database credentials, Redis passwords, Kafka broker credentials, Razorpay webhook secrets, and LLM API keys are ingested exclusively via environment variables (`DATABASE_URL`, `REDIS_PASSWORD`, `RAZORPAY_WEBHOOK_SECRET`, `GEMINI_API_KEY`).
   - Dockerfiles compile code from source or copy artifacts without baking secrets into image layers.
2. **`.gitignore` Enforcement**:
   - `.env`, `*.key`, `*.pem`, `*.log`, and IDE state are strictly ignored.
   - `.env.example` provides documentation with placeholder values only.

---

## 10. Vulnerability Disclosure Policy

If you discover a security vulnerability within RiskShield AI, please do not open a public GitHub issue. Send details and reproduction steps to `security@riskshield.ai`. Security reports are acknowledged within 24 hours.
