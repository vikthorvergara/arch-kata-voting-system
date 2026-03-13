#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Postgres + Spring Boot Serving API POC ==="

echo "Setting up Postgres tables..."
PGPASSWORD=postgres psql -h localhost -U postgres -d sales_analytics -c "
CREATE TABLE IF NOT EXISTS top_sales_city (
    city VARCHAR(255) PRIMARY KEY,
    total_amount NUMERIC(15,2) NOT NULL,
    last_updated TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS top_salesman_country (
    salesman_name VARCHAR(255),
    country VARCHAR(100),
    total_amount NUMERIC(15,2) NOT NULL,
    last_updated TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (salesman_name, country)
);
"

echo "Seeding initial data..."
PGPASSWORD=postgres psql -h localhost -U postgres -d sales_analytics -c "
INSERT INTO top_sales_city (city, total_amount, last_updated) VALUES
    ('New York', 1250000.00, NOW()),
    ('Los Angeles', 980000.00, NOW()),
    ('Chicago', 875000.00, NOW()),
    ('Houston', 720000.00, NOW()),
    ('Phoenix', 650000.00, NOW())
ON CONFLICT (city) DO UPDATE SET total_amount = EXCLUDED.total_amount, last_updated = NOW();

INSERT INTO top_salesman_country (salesman_name, country, total_amount, last_updated) VALUES
    ('John Smith', 'US', 450000.00, NOW()),
    ('Jane Doe', 'US', 380000.00, NOW()),
    ('Carlos Garcia', 'MX', 320000.00, NOW()),
    ('Maria Silva', 'BR', 290000.00, NOW()),
    ('James Wilson', 'US', 275000.00, NOW()),
    ('Ana Martinez', 'MX', 260000.00, NOW()),
    ('Pedro Santos', 'BR', 240000.00, NOW())
ON CONFLICT (salesman_name, country) DO UPDATE SET total_amount = EXCLUDED.total_amount, last_updated = NOW();
"

echo "Creating Kafka topics if needed..."
kafka-topics.sh --bootstrap-server localhost:9092 --create --topic sales.top-city --partitions 1 --replication-factor 1 --if-not-exists 2>/dev/null || true
kafka-topics.sh --bootstrap-server localhost:9092 --create --topic sales.top-salesman --partitions 1 --replication-factor 1 --if-not-exists 2>/dev/null || true

echo "Building Spring Boot application..."
mvn -q clean package -DskipTests

echo "Starting Spring Boot application..."
java -jar target/spring-api-1.0.0.jar &
APP_PID=$!

echo "Waiting for application to start..."
until curl -sf http://localhost:8080/api/health > /dev/null 2>&1; do sleep 1; done

echo ""
echo "=== Testing Endpoints ==="

echo ""
echo "--- GET /api/health ---"
curl -s http://localhost:8080/api/health | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8080/api/health

echo ""
echo "--- GET /api/top-cities ---"
curl -s http://localhost:8080/api/top-cities | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8080/api/top-cities

echo ""
echo "--- GET /api/top-salesmen?country=US ---"
curl -s "http://localhost:8080/api/top-salesmen?country=US" | python3 -m json.tool 2>/dev/null || curl -s "http://localhost:8080/api/top-salesmen?country=US"

echo ""
echo "--- GET /api/top-salesmen (all countries) ---"
curl -s http://localhost:8080/api/top-salesmen | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8080/api/top-salesmen

echo ""
echo ""
echo "=== API running at http://localhost:8080 ==="
echo "Press Ctrl+C to stop..."

trap "kill $APP_PID 2>/dev/null; exit 0" INT TERM
wait $APP_PID
