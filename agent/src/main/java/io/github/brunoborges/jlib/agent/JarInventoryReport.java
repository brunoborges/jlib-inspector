package io.github.brunoborges.jlib.agent;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.github.brunoborges.jlib.common.JarMetadata;

/**
 * Handles reporting and formatting of JAR inventory data.
 * 
 * <p>This class is responsible for generating human-readable reports from JAR inventory data,
 * including summary statistics, detailed tables, and various formatting utilities.
 */
class JarInventoryReport {

    /**
     * Generates a comprehensive human-readable report of JAR inventory data.
     * 
     * @param jarData Collection of JAR metadata to report on
     * @param out PrintStream to write the report to
     */
    public static void generateReport(Collection<JarMetadata> jarData, PrintStream out) {
        var list = new ArrayList<>(jarData);
        
        // Sort: loaded first, then top-level before nested, then filename
        list.sort((a, b) -> {
            int cmpLoaded = Boolean.compare(b.isLoaded(), a.isLoaded());
            if (cmpLoaded != 0)
                return cmpLoaded;
            int cmpNest = Boolean.compare(a.isTopLevel(), b.isTopLevel()); // top-level (true) should come first
            if (cmpNest != 0)
                return -cmpNest; // invert because true > false
            return a.fileName.compareToIgnoreCase(b.fileName);
        });

        printSummary(list, out);
        printDetailedTable(list, out);
    }

    /**
     * Prints summary statistics about the JAR inventory.
     */
    private static void printSummary(List<JarMetadata> list, PrintStream out) {
        int total = list.size();
        long loaded = list.stream().filter(JarMetadata::isLoaded).count();
        long topLevel = list.stream().filter(r -> r.isTopLevel()).count();
        long topLevelLoaded = list.stream().filter(r -> r.isTopLevel() && r.isLoaded()).count();
        long nested = total - topLevel;
        long nestedLoaded = loaded - topLevelLoaded;
        long totalBytes = list.stream().filter(r -> r.size >= 0).mapToLong(r -> r.size).sum();
        long loadedBytes = list.stream().filter(r -> r.size >= 0 && r.isLoaded()).mapToLong(r -> r.size).sum();

        out.println("Summary");
        out.println(repeat('-', 72));
        out.printf("Total JARs       : %d%n", total);
        out.printf("Loaded JARs      : %d (%.1f%%) %n", loaded, percentage(loaded, total));
        out.printf("Top-level JARs   : %d (loaded %d, %.1f%%) %n", topLevel, topLevelLoaded,
                percentage(topLevelLoaded, topLevel));
        out.printf("Nested JARs      : %d (loaded %d, %.1f%%) %n", nested, nestedLoaded,
                percentage(nestedLoaded, nested == 0 ? 1 : nested));
        if (totalBytes > 0) {
            out.printf("Total Size       : %s (%d bytes)%n", humanReadableSize(totalBytes), totalBytes);
            out.printf("Loaded Size      : %s (%d bytes, %.1f%%) %n", humanReadableSize(loadedBytes), loadedBytes,
                    percentage(loadedBytes, totalBytes));
        }
        out.println();
    }

    /**
     * Prints a detailed table of all JAR entries.
     */
    private static void printDetailedTable(List<JarMetadata> list, PrintStream out) {
        // Table header
        String header = String.format("%s %s %s %8s %12s %s %s %s",
                pad("#", 3), "L", "T", "SIZE", "BYTES", pad("SHA256(12)", 12), pad("FILENAME", 40), "FULL-PATH / ID");
        
        out.println("Details");
        out.println(repeat('-', header.length()));
        out.println(header);
        out.println(repeat('-', header.length()));

        int index = 1;
        for (JarMetadata r : list) {
            String idx = pad(String.valueOf(index++), 3);
            String l = r.isLoaded() ? "Y" : "-";
            String t = r.isTopLevel() ? "T" : "N"; // top-level or nested
            String sizeHuman = r.size >= 0 ? humanReadableSize(r.size) : "?";
            String sizeBytes = r.size >= 0 ? String.valueOf(r.size) : "?";
            String hash = pad(truncateString(r.sha256Hash, 12), 12);
            String name = pad(truncateString(r.fileName, 40), 40);
            out.printf("%s %s %s %8s %12s %s %s %s%n", idx, l, t, sizeHuman, sizeBytes, hash, name, r.fullPath);
        }
        
        out.println(repeat('-', header.length()));
        out.printf("Legend: L=Loaded, T=Top-level, N=Nested. Size is human-readable (base 1024). Hash truncated to 12 chars.%n");
    }

    /**
     * Truncates a string to the specified maximum length, adding "..." if truncated.
     */
    private static String truncateString(String s, int max) {
        if (s == null) return "?";
        if (s.length() <= max) return s;
        if (max <= 3) return s.substring(0, max);
        return s.substring(0, max - 3) + "...";
    }

    /**
     * Calculates percentage as a double.
     */
    private static double percentage(long part, long total) {
        if (total <= 0) return 0.0;
        return (part * 100.0) / total;
    }

    /**
     * Creates a string by repeating a character n times.
     */
    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(Math.max(0, n));
    }

    /**
     * Pads a string to the specified width with spaces.
     */
    private static String pad(String s, int width) {
        if (s == null) s = "";
        return s.length() >= width ? s : s + repeat(' ', width - s.length());
    }

    /**
     * Converts bytes to human-readable format (e.g., "1.5KB", "2.3MB").
     */
    private static String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        
        double value = bytes;
        String[] units = { "KB", "MB", "GB", "TB", "PB" };
        int unitIndex = -1;
        
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
            if (value < 1024 || unitIndex == units.length - 1) break;
        }
        
        if (unitIndex < 0) unitIndex = 0; // safety fallback
        return String.format("%.1f%s", value, units[unitIndex]);
    }
}
