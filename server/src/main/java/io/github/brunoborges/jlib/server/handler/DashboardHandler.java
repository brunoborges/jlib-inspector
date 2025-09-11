package io.github.brunoborges.jlib.server.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.brunoborges.jlib.common.JavaApplication;
import io.github.brunoborges.jlib.json.JsonResponseBuilder;
import io.github.brunoborges.jlib.server.service.ApplicationService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Dashboard handler consolidating applications with their JAR inventories (including manifest attributes).
 * Provides a single payload optimized for the frontend dashboard polling/websocket updates.
 *
 * Response shape:
 * {
 *   "applications": [ { appDetails + jars[] }, ... ],
 *   "lastUpdated":"2025-...",
 *   "serverStatus":"connected"
 * }
 */
public class DashboardHandler implements HttpHandler {

    private final ApplicationService applicationService;

    public DashboardHandler(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed");
            return;
        }

        JSONArray apps = new JSONArray();
        for (JavaApplication app : applicationService.getAllApplications()) {
            apps.put(new JSONObject(JsonResponseBuilder.buildAppDetailsJson(app)));
        }
        JSONObject root = new JSONObject();
        root.put("applications", apps);
        root.put("lastUpdated", Instant.now().toString());
        root.put("serverStatus", "connected");

        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void send(HttpExchange ex, int code, String msg) throws IOException {
        byte[] b = msg.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }
}