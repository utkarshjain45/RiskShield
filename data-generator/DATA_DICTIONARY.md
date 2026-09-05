# 📖 RiskShield AI — Synthetic Data Dictionary

This document details the relational entity schemas, transaction fields, behavioral definitions, and chronological split methodology implemented by the RiskShield AI synthetic transaction generator.

---

## 1. Master Relational Entities (`data/raw/`)

### 1.1 `merchants.csv`
Stores merchant commercial entities, vertical categories, and risk profiles.

| Column | Type | Description | Example / Range |
| :--- | :--- | :--- | :--- |
| `merchant_id` | String (PK) | Unique merchant identifier | `mer_0001` |
| `merchant_name` | String | Commercial merchant trade brand | `FreshBazaar_0012` |
| `category` | String | Merchant industry vertical | `grocery_supermarket`, `electronics`, `fashion_apparel`, `food_dining`, `travel_airline`, `digital_gaming`, `fintech_lending`, `pharmacy_healthcare` |
| `merchant_risk_tier`| String | Inherent risk tier based on merchant category | `LOW`, `MEDIUM`, `HIGH` |
| `avg_ticket_inr` | Float | Baseline average transaction value in Indian Rupees | `850.00` |
| `std_ticket_inr` | Float | Standard deviation of transaction amount | `380.00` |
| `created_at` | ISO-8601 | Merchant onboarding timestamp | `2026-01-15T10:00:00` |

---

### 1.2 `customers.csv`
Stores consumer accounts, personal baseline spending profiles, and primary identity bindings.

| Column | Type | Description | Example / Range |
| :--- | :--- | :--- | :--- |
| `customer_id` | String (PK) | Unique customer account identifier | `cust_000001` |
| `account_created_at` | ISO-8601 | Account creation timestamp | `2026-02-10T14:22:00` |
| `typical_amount_inr` | Float | Personalized typical spending average (log-normal) | `645.50` |
| `typical_payment_method` | String | Primary preferred payment method | `upi`, `card`, `netbanking` |
| `primary_device_id` | String (FK) | Customer's primary authorized device (`devices.device_id`) | `dev_001042` |
| `primary_ip` | String (FK) | Customer's primary home/office IP address | `103.21.124.8` |
| `billing_state` | String | Customer registered Indian state | `Karnataka`, `Maharashtra`, `Delhi` |
| `risk_segment` | String | Customer risk profile tier | `low_risk` (82%), `standard` (15%), `high_risk` (3%) |

---

### 1.3 `devices.csv`
Stores hardware and browser fingerprint profiles.

| Column | Type | Description | Example / Range |
| :--- | :--- | :--- | :--- |
| `device_id` | String (PK) | Unique hardware device fingerprint | `dev_000001` |
| `device_type` | String | Operating system & device form factor | `mobile_android`, `mobile_ios`, `desktop_chrome`, `desktop_windows`, `tablet` |
| `is_emulator` | Integer (0/1) | Whether device exhibits software emulator attributes | `0` (Physical), `1` (Emulator/Rooted) |
| `first_seen_at` | ISO-8601 | First network observation timestamp | `2026-04-01T08:00:00` |

---

### 1.4 `ip_addresses.csv`
Stores network IP addresses, geolocation, and ASN attributes.

| Column | Type | Description | Example / Range |
| :--- | :--- | :--- | :--- |
| `ip_address` | String (PK) | IPv4 network address | `49.36.12.190` |
| `ip_country` | String | Two-letter ISO country code | `IN`, `US`, `SG`, `NL`, `GB` |
| `ip_state` | String | Indian state or International designation | `Karnataka`, `Maharashtra`, `International` |
| `isp` | String | Internet Service Provider or hosting entity | `Reliance Jio`, `Bharti Airtel`, `ACT Fibernet`, `DigitalOcean VPN`, `AWS Proxy` |
| `is_vpn_proxy` | Integer (0/1) | Data center or commercial VPN indicator | `0` (Residential/Mobile), `1` (VPN/Proxy) |
| `first_seen_at` | ISO-8601 | First network observation timestamp | `2026-03-20T12:00:00` |

---

## 2. Transactions Schema (`transactions.csv`, `train.csv`, `val.csv`, `test.csv`)

| Column | Type | Description | Invariant & Range |
| :--- | :--- | :--- | :--- |
| `transaction_id` | String (PK) | Unique sequential transaction identifier | `tx_00000001` |
| `merchant_id` | String (FK) | Reference to `merchants.merchant_id` | `mer_0001` |
| `customer_id` | String (FK) | Reference to `customers.customer_id` | `cust_000001` |
| `device_id` | String (FK) | Reference to `devices.device_id` | `dev_000001` |
| `ip_address` | String (FK) | Reference to `ip_addresses.ip_address` | `103.21.124.8` |
| `timestamp` | ISO-8601 | UTC transaction timestamp | `2026-06-01T19:45:12` |
| `amount` | Float | Transaction amount in Indian Rupees (INR) | `1499.00` |
| `amount_in_paise` | Integer | Transaction amount in Paise (1 INR = 100 paise) | `149900` |
| `currency` | String | Currency code | `INR` |
| `payment_method` | String | Payment rail used | `upi`, `card`, `netbanking` |
| `transaction_status` | String | Gateway authorization status | `SUCCESS`, `FAILED` |
| `customer_account_age_days` | Integer | Account age in days at the exact time of transaction | $\ge 1$ day |
| `is_new_device` | Integer (0/1) | 1 if device has not been used by customer previously | `0` (Known device), `1` (New device) |
| `is_new_ip` | Integer (0/1) | 1 if IP has not been used by customer previously | `0` (Known IP), `1` (New IP) |
| `hour_of_day` | Integer | UTC hour of transaction | `0` to `23` |
| `day_of_week` | Integer | Day of week (0 = Monday, 6 = Sunday) | `0` to `6` |
| `fraud_label` | Integer (0/1) | **Target Ground Truth**: 0 = Legitimate, 1 = Fraudulent | `{0, 1}` |
| `fraud_scenario` | String | Classification scenario or attack taxonomy | See section below |

---

## 3. Fraud Behavioral Taxonomy (Scenarios A through H)

RiskShield AI avoids arbitrary thresholds like *"amount > X is fraud"*. Instead, fraud emerges from **interacting behavioral signatures**:

| Code | Scenario Name | Description | Key Interacting Features |
| :--- | :--- | :--- | :--- |
| **—** | `legitimate` | Normal customer shopping patterns matching diurnal volume and historical spend. | Primary device, home IP, daytime hours, low failure rate (~2.5%). |
| **A** | `velocity_fraud` | Card testing or rapid fund extraction burst. | 6–12 transactions in 5–15 mins, micro amounts or high tickets, elevated failure rate (>50%). |
| **B** | `new_device_abuse` | Account Takeover (ATO) on established customer. | Account age > 90 days, brand new device (`is_new_device=1`), unfamiliar proxy IP (`is_new_ip=1`), 3.5x–7x typical amount, shift to digital/electronics. |
| **C** | `device_reuse_syndicate` | Device farm / carder syndicate hardware reuse. | 1 emulator device fingerprint (`dev_id`) transacting across 15–30 distinct customer accounts within hours. |
| **D** | `ip_pooling_abuse` | VPN / botnet exit node cluster attack. | 1 data-center IP originating transactions for 16–32 distinct customer accounts in a narrow window. |
| **E** | `amount_anomaly` | Extreme contextual deviation from customer & merchant baseline. | 8x–16x customer's typical amount on high-risk merchant (electronics, digital goods), new device/proxy. |
| **F** | `unusual_time_abuse` | Graveyard shift attack. | Occurs between 02:30 AM – 05:00 AM off-peak, high ticket, unfamiliar IP. |
| **G** | `abnormal_behavior` | Drastic deviation in merchant category and payment method. | Daytime grocery shopper suddenly purchasing digital gaming or fintech cashouts with new payment rails. |
| **H** | `coordinated_cluster_spike` | Synchronized velocity attack targeting a single merchant. | 40–90 rapid transactions in a 2-hour window against one merchant from a shared proxy subnet. Triggers statistical fraud spike detector. |

---

## 4. Chronological Splitting (Zero Future-Data Leakage)

Standard random splitting (e.g. `train_test_split(shuffle=True)`) leaks future behavioral trends, customer history, and velocity states into past training sets.

RiskShield AI enforces **strict chronological ordering**:
1. All generated transactions are sorted by `timestamp ASC`.
2. **Train Set (`train.csv`)**: First 70% of chronological timeline.
3. **Validation Set (`validation.csv`)**: Middle 15% of chronological timeline.
4. **Test Set (`test.csv`)**: Final 15% of chronological timeline (strictly held-out test set).

$$
\max(\text{Timestamp}_{\text{train}}) \le \min(\text{Timestamp}_{\text{val}}) \le \max(\text{Timestamp}_{\text{val}}) \le \min(\text{Timestamp}_{\text{test}})
$$
