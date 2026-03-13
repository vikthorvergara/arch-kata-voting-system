package com.sales;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Grouped;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NativeMetricsApp {

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "sales-native-metrics");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        props.put(StreamsConfig.METRICS_RECORDING_LEVEL_CONFIG, "DEBUG");
        props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams-native-metrics");

        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, String> salesStream = builder.stream("sales.events");

        salesStream
                .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                .count(Materialized.as("sales-count-store"));

        salesStream
                .mapValues(v -> v != null ? v : "unknown")
                .groupBy((k, v) -> v, Grouped.with(Serdes.String(), Serdes.String()))
                .count(Materialized.as("sales-by-value-store"));

        Topology topology = builder.build();
        System.out.println("=== Kafka Streams Topology ===");
        System.out.println(topology.describe());

        KafkaStreams streams = new KafkaStreams(topology, props);
        CountDownLatch latch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            streams.close();
            latch.countDown();
        }));

        streams.setStateListener((newState, oldState) ->
                System.out.println("State transition: " + oldState + " -> " + newState));

        streams.start();
        System.out.println("Kafka Streams started. JMX metrics available on port 9999.");

        var executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> queryJmxMetrics(), 5, 10, TimeUnit.SECONDS);

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executor.shutdown();
    }

    private static void queryJmxMetrics() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

            System.out.println("\n=== JMX Metrics Snapshot ===");

            printMetricsByPattern(mbs, "kafka.streams:type=stream-metrics,*",
                    "Stream Metrics");

            printMetricsByPattern(mbs, "kafka.streams:type=stream-thread-metrics,*",
                    "Thread Metrics");

            printMetricsByPattern(mbs, "kafka.streams:type=stream-task-metrics,*",
                    "Task Metrics");

            printMetricsByPattern(mbs, "kafka.streams:type=stream-state-metrics,*",
                    "State Store Metrics");

            printMetricsByPattern(mbs, "kafka.consumer:type=consumer-fetch-manager-metrics,*",
                    "Consumer Fetch Metrics");

            System.out.println("=== End Metrics Snapshot ===\n");
        } catch (Exception e) {
            System.err.println("Error querying JMX: " + e.getMessage());
        }
    }

    private static void printMetricsByPattern(MBeanServer mbs, String pattern, String section) {
        try {
            ObjectName query = new ObjectName(pattern);
            Set<ObjectName> names = mbs.queryNames(query, null);

            if (names.isEmpty()) return;

            System.out.println("\n--- " + section + " ---");

            for (ObjectName name : names) {
                var attributes = mbs.getMBeanInfo(name).getAttributes();
                for (var attr : attributes) {
                    try {
                        Object value = mbs.getAttribute(name, attr.getName());
                        if (value instanceof Number) {
                            double dVal = ((Number) value).doubleValue();
                            if (dVal != 0.0 && !Double.isNaN(dVal) && !Double.isInfinite(dVal)) {
                                System.out.printf("  %-50s = %.4f%n", attr.getName(), dVal);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error querying " + section + ": " + e.getMessage());
        }
    }
}
