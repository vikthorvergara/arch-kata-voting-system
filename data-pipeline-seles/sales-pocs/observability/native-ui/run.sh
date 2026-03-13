#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Engine-Native UIs POC (Kafka Streams JMX) ==="

echo "Building Java application..."
mvn -q clean package -DskipTests

echo "Creating Kafka topic if needed..."
kafka-topics.sh --bootstrap-server localhost:9092 --create --topic sales.events --partitions 3 --replication-factor 1 --if-not-exists 2>/dev/null || true

echo "Starting Kafka Streams with JMX on port 9999..."
java \
  -Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.port=9999 \
  -Dcom.sun.management.jmxremote.rmi.port=9999 \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false \
  -Dcom.sun.management.jmxremote.local.only=false \
  -Djava.rmi.server.hostname=localhost \
  -jar target/native-ui-1.0.0.jar &
APP_PID=$!

sleep 5

echo ""
echo "=== Producing test messages to sales.events ==="
for i in $(seq 1 20); do
  echo "city-$((i % 5)):sale-$i" | kafka-console-producer.sh --bootstrap-server localhost:9092 --topic sales.events --property "parse.key=true" --property "key.separator=:" 2>/dev/null || true
done
echo "Produced 20 test messages"

echo ""
echo "=== JMX Connection Info ==="
echo "JMX Port:     9999"
echo "JMX URL:      service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi"
echo ""
echo "Connect with JConsole: jconsole localhost:9999"
echo "Connect with VisualVM: add JMX connection to localhost:9999"
echo ""
echo "Key MBean paths:"
echo "  kafka.streams:type=stream-metrics"
echo "  kafka.streams:type=stream-thread-metrics"
echo "  kafka.streams:type=stream-task-metrics"
echo "  kafka.streams:type=stream-state-metrics"
echo ""
echo "Press Ctrl+C to stop..."

trap "kill $APP_PID 2>/dev/null; exit 0" INT TERM
wait $APP_PID
