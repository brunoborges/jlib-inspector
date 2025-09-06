package io.github.brunoborges.jlib.server.service;

import io.github.brunoborges.jlib.server.model.JavaApplication;
import io.github.brunoborges.jlib.util.JsonParser;
import io.github.brunoborges.jlib.server.model.JarInfo;

import java.util.List;
import java.util.Map;
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
        // Format: [{"path":"...", "fileName":"...", "size":123, "checksum":"...", "loaded":true}, ...]
        LOG.info("Processing JAR data: " + jarsData.substring(0, Math.min(200, jarsData.length())) + "...");
        
        // Check if we have a proper JSON array
        if (!jarsData.trim().startsWith("[") || !jarsData.trim().endsWith("]")) {
            LOG.warning("Expected JSON array format for JAR data, got: " + jarsData.substring(0, Math.min(100, jarsData.length())));
            return;
        }
        
        // Split the JSON array into individual entries
        List<String> jarEntries = JsonParser.splitJsonArray(jarsData);
        LOG.info("Found " + jarEntries.size() + " JAR entries");
        
        for (int i = 0; i < jarEntries.size(); i++) {
            String entry = jarEntries.get(i);
            LOG.info("Processing entry " + (i+1) + ": " + entry.substring(0, Math.min(100, entry.length())) + "...");
            
            Map<String, String> jarData = JsonParser.parseSimpleJson(entry);
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
            
            JarInfo jarInfo = app.jars.computeIfAbsent(path, 
                p -> new JarInfo(p, fileName, size, checksum, loaded));
            
            if (loaded) {
                jarInfo.markLoaded();
            }
        }
        LOG.info("Processed " + app.jars.size() + " total JARs for application");
    }
}
