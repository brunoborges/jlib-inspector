package io.github.brunoborges.jlib.server.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.brunoborges.jlib.common.JarMetadata;
import io.github.brunoborges.jlib.common.JavaApplication;
import io.github.brunoborges.jlib.server.service.ApplicationService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handler exposing global JAR inventory endpoints:
 * <ul>
 * <li>GET /api/jars - list all known JARs (deduplicated by jarId)</li>
 * <li>GET /api/jars/{jarId} - detail with applications that reference it</li>
 * </ul>
 */
public class JarsHandler implements HttpHandler {

    private final ApplicationService applicationService;

    public JarsHandler(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendPlain(exchange, 405, "Method not allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if ("/api/jars".equals(path)) {
            handleList(exchange);
        } else if (path.startsWith("/api/jars/")) {
            String jarId = path.substring("/api/jars/".length());
            handleDetail(exchange, jarId);
        } else {
            sendPlain(exchange, 404, "Not found");
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        // Deduplicate by jarId, choose first occurrence for basic info and count apps
        class Agg {
            JarMetadata jar;
            int appCount;
            int loadedCount;
            JSONArray applicationIds = new JSONArray();
        }
        Map<String, Agg> byId = new LinkedHashMap<>();
        for (JavaApplication app : applicationService.getAllApplications()) {
            for (JarMetadata jar : app.jars.values()) {
                String id = jar.getJarId();
                Agg agg = byId.computeIfAbsent(id, k -> {
                    Agg a = new Agg();
                    a.jar = jar;
                    return a;
                });
                agg.appCount++;
                if (jar.isLoaded())
                    agg.loadedCount++;
                // Track application id (avoid duplicates if same jar instance re-processed)
                // Simple linear check given typically low cardinality per jar.
                boolean already = false;
                for (int i = 0; i < agg.applicationIds.length(); i++) {
                    if (app.appId.equals(agg.applicationIds.getString(i))) { already = true; break; }
                }
                if (!already) {
                    agg.applicationIds.put(app.appId);
                }
            }
        }
        JSONArray arr = new JSONArray();
        for (Map.Entry<String, Agg> e : byId.entrySet()) {
            JarMetadata jar = e.getValue().jar;
            JSONObject o = new JSONObject();
            o.put("jarId", e.getKey());
            o.put("fileName", jar.fileName);
            o.put("checksum", jar.sha256Hash);
            o.put("size", jar.size);
            o.put("appCount", e.getValue().appCount);
            o.put("loadedAppCount", e.getValue().loadedCount);
            o.put("applicationIds", e.getValue().applicationIds);
            arr.put(o);
        }
        sendJson(exchange, new JSONObject().put("jars", arr).toString());
    }

    private void handleDetail(HttpExchange exchange, String jarId) throws IOException {
        JarMetadata representative = null;
        JSONArray apps = new JSONArray();
        for (JavaApplication app : applicationService.getAllApplications()) {
            for (JarMetadata jar : app.jars.values()) {
                if (jar.getJarId().equals(jarId)) {
                    if (representative == null)
                        representative = jar;
                    JSONObject a = new JSONObject();
                    a.put("appId", app.appId);
                    a.put("loaded", jar.isLoaded());
                    a.put("lastAccessed", jar.getLastAccessed().toString());
                    a.put("path", jar.fullPath);
                    apps.put(a);
                }
            }
        }
        if (representative == null) {
            sendPlain(exchange, 404, "JAR not found");
            return;
        }
        JSONObject root = new JSONObject();
        root.put("jarId", jarId);
        root.put("fileName", representative.fileName);
        root.put("checksum", representative.sha256Hash);
        root.put("size", representative.size);
        root.put("firstSeen", representative.firstSeen.toString());
        root.put("lastAccessed", representative.getLastAccessed().toString());
        if (representative.getManifestAttributes() != null && !representative.getManifestAttributes().isEmpty()) {
            JSONObject mf = new JSONObject();
            for (var e : representative.getManifestAttributes().entrySet()) {
                mf.put(e.getKey(), e.getValue());
            }
            root.put("manifest", mf);
        }
        root.put("applications", apps);
        sendJson(exchange, root.toString());
    }

    private void sendPlain(HttpExchange ex, int code, String msg) throws IOException {
        byte[] b = msg.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private void sendJson(HttpExchange ex, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }
}
