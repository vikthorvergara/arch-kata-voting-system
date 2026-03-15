package com.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DebeziumCdcIngestion {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Properties kafkaProps = new Properties();
        kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProps);

        Properties debeziumProps = new Properties();
        debeziumProps.setProperty("name", "sales-cdc-connector");
        debeziumProps.setProperty("connector.class", "io.debezium.connector.postgresql.PostgresConnector");
        debeziumProps.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        debeziumProps.setProperty("offset.storage.file.filename", "/tmp/debezium-offsets.dat");
        debeziumProps.setProperty("offset.flush.interval.ms", "1000");
        debeziumProps.setProperty("database.hostname", "localhost");
        debeziumProps.setProperty("database.port", "5432");
        debeziumProps.setProperty("database.user", "postgres");
        debeziumProps.setProperty("database.password", "postgres");
        debeziumProps.setProperty("database.dbname", "sales_db");
        debeziumProps.setProperty("database.server.name", "sales");
        debeziumProps.setProperty("topic.prefix", "sales");
        debeziumProps.setProperty("table.include.list", "public.payments");
        debeziumProps.setProperty("plugin.name", "pgoutput");
        debeziumProps.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
        debeziumProps.setProperty("schema.history.internal.file.filename", "/tmp/debezium-schema-history.dat");
        debeziumProps.setProperty("decimal.handling.mode", "double");
        debeziumProps.setProperty("time.precision.mode", "connect");

        DebeziumEngine<ChangeEvent<String, String>> engine = DebeziumEngine.create(Json.class)
                .using(debeziumProps)
                .notifying(record -> {
                    if (record.value() == null) return;
                    try {
                        JsonNode envelope = MAPPER.readTree(record.value());
                        JsonNode after = envelope.path("payload").path("after");
                        if (after.isMissingNode() || after.isNull()) return;

                        ObjectNode payment = MAPPER.createObjectNode();
                        payment.put("sale_id", after.path("sale_id").asText());
                        payment.put("salesman_name", after.path("salesman_name").asText());
                        payment.put("city", after.path("city").asText());
                        payment.put("country", after.path("country").asText());
                        payment.put("amount", after.path("amount").asDouble());
                        payment.put("payment_status", after.path("payment_status").asText());
                        long epochMs = after.path("payment_date").asLong();
                        String isoDate = Instant.ofEpochMilli(epochMs)
                                .atOffset(ZoneOffset.UTC)
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                        payment.put("payment_date", isoDate);

                        String saleId = payment.get("sale_id").asText();
                        String json = MAPPER.writeValueAsString(payment);
                        System.out.println("CDC captured: " + json);
                        producer.send(new ProducerRecord<>("sales.payments", saleId, json));
                        producer.flush();
                    } catch (Exception e) {
                        System.err.println("Error processing CDC event: " + e.getMessage());
                    }
                })
                .build();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(engine);
        System.out.println("Debezium CDC engine started. Listening for changes on public.payments...");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                engine.close();
                producer.close();
                executor.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        Thread.sleep(60000);
        engine.close();
        producer.close();
        executor.shutdown();
    }
}
