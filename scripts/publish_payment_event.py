#!/usr/bin/env python3
"""
RiskShield AI - Kafka Event Publisher
Publishes a test 'payment.created' event to Apache Kafka or the RiskShield backend API.

Usage:
    python scripts/publish_payment_event.py
    python scripts/publish_payment_event.py --amount 850000 --merchant mer_electronics_99 --fraud
"""

import argparse
import json
import sys
import time
import uuid
import urllib.request
import urllib.error

DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092,localhost:29092"
DEFAULT_TOPIC = "payment.created"
DEFAULT_API_URL = "http://localhost:8080/api/v1/transactions"

def generate_payment_event(tx_id=None, merchant_id=None, amount_paise=None, is_fraud=False):
    now_ms = int(time.time() * 1000)
    tx_id = tx_id or f"tx_sim_{uuid.uuid4().hex[:12]}"
    merchant_id = merchant_id or "mer_test_001"
    
    if is_fraud:
        amount_paise = amount_paise or 15000000  # ₹1,50,000 (abnormal amount)
        customer_id = "cust_suspicious_999"
        device_id = "dev_emulator_x86"
        ip_address = "185.220.101.5" # Tor exit node
        is_new_device = True
        is_new_ip = True
        acc_age = 2
    else:
        amount_paise = amount_paise or 499900    # ₹4,999
        customer_id = "cust_legit_101"
        device_id = "dev_iphone_15"
        ip_address = "49.207.200.15"
        is_new_device = False
        is_new_ip = False
        acc_age = 180

    event = {
        "event_id": f"evt_{uuid.uuid4().hex}",
        "event_type": "payment.created",
        "event_version": "v1.0.0",
        "occurred_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "transaction_id": tx_id,
        "merchant_id": merchant_id,
        "correlation_id": f"corr_{uuid.uuid4().hex[:12]}",
        "customer_id": customer_id,
        "customer_email": f"{customer_id}@example.com",
        "device_id": device_id,
        "ip_address": ip_address,
        "amount_in_paise": amount_paise,
        "currency": "INR",
        "payment_method": "card",
        "customer_account_age_days": acc_age,
        "is_new_device": is_new_device,
        "is_new_ip": is_new_ip
    }
    return event

def try_publish_kafka(bootstrap_servers, topic, event):
    """Attempts to publish directly to Kafka broker if kafka-python or confluent-kafka is available."""
    try:
        from kafka import KafkaProducer
        producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers.split(','),
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            key_serializer=lambda k: k.encode('utf-8')
        )
        producer.send(topic, key=event["transaction_id"], value=event)
        producer.flush()
        print(f" [KAFKA] Successfully published event {event['event_id']} to topic '{topic}'!")
        return True
    except ImportError:
        pass
    except Exception as e:
        print(f" [KAFKA WARNING] Direct Kafka publish failed: {e}")
    return False

def publish_via_api(api_url, event):
    """Publishes transaction via Spring Boot REST API which ingests into Kafka automatically."""
    payload = {
        "merchantId": event["merchant_id"],
        "customerId": event["customer_id"],
        "customerEmail": event["customer_email"],
        "deviceId": event["device_id"],
        "ipAddress": event["ip_address"],
        "amountInPaise": event["amount_in_paise"],
        "currency": event["currency"],
        "paymentMethod": event["payment_method"],
        "customerAccountAgeDays": event["customer_account_age_days"],
        "isNewDevice": event["is_new_device"],
        "isNewIp": event["is_new_ip"]
    }
    req_data = json.dumps(payload).encode('utf-8')
    headers = {
        "Content-Type": "application/json",
        "X-Correlation-ID": event["correlation_id"]
    }
    req = urllib.request.Request(api_url, data=req_data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as response:
            res_body = json.loads(response.read().decode('utf-8'))
            print(f" [API SUCCESS] Ingested tx {event['transaction_id']} via {api_url}: HTTP {response.status}")
            print(f" [API RESPONSE] {json.dumps(res_body, indent=2)}")
            return True
    except urllib.error.URLError as e:
        print(f" [API WARNING] Failed to connect to API ({api_url}): {e}")
    return False

def main():
    parser = argparse.ArgumentParser(description="Publish test payment.created event")
    parser.add_argument("--tx-id", help="Transaction ID")
    parser.add_argument("--merchant", default="mer_test_001", help="Merchant ID")
    parser.add_argument("--amount", type=int, help="Amount in paise (e.g. 499900 for Rs. 4999)")
    parser.add_argument("--fraud", action="store_true", help="Simulate high-risk fraud anomaly transaction")
    parser.add_argument("--topic", default=DEFAULT_TOPIC, help="Kafka topic name")
    parser.add_argument("--servers", default=DEFAULT_BOOTSTRAP_SERVERS, help="Kafka bootstrap servers")
    parser.add_argument("--api", default=DEFAULT_API_URL, help="Backend API endpoint URL")
    args = parser.parse_args()

    event = generate_payment_event(args.tx_id, args.merchant, args.amount, args.fraud)

    print("\n" + "="*70)
    print(" RiskShield AI - Kafka Event Publisher")
    print("="*70)
    print(f" Event ID       : {event['event_id']}")
    print(f" Event Type     : {event['event_type']}")
    print(f" Transaction ID : {event['transaction_id']}")
    print(f" Amount         : ₹{event['amount_in_paise']/100:.2f} ({event['amount_in_paise']} paise)")
    print(f" Fraud Pattern  : {'[HIGH RISK SIMULATION]' if args.fraud else '[STANDARD TRANSACTION]'}")
    print(f" Correlation ID : {event['correlation_id']}")
    print("="*70)
    print(json.dumps(event, indent=2))
    print("="*70 + "\n")

    # Try Kafka first, then fall back to REST API
    published = try_publish_kafka(args.servers, args.topic, event)
    if not published:
        print("Falling back to publishing via RiskShield Backend API...")
        published = publish_via_api(args.api, event)

    if published:
        print("\n [DONE] Event successfully dispatched to the RiskShield event pipeline.")
    else:
        print("\n [NOTICE] Could not reach active Kafka broker or Backend API locally.")
        print("To run the full stack with Kafka, execute: docker compose up -d")

if __name__ == "__main__":
    main()
