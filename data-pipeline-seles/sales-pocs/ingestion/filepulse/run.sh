#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

FILEPULSE_VERSION="2.15.0"
PLUGIN_DIR="/tmp/kafka-connect-plugins"
FILEPULSE_DIR="$PLUGIN_DIR/kafka-connect-filepulse"
INPUT_DIR="/tmp/filepulse-input"
KAFKA_HOME="/tmp/kafka-connect-standalone"
KAFKA_VERSION="3.8.1"
KAFKA_TGZ="kafka_2.13-${KAFKA_VERSION}.tgz"

docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --if-not-exists --topic sales.file --partitions 1 --replication-factor 1

mkdir -p "$PLUGIN_DIR" "$INPUT_DIR" "$KAFKA_HOME"

if [ ! -d "$FILEPULSE_DIR" ]; then
    echo "Downloading FilePulse connector..."
    curl -sL "https://github.com/streamthoughts/kafka-connect-file-pulse/releases/download/v${FILEPULSE_VERSION}/streamthoughts-kafka-connect-file-pulse-${FILEPULSE_VERSION}.zip" -o /tmp/filepulse.zip
    unzip -qo /tmp/filepulse.zip -d "$PLUGIN_DIR"
    mv "$PLUGIN_DIR/streamthoughts-kafka-connect-file-pulse-${FILEPULSE_VERSION}" "$FILEPULSE_DIR" 2>/dev/null || true
    rm -f /tmp/filepulse.zip
fi

if [ ! -f "$KAFKA_HOME/bin/connect-standalone.sh" ]; then
    echo "Downloading Kafka for Connect standalone..."
    curl -sL "https://downloads.apache.org/kafka/${KAFKA_VERSION}/${KAFKA_TGZ}" -o "/tmp/${KAFKA_TGZ}"
    tar -xzf "/tmp/${KAFKA_TGZ}" -C /tmp
    mv "/tmp/kafka_2.13-${KAFKA_VERSION}"/* "$KAFKA_HOME/"
    rm -f "/tmp/${KAFKA_TGZ}"
fi

rm -f /tmp/connect-offsets.dat

cp sample-data/sales.csv "$INPUT_DIR/sales.csv"

echo "Starting Kafka Connect standalone with FilePulse..."
"$KAFKA_HOME/bin/connect-standalone.sh" \
    "$SCRIPT_DIR/connect-standalone.properties" \
    "$SCRIPT_DIR/filepulse-connector.properties" &
CONNECT_PID=$!

sleep 15

echo "Checking messages on sales.file topic..."
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic sales.file --from-beginning --timeout-ms 5000 2>/dev/null || true

kill $CONNECT_PID 2>/dev/null || true
wait $CONNECT_PID 2>/dev/null || true

echo "FilePulse POC completed."
