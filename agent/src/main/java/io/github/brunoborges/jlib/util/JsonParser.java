package io.github.brunoborges.jlib.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Simple JSON parser utility. In production, use a proper JSON library.
 */
public class JsonParser {

    private static final Logger LOG = Logger.getLogger(JsonParser.class.getName());

    /**
     * Splits a JSON array string into individual object strings.
     */
    public static List<String> splitJsonArray(String jsonArray) {
        List<String> entries = new ArrayList<>();
        jsonArray = jsonArray.trim();

        // Remove outer array brackets
        if (jsonArray.startsWith("[") && jsonArray.endsWith("]")) {
            jsonArray = jsonArray.substring(1, jsonArray.length() - 1).trim();
        }

        // If empty after removing brackets, return empty list
        if (jsonArray.isEmpty()) {
            return entries;
        }

        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        boolean inQuotes = false;
        boolean escapeNext = false;

        for (int i = 0; i < jsonArray.length(); i++) {
            char c = jsonArray.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                current.append(c);
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                current.append(c);
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
            }

            if (!inQuotes) {
                if (c == '{') {
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                }
            }

            if (c == ',' && braceDepth == 0 && !inQuotes) {
                // End of current object
                String entry = current.toString().trim();
                if (!entry.isEmpty()) {
                    entries.add(entry);
                    LOG.fine("Split entry " + entries.size() + ": " + entry.substring(0, Math.min(50, entry.length()))
                            + "...");
                }
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        // Add the last entry
        String entry = current.toString().trim();
        if (!entry.isEmpty()) {
            entries.add(entry);
            LOG.fine("Final entry " + entries.size() + ": " + entry.substring(0, Math.min(50, entry.length())) + "...");
        }

        LOG.info("Successfully split JSON array into " + entries.size() + " entries");
        return entries;
    }

    /**
     * Parses a simple JSON object into a key-value map.
     */
    public static Map<String, String> parseSimpleJson(String json) {
        Map<String, String> result = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        }

        // Split by comma but be careful with nested structures
        List<String> pairs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;
        boolean inQuotes = false;

        for (char c : json.toCharArray()) {
            if (c == '"' && bracketDepth == 0) {
                inQuotes = !inQuotes;
            }
            if (!inQuotes) {
                if (c == '[' || c == '{')
                    bracketDepth++;
                if (c == ']' || c == '}')
                    bracketDepth--;
            }

            if (c == ',' && bracketDepth == 0 && !inQuotes) {
                pairs.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        pairs.add(current.toString().trim());

        for (String pair : pairs) {
            int colonIndex = pair.indexOf(':');
            if (colonIndex > 0) {
                String key = pair.substring(0, colonIndex).trim().replaceAll("\"", "");
                String value = pair.substring(colonIndex + 1).trim();

                // Remove quotes from string values but keep array/object structure
                if (value.startsWith("\"") && value.endsWith("\"") &&
                        !value.startsWith("\"[") && !value.startsWith("\"{")) {
                    value = value.substring(1, value.length() - 1);
                }

                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * Escapes special characters for JSON.
     */
    public static String escapeJson(String str) {
        if (str == null)
            return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
