#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== OTel + Prometheus + Grafana POC ==="

echo "Building Java application..."
mvn -q clean package -DskipTests

echo "Starting Prometheus and Grafana..."
docker compose up -d

echo "Waiting for Prometheus..."
until curl -sf http://localhost:9090/-/ready > /dev/null 2>&1; do sleep 1; done
echo "Prometheus ready at http://localhost:9090"

echo "Waiting for Grafana..."
until curl -sf http://localhost:3000/api/health > /dev/null 2>&1; do sleep 1; done
echo "Grafana ready at http://localhost:3000 (admin/admin)"

echo "Starting metrics application (port 8888)..."
java -jar target/otel-prometheus-1.0.0.jar &
APP_PID=$!

sleep 5

echo ""
echo "=== Checking metrics endpoint ==="
curl -s http://localhost:8888/metrics | head -50

echo ""
echo ""
echo "=== Checking Prometheus targets ==="
curl -s http://localhost:9090/api/v1/targets | head -5

echo ""
echo ""
echo "=== Endpoints ==="
echo "Metrics:    http://localhost:8888/metrics"
echo "Prometheus: http://localhost:9090"
echo "Grafana:    http://localhost:3000 (admin/admin)"
echo ""
echo "Press Ctrl+C to stop..."

trap "kill $APP_PID 2>/dev/null; docker compose down; exit 0" INT TERM
wait $APP_PID
