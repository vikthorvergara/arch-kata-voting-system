#!/bin/bash
set -e

KAFKA_CONTAINER="kafka"
INPUT_TOPIC="sales.raw"
TOP_CITY_TOPIC="sales.top-city"
TOP_SALESMAN_TOPIC="sales.top-salesman"

echo "=== Kafka Streams Processing POC ==="

echo "Creating topics..."
for topic in $INPUT_TOPIC $TOP_CITY_TOPIC $TOP_SALESMAN_TOPIC; do
  docker exec $KAFKA_CONTAINER kafka-topics --bootstrap-server localhost:9092 --create --topic $topic --partitions 1 --replication-factor 1 --if-not-exists 2>/dev/null || true
done

echo "Seeding test data..."
docker exec -i $KAFKA_CONTAINER kafka-console-producer --bootstrap-server localhost:9092 --topic $INPUT_TOPIC <<'EOF'
{"salesman_name":"Alice","city":"New York","country":"US","amount":1500.00}
{"salesman_name":"Bob","city":"London","country":"UK","amount":2300.00}
{"salesman_name":"Alice","city":"New York","country":"US","amount":800.00}
{"salesman_name":"Carlos","city":"Madrid","country":"ES","amount":3100.00}
{"salesman_name":"Bob","city":"London","country":"UK","amount":1200.00}
{"salesman_name":"Diana","city":"New York","country":"US","amount":4500.00}
{"salesman_name":"Carlos","city":"Barcelona","country":"ES","amount":2200.00}
{"salesman_name":"Eve","city":"London","country":"UK","amount":1800.00}
EOF

echo "Starting Kafka Streams processor in background..."
mvn -q compile exec:java -Dexec.mainClass="com.sales.KafkaStreamsProcessor" &
PROCESSOR_PID=$!

echo "Waiting for processing to complete..."
sleep 15

echo ""
echo "=== Top Sales per City ==="
docker exec $KAFKA_CONTAINER kafka-console-consumer --bootstrap-server localhost:9092 --topic $TOP_CITY_TOPIC --from-beginning --timeout-ms 10000 2>/dev/null || true

echo ""
echo "=== Top Salesman per Country ==="
docker exec $KAFKA_CONTAINER kafka-console-consumer --bootstrap-server localhost:9092 --topic $TOP_SALESMAN_TOPIC --from-beginning --timeout-ms 10000 2>/dev/null || true

echo ""
echo "Stopping processor..."
kill $PROCESSOR_PID 2>/dev/null || true
wait $PROCESSOR_PID 2>/dev/null || true

echo "Done."
