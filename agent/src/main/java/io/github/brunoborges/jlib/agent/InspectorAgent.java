package io.github.brunoborges.jlib.agent;

import io.github.brunoborges.jlib.common.ApplicationIdUtil;
import io.github.brunoborges.jlib.common.JarMetadata;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Java instrumentation agent for tracking JAR loading and usage across
 * applications.
 * 
 * <p>
 * Usage options:
 * <ul>
 * <li>{@code -javaagent:jlib-inspector-agent.jar} - Local reporting only</li>
 * <li>{@code -javaagent:jlib-inspector-agent.jar=server:8080} - Report to
 * server on localhost:8080</li>
 * <li>{@code -javaagent:jlib-inspector-agent.jar=server:remote-host:9000} -
 * Report to specific host:port</li>
 * </ul>
 * 
 * <p>
 * The agent tracks both declared JARs (from classpath) and actually loaded JARs
 * (from class loading events),
 * providing comprehensive visibility into JAR usage patterns and potential
 * optimization opportunities.
 */
public class InspectorAgent {

    private static final Logger LOG = Logger.getLogger(InspectorAgent.class.getName());
    private static final Set<WeakReference<ClassLoader>> LOADERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Server configuration (null if local-only mode)
    private static JLibServerClient serverClient;
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
                        if (id != null)
                            inventory.markLoaded(id);
                    }
                }
            } catch (Throwable ignored) {
                /* defensive */ }
        }

        var classTransformer = new ClassLoaderTrackerTransformer(LOADERS, inventory);
        inst.addTransformer(classTransformer, false);

        // Capture declared vs actually loaded classpath JARs at startup (registers into
        // inventory)
        ClasspathJarTracker classpathTracker = new ClasspathJarTracker(inst, inventory);

        // Generate application ID if server mode is enabled
        if (serverClient != null) {
            applicationId = generateApplicationId(inventory, classpathTracker);
            LOG.info("Application ID: " + applicationId + " (will report to server)");
        }

        // Shutdown hook to emit consolidated report
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("");
            inventory.report(System.out);
            System.out.println("");

            // Send to server if configured
            if (serverClient != null) {
                try {
                    serverClient.sendApplicationData(applicationId, inventory);
                } catch (Exception e) {
                    LOG.warning("Failed to send data to server: " + e.getMessage());
                }
            }
        }, "jlib-inspector-shutdown"));
    }

    private static String normalizeLocation(String url) {
        if (url == null)
            return null;
        int bang = url.indexOf('!');
        if (bang > 0)
            url = url.substring(0, bang);
        if (url.startsWith("file:")) {
            if (url.endsWith(".jar"))
                return url;
            if (!url.endsWith("/"))
                url += "/";
            return url;
        }
        return url;
    }

    /**
     * Parses agent arguments to configure optional server reporting.
     * 
     * @param args Agent arguments string. Format: "server:port" or
     *             "server:host:port"
     */
    private static void parseArguments(String args) {
        // 1) Highest precedence: environment variable JLIB_SERVER_URL
        if (serverClient == null) {
            serverClient = JLibServerClient.fromEnv();
            if (serverClient != null) {
                LOG.info("Configured to report to server via environment variable JLIB_SERVER_URL");
                return; // env var wins; ignore args
            }
        }

        if (args == null || args.trim().isEmpty()) {
            return; // Local reporting only (no env var either)
        }

        if (args.startsWith("server:")) {
            String serverSpec = args.substring(7);
            String[] parts = serverSpec.split(":");
            try {
                String serverHost;
                int serverPort;
                if (parts.length == 1) {
                    serverHost = "localhost";
                    serverPort = Integer.parseInt(parts[0]);
                } else if (parts.length == 2) {
                    serverHost = parts[0];
                    serverPort = Integer.parseInt(parts[1]);
                } else {
                    LOG.warning("Invalid server specification: " + args);
                    return;
                }
                serverClient = new JLibServerClient(serverHost, serverPort);
                LOG.info("Configured to report to server at " + serverHost + ":" + serverPort);
            } catch (NumberFormatException e) {
                LOG.warning("Invalid port in server specification: " + args);
            }
        }
    }

    /**
     * Generates a unique application ID based on command line and environment.
     * 
     * @return SHA-256 hash representing this unique application configuration
     */
    private static String generateApplicationId(JarInventory inventory, ClasspathJarTracker classpathTracker) {
        try {
            // Get complete command line
            String commandLine = getFullCommandLine();

            // Get JDK information
            String jdkVersion = System.getProperty("java.version");
            String jdkVendor = System.getProperty("java.vendor");
            String jdkPath = System.getProperty("java.home");

            // Extract checksums of top-level JARs from the classpath
            List<String> jarChecksums = new ArrayList<>();
            for (String jarUri : classpathTracker.getDeclaredClasspathJars()) {
                // Find the corresponding JAR record in the inventory
                for (JarMetadata jarRecord : inventory.snapshot()) {
                    if (jarRecord.fullPath.equals(jarUri)) {
                        // Only include JARs that have valid checksums (not "?")
                        if (!"?".equals(jarRecord.sha256Hash)) {
                            jarChecksums.add(jarRecord.sha256Hash);
                        }
                        break;
                    }
                }
            }

            LOG.info("Collected " + jarChecksums.size() + " top-level JAR checksums for application ID generation");

            // Use the utility class for application ID generation
            return ApplicationIdUtil.computeApplicationId(commandLine, jarChecksums,
                    jdkVersion, jdkVendor, jdkPath);
        } catch (Exception e) {
            LOG.warning("Failed to generate application ID: " + e.getMessage());
            return "unknown-" + System.currentTimeMillis();
        }
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
}
