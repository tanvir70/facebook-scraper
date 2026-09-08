package com.fbscraper.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fbscraper.model.SyncResult;
import com.fbscraper.service.SentimentSyncService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Small embedded HTTP server that exposes the dashboard and synchronization API.
 */
public final class LocalWebServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;
    private final SentimentSyncService syncService;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final AtomicReference<SyncResult> latestResult;

    public LocalWebServer(int port, SentimentSyncService syncService) {
        try {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the local web server", e);
        }

        this.syncService = syncService;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.latestResult = new AtomicReference<>(syncService.loadPreviousResult().orElse(null));
        this.executor = Executors.newFixedThreadPool(4);
        this.server.setExecutor(executor);
        this.server.createContext("/api/sync", this::handleSync);
        this.server.createContext("/api/status", this::handleStatus);
        this.server.createContext("/", this::handleDashboard);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public Optional<SyncResult> latestResult() {
        return Optional.ofNullable(latestResult.get());
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        if (!"/".equals(exchange.getRequestURI().getPath())) {
            sendJson(exchange, 404, Map.of("error", "Not found"));
            return;
        }

        try (InputStream input = getClass().getClassLoader().getResourceAsStream("web/index.html")) {
            if (input == null) {
                sendJson(exchange, 500, Map.of("error", "Dashboard resource is missing"));
                return;
            }
            send(exchange, 200, "text/html; charset=utf-8", input.readAllBytes());
        }
    }

    private void handleSync(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        if (!syncing.compareAndSet(false, true)) {
            sendJson(exchange, 409, Map.of("error", "A synchronization is already running"));
            return;
        }

        try {
            SyncResult result = syncService.sync();
            latestResult.set(result);
            sendJson(exchange, 200, result);
        } catch (RuntimeException e) {
            sendJson(exchange, 502, Map.of("error", readableMessage(e)));
        } finally {
            syncing.set(false);
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        SyncResult result = latestResult.get();
        if (result == null) {
            sendJson(exchange, 200, Map.of("status", "NOT_SYNCED", "syncing", syncing.get()));
            return;
        }
        sendJson(exchange, 200, result);
    }

    private String readableMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Synchronization failed" : message;
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", objectMapper.writeValueAsBytes(body));
    }

    private void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
