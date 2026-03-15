#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

dexec() { MSYS_NO_PATHCONV=1 docker exec "$@"; }

dexec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic sales.shipping --partitions 1 --replication-factor 1

echo "Starting Camel SOAP shipping ingestion..."
mvn -q compile exec:java -Dexec.mainClass="com.sales.CamelSoapIngestion" &
APP_PID=$!

echo "Waiting for SOAP endpoint..."
for i in $(seq 1 30); do
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/shipping?wsdl 2>/dev/null | grep -q "200"; then
        break
    fi
    sleep 1
done

UUIDS=("550e8400-e29b-41d4-a716-446655440000" "550e8400-e29b-41d4-a716-446655440001" "550e8400-e29b-41d4-a716-446655440002" "550e8400-e29b-41d4-a716-446655440003" "550e8400-e29b-41d4-a716-446655440004" "550e8400-e29b-41d4-a716-446655440005")
METHODS=("express" "standard" "express" "international" "standard" "international")
TRACKING=("TRK-12345" "TRK-12346" "TRK-12347" "TRK-12348" "TRK-12349" "TRK-12350")
ADDRESSES=("123 Main St, New York, NY" "45 Baker St, London, UK" "Calle Gran Via 10, Madrid, ES" "1-2-3 Shibuya, Tokyo, JP" "Unter den Linden 5, Berlin, DE" "Av Paulista 1000, Sao Paulo, BR")
DELIVERIES=("2025-01-20" "2025-01-25" "2025-02-10" "2025-02-20" "2025-03-10" "2025-03-20")

for i in $(seq 0 5); do
    echo "Sending shipping request ${UUIDS[$i]}..."
    curl -s -X POST http://localhost:8080/shipping \
        -H "Content-Type: text/xml; charset=utf-8" \
        -H "SOAPAction: http://sales.com/submitShipping" \
        -d "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ws=\"http://sales.com/wsdl\">
   <soapenv:Header/>
   <soapenv:Body>
      <ws:SubmitShippingRequest>
         <ws:saleId>${UUIDS[$i]}</ws:saleId>
         <ws:shippingMethod>${METHODS[$i]}</ws:shippingMethod>
         <ws:trackingNumber>${TRACKING[$i]}</ws:trackingNumber>
         <ws:deliveryAddress>${ADDRESSES[$i]}</ws:deliveryAddress>
         <ws:estimatedDelivery>${DELIVERIES[$i]}</ws:estimatedDelivery>
      </ws:SubmitShippingRequest>
   </soapenv:Body>
</soapenv:Envelope>"
    echo ""
done

sleep 3

echo "=== Messages on sales.shipping ==="
dexec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic sales.shipping --from-beginning --timeout-ms 5000 --property print.key=true 2>/dev/null || true

kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true

echo "Camel SOAP POC completed."
