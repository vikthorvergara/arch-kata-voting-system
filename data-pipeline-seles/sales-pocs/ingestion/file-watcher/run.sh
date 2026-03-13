#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

INPUT_DIR="/tmp/filewatcher-input"
rm -rf "$INPUT_DIR"
mkdir -p "$INPUT_DIR"

docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --if-not-exists --topic sales.filewatcher --partitions 1 --replication-factor 1

echo "Starting File Watcher ingestion..."
mvn -q compile exec:java -Dexec.mainClass="com.sales.FileWatcherIngestion" &
APP_PID=$!

sleep 3

cat > "$INPUT_DIR/sales_batch1.csv" <<'CSV'
salesman_name,city,country,amount,created_at
Alice Johnson,New York,USA,15000.50,2025-01-15 10:30:00
Bob Smith,London,UK,22000.75,2025-01-16 14:20:00
Carlos Ruiz,Madrid,Spain,18500.00,2025-02-01 09:00:00
CSV

echo "Dropped sales_batch1.csv into watched directory."
sleep 3

cat > "$INPUT_DIR/sales_batch2.csv" <<'CSV'
salesman_name,city,country,amount,created_at
Diana Lee,Tokyo,Japan,31000.25,2025-02-10 16:45:00
Eve Wilson,Berlin,Germany,27500.00,2025-03-01 11:15:00
CSV

echo "Dropped sales_batch2.csv into watched directory."
sleep 5

echo "Checking messages on sales.filewatcher topic..."
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic sales.filewatcher --from-beginning --timeout-ms 5000 2>/dev/null || true

kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true

echo "File Watcher POC completed."
