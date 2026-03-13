#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

docker exec postgres psql -U postgres -d sales_analytics -c "
CREATE TABLE IF NOT EXISTS sales (
    id SERIAL PRIMARY KEY,
    salesman_name VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    country VARCHAR(255) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);
ALTER TABLE sales REPLICA IDENTITY FULL;
"

docker exec postgres psql -U postgres -d sales_analytics -c "
SELECT pg_drop_replication_slot('sales_native_slot') FROM pg_replication_slots WHERE slot_name = 'sales_native_slot';
" 2>/dev/null || true

docker exec postgres psql -U postgres -d sales_analytics -c "
DROP PUBLICATION IF EXISTS sales_pub;
" 2>/dev/null || true

docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --if-not-exists --topic sales.native-cdc --partitions 1 --replication-factor 1

echo "Starting Native CDC ingestion..."
mvn -q compile exec:java -Dexec.mainClass="com.sales.NativeCdcIngestion" &
APP_PID=$!

sleep 5

docker exec postgres psql -U postgres -d sales_analytics -c "
INSERT INTO sales (salesman_name, city, country, amount) VALUES
('Eve Wilson', 'Berlin', 'Germany', 27500.00),
('Frank Chen', 'Shanghai', 'China', 41000.50);
"

echo "Inserted rows. Waiting for WAL capture..."
sleep 10

docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic sales.native-cdc --from-beginning --timeout-ms 5000 2>/dev/null || true

kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true

echo "Native CDC POC completed."
