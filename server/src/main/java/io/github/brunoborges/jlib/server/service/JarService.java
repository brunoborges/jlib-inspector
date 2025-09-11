package io.github.brunoborges.jlib.server.service;

import io.github.brunoborges.jlib.common.JarMetadata;
import io.github.brunoborges.jlib.common.JavaApplication;
// Replaced custom JsonParser usage with org.json for robust parsing.
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.logging.Logger;

/**
 * Service for processing JAR updates for applications.
 */
public class JarService {

    private static final Logger LOG = Logger.getLogger(JarService.class.getName());

    /**
     * Processes JAR updates for an application.
     */
    public void processJarUpdates(JavaApplication app, String jarsData) {
        // Parse JAR data - expects raw JSON array format
        // Format: [{"path":"...", "fileName":"...", "size":123, "checksum":"...",
        // "loaded":true}, ...]
        LOG.info("Processing JAR data: " + jarsData.substring(0, Math.min(200, jarsData.length())) + "...");

        // Check if we have a proper JSON array
        if (!jarsData.trim().startsWith("[") || !jarsData.trim().endsWith("]")) {
            LOG.warning("Expected JSON array format for JAR data, got: "
                    + jarsData.substring(0, Math.min(100, jarsData.length())));
            return;
        }

        try {
            JSONArray array = new JSONArray(jarsData);
            LOG.info("Found " + array.length() + " JAR entries");
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                LOG.info("Processing entry " + (i + 1) + ": " + obj.toString().substring(0, Math.min(100, obj.toString().length())) + "...");

                String path = obj.optString("path", null);
                String fileName = obj.optString("fileName", "");
                if (path == null) {
                    LOG.warning("Skipping JAR entry with null path. Index: " + i);
                    continue;
                }
                long size = obj.optLong("size", 0L);
                String checksum = obj.has("checksum") ? obj.optString("checksum", null) : null;
                boolean loaded = obj.optBoolean("loaded", false);

                JarMetadata jarInfo = app.jars.computeIfAbsent(path,
                        p -> new JarMetadata(p, fileName, size, checksum, Instant.now(), Instant.now(), loaded));

                // Manifest extraction: support nested 'manifest' object and flattened 'manifest.' keys
                java.util.Map<String,String> manifestMap = new java.util.LinkedHashMap<>();
                final String manifestPrefix = "manifest.";
                for (String key : obj.keySet()) {
                    if (key.startsWith(manifestPrefix)) {
                        String realKey = key.substring(manifestPrefix.length());
                        if (!realKey.isBlank()) manifestMap.put(realKey, trimQuotes(obj.optString(key))); 
                    }
                }
                if (obj.has("manifest") && obj.get("manifest") instanceof JSONObject nested) {
                    for (String k : nested.keySet()) {
                        String v = nested.optString(k, null);
                        if (v != null && !k.isBlank()) manifestMap.put(k, v);
                    }
                }

                boolean needsReplacement = (jarInfo.size != size || !java.util.Objects.equals(jarInfo.sha256Hash, checksum));
                if (needsReplacement) {
                    JarMetadata newJar = new JarMetadata(path, fileName, size, checksum, jarInfo.firstSeen, Instant.now(), loaded);
                    if (!manifestMap.isEmpty()) {
                        newJar.setManifestAttributesIfAbsent(manifestMap);
                    } else if (jarInfo.getManifestAttributes() != null) {
                        newJar.setManifestAttributesIfAbsent(jarInfo.getManifestAttributes());
                    }
                    app.jars.put(path, newJar);
                } else {
                    if (!manifestMap.isEmpty()) {
                        jarInfo.setManifestAttributesIfAbsent(manifestMap);
                    }
                    if (loaded) {
                        jarInfo.markLoaded();
                    }
                }
            }
        } catch (Exception e) {
            LOG.warning("Failed to parse JAR array with org.json: " + e.getMessage());
        }
        LOG.info("Processed " + app.jars.size() + " total JARs for application");
        
        // Update application's last updated timestamp
        app.lastUpdated = Instant.now();
    }

    private static String trimQuotes(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length()-1);
        }
        return s;
    }
}
