#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "Starting OpenMetadata services..."
docker compose up -d

echo "Waiting for OpenMetadata to be healthy..."
until curl -sf http://localhost:8585/api/v1/system/version > /dev/null 2>&1; do
    sleep 1
done
echo "OpenMetadata is ready."

echo "Building Java lineage emitter..."
mvn -q clean package -DskipTests
java -cp target/openmetadata-lineage-1.0.0.jar com.sales.OpenMetadataLineage

echo ""
echo "OpenMetadata UI available at: http://localhost:8585"
