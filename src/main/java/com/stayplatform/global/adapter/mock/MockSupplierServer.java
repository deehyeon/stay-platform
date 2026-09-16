package com.stayplatform.global.adapter.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class MockSupplierServer {

    private static final int PORT = 9090;

    private final List<MockSupplierHandler> handlers;

    private HttpServer server;

    @PostConstruct
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        handlers.forEach(handler -> handler.register(server));
        server.createContext("/control/", this::handleControl);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        log.info("Mock Supplier 서버 시작 (포트: {}, 등록된 공급사: {})",
                PORT, handlers.stream().map(MockSupplierHandler::supplierId).toList());
    }

    @PreDestroy
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleControl(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        String supplierId = extractSegment(path, 2);
        String value = extractQueryParam(query, "value");

        if (supplierId == null || value == null) {
            sendText(exchange, 400, "Bad Request");
            return;
        }

        MockSupplierMode mode = switch (value) {
            case "normal" -> MockSupplierMode.NORMAL;
            case "error" -> MockSupplierMode.ERROR;
            case "no-response" -> MockSupplierMode.NO_RESPONSE;
            default -> null;
        };

        if (mode == null) {
            sendText(exchange, 400, "Unknown mode: " + value);
            return;
        }

        Optional<MockSupplierHandler> target = handlers.stream()
                .filter(h -> h.supplierId().equals(supplierId))
                .findFirst();

        if (target.isEmpty()) {
            sendText(exchange, 400, "Unknown supplier: " + supplierId);
            return;
        }

        target.get().setMode(mode);
        log.info("Mock Supplier {} 모드 변경: {}", supplierId.toUpperCase(), mode);
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

    private void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
