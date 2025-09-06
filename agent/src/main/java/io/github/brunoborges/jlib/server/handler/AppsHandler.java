package io.github.brunoborges.jlib.server.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.brunoborges.jlib.server.model.JavaApplication;
import io.github.brunoborges.jlib.server.service.ApplicationService;
import io.github.brunoborges.jlib.server.service.JarService;
import io.github.brunoborges.jlib.util.JsonParser;
import io.github.brunoborges.jlib.util.JsonResponseBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

/**
 * HTTP handler for /api/apps endpoints.
 */
public class AppsHandler implements HttpHandler {
    
    private static final Logger LOG = Logger.getLogger(AppsHandler.class.getName());
    
    private final ApplicationService applicationService;
    private final JarService jarService;
    
    public AppsHandler(ApplicationService applicationService, JarService jarService) {
        this.applicationService = applicationService;
        this.jarService = jarService;
    }
    
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
            sendJsonResponse(exchange, 200, JsonResponseBuilder.buildAppsListJson(applicationService.getAllApplications()));
        } else if (path.matches("/api/apps/[^/]+$")) {
            // GET /api/apps/{appId} - Get specific application
            String appId = extractAppId(path);
            JavaApplication app = applicationService.getApplication(appId);
            if (app != null) {
                sendJsonResponse(exchange, 200, JsonResponseBuilder.buildAppDetailsJson(app));
            } else {
                sendResponse(exchange, 404, "Application not found");
            }
        } else if (path.matches("/api/apps/[^/]+/jars$")) {
            // GET /api/apps/{appId}/jars - List JARs for application
            String appId = extractAppId(path);
            JavaApplication app = applicationService.getApplication(appId);
            if (app != null) {
                sendJsonResponse(exchange, 200, JsonResponseBuilder.buildJarsListJson(app));
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
        Map<String, String> data = JsonParser.parseSimpleJson(jsonData);
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
        
        JavaApplication app = applicationService.getOrCreateApplication(appId, commandLine, jdkVersion, jdkVendor, jdkPath);
        app.updateLastSeen();
        
        // Process JAR updates if present
        String jarsData = data.get("jars");
        if (jarsData != null) {
            jarService.processJarUpdates(app, jarsData);
        }
        
        LOG.info("Updated application: " + appId);
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
