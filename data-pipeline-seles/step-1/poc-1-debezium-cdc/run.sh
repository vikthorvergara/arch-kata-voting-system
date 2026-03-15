#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

dexec() { MSYS_NO_PATHCONV=1 docker exec "$@"; }

dexec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic sales.payments --partitions 1 --replication-factor 1

rm -f /tmp/debezium-offsets.dat /tmp/debezium-schema-history.dat D:/tmp/debezium-offsets.dat D:/tmp/debezium-schema-history.dat

dexec postgres psql -U postgres -d sales_db -c "
INSERT INTO payments (sale_id, salesman_name, city, country, amount, payment_status, payment_date) VALUES
('550e8400-e29b-41d4-a716-446655440000', 'Alice Johnson', 'New York', 'US', 15000.50, 'completed', '2025-01-15 10:30:00'),
('550e8400-e29b-41d4-a716-446655440001', 'Bob Smith', 'London', 'UK', 22000.75, 'completed', '2025-01-16 14:20:00'),
('550e8400-e29b-41d4-a716-446655440002', 'Carlos Ruiz', 'Madrid', 'ES', 18500.00, 'pending', '2025-02-01 09:00:00'),
('550e8400-e29b-41d4-a716-446655440003', 'Diana Lee', 'Tokyo', 'JP', 31000.25, 'completed', '2025-02-10 16:45:00'),
('550e8400-e29b-41d4-a716-446655440004', 'Eve Wilson', 'Berlin', 'DE', 27500.00, 'completed', '2025-03-01 11:15:00')
ON CONFLICT (sale_id) DO NOTHING;
"

echo "Starting Debezium CDC ingestion..."
mvn -q compile exec:java -Dexec.mainClass="com.sales.DebeziumCdcIngestion" &
APP_PID=$!

echo "Waiting for CDC engine to initialize..."
sleep 10

echo "Inserting live payment..."
dexec postgres psql -U postgres -d sales_db -c "
INSERT INTO payments (sale_id, salesman_name, city, country, amount, payment_status, payment_date) VALUES
('550e8400-e29b-41d4-a716-446655440005', 'Frank Chen', 'Sao Paulo', 'BR', 35000.00, 'completed', '2025-03-10 08:00:00')
ON CONFLICT (sale_id) DO NOTHING;
"

echo "Waiting for CDC capture..."
sleep 10

echo "=== Messages on sales.payments ==="
dexec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic sales.payments --from-beginning --timeout-ms 5000 2>/dev/null || true

kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true

echo "Debezium CDC POC completed."
