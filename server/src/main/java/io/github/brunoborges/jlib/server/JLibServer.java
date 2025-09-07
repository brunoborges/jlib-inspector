package io.github.brunoborges.jlib.server;

import com.sun.net.httpserver.HttpServer;

import io.github.brunoborges.jlib.common.ApplicationIdUtil;
import io.github.brunoborges.jlib.server.handler.AppsHandler;
import io.github.brunoborges.jlib.server.handler.HealthHandler;
import io.github.brunoborges.jlib.server.service.ApplicationService;
import io.github.brunoborges.jlib.server.service.JarService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * HTTP Server for tracking Java applications and their JAR file usage.
 * 
 * <p>
 * This server receives push events from Java agents running in other JVM
 * processes
 * and provides a REST API for querying application and JAR information.
 * 
 * <h3>API Endpoints:</h3>
 * <ul>
 * <li>POST /api/apps - Register a Java application</li>
 * <li>GET /api/apps - List all tracked applications</li>
 * <li>POST /api/apps - Update JAR information for an application</li>
 * <li>GET /health - Health check endpoint</li>
 * </ul>
 * 
 * <h3>Application Data Model:</h3>
 * <p>
 * Each Java application is identified by a hash ID computed from:
 * <ul>
 * <li>JVM command line arguments</li>
 * <li>Checksums of all JAR files mentioned in the command line</li>
 * <li>JDK version</li>
 * </ul>
 */
public class JLibServer {

    private static final Logger logger = Logger.getLogger(JLibServer.class.getName());
    private static final int PORT = 8080;

    private HttpServer server;
    private ApplicationService applicationService;
    private JarService jarService;

    /**
     * Starts the HTTP server on the specified port.
     */
    public void start(int port) throws IOException {
        // Initialize services
        applicationService = new ApplicationService();
        jarService = new JarService();

        // Create HTTP server
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // Configure handlers with dependency injection
        server.createContext("/api/apps", new AppsHandler(applicationService, jarService));
        server.createContext("/health", new HealthHandler(applicationService));

        server.start();
        logger.info("JLib Server started on port " + port);
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("JLib Server stopped");
        }
    }

    /**
     * Registers a new Java application with the server.
     * 
     * @param name         The application name
     * @param commandLine  The command line used to start the application
     * @param jarPaths     List of JAR file paths on the classpath
     * @param jdkVersion   The JDK version
     * @param jarChecksums List of checksums for the top-level JAR files
     */
    public void registerApplication(String name, String commandLine, List<String> jarPaths, String jdkVersion,
            List<String> jarChecksums) {
        String applicationId = ApplicationIdUtil.computeApplicationId(commandLine, jarChecksums, jdkVersion, "unknown",
                "unknown");
        applicationService.getOrCreateApplication(applicationId, commandLine, jdkVersion, "unknown", "unknown");
        logger.info("Registered application: " + applicationId + " (" + name + ")");
    }

    /**
     * Updates JAR information for a specific application.
     * 
     * @param applicationId The application ID
     * @param jarPath       The JAR file path
     * @param jarHash       The JAR file hash
     */
    public void updateJarInApplication(String applicationId, String jarPath, String jarHash) {
        // For now, just log this - we'll need to enhance JarService for individual JAR
        // updates
        logger.info("JAR update for app " + applicationId + ": " + jarPath + " (hash: " + jarHash + ")");
    }

    /**
     * Main entry point for running the server.
     */
    public static void main(String[] args) {
        int port = PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0] + ". Using default port " + PORT);
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
}
