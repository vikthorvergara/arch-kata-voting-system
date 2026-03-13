package com.sales;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DebeziumCdcIngestion {

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
        debeziumProps.setProperty("database.dbname", "sales_analytics");
        debeziumProps.setProperty("database.server.name", "sales");
        debeziumProps.setProperty("topic.prefix", "sales");
        debeziumProps.setProperty("table.include.list", "public.sales");
        debeziumProps.setProperty("plugin.name", "pgoutput");
        debeziumProps.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
        debeziumProps.setProperty("schema.history.internal.file.filename", "/tmp/debezium-schema-history.dat");

        DebeziumEngine<ChangeEvent<String, String>> engine = DebeziumEngine.create(Json.class)
                .using(debeziumProps)
                .notifying(record -> {
                    if (record.value() != null) {
                        System.out.println("CDC event captured: " + record.value().substring(0, Math.min(200, record.value().length())));
                        producer.send(new ProducerRecord<>("sales.cdc", record.key(), record.value()));
                    }
                })
                .build();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(engine);

        System.out.println("Debezium CDC engine started. Listening for changes on public.sales...");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                engine.close();
                producer.close();
                executor.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        Thread.sleep(30000);
        System.out.println("Shutting down after 30 seconds.");
        engine.close();
        producer.close();
        executor.shutdown();
    }
}
