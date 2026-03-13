package com.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class KafkaStreamsProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String INPUT_TOPIC = "sales.raw";
    private static final String TOP_CITY_TOPIC = "sales.top-city";
    private static final String TOP_SALESMAN_TOPIC = "sales.top-salesman";

    public static void main(String[] args) throws InterruptedException {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "sales-streams-processor");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> salesStream = builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        salesStream
                .groupBy((key, value) -> extractField(value, "city"), Grouped.with(Serdes.String(), Serdes.String()))
                .aggregate(
                        () -> "0.0",
                        (city, sale, currentTotal) -> {
                            double total = Double.parseDouble(currentTotal) + extractAmount(sale);
                            return String.valueOf(total);
                        },
                        Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as("city-sales-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.String())
                )
                .toStream()
                .mapValues((city, total) -> "{\"city\":\"" + city + "\",\"total_amount\":" + total + "}")
                .to(TOP_CITY_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        salesStream
                .groupBy((key, value) -> extractField(value, "country") + "|" + extractField(value, "salesman_name"),
                        Grouped.with(Serdes.String(), Serdes.String()))
                .aggregate(
                        () -> "0.0",
                        (compositeKey, sale, currentTotal) -> {
                            double total = Double.parseDouble(currentTotal) + extractAmount(sale);
                            return String.valueOf(total);
                        },
                        Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as("salesman-sales-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.String())
                )
                .toStream()
                .mapValues((compositeKey, total) -> {
                    String[] parts = compositeKey.split("\\|");
                    return "{\"country\":\"" + parts[0] + "\",\"salesman_name\":\"" + parts[1] + "\",\"total_amount\":" + total + "}";
                })
                .to(TOP_SALESMAN_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        CountDownLatch latch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            streams.close();
            latch.countDown();
        }));

        streams.start();
        System.out.println("Kafka Streams processor started");
        latch.await();
    }

    private static String extractField(String json, String field) {
        try {
            JsonNode node = MAPPER.readTree(json);
            return node.get(field).asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static double extractAmount(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            return node.get("amount").asDouble();
        } catch (Exception e) {
            return 0.0;
        }
    }
}
