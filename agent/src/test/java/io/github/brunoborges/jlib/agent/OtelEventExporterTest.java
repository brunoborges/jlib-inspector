package io.github.brunoborges.jlib.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

@DisplayName("OtelEventExporter Tests")
class OtelEventExporterTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("Should resolve the OTLP logs endpoint from argument, environment or default")
    void shouldResolveEndpoint() {
        assertEquals("http://localhost:4318/v1/logs",
                OtelEventExporter.resolveEndpoint(null, Map.of()).toString());
        assertEquals("http://collector:4318/v1/logs",
                OtelEventExporter.resolveEndpoint(null, Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318/"))
                        .toString());
        assertEquals("https://logs.example.com/custom",
                OtelEventExporter.resolveEndpoint(null, Map.of(
                        "OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318",
                        "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT", "https://logs.example.com/custom")).toString());
        assertEquals("http://agent-arg:4318/v1/logs",
                OtelEventExporter.resolveEndpoint("agent-arg:4318",
                        Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318")).toString());
    }

    @Test
    @DisplayName("Should map top-level JARs to package.info events with OpenTelemetry attribute keys")
    void shouldBuildPackageInfoEventsForTopLevelJars() throws Exception {
        Path jar = createJar(tmp.resolve("demo-lib-1.2.3.jar"), "com.example", "demo-lib", "1.2.3");
        String jarId = jar.toUri().toString();
        JarInventory inventory = new JarInventory();
        inventory.registerDeclared(jarId, Files.size(jar), JarInventory.fileHashSupplier(jar.toFile()));
        inventory.registerDeclared(tmp.resolve("unused-0.1.jar").toUri().toString(), -1L, null);
        inventory.markLoaded(jarId);
        inventory.markLoaded(tmp.toUri().toString()); // exploded classes directory: not an archive

        OtelEventExporter exporter = new OtelEventExporter(null, Map.of(
                "OTEL_SERVICE_NAME", "orders",
                "OTEL_RESOURCE_ATTRIBUTES", "deployment.environment.name=prod,team=a%2Cb"));
        JSONObject payload = exporter.buildPayload(inventory.snapshot(), "app-123");

        JSONObject resourceLogs = payload.getJSONArray("resourceLogs").getJSONObject(0);
        Map<String, Object> resource = attributes(resourceLogs.getJSONObject("resource").getJSONArray("attributes"));
        assertEquals("orders", resource.get("service.name"));
        assertEquals("prod", resource.get("deployment.environment.name"));
        assertEquals("a,b", resource.get("team"));
        assertEquals("app-123", resource.get("jlib.application.id"));

        JSONArray records = resourceLogs.getJSONArray("scopeLogs").getJSONObject(0).getJSONArray("logRecords");
        assertEquals(2, records.length());

        JSONObject record = findRecord(records, "demo-lib-1.2.3.jar");
        assertEquals("package.info", record.getString("eventName"));
        Map<String, Object> attrs = attributes(record.getJSONArray("attributes"));
        assertEquals("com.example:demo-lib", attrs.get("package.name"));
        assertEquals("1.2.3", attrs.get("package.version"));
        assertEquals("jar", attrs.get("package.type"));
        assertEquals("Demo Lib by Example Corp", attrs.get("package.description"));
        assertEquals(sha256Hex(Files.readAllBytes(jar)), attrs.get("package.checksum"));
        assertEquals("SHA-256", attrs.get("package.checksum_algorithm"));
        assertEquals(jarId, attrs.get("jlib.jar.path"));
        assertEquals(true, attrs.get("jlib.jar.loaded"));
        assertEquals(Long.toString(Files.size(jar)), attrs.get("jlib.jar.size"));

        Map<String, Object> unused = attributes(findRecord(records, "unused-0.1.jar").getJSONArray("attributes"));
        assertEquals(false, unused.get("jlib.jar.loaded"));
        assertFalse(unused.containsKey("package.checksum"));
        assertFalse(unused.containsKey("jlib.jar.size"));
    }

    @Test
    @DisplayName("Should read Maven coordinates and checksum of nested JARs")
    void shouldBuildPackageInfoEventsForNestedJars() throws Exception {
        byte[] inner = Files.readAllBytes(createJar(tmp.resolve("inner.jar"), "org.acme", "inner-lib", "2.0.0"));
        Path outer = tmp.resolve("app.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(outer))) {
            out.putNextEntry(new JarEntry("BOOT-INF/lib/inner-lib-2.0.0.jar"));
            out.write(inner);
            out.closeEntry();
        }
        String nestedId = outer.toUri() + "!/BOOT-INF/lib/inner-lib-2.0.0.jar";
        JarInventory inventory = new JarInventory();
        inventory.markLoaded(nestedId); // loaded without a known checksum

        JSONObject payload = new OtelEventExporter(null, Map.of()).buildPayload(inventory.snapshot(), null);

        JSONArray records = payload.getJSONArray("resourceLogs").getJSONObject(0)
                .getJSONArray("scopeLogs").getJSONObject(0).getJSONArray("logRecords");
        Map<String, Object> attrs = attributes(records.getJSONObject(0).getJSONArray("attributes"));
        assertEquals("org.acme:inner-lib", attrs.get("package.name"));
        assertEquals("2.0.0", attrs.get("package.version"));
        assertEquals("inner-lib-2.0.0.jar", attrs.get("package.path"));
        assertEquals(sha256Hex(inner), attrs.get("package.checksum"));
        assertEquals(true, attrs.get("jlib.jar.nested"));
    }

    @Test
    @DisplayName("Should POST OTLP/JSON logs with configured headers")
    void shouldExportOverOtlpHttp() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<Map<String, List<String>>> headers = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            headers.set(new HashMap<>(exchange.getRequestHeaders()));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            JarInventory inventory = new JarInventory();
            inventory.markLoaded(tmp.resolve("lib-1.0.jar").toUri().toString());
            OtelEventExporter exporter = new OtelEventExporter(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Map.of("OTEL_EXPORTER_OTLP_HEADERS", "Authorization=Bearer%20secret"));

            exporter.exportAsync(inventory, "app-1").get(10, TimeUnit.SECONDS);

            assertEquals("/v1/logs", path.get());
            assertEquals(List.of("application/json"), headers.get().get("Content-type"));
            assertEquals(List.of("Bearer secret"), headers.get().get("Authorization"));
            assertTrue(new JSONObject(body.get()).getJSONArray("resourceLogs").length() > 0);
        } finally {
            server.stop(0);
        }
    }

    private static Path createJar(Path file, String groupId, String artifactId, String version) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_TITLE, "Demo Lib");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VENDOR, "Example Corp");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(bytes, manifest)) {
            out.putNextEntry(new JarEntry("META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties"));
            out.write(("groupId=" + groupId + "\nartifactId=" + artifactId + "\nversion=" + version + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return Files.write(file, bytes.toByteArray());
    }

    private static JSONObject findRecord(JSONArray records, String fileName) {
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            if (fileName.equals(attributes(record.getJSONArray("attributes")).get("package.path"))) {
                return record;
            }
        }
        throw new AssertionError("No package.info event for " + fileName);
    }

    private static Map<String, Object> attributes(JSONArray array) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject attr = array.getJSONObject(i);
            JSONObject value = attr.getJSONObject("value");
            String type = value.keys().next();
            result.put(attr.getString("key"), value.get(type));
        }
        return result;
    }

    private static String sha256Hex(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
