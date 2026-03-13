package com.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

public class FlinkProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String INPUT_TOPIC = "sales.raw";
    private static final String TOP_CITY_TOPIC = "sales.top-city";
    private static final String TOP_SALESMAN_TOPIC = "sales.top-salesman";

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(BOOTSTRAP_SERVERS)
                .setTopics(INPUT_TOPIC)
                .setGroupId("flink-sales-processor")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> salesStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-source");

        KafkaSink<String> citySink = KafkaSink.<String>builder()
                .setBootstrapServers(BOOTSTRAP_SERVERS)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(TOP_CITY_TOPIC)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        KafkaSink<String> salesmanSink = KafkaSink.<String>builder()
                .setBootstrapServers(BOOTSTRAP_SERVERS)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(TOP_SALESMAN_TOPIC)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        salesStream
                .map(value -> {
                    JsonNode node = MAPPER.readTree(value);
                    return new Tuple2<>(node.get("city").asText(), node.get("amount").asDouble());
                })
                .returns(org.apache.flink.api.common.typeinfo.Types.TUPLE(
                        org.apache.flink.api.common.typeinfo.Types.STRING,
                        org.apache.flink.api.common.typeinfo.Types.DOUBLE))
                .keyBy(t -> t.f0)
                .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                .aggregate(new CityAggregator())
                .map(t -> "{\"city\":\"" + t.f0 + "\",\"total_amount\":" + t.f1 + "}")
                .sinkTo(citySink);

        salesStream
                .map(value -> {
                    JsonNode node = MAPPER.readTree(value);
                    return new Tuple3<>(
                            node.get("country").asText(),
                            node.get("salesman_name").asText(),
                            node.get("amount").asDouble());
                })
                .returns(org.apache.flink.api.common.typeinfo.Types.TUPLE(
                        org.apache.flink.api.common.typeinfo.Types.STRING,
                        org.apache.flink.api.common.typeinfo.Types.STRING,
                        org.apache.flink.api.common.typeinfo.Types.DOUBLE))
                .keyBy(t -> t.f0 + "|" + t.f1)
                .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                .aggregate(new SalesmanAggregator())
                .map(t -> "{\"country\":\"" + t.f0 + "\",\"salesman_name\":\"" + t.f1 + "\",\"total_amount\":" + t.f2 + "}")
                .sinkTo(salesmanSink);

        System.out.println("Flink processor started");
        env.execute("Sales Processing Pipeline");
    }

    public static class CityAggregator implements AggregateFunction<Tuple2<String, Double>, Tuple2<String, Double>, Tuple2<String, Double>> {
        @Override
        public Tuple2<String, Double> createAccumulator() {
            return new Tuple2<>("", 0.0);
        }

        @Override
        public Tuple2<String, Double> add(Tuple2<String, Double> value, Tuple2<String, Double> acc) {
            return new Tuple2<>(value.f0, acc.f1 + value.f1);
        }

        @Override
        public Tuple2<String, Double> getResult(Tuple2<String, Double> acc) {
            return acc;
        }

        @Override
        public Tuple2<String, Double> merge(Tuple2<String, Double> a, Tuple2<String, Double> b) {
            return new Tuple2<>(a.f0, a.f1 + b.f1);
        }
    }

    public static class SalesmanAggregator implements AggregateFunction<Tuple3<String, String, Double>, Tuple3<String, String, Double>, Tuple3<String, String, Double>> {
        @Override
        public Tuple3<String, String, Double> createAccumulator() {
            return new Tuple3<>("", "", 0.0);
        }

        @Override
        public Tuple3<String, String, Double> add(Tuple3<String, String, Double> value, Tuple3<String, String, Double> acc) {
            return new Tuple3<>(value.f0, value.f1, acc.f2 + value.f2);
        }

        @Override
        public Tuple3<String, String, Double> getResult(Tuple3<String, String, Double> acc) {
            return acc;
        }

        @Override
        public Tuple3<String, String, Double> merge(Tuple3<String, String, Double> a, Tuple3<String, String, Double> b) {
            return new Tuple3<>(a.f0, a.f1, a.f2 + b.f2);
        }
    }
}
