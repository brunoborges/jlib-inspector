package io.github.brunoborges.jlib.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Utility class for computing application IDs.
 */
public class ApplicationIdUtil {

    /**
     * Computes a hash ID for a Java application based on command line and JAR
     * checksums.
     * 
     * @param commandLine  The full JVM command line
     * @param jarChecksums List of checksums for all JARs in the command line
     * @param jdkVersion   JDK version string
     * @param jdkVendor    JDK vendor string
     * @param jdkPath      JDK installation path
     * @return SHA-256 hash ID representing this unique application configuration
     */
    public static String computeApplicationId(String commandLine, List<String> jarChecksums,
            String jdkVersion, String jdkVendor, String jdkPath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Include all identifying information in the hash
            digest.update(commandLine.getBytes(StandardCharsets.UTF_8));
            digest.update(jdkVersion.getBytes(StandardCharsets.UTF_8));
            digest.update(jdkVendor.getBytes(StandardCharsets.UTF_8));
            digest.update(jdkPath.getBytes(StandardCharsets.UTF_8));

            // Include JAR checksums in sorted order for consistency
            jarChecksums.stream()
                    .sorted()
                    .forEach(checksum -> digest.update(checksum.getBytes(StandardCharsets.UTF_8)));

            // Convert to hex string
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to compute application ID", e);
        }
    }
}
