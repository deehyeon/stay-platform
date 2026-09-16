package com.stayplatform.global.adapter.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractMockSupplierHandler implements MockSupplierHandler {

    private final AtomicReference<MockSupplierMode> mode = new AtomicReference<>(MockSupplierMode.NORMAL);

    @Override
    public void setMode(MockSupplierMode mode) {
        this.mode.set(mode);
    }

    @Override
    public void register(HttpServer server) {
        server.createContext(hotelListPath(), this::handleHotelList);
        server.createContext(availabilityPath(), exchange -> handleAvailability(exchange, mode.get()));
    }

    protected abstract String hotelListPath();

    protected abstract String availabilityPath();

    protected abstract String hotelListResponseBody();

    protected abstract String normalAvailabilityResponseBody();

    /**
     * 공급사마다 에러 표현 방식이 다르므로 각 구현체가 정의한다.
     * - Supplier A: HTTP 503
     * - Supplier B: HTTP 200 + resultCode: E503
     */
    protected abstract void handleError(HttpExchange exchange) throws IOException;

    private void handleHotelList(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, hotelListResponseBody());
    }

    private void handleAvailability(HttpExchange exchange, MockSupplierMode currentMode) throws IOException {
        switch (currentMode) {
            case ERROR -> handleError(exchange);
            case NO_RESPONSE -> holdConnection();
            default -> sendJson(exchange, 200, normalAvailabilityResponseBody());
        }
    }

    protected void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void holdConnection() {
        try {
            Thread.sleep(600_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
