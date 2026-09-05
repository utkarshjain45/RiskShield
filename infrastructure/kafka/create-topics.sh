#!/bin/bash
# Initialize Kafka topics for RiskShield AI

echo "Creating Kafka topics for RiskShield AI..."

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic riskshield.payment.events \
  --partitions 3 \
  --replication-factor 1

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic riskshield.fraud.alerts \
  --partitions 3 \
  --replication-factor 1

kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists \
  --topic riskshield.spike.notifications \
  --partitions 1 \
  --replication-factor 1

echo "Kafka topics created successfully."
