package io.github.brunoborges.jlib.agent;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
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

    // Single-thread executor for async HTTP sends (daemon so it won't block JVM exit)
    private final ExecutorService httpExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "jlib-server-client-http");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    });

    /**
     * Asynchronously sends application and JAR inventory data to the configured server.
     * Fire-and-forget style: returns a {@link CompletableFuture} you may observe, but
     * the agent will not block waiting for completion. Heavy work (JSON build + HTTP)
     * is performed off the caller thread.
     *
     * @param applicationId The unique application identifier
     * @param inventory     The jar inventory to send
     * @return future that completes when the HTTP call finishes (success or failure)
     */
    public CompletableFuture<Void> sendApplicationDataAsync(final String applicationId, final JarInventory inventory) {
        return CompletableFuture.supplyAsync(() -> {
            final long start = System.currentTimeMillis();
            try {
                final int jarCount = inventory.snapshot().size();
                LOG.info(() -> "Preparing to send application inventory (async): appId=" + applicationId + ", jars=" + jarCount
                        + ", target=" + baseUrl);

                String json = buildApplicationJson(inventory); // build inside background
                int payloadBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                LOG.info(() -> String.format("Async send %d bytes of inventory data for appId=%s to %s/api/apps/%s", payloadBytes,
                        applicationId, baseUrl, applicationId));

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/apps/" + applicationId))
                        .timeout(Duration.ofSeconds(15))
                        .PUT(HttpRequest.BodyPublishers.ofString(json))
                        .header("Content-Type", "application/json")
                        .build();

                return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .orTimeout(20, TimeUnit.SECONDS)
                        .whenComplete((resp, err) -> {
                            long took = System.currentTimeMillis() - start;
                            if (err != null) {
                                LOG.warning("Async send failed for appId=" + applicationId + ": " + err.getClass().getSimpleName() + " - " + err.getMessage());
                            } else if (resp.statusCode() == 200) {
                                LOG.info(() -> String.format(
                                        "Async inventory sent appId=%s status=%d bytes=%d took=%dms",
                                        applicationId, resp.statusCode(), payloadBytes, took));
                            } else {
                                LOG.warning("Async send non-200 for appId=" + applicationId + " status=" + resp.statusCode() +
                                        " took=" + took + "ms body=" + resp.body());
                            }
                        });
            } catch (Throwable t) {
                LOG.warning("Failed to schedule async send for appId=" + applicationId + ": " + t.getMessage());
                return CompletableFuture.<HttpResponse<String>>failedFuture(t);
            }
        }, httpExecutor).thenCompose(f -> f).thenAccept(r -> {}); // Convert to CompletableFuture<Void>
    }

    /**
     * Best-effort attempt to shutdown the HTTP executor; called optionally in shutdown hooks.
     * Not required since threads are daemon, but allows flushing if desired.
     * @param timeoutMillis time to await termination
     */
    public void shutdown(long timeoutMillis) {
        httpExecutor.shutdown();
        try {
            httpExecutor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
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

        // JVM details snapshot (may be heavy; consider pruning later if needed)
        try {
            io.github.brunoborges.jlib.agent.jvm.ShowJVM show = new io.github.brunoborges.jlib.agent.jvm.ShowJVM();
            var details = show.extractJVMDetails();
            root.put("jvmDetails", details.toJson());
        } catch (Throwable t) {
            LOG.warning("Failed to collect JVM details: " + t.getMessage());
        }

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
