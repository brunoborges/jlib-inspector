package io.github.brunoborges.jlib.common;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unified representation of a JAR file with metadata and tracking information.
 * 
 * <p>This class combines the features needed by both the agent (thread-safe tracking)
 * and the server (temporal information). It supports:
 * <ul>
 *   <li>Full path identification including nested JARs (e.g., outer.jar!/inner.jar)</li>
 *   <li>Thread-safe loaded state tracking</li>
 *   <li>Cryptographic hash verification</li>
 *   <li>Temporal access tracking</li>
 * </ul>
 */
public final class JarMetadata {
    
    /** Full path identifier, supports nested JAR notation (outer.jar!/inner.jar) */
    public final String fullPath;
    
    /** Simple filename without path */
    public final String fileName;
    
    /** File size in bytes, -1 if unknown */
    public final long size;
    
    /** SHA-256 hash in URL-safe base64 format, or "?" if unavailable */
    public final String sha256Hash;

    /** Cached computed JAR identifier (derived hash) */
    private volatile String jarId;
    
    /** Thread-safe loaded state */
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    
    /** When this JAR was first seen */
    public final Instant firstSeen;
    
    /** Last time this JAR was accessed/loaded */
    private volatile Instant lastAccessed;

    /** Immutable manifest attributes (captured once) */
    private volatile Map<String,String> manifestAttributes; // may remain null

    /**
     * Creates a new JAR metadata record.
     * 
     * @param fullPath Full path identifier (supports nested JAR notation)
     * @param fileName Simple filename
     * @param size File size in bytes (-1 if unknown)
     * @param sha256Hash SHA-256 hash in URL-safe base64, or "?" if unavailable
     */
    public JarMetadata(String fullPath, String fileName, long size, String sha256Hash) {
        this.fullPath = fullPath;
        this.fileName = fileName;
        this.size = size;
        this.sha256Hash = sha256Hash;
        this.firstSeen = Instant.now();
        this.lastAccessed = this.firstSeen;
    }

    /**
     * Creates a JAR metadata record with specific timestamps.
     * 
     * @param fullPath Full path identifier
     * @param fileName Simple filename  
     * @param size File size in bytes
     * @param sha256Hash SHA-256 hash
     * @param firstSeen When first discovered
     * @param lastAccessed Last access time
     * @param isLoaded Initial loaded state
     */
    public JarMetadata(String fullPath, String fileName, long size, String sha256Hash, 
                      Instant firstSeen, Instant lastAccessed, boolean isLoaded) {
        this.fullPath = fullPath;
        this.fileName = fileName;
        this.size = size;
        this.sha256Hash = sha256Hash;
        this.firstSeen = firstSeen;
        this.lastAccessed = lastAccessed;
        this.loaded.set(isLoaded);
    }

    /**
     * Checks if this JAR has been loaded (thread-safe).
     */
    public boolean isLoaded() {
        return loaded.get();
    }

    /**
     * Marks this JAR as loaded and updates last accessed time (thread-safe).
     */
    public void markLoaded() {
        loaded.set(true);
        lastAccessed = Instant.now();
    }

    /**
     * Updates the last accessed time.
     */
    public void updateLastAccessed() {
        lastAccessed = Instant.now();
    }

    /**
     * Gets the last accessed time.
     */
    public Instant getLastAccessed() {
        return lastAccessed;
    }

    /**
     * Checks if this is a top-level JAR (not nested).
     */
    public boolean isTopLevel() {
        return !fullPath.contains("!/");
    }

    /**
     * Checks if this is a nested JAR.
     */
    public boolean isNested() {
        return fullPath.contains("!/");
    }

    /**
     * Sets manifest main-section attributes if not already set. Ignores null or empty maps.
     */
    public void setManifestAttributesIfAbsent(Map<String,String> attrs) {
        if (manifestAttributes != null) return;
        if (attrs == null || attrs.isEmpty()) return;
        synchronized (this) {
            if (manifestAttributes == null && attrs != null && !attrs.isEmpty()) {
                // preserve insertion order
                manifestAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(attrs));
            }
        }
    }

    /** Returns manifest main-section attributes or null if not captured. */
    public Map<String,String> getManifestAttributes() {
        return manifestAttributes;
    }

    /**
     * Gets the container JAR path for nested JARs.
     * 
     * @return The outer JAR path, or null if this is a top-level JAR
     */
    public String getContainerJarPath() {
        int bangIndex = fullPath.indexOf("!/");
        return bangIndex >= 0 ? fullPath.substring(0, bangIndex) : null;
    }

    /**
     * Gets the inner path for nested JARs.
     * 
     * @return The inner path, or the full path if this is a top-level JAR
     */
    public String getInnerPath() {
        int bangIndex = fullPath.indexOf("!/");
        return bangIndex >= 0 ? fullPath.substring(bangIndex + 2) : fullPath;
    }

    /**
     * Returns a stable jarId derived from the JAR's checksum and filename.
     * <p>Definition:
     * <ul>
     *   <li>If {@code sha256Hash} is known (not null/"?"), jarId = SHA-256 hex of (sha256Hash + ":" + fileName)</li>
     *   <li>Otherwise jarId = SHA-256 hex of (fileName + ":" + size + ":" + fullPath)</li>
     * </ul>
     * The fallback ensures a deterministic id within a single server runtime even when the real checksum
     * has not yet been computed. Once a checksum becomes available the id may change (callers should be
     * resilient if they query again after hash computation).
     */
    public String getJarId() {
        String local = jarId;
        if (local != null) return local;
        synchronized (this) {
            if (jarId == null) {
                String basis;
                if (sha256Hash != null && !sha256Hash.isEmpty() && !"?".equals(sha256Hash)) {
                    basis = sha256Hash + ":" + fileName;
                } else {
                    basis = fileName + ":" + size + ":" + fullPath;
                }
                jarId = sha256Hex(basis);
            }
            return jarId;
        }
    }

    private static String sha256Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // Very unlikely; fallback to hex of hashCode
            return Integer.toHexString(input.hashCode());
        }
    }

    @Override
    public String toString() {
        return String.format("JarMetadata{path='%s', fileName='%s', size=%d, loaded=%s, hash='%s'}", 
                           fullPath, fileName, size, isLoaded(), 
                           sha256Hash.length() > 12 ? sha256Hash.substring(0, 12) + "..." : sha256Hash);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JarMetadata that = (JarMetadata) o;
        return fullPath.equals(that.fullPath);
    }

    @Override
    public int hashCode() {
        return fullPath.hashCode();
    }
}
