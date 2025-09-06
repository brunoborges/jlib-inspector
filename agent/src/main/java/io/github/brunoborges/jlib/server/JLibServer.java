package io.github.brunoborges.jlib.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * HTTP Server for tracking Java applications and their JAR file usage.
 * 
 * <p>This server receives push events from Java agents running in other JVM processes
 * and provides a REST API for querying application and JAR information.
 * 
 * <h3>API Endpoints:</h3>
 * <ul>
 *   <li>PUT /api/apps/{appId} - Register or update a Java application</li>
 *   <li>GET /api/apps - List all tracked applications</li>
 *   <li>GET /api/apps/{appId} - Get details for a specific application</li>
 *   <li>GET /api/apps/{appId}/jars - List JARs for a specific application</li>
 *   <li>GET /health - Health check endpoint</li>
 * </ul>
 * 
 * <h3>Application Data Model:</h3>
 * <p>Each Java application is identified by a hash ID computed from:
 * <ul>
 *   <li>JVM command line arguments</li>
 *   <li>Checksums of all JAR files mentioned in the command line</li>
 *   <li>JDK version, vendor, and installation path</li>
 * </ul>
 */
public class JLibServer {
    
    private static final Logger LOG = Logger.getLogger(JLibServer.class.getName());
    private static final int DEFAULT_PORT = 8080;
    
    /** In-memory storage for tracked Java applications */
    private final Map<String, JavaApplication> applications = new ConcurrentHashMap<>();
    
    /** HTTP server instance */
    private HttpServer server;
    
    /**
     * Represents a tracked Java application with its metadata and JAR inventory.
     */
    public static class JavaApplication {
        public final String appId;
        public final String commandLine;
        public final String jdkVersion;
        public final String jdkVendor;
        public final String jdkPath;
        public final Instant firstSeen;
        public volatile Instant lastUpdated;
        public final Map<String, JarInfo> jars = new ConcurrentHashMap<>();
        
        public JavaApplication(String appId, String commandLine, String jdkVersion, 
                             String jdkVendor, String jdkPath) {
            this.appId = appId;
            this.commandLine = commandLine;
            this.jdkVersion = jdkVersion;
            this.jdkVendor = jdkVendor;
            this.jdkPath = jdkPath;
            this.firstSeen = Instant.now();
            this.lastUpdated = this.firstSeen;
        }
        
        public void updateLastSeen() {
            this.lastUpdated = Instant.now();
        }
    }
    
    /**
     * Represents a JAR file with its metadata and load status.
     */
    public static class JarInfo {
        public final String jarPath;
        public final String fileName;
        public final long size;
        public final String checksum;
        public volatile boolean isLoaded;
        public volatile Instant lastAccessed;
        
        public JarInfo(String jarPath, String fileName, long size, String checksum, boolean isLoaded) {
            this.jarPath = jarPath;
            this.fileName = fileName;
            this.size = size;
            this.checksum = checksum;
            this.isLoaded = isLoaded;
            this.lastAccessed = Instant.now();
        }
        
        public void markLoaded() {
            this.isLoaded = true;
            this.lastAccessed = Instant.now();
        }
    }
    
    /**
     * Starts the HTTP server on the specified port.
     * 
     * @param port The port to bind the server to
     * @throws IOException If the server cannot be started
     */
    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));
        
        // Register API endpoints
        server.createContext("/api/apps", new AppsHandler());
        server.createContext("/health", new HealthHandler());
        
        server.start();
        LOG.info("JLib HTTP Server started on port " + port);
        LOG.info("Available endpoints:");
        LOG.info("  PUT /api/apps/{appId} - Register/update application");
        LOG.info("  GET /api/apps - List all applications");
        LOG.info("  GET /api/apps/{appId} - Get application details");
        LOG.info("  GET /api/apps/{appId}/jars - List application JARs");
        LOG.info("  GET /health - Health check");
    }
    
    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            LOG.info("JLib HTTP Server stopped");
        }
    }
    
    /**
     * Main entry point for running the server.
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0] + ". Using default port " + DEFAULT_PORT);
            }
        }
        
        JLibServer server = new JLibServer();
        try {
            server.start(port);
            
            // Keep the server running
            System.out.println("JLib Server is running on port " + port);
            System.out.println("Press Ctrl+C to stop the server");
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            
            // Keep main thread alive
            Thread.currentThread().join();
            
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.stop();
        }
    }
    
    /**
     * Computes a hash ID for a Java application based on command line and JAR checksums.
     * This method should be called by the InspectorAgent.
     * 
     * @param commandLine The full JVM command line
     * @param jarChecksums List of checksums for all JARs in the command line
     * @param jdkVersion JDK version string
     * @param jdkVendor JDK vendor string
     * @param jdkPath JDK installation path
     * @return SHA-256 hash ID representing this unique application configuration
     */
    public static String computeApplicationId(String commandLine, List<String> jarChecksums, 
                                            String jdkVersion, String jdkVendor, String jdkPath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Include all identifying information in the hash
            digest.update(commandLine.getBytes(StandardCharsets.UTF_8));
            digest.update(jdkVersion.getBytes(StandardCharsets.UTF_8));
            digest.update(jdkVendor.getBytes(StandardCharsets.UTF_8));
            digest.update(jdkPath.getBytes(StandardCharsets.UTF_8));
            
            // Include JAR checksums in sorted order for consistency
            jarChecksums.stream()
                    .sorted()
                    .forEach(checksum -> digest.update(checksum.getBytes(StandardCharsets.UTF_8)));
            
            // Convert to hex string
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute application ID", e);
        }
    }
    
    /**
     * HTTP handler for /api/apps endpoints.
     */
    private class AppsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            
            try {
                switch (method) {
                    case "GET":
                        handleGet(exchange, path);
                        break;
                    case "PUT":
                        handlePut(exchange, path);
                        break;
                    default:
                        sendResponse(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                LOG.severe("Error handling request: " + e.getMessage());
                sendResponse(exchange, 500, "Internal server error");
            }
        }
        
        private void handleGet(HttpExchange exchange, String path) throws IOException {
            if (path.equals("/api/apps")) {
                // GET /api/apps - List all applications
                sendJsonResponse(exchange, 200, buildAppsListJson());
            } else if (path.matches("/api/apps/[^/]+$")) {
                // GET /api/apps/{appId} - Get specific application
                String appId = extractAppId(path);
                JavaApplication app = applications.get(appId);
                if (app != null) {
                    sendJsonResponse(exchange, 200, buildAppDetailsJson(app));
                } else {
                    sendResponse(exchange, 404, "Application not found");
                }
            } else if (path.matches("/api/apps/[^/]+/jars$")) {
                // GET /api/apps/{appId}/jars - List JARs for application
                String appId = extractAppId(path);
                JavaApplication app = applications.get(appId);
                if (app != null) {
                    sendJsonResponse(exchange, 200, buildJarsListJson(app));
                } else {
                    sendResponse(exchange, 404, "Application not found");
                }
            } else {
                sendResponse(exchange, 404, "Not found");
            }
        }
        
        private void handlePut(HttpExchange exchange, String path) throws IOException {
            if (path.matches("/api/apps/[^/]+$")) {
                // PUT /api/apps/{appId} - Register/update application
                String appId = extractAppId(path);
                String requestBody = readRequestBody(exchange);
                
                LOG.info("PUT request for app: " + appId);
                LOG.info("Request body: " + requestBody);
                
                try {
                    processApplicationUpdate(appId, requestBody);
                    sendResponse(exchange, 200, "Application updated successfully");
                } catch (Exception e) {
                    LOG.warning("Failed to process application update: " + e.getMessage());
                    e.printStackTrace(); // Add stack trace for debugging
                    sendResponse(exchange, 400, "Invalid request data: " + e.getMessage());
                }
            } else {
                sendResponse(exchange, 404, "Not found");
            }
        }
        
        private String extractAppId(String path) {
            String[] parts = path.split("/");
            return parts[3]; // /api/apps/{appId}
        }
        
        private String readRequestBody(HttpExchange exchange) throws IOException {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line).append("\n");
                }
                return body.toString().trim();
            }
        }
        
        private void processApplicationUpdate(String appId, String jsonData) {
            LOG.info("Processing update for app: " + appId);
            LOG.info("JSON data received: " + jsonData);
            
            // Simple JSON parsing - in production, use a proper JSON library
            Map<String, String> data = parseSimpleJson(jsonData);
            LOG.info("Parsed data: " + data);
            
            String commandLine = data.get("commandLine");
            String jdkVersion = data.get("jdkVersion");
            String jdkVendor = data.get("jdkVendor");
            String jdkPath = data.get("jdkPath");
            
            if (commandLine == null || jdkVersion == null || jdkVendor == null || jdkPath == null) {
                LOG.warning("Missing required fields. commandLine=" + commandLine + 
                           ", jdkVersion=" + jdkVersion + ", jdkVendor=" + jdkVendor + ", jdkPath=" + jdkPath);
                throw new IllegalArgumentException("Missing required application fields");
            }
            
            JavaApplication app = applications.computeIfAbsent(appId, 
                id -> new JavaApplication(id, commandLine, jdkVersion, jdkVendor, jdkPath));
            
            app.updateLastSeen();
            
            // Process JAR updates if present
            String jarsData = data.get("jars");
            if (jarsData != null) {
                processJarUpdates(app, jarsData);
            }
            
            LOG.info("Updated application: " + appId);
        }
        
        private void processJarUpdates(JavaApplication app, String jarsData) {
            // Parse JAR data - now expects raw JSON array format
            // Format: [{"path":"...", "fileName":"...", "size":123, "checksum":"...", "loaded":true}, ...]
            LOG.info("Processing JAR data: " + jarsData.substring(0, Math.min(200, jarsData.length())) + "...");
            
            // Check if we have a proper JSON array
            if (!jarsData.trim().startsWith("[") || !jarsData.trim().endsWith("]")) {
                LOG.warning("Expected JSON array format for JAR data, got: " + jarsData.substring(0, Math.min(100, jarsData.length())));
                return;
            }
            
            // Split the JSON array into individual entries
            List<String> jarEntries = splitJsonArray(jarsData);
            LOG.info("Found " + jarEntries.size() + " JAR entries");
            
            for (int i = 0; i < jarEntries.size(); i++) {
                String entry = jarEntries.get(i);
                LOG.info("Processing entry " + (i+1) + ": " + entry.substring(0, Math.min(100, entry.length())) + "...");
                
                Map<String, String> jarData = parseSimpleJson(entry);
                String path = jarData.get("path");
                String fileName = jarData.get("fileName");
                
                LOG.info("Parsed path: " + path + ", fileName: " + fileName);
                
                // Skip entries with null path (parsing failed)
                if (path == null) {
                    LOG.warning("Skipping JAR entry with null path. Entry: " + entry);
                    continue;
                }
                
                long size = Long.parseLong(jarData.getOrDefault("size", "0"));
                String checksum = jarData.get("checksum");
                boolean loaded = Boolean.parseBoolean(jarData.getOrDefault("loaded", "false"));
                
                JarInfo jarInfo = app.jars.computeIfAbsent(path, 
                    p -> new JarInfo(p, fileName, size, checksum, loaded));
                
                if (loaded) {
                    jarInfo.markLoaded();
                }
            }
            LOG.info("Processed " + app.jars.size() + " total JARs for application");
        }
        
        private List<String> splitJsonArray(String jsonArray) {
            List<String> entries = new ArrayList<>();
            jsonArray = jsonArray.trim();
            
            // Remove outer array brackets
            if (jsonArray.startsWith("[") && jsonArray.endsWith("]")) {
                jsonArray = jsonArray.substring(1, jsonArray.length() - 1).trim();
            }
            
            // If empty after removing brackets, return empty list
            if (jsonArray.isEmpty()) {
                return entries;
            }
            
            StringBuilder current = new StringBuilder();
            int braceDepth = 0;
            boolean inQuotes = false;
            boolean escapeNext = false;
            
            for (int i = 0; i < jsonArray.length(); i++) {
                char c = jsonArray.charAt(i);
                
                if (escapeNext) {
                    escapeNext = false;
                    current.append(c);
                    continue;
                }
                
                if (c == '\\') {
                    escapeNext = true;
                    current.append(c);
                    continue;
                }
                
                if (c == '"') {
                    inQuotes = !inQuotes;
                }
                
                if (!inQuotes) {
                    if (c == '{') {
                        braceDepth++;
                    } else if (c == '}') {
                        braceDepth--;
                    }
                }
                
                if (c == ',' && braceDepth == 0 && !inQuotes) {
                    // End of current object
                    String entry = current.toString().trim();
                    if (!entry.isEmpty()) {
                        entries.add(entry);
                        LOG.info("Split entry " + entries.size() + ": " + entry.substring(0, Math.min(50, entry.length())) + "...");
                    }
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
            
            // Add the last entry
            String entry = current.toString().trim();
            if (!entry.isEmpty()) {
                entries.add(entry);
                LOG.info("Final entry " + entries.size() + ": " + entry.substring(0, Math.min(50, entry.length())) + "...");
            }
            
            LOG.info("Successfully split JSON array into " + entries.size() + " entries");
            return entries;
        }
        
        private Map<String, String> parseSimpleJson(String json) {
            Map<String, String> result = new HashMap<>();
            // Very basic JSON parsing - replace with proper JSON library
            json = json.trim();
            if (json.startsWith("{") && json.endsWith("}")) {
                json = json.substring(1, json.length() - 1);
            }
            
            // Split by comma but be careful with nested structures
            List<String> pairs = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            int bracketDepth = 0;
            boolean inQuotes = false;
            
            for (char c : json.toCharArray()) {
                if (c == '"' && bracketDepth == 0) {
                    inQuotes = !inQuotes;
                }
                if (!inQuotes) {
                    if (c == '[' || c == '{') bracketDepth++;
                    if (c == ']' || c == '}') bracketDepth--;
                }
                
                if (c == ',' && bracketDepth == 0 && !inQuotes) {
                    pairs.add(current.toString().trim());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
            pairs.add(current.toString().trim());
            
            for (String pair : pairs) {
                int colonIndex = pair.indexOf(':');
                if (colonIndex > 0) {
                    String key = pair.substring(0, colonIndex).trim().replaceAll("\"", "");
                    String value = pair.substring(colonIndex + 1).trim();
                    
                    // Remove quotes from string values but keep array/object structure
                    if (value.startsWith("\"") && value.endsWith("\"") && 
                        !value.startsWith("\"[") && !value.startsWith("\"{")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    result.put(key, value);
                }
            }
            return result;
        }
        
        private String buildAppsListJson() {
            StringBuilder json = new StringBuilder();
            json.append("{\"applications\":[");
            boolean first = true;
            for (JavaApplication app : applications.values()) {
                if (!first) json.append(",");
                json.append("{")
                    .append("\"appId\":\"").append(app.appId).append("\",")
                    .append("\"commandLine\":\"").append(escapeJson(app.commandLine)).append("\",")
                    .append("\"jdkVersion\":\"").append(app.jdkVersion).append("\",")
                    .append("\"jdkVendor\":\"").append(app.jdkVendor).append("\",")
                    .append("\"firstSeen\":\"").append(app.firstSeen).append("\",")
                    .append("\"lastUpdated\":\"").append(app.lastUpdated).append("\",")
                    .append("\"jarCount\":").append(app.jars.size())
                    .append("}");
                first = false;
            }
            json.append("]}");
            return json.toString();
        }
        
        private String buildAppDetailsJson(JavaApplication app) {
            return "{" +
                "\"appId\":\"" + app.appId + "\"," +
                "\"commandLine\":\"" + escapeJson(app.commandLine) + "\"," +
                "\"jdkVersion\":\"" + app.jdkVersion + "\"," +
                "\"jdkVendor\":\"" + app.jdkVendor + "\"," +
                "\"jdkPath\":\"" + escapeJson(app.jdkPath) + "\"," +
                "\"firstSeen\":\"" + app.firstSeen + "\"," +
                "\"lastUpdated\":\"" + app.lastUpdated + "\"," +
                "\"jarCount\":" + app.jars.size() +
                "}";
        }
        
        private String buildJarsListJson(JavaApplication app) {
            StringBuilder json = new StringBuilder();
            json.append("{\"jars\":[");
            boolean first = true;
            for (JarInfo jar : app.jars.values()) {
                if (!first) json.append(",");
                json.append("{")
                    .append("\"path\":\"").append(escapeJson(jar.jarPath)).append("\",")
                    .append("\"fileName\":\"").append(jar.fileName).append("\",")
                    .append("\"size\":").append(jar.size).append(",")
                    .append("\"checksum\":\"").append(jar.checksum).append("\",")
                    .append("\"loaded\":").append(jar.isLoaded).append(",")
                    .append("\"lastAccessed\":\"").append(jar.lastAccessed).append("\"")
                    .append("}");
                first = false;
            }
            json.append("]}");
            return json.toString();
        }
        
        private String escapeJson(String str) {
            if (str == null) return "";
            return str.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
        }
    }
    
    /**
     * HTTP handler for /health endpoint.
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String response = "{\"status\":\"healthy\",\"applications\":" + applications.size() + "}";
                sendJsonResponse(exchange, 200, response);
            } else {
                sendResponse(exchange, 405, "Method not allowed");
            }
        }
    }
    
    /**
     * Sends a plain text HTTP response.
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    /**
     * Sends a JSON HTTP response.
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
