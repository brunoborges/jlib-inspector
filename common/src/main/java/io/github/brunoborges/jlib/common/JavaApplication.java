package io.github.brunoborges.jlib.common;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a tracked Java application with its metadata and JAR inventory.
 */
public class JavaApplication {
    public final String appId;
    public final String commandLine;
    public final String jdkVersion;
    public final String jdkVendor;
    public final String jdkPath;
    public final Instant firstSeen;
    public volatile Instant lastUpdated;
    public final Map<String, JarMetadata> jars = new ConcurrentHashMap<>();

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
