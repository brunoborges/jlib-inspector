package io.github.brunoborges.jlib.json;

import java.util.List;
import java.util.Map;

/**
 * Interface for JSON parsing operations.
 * 
 * <p>This interface defines the contract for JSON parsing implementations,
 * allowing the system to use different JSON libraries while maintaining
 * consistent behavior.
 */
@Deprecated(forRemoval = true)
public interface JsonParserInterface {

    /**
     * Splits a JSON array string into individual object strings.
     * 
     * @param jsonArray The JSON array string to split
     * @return List of individual JSON object strings
     */
    List<String> splitJsonArray(String jsonArray);

    /**
     * Parses a simple JSON object into a key-value map.
     * 
     * @param json The JSON object string to parse
     * @return Map containing the parsed key-value pairs
     */
    Map<String, String> parseSimpleJson(String json);

    /**
     * Escapes special characters for JSON string values.
     * 
     * @param str The string to escape
     * @return JSON-safe escaped string
     */
    String escapeJson(String str);
}
