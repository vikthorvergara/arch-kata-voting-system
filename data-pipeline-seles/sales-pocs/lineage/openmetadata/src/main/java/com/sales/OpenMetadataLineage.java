package com.sales;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OpenMetadataLineage {

    private static final String BASE_URL = "http://localhost:8585/api/v1";
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        System.out.println("Registering services and lineage in OpenMetadata...");

        String postgresServiceId = createDatabaseService("sales-postgres", "Postgres");
        String postgresDbId = createDatabase(postgresServiceId, "sales_analytics");
        String schemaId = createDatabaseSchema(postgresDbId, "public");

        String salesTableId = createTable(schemaId, "sales", new String[][]{
                {"id", "BIGINT"}, {"salesman_name", "VARCHAR"}, {"city", "VARCHAR"},
                {"country", "VARCHAR"}, {"amount", "DECIMAL"}, {"created_at", "TIMESTAMP"}
        });

        String topCityTableId = createTable(schemaId, "top_sales_city", new String[][]{
                {"city", "VARCHAR"}, {"total_amount", "DECIMAL"}, {"sale_count", "BIGINT"}
        });

        String topSalesmanTableId = createTable(schemaId, "top_salesman_country", new String[][]{
                {"salesman_name", "VARCHAR"}, {"country", "VARCHAR"}, {"total_amount", "DECIMAL"}
        });

        String kafkaServiceId = createMessagingService("sales-kafka");

        String rawTopicId = createTopic(kafkaServiceId, "sales.raw");
        String topCityTopicId = createTopic(kafkaServiceId, "sales.top-city");
        String topSalesmanTopicId = createTopic(kafkaServiceId, "sales.top-salesman");

        addLineage(salesTableId, "table", rawTopicId, "topic");
        addLineage(rawTopicId, "topic", topCityTopicId, "topic");
        addLineage(rawTopicId, "topic", topSalesmanTopicId, "topic");
        addLineage(topCityTopicId, "topic", topCityTableId, "table");
        addLineage(topSalesmanTopicId, "topic", topSalesmanTableId, "table");

        System.out.println("\nQuerying lineage for sales table...");
        queryLineage(salesTableId, "table");

        System.out.println("\nAll lineage registered successfully.");
    }

    private static String createDatabaseService(String name, String type) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "serviceType": "%s",
                  "connection": {
                    "config": {
                      "type": "%s",
                      "hostPort": "localhost:5432",
                      "username": "postgres",
                      "database": "sales_analytics",
                      "authType": {"password": "postgres"}
                    }
                  }
                }
                """.formatted(name, type, type);

        return postAndGetId("/services/databaseServices", body, "database service [" + name + "]");
    }

    private static String createDatabase(String serviceId, String name) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "service": "sales-postgres"
                }
                """.formatted(name);

        return postAndGetId("/databases", body, "database [" + name + "]");
    }

    private static String createDatabaseSchema(String databaseId, String name) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "database": "sales-postgres.sales_analytics"
                }
                """.formatted(name);

        return postAndGetId("/databaseSchemas", body, "schema [" + name + "]");
    }

    private static String createTable(String schemaId, String name, String[][] columns) throws Exception {
        StringBuilder cols = new StringBuilder("[");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) cols.append(",");
            cols.append("""
                    {"name": "%s", "dataType": "%s"}""".formatted(columns[i][0], columns[i][1]));
        }
        cols.append("]");

        String body = """
                {
                  "name": "%s",
                  "databaseSchema": "sales-postgres.sales_analytics.public",
                  "columns": %s
                }
                """.formatted(name, cols);

        return postAndGetId("/tables", body, "table [" + name + "]");
    }

    private static String createMessagingService(String name) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "serviceType": "Kafka",
                  "connection": {
                    "config": {
                      "type": "Kafka",
                      "bootstrapServers": "localhost:9092"
                    }
                  }
                }
                """.formatted(name);

        return postAndGetId("/services/messagingServices", body, "messaging service [" + name + "]");
    }

    private static String createTopic(String serviceId, String name) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "service": "sales-kafka",
                  "partitions": 1
                }
                """.formatted(name);

        return postAndGetId("/topics", body, "topic [" + name + "]");
    }

    private static void addLineage(String fromId, String fromType, String toId, String toType) throws Exception {
        String body = """
                {
                  "edge": {
                    "fromEntity": {"id": "%s", "type": "%s"},
                    "toEntity": {"id": "%s", "type": "%s"}
                  }
                }
                """.formatted(fromId, fromType, toId, toType);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/lineage"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Added lineage edge status=" + response.statusCode());
    }

    private static void queryLineage(String entityId, String entityType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/lineage/getByEntityId?entityId=" + entityId + "&entityType=" + entityType + "&upstreamDepth=0&downstreamDepth=3"))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Lineage response: " + response.body());
    }

    private static String postAndGetId(String path, String body, String label) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Created " + label + " status=" + response.statusCode());

        String responseBody = response.body();
        int idStart = responseBody.indexOf("\"id\":\"") + 6;
        int idEnd = responseBody.indexOf("\"", idStart);
        if (idStart > 5 && idEnd > idStart) {
            return responseBody.substring(idStart, idEnd);
        }
        throw new RuntimeException("Failed to extract id from response: " + responseBody);
    }
}
