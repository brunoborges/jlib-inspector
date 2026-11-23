package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the repackaged sample app with the shaded jlib-inspector agent attached and
 * verifies the agent reports the Spring Boot jars (nested in the fat jar) over OTLP.
 */
class AgentIntegrationIT {

    @Test
    void agentReportsSpringBootJarsFromRepackagedApp(@TempDir Path tmp) throws Exception {
        Path appJar = requiredPath("app.jar");
        Path agentJar = requiredPath("agent.jar");
        String bootVersion = System.getProperty("spring.boot.version");
        assertNotNull(bootVersion, "spring.boot.version not set");

        List<String> bodies = new CopyOnWriteArrayList<>();
        List<String> requestLines = new CopyOnWriteArrayList<>();
        List<String> contentTypes = new CopyOnWriteArrayList<>();
        HttpServer otel = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        otel.createContext("/", exchange -> {
            requestLines.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            contentTypes.add(String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        otel.start();
        try {
            Path output = tmp.resolve("app-output.log");
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            Process process = new ProcessBuilder(java,
                    "-javaagent:" + agentJar + "=otel:http://127.0.0.1:" + otel.getAddress().getPort(),
                    "-jar", appJar.toString(), "hello-it")
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();

            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("App did not exit within 60s:\n" + Files.readString(output));
            }
            String log = Files.readString(output);

            assertEquals(0, process.exitValue(), log);
            assertTrue(log.contains("EXECUTING : command line runner"), log);
            assertTrue(log.contains("args[0]: hello-it"), log);

            assertFalse(bodies.isEmpty(), "Agent exported no OTLP events:\n" + log);
            assertFalse(log.contains("OpenTelemetry export to"), "Agent reported an export failure:\n" + log);
            assertTrue(requestLines.stream().allMatch("POST /v1/logs"::equals),
                    "Unexpected OTLP requests: " + requestLines);
            assertTrue(contentTypes.stream().allMatch(c -> c.startsWith("application/json")),
                    "Unexpected OTLP content types: " + contentTypes);

            String payload = String.join("\n", bodies);
            assertTrue(payload.contains("\"resourceLogs\""), payload);
            assertTrue(payload.contains("\"logRecords\""), payload);
            assertTrue(payload.contains("\"eventName\":\"package.info\""), payload);

            // Resource attributes identify the reporting process
            List<Map<String, String>> attributeSets = attributeSets(payload);
            assertTrue(attributeSets.stream().anyMatch(a -> a.containsKey("service.name") && a.containsKey("process.pid")),
                    "Missing service.name/process.pid resource attributes:\n" + payload);

            // The spring-boot jar is nested inside the fat jar and must be fully described
            Map<String, String> springBoot = packageRecord(attributeSets, "spring-boot-" + bootVersion + ".jar");
            assertNotNull(springBoot, "Expected spring-boot " + bootVersion + " in agent report:\n" + payload);
            assertEquals("jar", springBoot.get("package.type"), springBoot.toString());
            assertEquals("SHA-256", springBoot.get("package.checksum_algorithm"), springBoot.toString());
            assertTrue(springBoot.getOrDefault("package.checksum", "").matches("[0-9a-f]{64}"),
                    "Expected a SHA-256 hex checksum: " + springBoot);
            assertEquals("true", springBoot.get("jlib.jar.nested"), springBoot.toString());

            assertNotNull(packageRecord(attributeSets, "spring-core-"), "Expected spring-core in agent report");
        } finally {
            otel.stop(0);
        }
    }

    private static final Pattern ATTRIBUTES = Pattern.compile("\"attributes\":\\[(.*?)\\]");
    // org.json does not preserve key order, so accept "key" before or after "value"
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "\\{\"key\":\"([^\"]+)\",\"value\":\\{\"\\w+\":\"?([^\"}]*)\"?\\}\\}"
                    + "|\\{\"value\":\\{\"\\w+\":\"?([^\"}]*)\"?\\},\"key\":\"([^\"]+)\"\\}");

    /** Parses every OTLP attribute list in the payload into key to value maps. */
    private static List<Map<String, String>> attributeSets(String payload) {
        List<Map<String, String>> sets = new ArrayList<>();
        Matcher lists = ATTRIBUTES.matcher(payload);
        while (lists.find()) {
            Map<String, String> attrs = new HashMap<>();
            Matcher m = ATTRIBUTE.matcher(lists.group(1));
            while (m.find()) {
                if (m.group(1) != null) {
                    attrs.put(m.group(1), m.group(2));
                } else {
                    attrs.put(m.group(4), m.group(3));
                }
            }
            sets.add(attrs);
        }
        return sets;
    }

    /** Returns the first package.info record whose package.path contains the given text, or null. */
    private static Map<String, String> packageRecord(List<Map<String, String>> sets, String pathFragment) {
        return sets.stream()
                .filter(a -> "package.info".equals(a.get("event.name")))
                .filter(a -> a.getOrDefault("package.path", "").contains(pathFragment))
                .findFirst()
                .orElse(null);
    }

    private static Path requiredPath(String property) {
        String value = System.getProperty(property);
        assertNotNull(value, property + " not set");
        Path path = Path.of(value).normalize();
        assertTrue(Files.isRegularFile(path), path + " does not exist");
        return path;
    }
}
