package io.github.brunoborges.jlib.agent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.brunoborges.jlib.common.JarMetadata;

/**
 * Central inventory of all JARs (top-level + nested) observed or declared by
 * the agent.
 * Each record tracks: loaded state, filename, full path/identifier (including
 * nested !/ form),
 * file size (bytes) and SHA-256 hash (URL-safe base64, or '?' if unavailable).
 */
public final class JarInventory {

    private final Map<String, JarMetadata> jars = new ConcurrentHashMap<>();

    /**
     * Register a jar we discovered (declared) with optional size & hash supplier.
     */
    public JarMetadata registerDeclared(String id, long size, HashSupplier hashSupplier) {
        return jars.computeIfAbsent(id, k -> new JarMetadata(k, simpleName(k), size, computeHash(hashSupplier)));
    }

    /**
     * Mark a jar (top-level or nested) as having provided at least one loaded
     * class.
     */
    public void markLoaded(String id) {
        if (id == null)
            return;
        jars.compute(id, (k, existing) -> {
            if (existing == null) {
                existing = new JarMetadata(k, simpleName(k), -1L, "?");
            }
            existing.markLoaded();
            return existing;
        });
    }

    public Collection<JarMetadata> snapshot() {
        return new ArrayList<>(jars.values());
    }

    /** Attach manifest attributes to an existing jar if absent. */
    public void attachManifest(String id, Map<String,String> manifestAttrs) {
        if (id == null || manifestAttrs == null || manifestAttrs.isEmpty()) return;
        JarMetadata meta = jars.get(id);
        if (meta != null) {
            meta.setManifestAttributesIfAbsent(manifestAttrs);
        }
    }

    private String computeHash(HashSupplier supplier) {
        if (supplier == null) {
            return "?";
        }

        try {
            return supplier.hash();
        } catch (Exception e) {
            return "?";
        }
    }

    private String simpleName(String id) {
        int bang = id.lastIndexOf("!/");
        String tail = bang >= 0 ? id.substring(bang + 2) : id;
        int slash = Math.max(tail.lastIndexOf('/'), tail.lastIndexOf('\\'));
        return slash >= 0 ? tail.substring(slash + 1) : tail;
    }

    /** Print improved human-readable report (sorted + summary). */
    public void report(PrintStream out) {
        JarInventoryReport.generateReport(snapshot(), out);
    }

    @FunctionalInterface
    public interface HashSupplier {
        String hash() throws Exception;
    }

    public static HashSupplier fileHashSupplier(File file) {
        return () -> digest(new FileInputStream(file));
    }

    public static HashSupplier nestedJarHashSupplier(java.util.jar.JarFile outer, java.util.jar.JarEntry entry) {
        return () -> digest(outer.getInputStream(entry));
    }

    private static String digest(InputStream in) throws IOException, NoSuchAlgorithmException {
        try (InputStream is = in;
                DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("SHA-256"))) {
            byte[] buf = new byte[8192];
            while (dis.read(buf) != -1) {
                /* consume */ }
            byte[] d = dis.getMessageDigest().digest();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
        }
    }
}