package io.github.brunoborges.jlib.server.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import io.github.brunoborges.jlib.common.JarMetadata;
import io.github.brunoborges.jlib.common.JavaApplication;
import io.github.brunoborges.jlib.json.JsonParserFactory;
import io.github.brunoborges.jlib.json.JsonParserInterface;
import io.github.brunoborges.jlib.server.service.ApplicationService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HTTP handler for /report endpoint that aggregates unique JARs across applications.
 */
public class ReportHandler implements HttpHandler {

    private final ApplicationService applicationService;
    private final JsonParserInterface jsonParser = JsonParserFactory.getDefaultParser();

    public ReportHandler(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed");
            return;
        }

        // Aggregate by checksum when available; fallback to fullPath
        class Agg {
            String key; // checksum or fullPath
            String checksum;
            long size = -1L;
            String sampleFileName;
            Instant firstSeen = null;
            Instant lastAccessed = null;
            int loadedCount = 0;
            final Set<String> paths = new HashSet<>();
            final Set<String> fileNames = new HashSet<>();
            final List<Map<String, Object>> applications = new ArrayList<>();
        }

        Map<String, Agg> aggByKey = new HashMap<>();

        for (JavaApplication app : applicationService.getAllApplications()) {
            for (JarMetadata jar : app.jars.values()) {
                if (!jar.isLoaded()) {
                    continue; // only include applications that loaded the jar
                }
                String checksum = jar.sha256Hash;
                boolean hasHash = checksum != null && !checksum.isEmpty() && !"?".equals(checksum);
                String key = hasHash ? checksum : jar.fullPath;

                Agg agg = aggByKey.computeIfAbsent(key, k -> {
                    Agg a = new Agg();
                    a.key = k;
                    a.checksum = hasHash ? checksum : null;
                    a.size = jar.size;
                    a.sampleFileName = jar.fileName;
                    a.firstSeen = jar.firstSeen;
                    a.lastAccessed = jar.getLastAccessed();
                    return a;
                });

                // Update aggregate
                agg.paths.add(jar.fullPath);
                agg.fileNames.add(jar.fileName);
                if (agg.size < 0 && jar.size >= 0) agg.size = jar.size;
                if (agg.sampleFileName == null) agg.sampleFileName = jar.fileName;
                if (agg.firstSeen == null || jar.firstSeen.isBefore(agg.firstSeen)) agg.firstSeen = jar.firstSeen;
                if (agg.lastAccessed == null || jar.getLastAccessed().isAfter(agg.lastAccessed)) agg.lastAccessed = jar.getLastAccessed();
                agg.loadedCount++;

                Map<String, Object> appInfo = new HashMap<>();
                appInfo.put("appId", app.appId);
                appInfo.put("jdkVersion", app.jdkVersion);
                appInfo.put("jdkVendor", app.jdkVendor);
                appInfo.put("jdkPath", app.jdkPath);
                appInfo.put("firstSeen", app.firstSeen.toString());
                appInfo.put("lastUpdated", app.lastUpdated.toString());
                appInfo.put("jarPath", jar.fullPath);
                appInfo.put("loaded", jar.isLoaded());
                appInfo.put("lastAccessed", jar.getLastAccessed().toString());
                agg.applications.add(appInfo);
            }
        }

        // Build JSON response
        StringBuilder sb = new StringBuilder();
        sb.append("{\"uniqueJars\":[");
        boolean first = true;
        for (Agg agg : aggByKey.values()) {
            if (!first) sb.append(",");
            sb.append("{");
            if (agg.checksum != null) {
                sb.append("\"checksum\":\"").append(jsonParser.escapeJson(agg.checksum)).append("\",");
            }
            sb.append("\"size\":").append(agg.size).append(",")
              .append("\"fileName\":\"").append(jsonParser.escapeJson(agg.sampleFileName == null ? "" : agg.sampleFileName)).append("\",")
              .append("\"firstSeen\":\"").append(agg.firstSeen == null ? "" : agg.firstSeen.toString()).append("\",")
              .append("\"lastAccessed\":\"").append(agg.lastAccessed == null ? "" : agg.lastAccessed.toString()).append("\",")
              .append("\"loadedCount\":").append(agg.loadedCount).append(",");

            // paths
            sb.append("\"paths\":[");
            boolean firstPath = true;
            for (String p : agg.paths) {
                if (!firstPath) sb.append(",");
                sb.append("\"").append(jsonParser.escapeJson(p)).append("\"");
                firstPath = false;
            }
            sb.append("],");

            // fileNames
            sb.append("\"fileNames\":[");
            boolean firstFn = true;
            for (String fn : agg.fileNames) {
                if (!firstFn) sb.append(",");
                sb.append("\"").append(jsonParser.escapeJson(fn)).append("\"");
                firstFn = false;
            }
            sb.append("],");

            // applications
            sb.append("\"applications\":[");
            boolean firstApp = true;
            for (Map<String, Object> ai : agg.applications) {
                if (!firstApp) sb.append(",");
                sb.append("{")
                  .append("\"appId\":\"").append(ai.get("appId")).append("\",")
                  .append("\"jdkVersion\":\"").append(ai.get("jdkVersion")).append("\",")
                  .append("\"jdkVendor\":\"").append(ai.get("jdkVendor")).append("\",")
                  .append("\"jdkPath\":\"").append(jsonParser.escapeJson((String) ai.get("jdkPath"))).append("\",")
                  .append("\"firstSeen\":\"").append(ai.get("firstSeen")).append("\",")
                  .append("\"lastUpdated\":\"").append(ai.get("lastUpdated")).append("\",")
                  .append("\"jarPath\":\"").append(jsonParser.escapeJson((String) ai.get("jarPath"))).append("\",")
                  .append("\"loaded\":").append(ai.get("loaded")).append(",")
                  .append("\"lastAccessed\":\"").append(ai.get("lastAccessed")).append("\"")
                  .append("}");
                firstApp = false;
            }
            sb.append("]}");
            first = false;
        }
        sb.append("]}");

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
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
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
}
