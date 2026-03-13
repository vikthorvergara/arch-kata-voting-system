package com.sales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@RestController
public class ServingApplication {

    private final JdbcTemplate jdbc;

    public ServingApplication(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static void main(String[] args) {
        SpringApplication.run(ServingApplication.class, args);
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString(),
                "service", "sales-serving-api"
        );
    }

    @GetMapping("/api/top-cities")
    public List<Map<String, Object>> topCities() {
        return jdbc.queryForList(
                "SELECT city, total_amount, last_updated FROM top_sales_city ORDER BY total_amount DESC"
        );
    }

    @GetMapping("/api/top-salesmen")
    public List<Map<String, Object>> topSalesmen(
            @RequestParam(required = false) String country) {
        if (country != null && !country.isBlank()) {
            return jdbc.queryForList(
                    "SELECT salesman_name, country, total_amount, last_updated FROM top_salesman_country WHERE country = ? ORDER BY total_amount DESC",
                    country
            );
        }
        return jdbc.queryForList(
                "SELECT salesman_name, country, total_amount, last_updated FROM top_salesman_country ORDER BY total_amount DESC"
        );
    }

    @KafkaListener(topics = "sales.top-city", groupId = "sales-serving-api")
    public void consumeTopCity(String message) {
        try {
            String[] parts = message.split(",");
            if (parts.length >= 2) {
                String city = parts[0].trim();
                double amount = Double.parseDouble(parts[1].trim());
                jdbc.update(
                        "INSERT INTO top_sales_city (city, total_amount, last_updated) VALUES (?, ?, NOW()) " +
                                "ON CONFLICT (city) DO UPDATE SET total_amount = ?, last_updated = NOW()",
                        city, amount, amount
                );
                System.out.println("Updated top city: " + city + " = " + amount);
            }
        } catch (Exception e) {
            System.err.println("Error processing top-city message: " + e.getMessage());
        }
    }

    @KafkaListener(topics = "sales.top-salesman", groupId = "sales-serving-api")
    public void consumeTopSalesman(String message) {
        try {
            String[] parts = message.split(",");
            if (parts.length >= 3) {
                String name = parts[0].trim();
                String country = parts[1].trim();
                double amount = Double.parseDouble(parts[2].trim());
                jdbc.update(
                        "INSERT INTO top_salesman_country (salesman_name, country, total_amount, last_updated) VALUES (?, ?, ?, NOW()) " +
                                "ON CONFLICT (salesman_name, country) DO UPDATE SET total_amount = ?, last_updated = NOW()",
                        name, country, amount, amount
                );
                System.out.println("Updated top salesman: " + name + " (" + country + ") = " + amount);
            }
        } catch (Exception e) {
            System.err.println("Error processing top-salesman message: " + e.getMessage());
        }
    }
}
