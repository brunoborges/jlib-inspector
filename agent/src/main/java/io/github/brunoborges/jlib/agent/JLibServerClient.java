package io.github.brunoborges.jlib.agent;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.logging.Logger;

import io.github.brunoborges.jlib.common.JarMetadata;

/**
 * Handles HTTP communication with the JLib server.
 * 
 * <p>This class is responsible for sending application and JAR inventory data
 * to the configured JLib server endpoint, including JSON serialization and
 * HTTP request handling.
 */
public class JLibServerClient {
    
    private static final Logger LOG = Logger.getLogger(JLibServerClient.class.getName());
    
    private final String serverHost;
    private final int serverPort;
    
    /**
     * Creates a new server client for the specified host and port.
     * 
     * @param serverHost The server hostname or IP address
     * @param serverPort The server port number
     */
    public JLibServerClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }
    
    /**
     * Sends application and JAR inventory data to the configured server.
     * 
     * @param applicationId The unique application identifier
     * @param inventory The jar inventory to send
     * @throws Exception if sending fails
     */
    public void sendApplicationData(String applicationId, JarInventory inventory) throws Exception {
        LOG.info("Sending data to server at " + serverHost + ":" + serverPort);

        // Build JSON payload
        String json = buildApplicationJson(inventory);

        // Send PUT request using modern HTTP client
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + serverHost + ":" + serverPort + "/api/apps/" + applicationId))
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            LOG.info("Successfully sent data to server");
        } else {
            LOG.warning("Server returned error code: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * Builds JSON representation of the application for server reporting.
     * 
     * @param inventory The jar inventory
     * @return JSON string representing the application data
     */
    private String buildApplicationJson(JarInventory inventory) {
        StringBuilder json = new StringBuilder();

        // Get JDK information
        String jdkVersion = System.getProperty("java.version");
        String jdkVendor = System.getProperty("java.vendor");
        String jdkPath = System.getProperty("java.home");

        // Get the complete command line including main class and arguments
        String commandLine = getFullCommandLine();

        json.append("{");
        json.append("\"commandLine\":\"").append(escapeJson(commandLine)).append("\",");
        json.append("\"jdkVersion\":\"").append(jdkVersion).append("\",");
        json.append("\"jdkVendor\":\"").append(escapeJson(jdkVendor)).append("\",");
        json.append("\"jdkPath\":\"").append(escapeJson(jdkPath)).append("\",");

        // Include JAR inventory data as a proper JSON array (not escaped string)
        json.append("\"jars\":[");
        boolean first = true;
        for (JarMetadata jar : inventory.snapshot()) {
            if (!first) {
                json.append(",");
            }
            json.append("{");
            json.append("\"path\":\"").append(escapeJson(jar.fullPath)).append("\",");
            json.append("\"fileName\":\"").append(escapeJson(jar.fileName)).append("\",");
            json.append("\"size\":").append(jar.size).append(",");
            json.append("\"checksum\":\"").append(escapeJson(jar.sha256Hash)).append("\",");
            json.append("\"loaded\":").append(jar.isLoaded());
            json.append("}");
            first = false;
        }
        json.append("]");
        json.append("}");

        return json.toString();
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
     * Escapes special characters for JSON string values.
     * 
     * @param str The string to escape
     * @return JSON-safe escaped string
     */
    private static String escapeJson(String str) {
        if (str == null)
            return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
