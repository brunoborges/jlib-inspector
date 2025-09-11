package io.github.brunoborges.jlib.server.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import io.github.brunoborges.jlib.common.JarMetadata;
import io.github.brunoborges.jlib.common.JavaApplication;
// Custom JsonParser no longer needed directly; using org.json for building output.
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
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * HTTP handler for /report endpoint that aggregates unique JARs across
 * applications.
 */
public class ReportHandler implements HttpHandler {

    private final ApplicationService applicationService;
    // Deprecated parser usage removed.

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
                if (agg.size < 0 && jar.size >= 0)
                    agg.size = jar.size;
                if (agg.sampleFileName == null)
                    agg.sampleFileName = jar.fileName;
                if (agg.firstSeen == null || jar.firstSeen.isBefore(agg.firstSeen))
                    agg.firstSeen = jar.firstSeen;
                if (agg.lastAccessed == null || jar.getLastAccessed().isAfter(agg.lastAccessed))
                    agg.lastAccessed = jar.getLastAccessed();
                agg.loadedCount++;

                Map<String, Object> appInfo = new HashMap<>();
                appInfo.put("key", agg.key);
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

        JSONArray uniqueJars = new JSONArray();
        for (Agg agg : aggByKey.values()) {
            JSONObject obj = new JSONObject();
            if (agg.checksum != null) obj.put("checksum", agg.checksum);
            obj.put("size", agg.size);
            obj.put("fileName", agg.sampleFileName == null ? "" : agg.sampleFileName);
            obj.put("firstSeen", agg.firstSeen == null ? "" : agg.firstSeen.toString());
            obj.put("lastAccessed", agg.lastAccessed == null ? "" : agg.lastAccessed.toString());
            obj.put("loadedCount", agg.loadedCount);

            JSONArray pathsArr = new JSONArray();
            for (String p : agg.paths) pathsArr.put(p);
            obj.put("paths", pathsArr);

            JSONArray fnArr = new JSONArray();
            for (String fn : agg.fileNames) fnArr.put(fn);
            obj.put("fileNames", fnArr);

            JSONArray appsArr = new JSONArray();
            for (Map<String, Object> ai : agg.applications) {
                JSONObject jo = new JSONObject();
                jo.put("appId", ai.get("appId"));
                jo.put("jdkVersion", ai.get("jdkVersion"));
                jo.put("jdkVendor", ai.get("jdkVendor"));
                jo.put("jdkPath", ai.get("jdkPath"));
                jo.put("firstSeen", ai.get("firstSeen"));
                jo.put("lastUpdated", ai.get("lastUpdated"));
                jo.put("jarPath", ai.get("jarPath"));
                jo.put("loaded", ai.get("loaded"));
                jo.put("lastAccessed", ai.get("lastAccessed"));
                appsArr.put(jo);
            }
            obj.put("applications", appsArr);
            uniqueJars.put(obj);
        }

        JSONObject root = new JSONObject().put("uniqueJars", uniqueJars);
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
