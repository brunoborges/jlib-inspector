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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central inventory of all JARs (top-level + nested) observed or declared by the agent.
 * Each record tracks: loaded state, filename, full path/identifier (including nested !/ form),
 * file size (bytes) and SHA-256 hash (URL-safe base64, or '?' if unavailable).
 */
public final class JarInventory {

    public static final class JarRecord {
        public final String id;          // full path uri, nested keeps outer.jar!/path/inner.jar
        public final String fileName;    // simple filename
        public final long size;          // bytes (-1 if unknown)
        public final String sha256;      // hash or '?'
        private final AtomicBoolean loaded = new AtomicBoolean(false);

        JarRecord(String id, String fileName, long size, String sha256) {
            this.id = id;
            this.fileName = fileName;
            this.size = size;
            this.sha256 = sha256;
        }

        public boolean isLoaded() { return loaded.get(); }
        void markLoaded() { loaded.set(true); }
    }

    private final Map<String, JarRecord> jars = new ConcurrentHashMap<>();

    /** Register a jar we discovered (declared) with optional size & hash supplier. */
    public JarRecord registerDeclared(String id, long size, HashSupplier hashSupplier) {
        return jars.computeIfAbsent(id, k -> new JarRecord(k, simpleName(k), size, computeHash(hashSupplier)));
    }

    /** Mark a jar (top-level or nested) as having provided at least one loaded class. */
    public void markLoaded(String id) {
        if (id == null) return;
        jars.compute(id, (k, existing) -> {
            if (existing == null) {
                existing = new JarRecord(k, simpleName(k), -1L, "?");
            }
            existing.markLoaded();
            return existing;
        });
    }

    public Collection<JarRecord> snapshot() { return new ArrayList<>(jars.values()); }

    private String computeHash(HashSupplier supplier) {
        if (supplier == null) return "?";
        try { return supplier.hash(); } catch (Exception e) { return "?"; }
    }

    private String simpleName(String id) {
        int bang = id.lastIndexOf("!/");
        String tail = bang >= 0 ? id.substring(bang + 2) : id;
        int slash = Math.max(tail.lastIndexOf('/'), tail.lastIndexOf('\\'));
        return slash >= 0 ? tail.substring(slash + 1) : tail;
    }

    /** Print improved human-readable report (sorted + summary). */
    public void report(PrintStream out) {
        var list = new ArrayList<>(snapshot());
        // Sort: loaded first, then top-level before nested, then filename
        list.sort((a,b) -> {
            int cmpLoaded = Boolean.compare(b.isLoaded(), a.isLoaded());
            if (cmpLoaded != 0) return cmpLoaded;
            int cmpNest = Boolean.compare(isTopLevel(a.id), isTopLevel(b.id)); // top-level (true) should come first
            if (cmpNest != 0) return -cmpNest; // invert because true > false
            return a.fileName.compareToIgnoreCase(b.fileName);
        });

        int total = list.size();
        long loaded = list.stream().filter(JarRecord::isLoaded).count();
        long topLevel = list.stream().filter(r -> isTopLevel(r.id)).count();
        long topLevelLoaded = list.stream().filter(r -> isTopLevel(r.id) && r.isLoaded()).count();
        long nested = total - topLevel;
        long nestedLoaded = loaded - topLevelLoaded;
        long totalBytes = list.stream().filter(r -> r.size >= 0).mapToLong(r -> r.size).sum();
        long loadedBytes = list.stream().filter(r -> r.size >= 0 && r.isLoaded()).mapToLong(r -> r.size).sum();

        out.println("Summary");
        out.println(repeat('-', 72));
        out.printf("Total JARs       : %d%n", total);
        out.printf("Loaded JARs      : %d (%.1f%%) %n", loaded, pct(loaded, total));
        out.printf("Top-level JARs   : %d (loaded %d, %.1f%%) %n", topLevel, topLevelLoaded, pct(topLevelLoaded, topLevel));
        out.printf("Nested JARs      : %d (loaded %d, %.1f%%) %n", nested, nestedLoaded, pct(nestedLoaded, nested == 0 ? 1 : nested));
        if (totalBytes > 0) {
            out.printf("Total Size       : %s (%d bytes)%n", human(totalBytes), totalBytes);
            out.printf("Loaded Size      : %s (%d bytes, %.1f%%) %n", human(loadedBytes), loadedBytes, pct(loadedBytes, totalBytes));
        }
        out.println();

        // Table header
        String header = String.format("%s %s %s %8s %12s %s %s %s",
                pad("#",3), "L", "T", "SIZE", "BYTES", pad("SHA256(12)",12), pad("FILENAME",40), "FULL-PATH / ID");
        out.println("Details");
        out.println(repeat('-', header.length()));
        out.println(header);
        out.println(repeat('-', header.length()));

        int index = 1;
        for (JarRecord r : list) {
            String idx = pad(String.valueOf(index++),3);
            String l = r.isLoaded() ? "Y" : "-";
            String t = isTopLevel(r.id) ? "T" : "N"; // top-level or nested
            String sizeHuman = r.size >= 0 ? human(r.size) : "?";
            String sizeBytes = r.size >= 0 ? String.valueOf(r.size) : "?";
            String hash = pad(shorten(r.sha256),12);
            String name = pad(truncate(r.fileName,40),40);
            out.printf("%s %s %s %8s %12s %s %s %s%n", idx, l, t, sizeHuman, sizeBytes, hash, name, r.id);
        }
        out.println(repeat('-', header.length()));
        out.printf("Legend: L=Loaded, T=Top-level, N=Nested. Size is human-readable (base 1024). Hash truncated to 12 chars.%n");
    }

    private String shorten(String h) { return h == null ? "?" : h.length() > 12 ? h.substring(0,12) : h; }
    private String truncate(String s, int max) { return s==null||s.length()<=max? s : s.substring(0,max-3)+"..."; }

    private static boolean isTopLevel(String id) { return !id.contains("!/"); }
    private static double pct(long part, long total) { if (total <= 0) return 0.0; return (part * 100.0) / total; }
    private static String repeat(char c, int n) { return String.valueOf(c).repeat(Math.max(0,n)); }
    private static String pad(String s, int width) { return s.length() >= width ? s : s + repeat(' ', width - s.length()); }
    private static String human(long bytes) {
        if (bytes < 1024) return bytes + "B";
        double v = bytes;
        String[] units = {"KB","MB","GB","TB","PB"};
        int idx = -1; // after first division -> KB (index 0)
        while (v >= 1024 && idx < units.length - 1) {
            v /= 1024.0;
            idx++;
            if (v < 1024 || idx == units.length - 1) break;
        }
        if (idx < 0) idx = 0; // safety
        return String.format("%.1f%s", v, units[idx]);
    }

    @FunctionalInterface
    public interface HashSupplier { String hash() throws Exception; }

    public static HashSupplier fileHashSupplier(File file) {
        return () -> digest(new FileInputStream(file));
    }
    public static HashSupplier nestedJarHashSupplier(java.util.jar.JarFile outer, java.util.jar.JarEntry entry) {
        return () -> digest(outer.getInputStream(entry));
    }

    private static String digest(InputStream in) throws IOException, NoSuchAlgorithmException {
        try (InputStream is = in; DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("SHA-256"))) {
            byte[] buf = new byte[8192];
            while (dis.read(buf) != -1) { /* consume */ }
            byte[] d = dis.getMessageDigest().digest();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
        }
    }
}