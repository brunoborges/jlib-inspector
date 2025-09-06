package io.github.brunoborges.jlib.server.model;

import java.time.Instant;

/**
 * Represents a JAR file with its metadata and load status.
 */
public class JarInfo {
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
