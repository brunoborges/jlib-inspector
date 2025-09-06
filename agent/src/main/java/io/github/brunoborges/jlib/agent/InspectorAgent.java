package io.github.brunoborges.jlib.agent;

import io.github.brunoborges.jlib.server.JLibServer;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Java instrumentation agent for tracking JAR loading and usage across applications.
 * 
 * <p>Usage options:
 * <ul>
 *   <li>{@code -javaagent:jlib-inspector-agent.jar} - Local reporting only</li>
 *   <li>{@code -javaagent:jlib-inspector-agent.jar=server:8080} - Report to server on localhost:8080</li>
 *   <li>{@code -javaagent:jlib-inspector-agent.jar=server:remote-host:9000} - Report to specific host:port</li>
 * </ul>
 * 
 * <p>The agent tracks both declared JARs (from classpath) and actually loaded JARs (from class loading events),
 * providing comprehensive visibility into JAR usage patterns and potential optimization opportunities.
 */
public class InspectorAgent {

  private static final Logger LOG = Logger.getLogger(InspectorAgent.class.getName());
  private static final Set<WeakReference<ClassLoader>> LOADERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
  
  // Server configuration (null if local-only mode)
  private static String serverHost;
  private static int serverPort;
  private static String applicationId;

  public static void premain(String args, Instrumentation inst) {
    // Parse agent arguments for optional server configuration
    parseArguments(args);
    
    JarInventory inventory = new JarInventory();

    // Snapshot what's already there (and mark their sources as loaded)
    for (Class<?> c : inst.getAllLoadedClasses()) {
      ClassLoader cl = c.getClassLoader(); // null == bootstrap
      if (cl != null) {
        LOADERS.add(new WeakReference<>(cl));
      }
      try {
        java.security.ProtectionDomain pd = c.getProtectionDomain();
        if (pd != null) {
          java.security.CodeSource cs = pd.getCodeSource();
          if (cs != null && cs.getLocation() != null) {
            String id = normalizeLocation(cs.getLocation().toString());
            if (id != null) inventory.markLoaded(id);
          }
        }
      } catch (Throwable ignored) { /* defensive */ }
    }

    var classTransformer = new ClassLoaderTrackerTransformer(LOADERS, inventory);
    inst.addTransformer(classTransformer, false);

    // Capture declared vs actually loaded classpath JARs at startup (registers into inventory)
    new ClasspathJarTracker(inst, inventory);

    // Generate application ID if server mode is enabled
    if (serverHost != null && serverPort > 0) {
      applicationId = generateApplicationId();
      LOG.info("Application ID: " + applicationId + " (will report to " + serverHost + ":" + serverPort + ")");
    }

    // Shutdown hook to emit consolidated report
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("");
      inventory.report(System.out);
      System.out.println("");
      
      // Send to server if configured
      if (serverHost != null && serverPort > 0) {
        try {
          sendToServer(inventory);
        } catch (Exception e) {
          LOG.warning("Failed to send data to server: " + e.getMessage());
        }
      }
    }, "jlib-inspector-shutdown"));
  }

  private static String normalizeLocation(String url) {
    if (url == null) return null;
    int bang = url.indexOf('!');
    if (bang > 0) url = url.substring(0, bang);
    if (url.startsWith("file:")) {
      if (url.endsWith(".jar")) return url;
      if (!url.endsWith("/")) url += "/";
      return url;
    }
    return url;
  }

  /**
   * Parses agent arguments to configure optional server reporting.
   * 
   * @param args Agent arguments string. Format: "server:port" or "server:host:port"
   */
  private static void parseArguments(String args) {
    if (args == null || args.trim().isEmpty()) {
      return; // Local reporting only
    }
    
    // Format: server:port or server:host:port
    if (args.startsWith("server:")) {
      String serverSpec = args.substring(7);
      String[] parts = serverSpec.split(":");
      
      if (parts.length == 1) {
        // server:port
        serverHost = "localhost";
        serverPort = Integer.parseInt(parts[0]);
      } else if (parts.length == 2) {
        // server:host:port
        serverHost = parts[0];
        serverPort = Integer.parseInt(parts[1]);
      }
      
      LOG.info("Configured to report to server at " + serverHost + ":" + serverPort);
    }
  }

  /**
   * Generates a unique application ID based on command line and environment.
   * 
   * @return SHA-256 hash representing this unique application configuration
   */
  private static String generateApplicationId() {
    try {
      // Get complete command line
      String commandLine = getFullCommandLine();
      
      // Get JDK information
      String jdkVersion = System.getProperty("java.version");
      String jdkVendor = System.getProperty("java.vendor");
      String jdkPath = System.getProperty("java.home");
      
      // Use the server's application ID generation logic
      return JLibServer.computeApplicationId(commandLine, Collections.emptyList(), 
                                            jdkVersion, jdkVendor, jdkPath);
    } catch (Exception e) {
      LOG.warning("Failed to generate application ID: " + e.getMessage());
      return "unknown-" + System.currentTimeMillis();
    }
  }

  /**
   * Sends application and JAR inventory data to the configured server.
   * 
   * @param inventory The jar inventory to send
   * @throws Exception if sending fails
   */
  private static void sendToServer(JarInventory inventory) throws Exception {
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
  private static String buildApplicationJson(JarInventory inventory) {
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
    for (JarInventory.JarRecord jar : inventory.snapshot()) {
      if (!first) {
        json.append(",");
      }
      json.append("{");
      json.append("\"path\":\"").append(escapeJson(jar.id)).append("\",");
      json.append("\"fileName\":\"").append(escapeJson(jar.fileName)).append("\",");
      json.append("\"size\":").append(jar.size).append(",");
      json.append("\"checksum\":\"").append(escapeJson(jar.sha256)).append("\",");
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
    if (str == null) return "";
    return str.replace("\\", "\\\\")
             .replace("\"", "\\\"")
             .replace("\n", "\\n")
             .replace("\r", "\\r")
             .replace("\t", "\\t");
  }
}
