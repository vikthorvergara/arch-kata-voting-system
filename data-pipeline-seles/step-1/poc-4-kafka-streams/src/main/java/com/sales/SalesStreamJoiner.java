package com.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.ValueJoiner;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class SalesStreamJoiner {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PAYMENTS_TOPIC = "sales.payments";
    private static final String PRODUCTS_TOPIC = "sales.products";
    private static final String SHIPPING_TOPIC = "sales.shipping";
    private static final String ENRICHED_TOPIC = "sales.enriched";

    public static void main(String[] args) throws InterruptedException {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "sales-stream-joiner");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
        props.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);

        StreamsBuilder builder = new StreamsBuilder();

        KTable<String, String> payments = builder.table(PAYMENTS_TOPIC,
                Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.as("payments-store"));

        KTable<String, String> products = builder.table(PRODUCTS_TOPIC,
                Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.as("products-store"));

        KTable<String, String> shipping = builder.table(SHIPPING_TOPIC,
                Consumed.with(Serdes.String(), Serdes.String()),
                Materialized.as("shipping-store"));

        ValueJoiner<String, String, String> paymentProductJoiner = (paymentJson, productJson) ->
                mergeJson(paymentJson, productJson);

        ValueJoiner<String, String, String> partialShippingJoiner = (partialJson, shippingJson) ->
                mergeJson(partialJson, shippingJson);

        KTable<String, String> paymentWithProduct = payments.leftJoin(products, paymentProductJoiner);

        KTable<String, String> enriched = paymentWithProduct.leftJoin(shipping, partialShippingJoiner);

        enriched.toStream()
                .peek((key, value) -> System.out.println("Enriched sale: " + key + " -> " + value))
                .to(ENRICHED_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        CountDownLatch latch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            streams.close();
            latch.countDown();
        }));

        streams.start();
        System.out.println("Sales Stream Joiner started. Joining payments + products + shipping -> enriched");
        latch.await();
    }

    private static String mergeJson(String base, String overlay) {
        if (overlay == null) return base;
        if (base == null) return overlay;
        try {
            ObjectNode baseNode = (ObjectNode) MAPPER.readTree(base);
            JsonNode overlayNode = MAPPER.readTree(overlay);
            overlayNode.fields().forEachRemaining(field -> baseNode.set(field.getKey(), field.getValue()));
            return MAPPER.writeValueAsString(baseNode);
        } catch (Exception e) {
            return base;
        }
    }
}
