package io.github.brunoborges.jlib.agent;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import io.github.brunoborges.jlib.common.JarMetadata;

/**
 * Handles HTTP communication with the JLib server.
 * 
 * <p>
 * This class is responsible for sending application and JAR inventory data
 * to the configured JLib server endpoint, including JSON serialization and
 * HTTP request handling.
 */
public class JLibServerClient {

    private static final Logger LOG = Logger.getLogger(JLibServerClient.class.getName());
    private static final String ENV_VAR = "JLIB_SERVER_URL";
    private static final int DEFAULT_PORT = 8080;

    // Normalized base URL (scheme://host[:port][/optionalPath]) with no trailing
    // '/'
    private final String baseUrl;

    /**
     * Creates a new server client for the specified host and port.
     * 
     * @param serverHost The server hostname or IP address
     * @param serverPort The server port number
     */
    public JLibServerClient(String serverHost, int serverPort) {
        String override = System.getenv(ENV_VAR);
        if (override != null && !override.isBlank()) {
            this.baseUrl = normalizeBaseUrl(override);
            LOG.info("Using server URL from env var " + ENV_VAR + " = " + this.baseUrl);
        } else {
            this.baseUrl = normalizeBaseUrl("http://" + serverHost + ":" + serverPort);
        }
    }

    /**
     * Factory that returns a client if the environment variable is present,
     * otherwise null.
     */
    public static JLibServerClient fromEnv() {
        String override = System.getenv(ENV_VAR);
        if (override == null || override.isBlank())
            return null;
        return new JLibServerClient("localhost", DEFAULT_PORT); // constructor will override with env
    }

    /**
     * Sends application and JAR inventory data to the configured server.
     * 
     * @param applicationId The unique application identifier
     * @param inventory     The jar inventory to send
     * @throws Exception if sending fails
     */
    public void sendApplicationData(final String applicationId, JarInventory inventory) throws Exception {
        final long start = System.currentTimeMillis();
        final int jarCount = inventory.snapshot().size();

        LOG.info(() -> "Preparing to send application inventory: appId=" + applicationId + ", jars=" + jarCount
                + ", target=" + baseUrl);

        // Build JSON payload
        String json = buildApplicationJson(inventory);
        int payloadBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        LOG.info(() -> String.format("Sending %d bytes of inventory data for appId=%s to %s/api/apps/%s", payloadBytes,
                applicationId, baseUrl, applicationId));

        // Send PUT request using modern HTTP client
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/apps/" + applicationId))
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            long took = System.currentTimeMillis() - start;
            LOG.info(() -> String.format(
                    "Successfully sent inventory for appId=%s (status=%d, jars=%d, bytes=%d, took=%dms)",
                    applicationId, response.statusCode(), jarCount, payloadBytes, took));
        } else {
            long took = System.currentTimeMillis() - start;
            LOG.warning("Failed to send inventory for appId=" + applicationId +
                    " status=" + response.statusCode() +
                    " took=" + took + "ms body=" + response.body());
        }
    }

    /**
     * Builds JSON representation of the application for server reporting.
     * 
     * @param inventory The jar inventory
     * @return JSON string representing the application data
     */
    private String buildApplicationJson(JarInventory inventory) {
        JSONObject root = new JSONObject();
        root.put("commandLine", getFullCommandLine());
        root.put("jdkVersion", System.getProperty("java.version"));
        root.put("jdkVendor", System.getProperty("java.vendor"));
        root.put("jdkPath", System.getProperty("java.home"));

        JSONArray jars = new JSONArray();
        for (JarMetadata jar : inventory.snapshot()) {
            JSONObject jo = new JSONObject();
            jo.put("path", jar.fullPath);
            jo.put("fileName", jar.fileName);
            jo.put("size", jar.size);
            jo.put("checksum", jar.sha256Hash);
            jo.put("loaded", jar.isLoaded());
            if (jar.getManifestAttributes() != null && !jar.getManifestAttributes().isEmpty()) {
                JSONObject man = new JSONObject();
                for (var e : jar.getManifestAttributes().entrySet()) {
                    man.put(e.getKey(), e.getValue());
                }
                jo.put("manifest", man);
            }
            jars.put(jo);
        }
        root.put("jars", jars);
        return root.toString();
    }

    /**
     * Gets the complete command line of the current Java process.
     * This includes JVM arguments, main class, and program arguments.
     * 
     * @return Complete command line string
     */
    private static String getFullCommandLine() {
        try {
            // Try to get the complete command line using ProcessHandle (Java 9+)
            ProcessHandle currentProcess = ProcessHandle.current();
            ProcessHandle.Info processInfo = currentProcess.info();

            if (processInfo.commandLine().isPresent()) {
                String fullCmd = processInfo.commandLine().get();
                // If we got a complete command line, return it
                if (fullCmd.contains("java") && (fullCmd.contains("-jar") || fullCmd.contains(" "))) {
                    return fullCmd;
                }
            }

            // Fallback: construct from available information
            StringBuilder cmdLine = new StringBuilder();

            // Add java executable path
            cmdLine.append("java");

            // Add JVM arguments
            List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : jvmArgs) {
                cmdLine.append(" ").append(arg);
            }

            // Add main class and arguments
            String mainClass = System.getProperty("sun.java.command");
            if (mainClass != null && !mainClass.trim().isEmpty()) {
                cmdLine.append(" ");

                // Check if it's a JAR execution
                String[] parts = mainClass.split(" ", 2);
                String mainPart = parts[0];

                if (mainPart.endsWith(".jar")) {
                    // It's a JAR file, add -jar flag
                    cmdLine.append("-jar ").append(mainClass);
                } else {
                    // It's a main class
                    cmdLine.append(mainClass);
                }
            }

            return cmdLine.toString();

        } catch (Exception e) {
            LOG.warning("Failed to get full command line: " + e.getMessage());
            // Final fallback - return what we can get
            StringBuilder fallback = new StringBuilder("java");
            List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : jvmArgs) {
                fallback.append(" ").append(arg);
            }
            String mainClass = System.getProperty("sun.java.command");
            if (mainClass != null && !mainClass.trim().isEmpty()) {
                fallback.append(" ");
                if (mainClass.endsWith(".jar")) {
                    fallback.append("-jar ");
                }
                fallback.append(mainClass);
            }
            return fallback.toString();
        }
    }
    /**
     * Normalizes and validates the base URL.
     * Ensures it has a scheme, host, and optional port/path, with no trailing slash.
     * 
     * @param raw The raw URL string
     * @return Normalized URL string
     */
    private static String normalizeBaseUrl(String raw) {
        try {
            String v = raw.trim();
            if (!v.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
                v = "http://" + v; // default scheme
            }
            URI uri = URI.create(v);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            // If URI couldn't parse host (e.g., raw host w/out scheme already handled; else
            // fallback)
            if (host == null) {
                // Could be something like http://localhost:8080 without parsing? host null
                // rarely.
                return v.replaceAll("/$", "");
            }
            int port = uri.getPort();
            StringBuilder b = new StringBuilder();
            b.append(scheme).append("://").append(host);
            if (port != -1)
                b.append(":").append(port);
            String path = uri.getPath();
            if (path != null && !path.isBlank() && !"/".equals(path)) {
                if (!path.startsWith("/"))
                    b.append('/');
                if (path.endsWith("/"))
                    path = path.substring(0, path.length() - 1);
                b.append(path);
            }
            return b.toString();
        } catch (Exception e) {
            LOG.warning("Failed to normalize server URL '" + raw + "': " + e.getMessage());
            return raw.replaceAll("/$", "");
        }
    }
}
