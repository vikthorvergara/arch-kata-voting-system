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

docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --if-not-exists --topic sales.cdc --partitions 1 --replication-factor 1

rm -f /tmp/debezium-offsets.dat /tmp/debezium-schema-history.dat

docker exec postgres psql -U postgres -d sales_analytics -c "
INSERT INTO sales (salesman_name, city, country, amount) VALUES
('Alice Johnson', 'New York', 'USA', 15000.50),
('Bob Smith', 'London', 'UK', 22000.75),
('Carlos Ruiz', 'Madrid', 'Spain', 18500.00);
"

echo "Starting Debezium CDC ingestion..."
mvn -q compile exec:java -Dexec.mainClass="com.sales.DebeziumCdcIngestion" &
APP_PID=$!

sleep 5

docker exec postgres psql -U postgres -d sales_analytics -c "
INSERT INTO sales (salesman_name, city, country, amount) VALUES
('Diana Lee', 'Tokyo', 'Japan', 31000.25);
"

echo "Inserted new row. Waiting for CDC capture..."
sleep 10

docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic sales.cdc --from-beginning --timeout-ms 5000 2>/dev/null || true

kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true

echo "Debezium CDC POC completed."
