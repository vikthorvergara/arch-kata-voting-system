package com.sales;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DataHubLineageEmitter {

    private static final String GMS_URL = "http://localhost:8080";
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        System.out.println("Emitting lineage metadata to DataHub...");

        createDataset("postgres", "sales_analytics", "public.sales",
                "urn:li:dataset:(urn:li:dataPlatform:postgres,sales_analytics.public.sales,PROD)");

        createDataset("kafka", "sales", "sales.raw",
                "urn:li:dataset:(urn:li:dataPlatform:kafka,sales.raw,PROD)");

        createDataset("kafka", "sales", "sales.top-city",
                "urn:li:dataset:(urn:li:dataPlatform:kafka,sales.top-city,PROD)");

        createDataset("kafka", "sales", "sales.top-salesman",
                "urn:li:dataset:(urn:li:dataPlatform:kafka,sales.top-salesman,PROD)");

        createDataset("postgres", "sales_analytics", "public.top_sales_city",
                "urn:li:dataset:(urn:li:dataPlatform:postgres,sales_analytics.public.top_sales_city,PROD)");

        createDataset("postgres", "sales_analytics", "public.top_salesman_country",
                "urn:li:dataset:(urn:li:dataPlatform:postgres,sales_analytics.public.top_salesman_country,PROD)");

        addLineage(
                "urn:li:dataset:(urn:li:dataPlatform:postgres,sales_analytics.public.sales,PROD)",
                "urn:li:dataset:(urn:li:dataPlatform:kafka,sales.raw,PROD)");

        addLineage(
                "urn:li:dataset:(urn:li:dataPlatform:kafka,sales.raw,PROD)",
                "urn:li:dataset:(urn:li:dataPlatform:kafka,sales.top-city,PROD)");

        addLineage(
                "urn:li:dataset:(urn:li:dataPlatform:kafka,sales.raw,PROD)",
                "urn:li:dataset:(urn:li:dataPlatform:kafka,sales.top-salesman,PROD)");

        addLineage(
                "urn:li:dataset:(urn:li:dataPlatform:kafka,sales.top-city,PROD)",
                "urn:li:dataset:(urn:li:dataPlatform:postgres,sales_analytics.public.top_sales_city,PROD)");

        addLineage(
                "urn:li:dataset:(urn:li:dataPlatform:kafka,sales.top-salesman,PROD)",
                "urn:li:dataset:(urn:li:dataPlatform:postgres,sales_analytics.public.top_salesman_country,PROD)");

        System.out.println("Lineage metadata emitted successfully.");
        queryLineage("urn:li:dataset:(urn:li:dataPlatform:postgres,sales_analytics.public.sales,PROD)");
    }

    private static void createDataset(String platform, String database, String name, String urn) throws Exception {
        String schemaFields = "";
        if (platform.equals("postgres") && name.equals("public.sales")) {
            schemaFields = buildSalesSchemaFields();
        }

        String proposal = """
                {
                  "proposal": {
                    "entityType": "dataset",
                    "entityUrn": "%s",
                    "aspectName": "datasetProperties",
                    "aspect": {
                      "value": "{\\"name\\": \\"%s\\", \\"qualifiedName\\": \\"%s.%s\\", \\"description\\": \\"Sales pipeline dataset\\"}",
                      "contentType": "application/json"
                    },
                    "changeType": "UPSERT"
                  }
                }
                """.formatted(urn, name, database, name);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GMS_URL + "/aspects?action=ingestProposal"))
                .header("Content-Type", "application/json")
                .header("X-RestLi-Protocol-Version", "2.0.0")
                .POST(HttpRequest.BodyPublishers.ofString(proposal))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Created dataset [" + name + "] status=" + response.statusCode());

        if (!schemaFields.isEmpty()) {
            ingestSchema(urn, schemaFields);
        }
    }

    private static String buildSalesSchemaFields() {
        return """
                [
                  {"fieldPath": "id", "type": {"type": {"com.linkedin.schema.NumberType": {}}}, "nativeDataType": "bigint"},
                  {"fieldPath": "salesman_name", "type": {"type": {"com.linkedin.schema.StringType": {}}}, "nativeDataType": "varchar"},
                  {"fieldPath": "city", "type": {"type": {"com.linkedin.schema.StringType": {}}}, "nativeDataType": "varchar"},
                  {"fieldPath": "country", "type": {"type": {"com.linkedin.schema.StringType": {}}}, "nativeDataType": "varchar"},
                  {"fieldPath": "amount", "type": {"type": {"com.linkedin.schema.NumberType": {}}}, "nativeDataType": "decimal"},
                  {"fieldPath": "created_at", "type": {"type": {"com.linkedin.schema.DateType": {}}}, "nativeDataType": "timestamp"}
                ]""";
    }

    private static void ingestSchema(String urn, String fields) throws Exception {
        String proposal = """
                {
                  "proposal": {
                    "entityType": "dataset",
                    "entityUrn": "%s",
                    "aspectName": "schemaMetadata",
                    "aspect": {
                      "value": "{\\"schemaName\\": \\"sales\\", \\"platform\\": \\"urn:li:dataPlatform:postgres\\", \\"version\\": 0, \\"hash\\": \\"\\", \\"platformSchema\\": {\\"com.linkedin.schema.OtherSchema\\": {\\"rawSchema\\": \\"\\"}}, \\"fields\\": %s}",
                      "contentType": "application/json"
                    },
                    "changeType": "UPSERT"
                  }
                }
                """.formatted(urn, fields.replace("\"", "\\\"").replace("\\\"", "\\\\\""));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GMS_URL + "/aspects?action=ingestProposal"))
                .header("Content-Type", "application/json")
                .header("X-RestLi-Protocol-Version", "2.0.0")
                .POST(HttpRequest.BodyPublishers.ofString(proposal))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Ingested schema status=" + response.statusCode());
    }

    private static void addLineage(String upstreamUrn, String downstreamUrn) throws Exception {
        String proposal = """
                {
                  "proposal": {
                    "entityType": "dataset",
                    "entityUrn": "%s",
                    "aspectName": "upstreamLineage",
                    "aspect": {
                      "value": "{\\"upstreams\\": [{\\"dataset\\": \\"%s\\", \\"type\\": \\"TRANSFORMED\\"}]}",
                      "contentType": "application/json"
                    },
                    "changeType": "UPSERT"
                  }
                }
                """.formatted(downstreamUrn, upstreamUrn);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GMS_URL + "/aspects?action=ingestProposal"))
                .header("Content-Type", "application/json")
                .header("X-RestLi-Protocol-Version", "2.0.0")
                .POST(HttpRequest.BodyPublishers.ofString(proposal))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Added lineage [" + upstreamUrn + " -> " + downstreamUrn + "] status=" + response.statusCode());
    }

    private static void queryLineage(String urn) throws Exception {
        String graphqlQuery = """
                {
                  "query": "{ dataset(urn: \\"%s\\") { urn properties { name qualifiedName } lineage(input: {direction: DOWNSTREAM, start: 0, count: 10}) { relationships { entity { urn type } } } } }"
                }
                """.formatted(urn);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/graphql"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(graphqlQuery))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("\nLineage query result:");
        System.out.println(response.body());
    }
}
