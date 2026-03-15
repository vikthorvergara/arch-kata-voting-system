#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

dexec() { MSYS_NO_PATHCONV=1 docker exec "$@"; }

LIVE=false
if [ "${1}" = "--live" ]; then
    LIVE=true
fi

TOPICS="sales.payments sales.products sales.shipping sales.enriched"
for topic in $TOPICS; do
    dexec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic "$topic" --partitions 1 --replication-factor 1
done

if [ "$LIVE" = false ]; then
    echo "Seeding test data..."

    dexec -i kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic sales.payments --property "parse.key=true" --property "key.separator=|" <<'EOF'
550e8400-e29b-41d4-a716-446655440000|{"sale_id":"550e8400-e29b-41d4-a716-446655440000","salesman_name":"Alice Johnson","city":"New York","country":"US","amount":15000.50,"payment_status":"completed","payment_date":"2025-01-15T10:30:00Z"}
550e8400-e29b-41d4-a716-446655440001|{"sale_id":"550e8400-e29b-41d4-a716-446655440001","salesman_name":"Bob Smith","city":"London","country":"UK","amount":22000.75,"payment_status":"completed","payment_date":"2025-01-16T14:20:00Z"}
550e8400-e29b-41d4-a716-446655440002|{"sale_id":"550e8400-e29b-41d4-a716-446655440002","salesman_name":"Carlos Ruiz","city":"Madrid","country":"ES","amount":18500.00,"payment_status":"pending","payment_date":"2025-02-01T09:00:00Z"}
550e8400-e29b-41d4-a716-446655440003|{"sale_id":"550e8400-e29b-41d4-a716-446655440003","salesman_name":"Diana Lee","city":"Tokyo","country":"JP","amount":31000.25,"payment_status":"completed","payment_date":"2025-02-10T16:45:00Z"}
550e8400-e29b-41d4-a716-446655440004|{"sale_id":"550e8400-e29b-41d4-a716-446655440004","salesman_name":"Eve Wilson","city":"Berlin","country":"DE","amount":27500.00,"payment_status":"completed","payment_date":"2025-03-01T11:15:00Z"}
550e8400-e29b-41d4-a716-446655440005|{"sale_id":"550e8400-e29b-41d4-a716-446655440005","salesman_name":"Frank Chen","city":"Sao Paulo","country":"BR","amount":35000.00,"payment_status":"completed","payment_date":"2025-03-10T08:00:00Z"}
EOF

    dexec -i kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic sales.products --property "parse.key=true" --property "key.separator=|" <<'EOF'
550e8400-e29b-41d4-a716-446655440000|{"sale_id":"550e8400-e29b-41d4-a716-446655440000","product_name":"Enterprise License","quantity":5,"unit_price":3000.10,"category":"software"}
550e8400-e29b-41d4-a716-446655440001|{"sale_id":"550e8400-e29b-41d4-a716-446655440001","product_name":"Cloud Storage 1TB","quantity":10,"unit_price":150.00,"category":"infrastructure"}
550e8400-e29b-41d4-a716-446655440002|{"sale_id":"550e8400-e29b-41d4-a716-446655440002","product_name":"Security Suite","quantity":3,"unit_price":2500.00,"category":"software"}
550e8400-e29b-41d4-a716-446655440003|{"sale_id":"550e8400-e29b-41d4-a716-446655440003","product_name":"API Gateway","quantity":1,"unit_price":5000.00,"category":"infrastructure"}
550e8400-e29b-41d4-a716-446655440004|{"sale_id":"550e8400-e29b-41d4-a716-446655440004","product_name":"Support Plan Premium","quantity":2,"unit_price":1200.00,"category":"services"}
550e8400-e29b-41d4-a716-446655440005|{"sale_id":"550e8400-e29b-41d4-a716-446655440005","product_name":"Data Analytics Platform","quantity":1,"unit_price":8000.00,"category":"software"}
EOF

    dexec -i kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic sales.shipping --property "parse.key=true" --property "key.separator=|" <<'EOF'
550e8400-e29b-41d4-a716-446655440000|{"sale_id":"550e8400-e29b-41d4-a716-446655440000","shipping_method":"express","tracking_number":"TRK-12345","delivery_address":"123 Main St, New York, NY","estimated_delivery":"2025-01-20"}
550e8400-e29b-41d4-a716-446655440001|{"sale_id":"550e8400-e29b-41d4-a716-446655440001","shipping_method":"standard","tracking_number":"TRK-12346","delivery_address":"45 Baker St, London, UK","estimated_delivery":"2025-01-25"}
550e8400-e29b-41d4-a716-446655440002|{"sale_id":"550e8400-e29b-41d4-a716-446655440002","shipping_method":"express","tracking_number":"TRK-12347","delivery_address":"Calle Gran Via 10, Madrid, ES","estimated_delivery":"2025-02-10"}
550e8400-e29b-41d4-a716-446655440003|{"sale_id":"550e8400-e29b-41d4-a716-446655440003","shipping_method":"international","tracking_number":"TRK-12348","delivery_address":"1-2-3 Shibuya, Tokyo, JP","estimated_delivery":"2025-02-20"}
550e8400-e29b-41d4-a716-446655440004|{"sale_id":"550e8400-e29b-41d4-a716-446655440004","shipping_method":"standard","tracking_number":"TRK-12349","delivery_address":"Unter den Linden 5, Berlin, DE","estimated_delivery":"2025-03-10"}
550e8400-e29b-41d4-a716-446655440005|{"sale_id":"550e8400-e29b-41d4-a716-446655440005","shipping_method":"international","tracking_number":"TRK-12350","delivery_address":"Av Paulista 1000, Sao Paulo, BR","estimated_delivery":"2025-03-20"}
EOF

    echo "Test data seeded."
fi

echo "Starting Sales Stream Joiner..."
mvn -q compile exec:java -Dexec.mainClass="com.sales.SalesStreamJoiner" &
PROCESSOR_PID=$!

echo "Waiting for stream processing..."
sleep 15

echo "=== Enriched Sales on sales.enriched ==="
dexec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic sales.enriched --from-beginning --timeout-ms 10000 --property print.key=true 2>/dev/null || true

kill $PROCESSOR_PID 2>/dev/null || true
wait $PROCESSOR_PID 2>/dev/null || true

echo "Kafka Streams Joiner POC completed."
