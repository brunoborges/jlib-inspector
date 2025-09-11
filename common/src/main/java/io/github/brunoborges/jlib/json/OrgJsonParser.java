package io.github.brunoborges.jlib.json;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * JSON parser implementation using the org.json library.
 * 
 * <p>This implementation provides robust JSON parsing capabilities using the
 * well-established org.json library. It offers better error handling and
 * performance compared to the built-in parser for complex JSON structures.
 */
@Deprecated(forRemoval = true)
public class OrgJsonParser implements JsonParserInterface {

    private static final Logger LOG = Logger.getLogger(OrgJsonParser.class.getName());

    /**
     * Splits a JSON array string into individual object strings.
     */
    @Override
    public List<String> splitJsonArray(String jsonArray) {
        List<String> entries = new ArrayList<>();
        
        try {
            JSONArray array = new JSONArray(jsonArray);
            
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                entries.add(obj.toString());
                LOG.fine("Split entry " + (i + 1) + ": " + obj.toString().substring(0, Math.min(50, obj.toString().length())) + "...");
            }
            
            LOG.info("Successfully split JSON array into " + entries.size() + " entries using org.json");
            
        } catch (Exception e) {
            LOG.warning("Failed to parse JSON array with org.json: " + e.getMessage());
            // Return empty list on parse failure
        }
        
        return entries;
    }

    /**
     * Parses a simple JSON object into a key-value map.
     */
    @Override
    public Map<String, String> parseSimpleJson(String json) {
        Map<String, String> result = new HashMap<>();
        
        try {
            JSONObject obj = new JSONObject(json);
            
            for (String key : obj.keySet()) {
                Object value = obj.get(key);
                
                // Convert value to string representation
                if (value instanceof String) {
                    result.put(key, (String) value);
                } else if (value instanceof JSONArray || value instanceof JSONObject) {
                    // Keep complex structures as JSON strings
                    result.put(key, value.toString());
                } else {
                    // Convert primitives to strings
                    result.put(key, String.valueOf(value));
                }
            }
            
            LOG.fine("Successfully parsed JSON object with " + result.size() + " key-value pairs using org.json");
            
        } catch (Exception e) {
            LOG.warning("Failed to parse JSON object with org.json: " + e.getMessage());
            // Return empty map on parse failure
        }
        
        return result;
    }

    /**
     * Escapes special characters for JSON string values.
     * 
     * <p>Note: org.json library handles escaping internally when creating JSON,
     * but this method provides explicit escaping for cases where manual string 
     * construction is needed.
     */
    @Override
    public String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        
        // Use org.json's built-in escaping by creating a JSONObject and extracting the escaped value
        try {
            JSONObject temp = new JSONObject();
            temp.put("temp", str);
            String escaped = temp.toString();
            
            // Extract the escaped value between quotes: {"temp":"escaped_value"}
            int start = escaped.indexOf("\":\"") + 3;
            int end = escaped.lastIndexOf("\"}");
            
            if (start >= 3 && end > start) {
                return escaped.substring(start, end);
            }
        } catch (Exception e) {
            LOG.fine("org.json escaping failed, falling back to manual escaping: " + e.getMessage());
        }
        
        // Fallback to manual escaping
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
