package io.github.brunoborges.jlib.server.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.brunoborges.jlib.common.JavaApplication;
import io.github.brunoborges.jlib.server.service.ApplicationService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Dashboard handler returning a minimal summary optimized for the UI.
 *
 * New response shape (no per-jar arrays, no manifest data):
 * {
 *   "applicationCount": <int>,
 *   "jarCount": <int>,                // total jars across all apps
 *   "activeJarCount": <int>,          // loaded=true
 *   "inactiveJarCount": <int>,        // loaded=false
 *   "applications": [
 *      {
 *        "appId": "...",
 *        "name": "...",
 *        "commandLine": "...",
 *        "lastUpdated": "ISO-8601",
 *        "activeJarCount": <int>,
 *        "totalJarCount": <int>
 *      }
 *   ],
 *   "lastUpdated":"ISO-8601",
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

        int totalJars = 0;
        int activeJars = 0;
        JSONArray apps = new JSONArray();
        for (JavaApplication app : applicationService.getAllApplications()) {
            int appTotal = app.jars.size();
            int appActive = 0;
            for (var jar : app.jars.values()) {
                totalJars++;
                if (jar.isLoaded()) {
                    activeJars++; appActive++; }
            }
            JSONObject a = new JSONObject();
            a.put("appId", app.appId);
            a.put("name", app.name == null ? "" : app.name);
            a.put("commandLine", app.commandLine);
            a.put("lastUpdated", app.lastUpdated.toString());
            a.put("activeJarCount", appActive);
            a.put("totalJarCount", appTotal);
            apps.put(a);
        }
        int inactiveJars = totalJars - activeJars;
        JSONObject root = new JSONObject();
        root.put("applicationCount", apps.length());
        root.put("jarCount", totalJars);
        root.put("activeJarCount", activeJars);
        root.put("inactiveJarCount", inactiveJars);
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