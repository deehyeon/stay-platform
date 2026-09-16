package com.stayplatform.global.adapter.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@Profile("!test")
public class MockSupplierServer {

    private static final int PORT = 9090;

    private enum Mode { NORMAL, ERROR, NO_RESPONSE }

    private final AtomicReference<Mode> modeA = new AtomicReference<>(Mode.NORMAL);
    private final AtomicReference<Mode> modeB = new AtomicReference<>(Mode.NORMAL);

    private HttpServer server;

    @PostConstruct
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/a/v1/hotels", this::handleSupplierAHotels);
        server.createContext("/a/v1/availability", this::handleSupplierAAvailability);
        server.createContext("/b/api/properties", this::handleSupplierBProperties);
        server.createContext("/b/api/search", this::handleSupplierBSearch);
        server.createContext("/control/", this::handleControl);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        log.info("Mock Supplier 서버 시작 (포트: {})", PORT);
    }

    @PreDestroy
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleSupplierAHotels(HttpExchange exchange) throws IOException {
        String body = """
                {"items":[{"hotelCode":"A-10023","hotelName":"Mock Hotel A","roomTypes":[{"roomTypeCode":"DLX-TWN","roomTypeName":"Deluxe Twin","maxOccupancy":2}]}]}""";
        sendJson(exchange, 200, body);
    }

    private void handleSupplierAAvailability(HttpExchange exchange) throws IOException {
        switch (modeA.get()) {
            case ERROR -> sendText(exchange, 503, "Service Unavailable");
            case NO_RESPONSE -> holdConnection();
            default -> {
                String body = """
                        {"items":[{"hotelCode":"A-10023","roomTypeCode":"DLX-TWN","currency":"KRW","breakfastIncluded":false,"dailyRates":[{"date":"2026-09-01","nightlyRate":100000,"taxAmount":10000,"remainingRooms":5}]}]}""";
                sendJson(exchange, 200, body);
            }
        }
    }

    private void handleSupplierBProperties(HttpExchange exchange) throws IOException {
        String body = """
                {"resultCode":"0000","resultMessage":"SUCCESS","data":{"items":[{"propertyId":"B77120","propertyName":"Mock Hotel B","rooms":[{"roomId":"R001","roomName":"Standard Double","maxOccupancy":2}]}]}}""";
        sendJson(exchange, 200, body);
    }

    private void handleSupplierBSearch(HttpExchange exchange) throws IOException {
        switch (modeB.get()) {
            case ERROR -> {
                String body = """
                        {"resultCode":"E503","resultMessage":"TEMPORARILY_UNAVAILABLE","data":null}""";
                sendJson(exchange, 200, body);
            }
            case NO_RESPONSE -> holdConnection();
            default -> {
                String body = """
                        {"resultCode":"0000","resultMessage":"SUCCESS","data":{"items":[{"propertyId":"B77120","roomId":"R001","totalPrice":250000,"currency":"KRW","breakfastIncluded":true,"inventory":[{"date":"2026-09-01","remainingRooms":3}]}]}}""";
                sendJson(exchange, 200, body);
            }
        }
    }

    private void handleControl(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        String supplier = extractSegment(path, 2);
        String value = extractQueryParam(query, "value");

        if (supplier == null || value == null) {
            sendText(exchange, 400, "Bad Request");
            return;
        }

        Mode mode = switch (value) {
            case "normal" -> Mode.NORMAL;
            case "error" -> Mode.ERROR;
            case "no-response" -> Mode.NO_RESPONSE;
            default -> null;
        };

        if (mode == null) {
            sendText(exchange, 400, "Unknown mode: " + value);
            return;
        }

        if ("a".equals(supplier)) {
            modeA.set(mode);
        } else if ("b".equals(supplier)) {
            modeB.set(mode);
        } else {
            sendText(exchange, 400, "Unknown supplier: " + supplier);
            return;
        }

        log.info("Mock Supplier {} 모드 변경: {}", supplier.toUpperCase(), mode);
        sendText(exchange, 200, "OK");
    }

    private String extractSegment(String path, int index) {
        String[] parts = path.split("/");
        return parts.length > index ? parts[index] : null;
    }

    private String extractQueryParam(String query, String key) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) return kv[1];
        }
        return null;
    }

    private void holdConnection() {
        try {
            Thread.sleep(600_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
