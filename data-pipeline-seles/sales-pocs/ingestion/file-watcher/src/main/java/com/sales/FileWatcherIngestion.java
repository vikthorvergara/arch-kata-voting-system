package com.sales;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

public class FileWatcherIngestion {

    private static final String TOPIC = "sales.filewatcher";
    private static final Path WATCH_DIR = Paths.get("/tmp/filewatcher-input");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(WATCH_DIR);

        Properties kafkaProps = new Properties();
        kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        KafkaProducer<String, String> producer = new KafkaProducer<>(kafkaProps);

        processExistingFiles(producer);

        WatchService watchService = FileSystems.getDefault().newWatchService();
        WATCH_DIR.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

        System.out.println("File watcher started. Monitoring: " + WATCH_DIR);

        long startTime = System.currentTimeMillis();
        long timeoutMs = 30000;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            WatchKey key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS);
            if (key == null) continue;

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                    Path fileName = (Path) event.context();
                    if (fileName.toString().endsWith(".csv")) {
                        Path filePath = WATCH_DIR.resolve(fileName);
                        Thread.sleep(500);
                        processFile(filePath, producer);
                    }
                }
            }
            key.reset();
        }

        System.out.println("Shutting down after timeout.");
        producer.close();
        watchService.close();
    }

    private static void processExistingFiles(KafkaProducer<String, String> producer) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(WATCH_DIR, "*.csv")) {
            for (Path file : stream) {
                processFile(file, producer);
            }
        }
    }

    private static void processFile(Path filePath, KafkaProducer<String, String> producer) throws IOException {
        System.out.println("Processing file: " + filePath.getFileName());

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headers = headerLine.split(",");
            String line;
            int count = 0;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] values = line.split(",");
                StringBuilder json = new StringBuilder("{");
                for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                    if (i > 0) json.append(",");
                    json.append("\"").append(headers[i].trim()).append("\":\"").append(values[i].trim()).append("\"");
                }
                json.append("}");

                producer.send(new ProducerRecord<>(TOPIC, String.valueOf(count), json.toString()));
                System.out.println("Sent to Kafka: " + json);
                count++;
            }

            System.out.println("Processed " + count + " rows from " + filePath.getFileName());
        }
    }
}
