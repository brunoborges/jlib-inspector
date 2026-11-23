package io.github.brunoborges.jlib.agent;

import io.github.brunoborges.jlib.common.ApplicationIdUtil;
import io.github.brunoborges.jlib.common.JarMetadata;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
 * <li>{@code -javaagent:jlib-inspector-agent.jar=otel} - Export OpenTelemetry
 * events (endpoint from {@code OTEL_EXPORTER_OTLP_*} or
 * {@code http://localhost:4318})</li>
 * <li>{@code -javaagent:jlib-inspector-agent.jar=server:8080,otel:http://collector:4318}
 * - Combine options with commas</li>
 * <li>{@code -javaagent:jlib-inspector-agent.jar=server:8080,interval:5m} -
 * Also report every 5 minutes while the application runs (units: s, m,
 * h)</li>
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
    // OpenTelemetry exporter (null unless the "otel" option is set)
    private static OtelEventExporter otelExporter;
    // Interval between periodic reports (null to report only at shutdown)
    private static Duration reportInterval;
    private static String applicationId;

    private static final Duration MIN_REPORT_INTERVAL = Duration.ofSeconds(1);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;
    private static final long PERIODIC_TIMEOUT_SECONDS = 30;

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

        // Generate application ID if any remote reporting is enabled
        if (serverClient != null || otelExporter != null) {
            applicationId = generateApplicationId(inventory, classpathTracker);
            LOG.info("Application ID: " + applicationId + " (will be reported)");
        }

        ScheduledExecutorService reporter = startPeriodicReporting(inventory);

        // Shutdown hook to emit consolidated report
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (reporter != null) {
                reporter.shutdownNow();
            }
            System.out.println("");
            inventory.report(System.out);
            System.out.println("");

            // Best effort: wait a short time so the HTTP calls can complete, but don't block shutdown indefinitely.
            report(inventory, SHUTDOWN_TIMEOUT_SECONDS);
        }, "jlib-inspector-shutdown"));
    }

    /**
     * Starts reporting to the configured destinations every
     * {@link #reportInterval}, if set.
     *
     * @return the scheduler, or null when reporting happens only at shutdown
     */
    private static ScheduledExecutorService startPeriodicReporting(JarInventory inventory) {
        if (reportInterval == null) {
            return null;
        }
        if (serverClient == null && otelExporter == null) {
            LOG.warning("Ignoring interval option: enable server or otel reporting to use it");
            return null;
        }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jlib-inspector-reporter");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        long millis = reportInterval.toMillis();
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                report(inventory, PERIODIC_TIMEOUT_SECONDS);
            } catch (RuntimeException e) {
                // An exception would cancel all future runs
                LOG.warning("Periodic report failed: " + e);
            }
        }, millis, millis, TimeUnit.MILLISECONDS);
        LOG.info("Reporting every " + reportInterval.toSeconds() + "s and at shutdown");
        return scheduler;
    }

    /**
     * Sends the inventory to every configured destination and waits for the
     * sends to finish, up to the given timeout.
     */
    private static void report(JarInventory inventory, long timeoutSeconds) {
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        if (serverClient != null) {
            try {
                pending.add(serverClient.sendApplicationDataAsync(applicationId, inventory));
            } catch (Throwable e) {
                LOG.warning("Failed to schedule async send to server: " + e.getMessage());
            }
        }
        if (otelExporter != null) {
            try {
                pending.add(otelExporter.exportAsync(inventory, applicationId));
            } catch (Throwable e) {
                LOG.warning("Failed to schedule OpenTelemetry export: " + e.getMessage());
            }
        }
        if (!pending.isEmpty()) {
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                    .orTimeout(timeoutSeconds, TimeUnit.SECONDS).exceptionally(ex -> {
                        LOG.fine(() -> "Async send did not complete before timeout: " + ex.getMessage());
                        return null;
                    }).join();
        }
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
     * Parses agent arguments to configure optional server reporting and
     * OpenTelemetry export.
     * 
     * @param args Agent arguments string: comma-separated options among
     *             "server:port", "server:host:port", "otel", "otel:endpoint"
     *             and "interval:duration"
     */
    private static void parseArguments(String args) {
        // Environment variable JLIB_SERVER_URL takes precedence over the server option
        boolean serverFromEnv = false;
        if (serverClient == null) {
            serverClient = JLibServerClient.fromEnv();
            if (serverClient != null) {
                serverFromEnv = true;
                LOG.info("Configured to report to server via environment variable JLIB_SERVER_URL");
            }
        }

        if (args == null || args.trim().isEmpty()) {
            return; // Local reporting only (unless env var set)
        }

        for (String option : args.split(",")) {
            String opt = option.trim();
            if (opt.startsWith("server:")) {
                if (!serverFromEnv) {
                    parseServerOption(opt);
                }
            } else if (opt.equals("otel") || opt.startsWith("otel:")) {
                otelExporter = OtelEventExporter.create(opt.length() > 5 ? opt.substring(5) : null);
                LOG.info("Configured to export OpenTelemetry events to " + otelExporter.endpoint());
            } else if (opt.startsWith("interval:")) {
                try {
                    reportInterval = parseInterval(opt.substring(9));
                } catch (IllegalArgumentException | ArithmeticException e) {
                    LOG.warning("Invalid interval specification '" + opt + "': " + e.getMessage());
                }
            } else if (!opt.isEmpty()) {
                LOG.warning("Ignoring unrecognized agent option: " + opt);
            }
        }
    }

    /**
     * Parses a reporting interval such as {@code 30}, {@code 30s}, {@code 5m} or
     * {@code 1h}. A number without a unit is in seconds.
     *
     * @throws IllegalArgumentException if invalid or shorter than one second
     */
    static Duration parseInterval(String spec) {
        String s = spec.trim().toLowerCase(Locale.ROOT);
        long unitSeconds = 1;
        if (s.endsWith("h")) {
            unitSeconds = 3600;
        } else if (s.endsWith("m")) {
            unitSeconds = 60;
        }
        if (!s.isEmpty() && Character.isLetter(s.charAt(s.length() - 1))) {
            if ("hms".indexOf(s.charAt(s.length() - 1)) < 0) {
                throw new IllegalArgumentException("unknown unit, use s, m or h");
            }
            s = s.substring(0, s.length() - 1).trim();
        }
        Duration interval = Duration.ofMillis(
                Math.multiplyExact(Math.multiplyExact(Long.parseLong(s), unitSeconds), 1000L));
        if (interval.compareTo(MIN_REPORT_INTERVAL) < 0) {
            throw new IllegalArgumentException("must be at least " + MIN_REPORT_INTERVAL.toSeconds() + "s");
        }
        return interval;
    }

    private static void parseServerOption(String args) {
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
    static String getFullCommandLine() {
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
