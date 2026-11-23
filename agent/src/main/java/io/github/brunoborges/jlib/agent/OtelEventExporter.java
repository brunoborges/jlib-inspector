package io.github.brunoborges.jlib.agent;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.brunoborges.jlib.common.JarMetadata;

/**
 * Optional exporter that publishes the JAR inventory as OpenTelemetry events
 * over OTLP/HTTP (JSON encoding), without depending on the OpenTelemetry SDK.
 *
 * <p>
 * Each archive becomes one {@value #EVENT_NAME} log record that uses the same
 * attribute keys as the OpenTelemetry Java agent's package emitter
 * ({@code package.name}, {@code package.version}, {@code package.path},
 * {@code package.type}, {@code package.description}, {@code package.checksum},
 * {@code package.checksum_algorithm}), plus {@code jlib.jar.*} attributes for
 * data only jlib has, such as whether the JAR was actually loaded.
 *
 * <p>
 * Honors the standard OpenTelemetry environment variables
 * {@code OTEL_EXPORTER_OTLP_LOGS_ENDPOINT}, {@code OTEL_EXPORTER_OTLP_ENDPOINT},
 * {@code OTEL_EXPORTER_OTLP_HEADERS}, {@code OTEL_EXPORTER_OTLP_LOGS_HEADERS},
 * {@code OTEL_SERVICE_NAME} and {@code OTEL_RESOURCE_ATTRIBUTES}.
 */
final class OtelEventExporter {

    private static final Logger LOG = Logger.getLogger(OtelEventExporter.class.getName());

    static final String EVENT_NAME = "package.info";
    static final String DEFAULT_ENDPOINT = "http://localhost:4318";
    private static final String LOGS_PATH = "/v1/logs";
    private static final String SCOPE_NAME = "io.github.brunoborges.jlib.agent";
    private static final Set<String> ARCHIVE_TYPES = Set.of("jar", "war", "ear");

    private final URI endpoint;
    private final Map<String, String> headers;
    private final Map<String, String> env;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jlib-otel-exporter");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    /**
     * @param endpointOverride OTLP base endpoint from the agent arguments (e.g.
     *                         {@code http://collector:4318}); null to use the
     *                         environment or the default
     * @param env              environment variables to read configuration from
     */
    OtelEventExporter(String endpointOverride, Map<String, String> env) {
        this.env = env;
        this.endpoint = resolveEndpoint(endpointOverride, env);
        this.headers = resolveHeaders(env);
        String protocol = firstNonBlank(env.get("OTEL_EXPORTER_OTLP_LOGS_PROTOCOL"),
                env.get("OTEL_EXPORTER_OTLP_PROTOCOL"));
        if (protocol != null && !protocol.trim().startsWith("http/")) {
            LOG.warning("jlib-inspector exports OTLP over http/json; ignoring protocol '" + protocol
                    + "'. Make sure " + endpoint + " accepts OTLP/HTTP.");
        }
    }

    static OtelEventExporter create(String endpointOverride) {
        return new OtelEventExporter(endpointOverride, System.getenv());
    }

    URI endpoint() {
        return endpoint;
    }

    Map<String, String> headers() {
        return headers;
    }

    /**
     * Builds and sends the events off the caller thread.
     *
     * @return future that completes when the export finishes (successfully or
     *         not)
     */
    CompletableFuture<Void> exportAsync(JarInventory inventory, String applicationId) {
        long start = System.currentTimeMillis();
        return CompletableFuture.supplyAsync(() -> buildPayload(inventory.snapshot(), applicationId), executor)
                .thenCompose(payload -> {
                    int events = payload.getJSONArray("resourceLogs").getJSONObject(0)
                            .getJSONArray("scopeLogs").getJSONObject(0)
                            .getJSONArray("logRecords").length();
                    return send(payload.toString()).thenAccept(resp -> {
                        long took = System.currentTimeMillis() - start;
                        if (resp.statusCode() / 100 == 2) {
                            LOG.info(() -> String.format("Exported %d OpenTelemetry %s events to %s status=%d took=%dms",
                                    events, EVENT_NAME, endpoint, resp.statusCode(), took));
                        } else {
                            LOG.warning("OpenTelemetry export to " + endpoint + " failed: status=" + resp.statusCode()
                                    + " body=" + resp.body());
                        }
                    });
                })
                .whenComplete((ignored, err) -> {
                    if (err != null) {
                        LOG.warning("OpenTelemetry export to " + endpoint + " failed: " + err.getMessage());
                    }
                });
    }

    private CompletableFuture<HttpResponse<String>> send(String json) {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        headers.forEach((name, value) -> {
            try {
                request.header(name, value);
            } catch (IllegalArgumentException e) {
                LOG.warning("Skipping unsupported OTLP header '" + name + "': " + e.getMessage());
            }
        });
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Builds an OTLP/JSON {@code ExportLogsServiceRequest} for the archives. */
    JSONObject buildPayload(Collection<JarMetadata> jars, String applicationId) {
        String now = Long.toString(epochNanos(Instant.now()));
        JSONArray logRecords = new JSONArray();
        for (JarMetadata jar : jars) {
            String type = archiveType(jar.fileName);
            if (type != null) {
                logRecords.put(new JSONObject()
                        .put("timeUnixNano", now)
                        .put("observedTimeUnixNano", now)
                        .put("eventName", EVENT_NAME)
                        .put("attributes", toAttributes(packageAttributes(jar, type))));
            }
        }

        JSONObject scope = new JSONObject().put("name", SCOPE_NAME);
        String version = OtelEventExporter.class.getPackage().getImplementationVersion();
        if (version != null) {
            scope.put("version", version);
        }
        JSONObject resourceLogs = new JSONObject()
                .put("resource", new JSONObject().put("attributes", toAttributes(resourceAttributes(applicationId))))
                .put("scopeLogs", new JSONArray().put(new JSONObject().put("scope", scope).put("logRecords", logRecords)));
        return new JSONObject().put("resourceLogs", new JSONArray().put(resourceLogs));
    }

    private Map<String, Object> packageAttributes(JarMetadata jar, String type) {
        String checksum = base64UrlToHex(jar.sha256Hash);
        ArchiveDetails details = ArchiveDetails.read(jar.fullPath, jar.fileName, checksum == null);
        if (checksum == null) {
            checksum = details.sha256Hex;
        }
        Map<String, String> manifest = !details.manifest.isEmpty() ? details.manifest
                : jar.getManifestAttributes() != null ? jar.getManifestAttributes() : Map.of();

        Map<String, Object> attrs = new LinkedHashMap<>();
        // Mirrors eventName for backends that predate the LogRecord eventName field
        attrs.put("event.name", EVENT_NAME);
        attrs.put("package.path", jar.fileName);
        attrs.put("package.type", type);
        putIfPresent(attrs, "package.name", mavenName(details.pom));
        putIfPresent(attrs, "package.version", firstNonBlank(
                details.pom == null ? null : details.pom.getProperty("version"),
                manifest.get("Implementation-Version"),
                manifest.get("Bundle-Version")));
        putIfPresent(attrs, "package.description", description(manifest));
        if (checksum != null) {
            attrs.put("package.checksum", checksum);
            attrs.put("package.checksum_algorithm", "SHA-256");
        }
        attrs.put("jlib.jar.path", jar.fullPath);
        attrs.put("jlib.jar.loaded", jar.isLoaded());
        attrs.put("jlib.jar.nested", jar.isNested());
        if (jar.size >= 0) {
            attrs.put("jlib.jar.size", jar.size);
        }
        return attrs;
    }

    private Map<String, Object> resourceAttributes(String applicationId) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("process.pid", ProcessHandle.current().pid());
        attrs.put("process.executable.path",
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        attrs.put("process.command_line", InspectorAgent.getFullCommandLine());
        putIfPresent(attrs, "process.runtime.name", System.getProperty("java.runtime.name"));
        putIfPresent(attrs, "process.runtime.version", System.getProperty("java.runtime.version"));
        attrs.put("process.runtime.description", System.getProperty("java.vm.vendor") + " "
                + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
        try {
            attrs.put("host.name", InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            LOG.fine(() -> "Unable to resolve host name: " + e.getMessage());
        }
        putIfPresent(attrs, "jlib.application.id", applicationId);

        Map<String, String> configured = parseKeyValues(env.get("OTEL_RESOURCE_ATTRIBUTES"));
        attrs.putAll(configured);
        attrs.put("service.name", firstNonBlank(env.get("OTEL_SERVICE_NAME"), configured.get("service.name"),
                "unknown_service:java"));
        return attrs;
    }

    static URI resolveEndpoint(String override, Map<String, String> env) {
        if (!isBlank(override)) {
            String base = override.trim();
            if (!base.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
                base = "http://" + base;
            }
            return URI.create(withLogsPath(base));
        }
        String logsEndpoint = env.get("OTEL_EXPORTER_OTLP_LOGS_ENDPOINT");
        if (!isBlank(logsEndpoint)) {
            return URI.create(logsEndpoint.trim());
        }
        String base = env.get("OTEL_EXPORTER_OTLP_ENDPOINT");
        return URI.create(withLogsPath(isBlank(base) ? DEFAULT_ENDPOINT : base.trim()));
    }

    private static String withLogsPath(String base) {
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return b.endsWith(LOGS_PATH) ? b : b + LOGS_PATH;
    }

    private static Map<String, String> resolveHeaders(Map<String, String> env) {
        Map<String, String> headers = parseKeyValues(env.get("OTEL_EXPORTER_OTLP_HEADERS"));
        headers.putAll(parseKeyValues(env.get("OTEL_EXPORTER_OTLP_LOGS_HEADERS")));
        return headers;
    }

    /** Parses the OpenTelemetry {@code key1=value1,key2=value2} format with percent-encoded values. */
    static Map<String, String> parseKeyValues(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (isBlank(raw)) {
            return result;
        }
        for (String pair : raw.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            try {
                value = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // keep the raw value
            }
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static JSONArray toAttributes(Map<String, Object> attrs) {
        JSONArray array = new JSONArray();
        attrs.forEach((key, value) -> {
            JSONObject anyValue = new JSONObject();
            if (value instanceof Boolean b) {
                anyValue.put("boolValue", b);
            } else if (value instanceof Number n) {
                // OTLP/JSON encodes 64-bit integers as strings
                anyValue.put("intValue", Long.toString(n.longValue()));
            } else {
                anyValue.put("stringValue", String.valueOf(value));
            }
            array.put(new JSONObject().put("key", key).put("value", anyValue));
        });
        return array;
    }

    private static String archiveType(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return ARCHIVE_TYPES.contains(ext) ? ext : null;
    }

    private static String mavenName(Properties pom) {
        if (pom == null) {
            return null;
        }
        String groupId = pom.getProperty("groupId");
        String artifactId = pom.getProperty("artifactId");
        return isBlank(groupId) || isBlank(artifactId) ? null : groupId + ":" + artifactId;
    }

    /** Same format as the OpenTelemetry Java agent: "{Implementation-Title} by {Implementation-Vendor}". */
    private static String description(Map<String, String> manifest) {
        String title = manifest.get("Implementation-Title");
        String vendor = manifest.get("Implementation-Vendor");
        if (isBlank(title)) {
            return isBlank(vendor) ? null : vendor;
        }
        return isBlank(vendor) ? title : title + " by " + vendor;
    }

    /** Converts jlib's URL-safe base64 SHA-256 into the lowercase hex form used by OpenTelemetry. */
    static String base64UrlToHex(String base64Url) {
        if (isBlank(base64Url) || "?".equals(base64Url)) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(Base64.getUrlDecoder().decode(base64Url));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static long epochNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    private static void putIfPresent(Map<String, Object> attrs, String key, String value) {
        if (!isBlank(value)) {
            attrs.put(key, value);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
