package com.sales;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.postgresql.PGConnection;
import org.postgresql.replication.LogSequenceNumber;
import org.postgresql.replication.PGReplicationStream;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class NativeCdcIngestion {

    public static void main(String[] args) throws Exception {
        Properties kafkaProps = new Properties();
        kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProps);

        String url = "jdbc:postgresql://localhost:5432/sales_analytics";
        Properties connProps = new Properties();
        connProps.setProperty("user", "postgres");
        connProps.setProperty("password", "postgres");
        connProps.setProperty("assumeMinServerVersion", "15");
        connProps.setProperty("replication", "database");

        Connection replConn = DriverManager.getConnection(url, connProps);
        PGConnection pgConn = replConn.unwrap(PGConnection.class);

        try (Connection setupConn = DriverManager.getConnection(url, "postgres", "postgres");
             Statement stmt = setupConn.createStatement()) {
            stmt.execute("SELECT pg_create_logical_replication_slot('sales_native_slot', 'pgoutput') " +
                    "WHERE NOT EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = 'sales_native_slot')");
            stmt.execute("CREATE PUBLICATION sales_pub FOR TABLE sales");
        } catch (Exception e) {
            if (!e.getMessage().contains("already exists")) {
                throw e;
            }
        }

        PGReplicationStream stream = pgConn.getReplicationAPI()
                .replicationStream()
                .logical()
                .withSlotName("sales_native_slot")
                .withSlotOption("proto_version", "1")
                .withSlotOption("publication_names", "sales_pub")
                .withStatusInterval(5, TimeUnit.SECONDS)
                .start();

        System.out.println("Native CDC stream started. Listening for WAL changes...");

        long startTime = System.currentTimeMillis();
        long timeoutMs = 30000;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            ByteBuffer buffer = stream.readPending();
            if (buffer == null) {
                Thread.sleep(100);
                continue;
            }

            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            String walData = formatWalMessage(bytes);

            if (!walData.isEmpty()) {
                System.out.println("WAL change captured: " + walData);
                producer.send(new ProducerRecord<>("sales.native-cdc", walData));
            }

            LogSequenceNumber lsn = stream.getLastReceiveLSN();
            stream.setFlushedLSN(lsn);
            stream.setAppliedLSN(lsn);
        }

        System.out.println("Shutting down after timeout.");
        stream.close();
        replConn.close();
        producer.close();
    }

    private static String formatWalMessage(byte[] data) {
        if (data.length == 0) return "";

        char msgType = (char) data[0];
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(msgType).append("\",\"raw\":\"");

        for (int i = 1; i < Math.min(data.length, 500); i++) {
            byte b = data[i];
            if (b >= 32 && b < 127) {
                if (b == '"' || b == '\\') sb.append('\\');
                sb.append((char) b);
            } else {
                sb.append("\\x").append(String.format("%02x", b));
            }
        }
        sb.append("\"}");
        return sb.toString();
    }
}
