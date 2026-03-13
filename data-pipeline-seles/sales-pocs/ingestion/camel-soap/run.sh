#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --if-not-exists --topic sales.soap --partitions 1 --replication-factor 1

echo "Starting Camel SOAP ingestion..."
mvn -q compile exec:java -Dexec.mainClass="com.sales.CamelSoapIngestion" &
APP_PID=$!

echo "Waiting for SOAP endpoint to start..."
for i in $(seq 1 30); do
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/sales?wsdl 2>/dev/null | grep -q "200"; then
        break
    fi
    sleep 1
done

echo "Sending SOAP request..."
curl -s -X POST http://localhost:8080/sales \
    -H "Content-Type: text/xml; charset=utf-8" \
    -H "SOAPAction: http://sales.com/submitSale" \
    -d '<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ws="http://sales.com/wsdl">
   <soapenv:Header/>
   <soapenv:Body>
      <ws:SubmitSaleRequest>
         <ws:salesmanName>Alice Johnson</ws:salesmanName>
         <ws:city>New York</ws:city>
         <ws:country>USA</ws:country>
         <ws:amount>15000.50</ws:amount>
      </ws:SubmitSaleRequest>
   </soapenv:Body>
</soapenv:Envelope>'

echo ""
echo "SOAP response received."

sleep 3

echo "Checking messages on sales.soap topic..."
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic sales.soap --from-beginning --timeout-ms 5000 2>/dev/null || true

kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true

echo "Camel SOAP POC completed."
