package com.sales;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ObservabilityApp {

    private static final Random RANDOM = new Random();
    private static final String[] TOPICS = {"sales.orders", "sales.payments", "sales.shipments"};
    private static final String[] CITIES = {"New York", "Los Angeles", "Chicago", "Houston", "Phoenix"};

    public static void main(String[] args) throws InterruptedException {
        Resource resource = Resource.builder()
                .put(AttributeKey.stringKey("service.name"), "sales-pipeline")
                .build();

        PrometheusHttpServer prometheusServer = PrometheusHttpServer.builder()
                .setPort(8888)
                .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(prometheusServer)
                .build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .build();

        Meter meter = sdk.getMeter("sales-pipeline-metrics");

        LongCounter messagesProcessed = meter.counterBuilder("sales_messages_processed_total")
                .setDescription("Total messages processed")
                .build();

        LongHistogram processingLatency = meter.histogramBuilder("sales_processing_latency_ms")
                .setDescription("Processing latency in milliseconds")
                .ofLongs()
                .build();

        LongCounter errorCount = meter.counterBuilder("sales_processing_errors_total")
                .setDescription("Total processing errors")
                .build();

        LongUpDownCounter consumerLag = meter.upDownCounterBuilder("sales_consumer_lag")
                .setDescription("Consumer lag per topic")
                .build();

        System.out.println("Metrics server started on port 8888");
        System.out.println("Prometheus can scrape at http://localhost:8888/metrics");
        System.out.println("Simulating Kafka Streams pipeline...");

        long iteration = 0;
        while (true) {
            for (String topic : TOPICS) {
                int batchSize = 10 + RANDOM.nextInt(90);
                Attributes topicAttr = Attributes.of(AttributeKey.stringKey("topic"), topic);

                messagesProcessed.add(batchSize, topicAttr);

                long latency = 5 + RANDOM.nextInt(95);
                processingLatency.record(latency, topicAttr);

                if (RANDOM.nextInt(100) < 3) {
                    errorCount.add(1, topicAttr);
                }

                int lagDelta = RANDOM.nextInt(20) - 10;
                consumerLag.add(lagDelta, topicAttr);

                for (String city : CITIES) {
                    Attributes cityAttr = Attributes.of(
                            AttributeKey.stringKey("topic"), topic,
                            AttributeKey.stringKey("city"), city
                    );
                    messagesProcessed.add(RANDOM.nextInt(10), cityAttr);
                }
            }

            iteration++;
            if (iteration % 10 == 0) {
                System.out.println("Iteration " + iteration + " - metrics being exported");
            }

            TimeUnit.SECONDS.sleep(1);
        }
    }
}
