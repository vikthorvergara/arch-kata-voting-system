#!/bin/bash
set -e

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

POCS=(
    "ingestion/debezium-cdc"
    "ingestion/native-cdc"
    "ingestion/filepulse"
    "ingestion/file-watcher"
    "ingestion/camel-soap"
    "processing/kafka-streams"
    "processing/flink"
    "processing/spark"
    "lineage/datahub"
    "lineage/openmetadata"
    "lineage/amundsen"
    "observability/otel-prometheus"
    "observability/native-ui"
    "serving/spring-api"
)

if [ -n "$1" ]; then
    POC_DIR="$BASE_DIR/$1"
    if [ ! -f "$POC_DIR/run.sh" ]; then
        echo "ERROR: No run.sh found in $POC_DIR"
        exit 1
    fi
    echo "=== Running POC: $1 ==="
    cd "$POC_DIR" && bash run.sh
    exit 0
fi

echo "Available POCs:"
for i in "${!POCS[@]}"; do
    echo "  [$i] ${POCS[$i]}"
done

echo ""
echo "Usage:"
echo "  ./run-all-pocs.sh <poc-path>        # Run a specific POC"
echo "  ./run-all-pocs.sh ingestion/debezium-cdc"
echo "  ./run-all-pocs.sh processing/kafka-streams"
