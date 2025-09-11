package io.github.brunoborges.jlib.server.service;

import io.github.brunoborges.jlib.common.JarMetadata;
import io.github.brunoborges.jlib.common.JavaApplication;
import io.github.brunoborges.jlib.json.JsonParserFactory;
import io.github.brunoborges.jlib.json.JsonParserInterface;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Service for processing JAR updates for applications.
 */
public class JarService {

    private static final Logger LOG = Logger.getLogger(JarService.class.getName());
    private final JsonParserInterface jsonParser = JsonParserFactory.getDefaultParser();

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

        // Split the JSON array into individual entries
        List<String> jarEntries = jsonParser.splitJsonArray(jarsData);
        LOG.info("Found " + jarEntries.size() + " JAR entries");

        for (int i = 0; i < jarEntries.size(); i++) {
            String entry = jarEntries.get(i);
            LOG.info("Processing entry " + (i + 1) + ": " + entry.substring(0, Math.min(100, entry.length()))
                    + "...");

            Map<String, String> jarData = jsonParser.parseSimpleJson(entry);
            String path = jarData.get("path");
            String fileName = jarData.get("fileName");

            LOG.info("Parsed path: " + path + ", fileName: " + fileName);

            // Skip entries with null path (parsing failed)
            if (path == null) {
                LOG.warning("Skipping JAR entry with null path. Entry: " + entry);
                continue;
            }

            long size = Long.parseLong(jarData.getOrDefault("size", "0"));
            String checksum = jarData.get("checksum");
            boolean loaded = Boolean.parseBoolean(jarData.getOrDefault("loaded", "false"));

            // Get or create JAR metadata, then update it
            JarMetadata jarInfo = app.jars.computeIfAbsent(path,
                    p -> new JarMetadata(p, fileName, size, checksum, Instant.now(), Instant.now(), loaded));

            // Extract manifest sub-object if present.
            // Two formats supported:
            // 1) Flattened:   manifest.Implementation-Title="..."
            // 2) Nested obj:  manifest:{"Implementation-Title":"..."}
            java.util.Map<String,String> manifestMap = new java.util.LinkedHashMap<>();
            final String manifestPrefix = "manifest.";
            for (var e : jarData.entrySet()) {
                String k = e.getKey();
                if (k.startsWith(manifestPrefix)) {
                    String realKey = k.substring(manifestPrefix.length());
                    if (!realKey.isBlank()) {
                        manifestMap.put(realKey, trimQuotes(e.getValue()));
                    }
                } else if ("manifest".equals(k)) {
                    String rawObj = e.getValue(); // should be a JSON object string
                    if (rawObj != null && rawObj.startsWith("{") && rawObj.endsWith("}")) {
                        // Parse nested object
                        var nested = jsonParser.parseSimpleJson(rawObj);
                        for (var me : nested.entrySet()) {
                            if (!me.getKey().isBlank()) {
                                manifestMap.put(me.getKey(), trimQuotes(me.getValue()));
                            }
                        }
                    }
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
