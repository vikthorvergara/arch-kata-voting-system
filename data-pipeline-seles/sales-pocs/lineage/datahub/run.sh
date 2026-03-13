#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "Starting DataHub services..."
docker compose up -d

echo "Waiting for DataHub GMS to be healthy..."
until curl -sf http://localhost:8080/health > /dev/null 2>&1; do
    sleep 1
done
echo "DataHub GMS is ready."

echo "Building Java lineage emitter..."
mvn -q clean package -DskipTests
java -cp target/datahub-lineage-1.0.0.jar com.sales.DataHubLineageEmitter

echo ""
echo "DataHub UI available at: http://localhost:9002"
echo "DataHub GMS API at: http://localhost:8080"
