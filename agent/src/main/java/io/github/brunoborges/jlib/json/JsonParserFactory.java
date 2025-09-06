package io.github.brunoborges.jlib.json;

import java.util.logging.Logger;

/**
 * Factory for creating JSON parser instances.
 * 
 * <p>This factory provides a central point for obtaining JSON parser implementations,
 * allowing the system to choose between different parsers based on availability
 * and configuration preferences.
 */
public class JsonParserFactory {

    private static final Logger LOG = Logger.getLogger(JsonParserFactory.class.getName());
    
    /**
     * JSON parser implementation types.
     */
    public enum ParserType {
        /** Built-in lightweight parser with no external dependencies */
        BUILTIN,
        /** org.json library-based parser with robust JSON handling */
        ORG_JSON,
        /** Auto-detect and use the best available parser */
        AUTO
    }
    
    private static volatile JsonParserInterface defaultParser;
    private static volatile ParserType configuredType = ParserType.AUTO;
    
    /**
     * Gets the default JSON parser instance.
     * 
     * <p>This method returns a singleton parser instance based on the configured
     * parser type. The parser is created lazily and cached for subsequent calls.
     * 
     * @return JSON parser instance
     */
    public static JsonParserInterface getDefaultParser() {
        if (defaultParser == null) {
            synchronized (JsonParserFactory.class) {
                if (defaultParser == null) {
                    defaultParser = createParser(configuredType);
                }
            }
        }
        return defaultParser;
    }
    
    /**
     * Creates a new JSON parser instance of the specified type.
     * 
     * @param type The parser type to create
     * @return JSON parser instance
     */
    public static JsonParserInterface createParser(ParserType type) {
        switch (type) {
            case BUILTIN:
                LOG.info("Creating built-in JSON parser");
                return new JsonParser();
                
            case ORG_JSON:
                try {
                    // Check if org.json library is available
                    Class.forName("org.json.JSONObject");
                    LOG.info("Creating org.json-based parser");
                    return new OrgJsonParser();
                } catch (ClassNotFoundException e) {
                    LOG.warning("org.json library not found, falling back to built-in parser");
                    return new JsonParser();
                }
                
            case AUTO:
            default:
                // Auto-detect: prefer org.json if available, fallback to built-in
                try {
                    Class.forName("org.json.JSONObject");
                    LOG.info("Auto-selected org.json-based parser");
                    return new OrgJsonParser();
                } catch (ClassNotFoundException e) {
                    LOG.info("Auto-selected built-in JSON parser (org.json not available)");
                    return new JsonParser();
                }
        }
    }
    
    /**
     * Sets the default parser type for future parser creation.
     * 
     * <p>This method allows runtime configuration of the parser type preference.
     * Changing this setting will affect the next call to {@link #getDefaultParser()}
     * after the current cached instance is cleared.
     * 
     * @param type The parser type to use as default
     */
    public static void setDefaultParserType(ParserType type) {
        synchronized (JsonParserFactory.class) {
            configuredType = type;
            defaultParser = null; // Clear cached instance to force recreation
            LOG.info("Default parser type set to: " + type);
        }
    }
    
    /**
     * Gets the currently configured default parser type.
     * 
     * @return The configured parser type
     */
    public static ParserType getConfiguredParserType() {
        return configuredType;
    }
    
    /**
     * Checks if the org.json library is available on the classpath.
     * 
     * @return true if org.json is available, false otherwise
     */
    public static boolean isOrgJsonAvailable() {
        try {
            Class.forName("org.json.JSONObject");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Clears the cached default parser instance.
     * 
     * <p>This method forces the factory to create a new parser instance
     * on the next call to {@link #getDefaultParser()}, which can be useful
     * for testing or when parser configuration changes.
     */
    public static void clearCache() {
        synchronized (JsonParserFactory.class) {
            defaultParser = null;
            LOG.fine("Parser cache cleared");
        }
    }
}
