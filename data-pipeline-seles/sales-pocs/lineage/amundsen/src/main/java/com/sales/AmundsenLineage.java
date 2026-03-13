package com.sales;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AmundsenLineage {

    private static final String METADATA_URL = "http://localhost:5002";
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        System.out.println("Registering data assets and lineage in Amundsen...");

        registerTable("postgres://sales_analytics.public/sales", "sales", "sales_analytics",
                "public", "postgres", new String[][]{
                        {"id", "bigint"}, {"salesman_name", "varchar"}, {"city", "varchar"},
                        {"country", "varchar"}, {"amount", "decimal"}, {"created_at", "timestamp"}
                });

        registerTable("postgres://sales_analytics.public/top_sales_city", "top_sales_city",
                "sales_analytics", "public", "postgres", new String[][]{
                        {"city", "varchar"}, {"total_amount", "decimal"}, {"sale_count", "bigint"}
                });

        registerTable("postgres://sales_analytics.public/top_salesman_country", "top_salesman_country",
                "sales_analytics", "public", "postgres", new String[][]{
                        {"salesman_name", "varchar"}, {"country", "varchar"}, {"total_amount", "decimal"}
                });

        registerTable("kafka://sales_cluster/sales.raw", "sales.raw",
                "sales_cluster", "default", "kafka", new String[][]{});

        registerTable("kafka://sales_cluster/sales.top-city", "sales.top-city",
                "sales_cluster", "default", "kafka", new String[][]{});

        registerTable("kafka://sales_cluster/sales.top-salesman", "sales.top-salesman",
                "sales_cluster", "default", "kafka", new String[][]{});

        addLineage("postgres://sales_analytics.public/sales",
                "kafka://sales_cluster/sales.raw", "cdc_ingestion");

        addLineage("kafka://sales_cluster/sales.raw",
                "kafka://sales_cluster/sales.top-city", "streams_aggregation");

        addLineage("kafka://sales_cluster/sales.raw",
                "kafka://sales_cluster/sales.top-salesman", "streams_aggregation");

        addLineage("kafka://sales_cluster/sales.top-city",
                "postgres://sales_analytics.public/top_sales_city", "sink_connector");

        addLineage("kafka://sales_cluster/sales.top-salesman",
                "postgres://sales_analytics.public/top_salesman_country", "sink_connector");

        System.out.println("\nQuerying registered tables...");
        queryTable("postgres://sales_analytics.public/sales");

        System.out.println("\nAll lineage registered successfully.");
    }

    private static void registerTable(String key, String tableName, String database,
                                      String schema, String clusterName, String[][] columns) throws Exception {
        StringBuilder colArray = new StringBuilder("[");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) colArray.append(",");
            colArray.append("""
                    {"name": "%s", "col_type": "%s", "sort_order": %d}""".formatted(
                    columns[i][0], columns[i][1], i));
        }
        colArray.append("]");

        String body = """
                {
                  "tableKey": "%s",
                  "tableName": "%s",
                  "database": "%s",
                  "schema": "%s",
                  "cluster": "%s",
                  "columns": %s,
                  "tags": [],
                  "description": "Sales pipeline asset"
                }
                """.formatted(key, tableName, database, schema, clusterName, colArray);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(METADATA_URL + "/table"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Registered table [" + tableName + "] status=" + response.statusCode());
    }

    private static void addLineage(String upstreamKey, String downstreamKey, String jobName) throws Exception {
        String body = """
                {
                  "upstream_entity_key": {
                    "table_key": "%s"
                  },
                  "downstream_entity_key": {
                    "table_key": "%s"
                  },
                  "transformation_type": "dataflow",
                  "job_name": "%s"
                }
                """.formatted(upstreamKey, downstreamKey, jobName);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(METADATA_URL + "/lineage"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Added lineage [" + upstreamKey + " -> " + downstreamKey + "] status=" + response.statusCode());

        if (response.statusCode() >= 400) {
            addLineageViaNeo4j(upstreamKey, downstreamKey, jobName);
        }
    }

    private static void addLineageViaNeo4j(String upstreamKey, String downstreamKey, String jobName) throws Exception {
        String cypher = """
                {
                  "statements": [{
                    "statement": "MERGE (u:Table {key: '%s'}) MERGE (d:Table {key: '%s'}) MERGE (u)-[:HAS_DOWNSTREAM {job_name: '%s'}]->(d) MERGE (d)-[:HAS_UPSTREAM {job_name: '%s'}]->(u)"
                  }]
                }
                """.formatted(upstreamKey, downstreamKey, jobName, jobName);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:7474/db/data/transaction/commit"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("neo4j:test1234".getBytes()))
                .POST(HttpRequest.BodyPublishers.ofString(cypher))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("  Fallback Neo4j lineage status=" + response.statusCode());
    }

    private static void queryTable(String tableKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(METADATA_URL + "/table?key=" + tableKey))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Table query result: " + response.body());
    }
}
