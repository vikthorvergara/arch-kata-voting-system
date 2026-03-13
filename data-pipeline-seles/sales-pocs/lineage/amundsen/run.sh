#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "Starting Amundsen services..."
docker compose up -d

echo "Waiting for Neo4j to be healthy..."
until curl -sf http://localhost:7474 > /dev/null 2>&1; do
    sleep 1
done
echo "Neo4j is ready."

echo "Waiting for Amundsen Metadata service..."
until curl -sf http://localhost:5002/healthcheck > /dev/null 2>&1; do
    sleep 1
done
echo "Amundsen Metadata service is ready."

echo "Building Java lineage emitter..."
mvn -q clean package -DskipTests
java -cp target/amundsen-lineage-1.0.0.jar com.sales.AmundsenLineage

echo ""
echo "Amundsen Frontend available at: http://localhost:5000"
echo "Neo4j Browser available at: http://localhost:7474"
