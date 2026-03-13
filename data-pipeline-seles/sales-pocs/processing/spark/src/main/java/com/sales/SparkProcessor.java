package com.sales;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.Trigger;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;

import static org.apache.spark.sql.functions.*;

public class SparkProcessor {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String INPUT_TOPIC = "sales.raw";
    private static final String TOP_CITY_TOPIC = "sales.top-city";
    private static final String TOP_SALESMAN_TOPIC = "sales.top-salesman";

    public static void main(String[] args) throws Exception {
        SparkSession spark = SparkSession.builder()
                .appName("SalesProcessor")
                .master("local[*]")
                .config("spark.sql.shuffle.partitions", "2")
                .config("spark.ui.enabled", "false")
                .getOrCreate();

        spark.sparkContext().setLogLevel("WARN");

        StructType schema = new StructType()
                .add("salesman_name", DataTypes.StringType)
                .add("city", DataTypes.StringType)
                .add("country", DataTypes.StringType)
                .add("amount", DataTypes.DoubleType)
                .add("timestamp", DataTypes.StringType);

        Dataset<Row> rawStream = spark.readStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", BOOTSTRAP_SERVERS)
                .option("subscribe", INPUT_TOPIC)
                .option("startingOffsets", "earliest")
                .load();

        Dataset<Row> salesData = rawStream
                .selectExpr("CAST(value AS STRING) as json_str")
                .select(from_json(col("json_str"), schema).as("data"))
                .select("data.*")
                .withColumn("event_time", current_timestamp())
                .withWatermark("event_time", "10 seconds");

        Dataset<Row> cityAgg = salesData
                .groupBy(
                        window(col("event_time"), "10 seconds"),
                        col("city"))
                .agg(sum("amount").as("total_amount"))
                .select(
                        col("city").as("key"),
                        to_json(struct(col("city"), col("total_amount"))).as("value"));

        Dataset<Row> salesmanAgg = salesData
                .groupBy(
                        window(col("event_time"), "10 seconds"),
                        col("country"),
                        col("salesman_name"))
                .agg(sum("amount").as("total_amount"))
                .select(
                        concat(col("country"), lit("|"), col("salesman_name")).as("key"),
                        to_json(struct(col("country"), col("salesman_name"), col("total_amount"))).as("value"));

        StreamingQuery cityQuery = cityAgg.writeStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", BOOTSTRAP_SERVERS)
                .option("topic", TOP_CITY_TOPIC)
                .option("checkpointLocation", "/tmp/spark-checkpoints/city-sales")
                .outputMode("update")
                .trigger(Trigger.ProcessingTime("5 seconds"))
                .start();

        StreamingQuery salesmanQuery = salesmanAgg.writeStream()
                .format("kafka")
                .option("kafka.bootstrap.servers", BOOTSTRAP_SERVERS)
                .option("topic", TOP_SALESMAN_TOPIC)
                .option("checkpointLocation", "/tmp/spark-checkpoints/salesman-sales")
                .outputMode("update")
                .trigger(Trigger.ProcessingTime("5 seconds"))
                .start();

        System.out.println("Spark Structured Streaming processor started");

        spark.streams().awaitAnyTermination();
    }
}
