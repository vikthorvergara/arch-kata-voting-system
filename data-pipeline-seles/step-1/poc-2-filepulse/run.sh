#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

dexec() { MSYS_NO_PATHCONV=1 docker exec "$@"; }

dexec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic sales.products --partitions 1 --replication-factor 1

echo "Waiting for Kafka Connect REST API..."
for i in $(seq 1 60); do
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/connectors 2>/dev/null | grep -q "200"; then
        break
    fi
    sleep 1
done

dexec kafka-connect mkdir -p /tmp/filepulse-input
MSYS_NO_PATHCONV=1 docker cp ../sample-data/products.csv kafka-connect:/tmp/filepulse-input/products.csv

echo "Deploying FilePulse connector..."
curl -s -X POST http://localhost:8083/connectors \
    -H "Content-Type: application/json" \
    -d @filepulse-connector.json | python3 -m json.tool 2>/dev/null || true

echo "Waiting for connector to process file..."
sleep 15

echo "=== Messages on sales.products ==="
dexec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic sales.products --from-beginning --timeout-ms 5000 --property print.key=true 2>/dev/null || true

echo "Cleaning up connector..."
curl -s -X DELETE http://localhost:8083/connectors/filepulse-products-connector 2>/dev/null || true

echo "FilePulse POC completed."
