package io.github.brunoborges.jlib.json;

import io.github.brunoborges.jlib.common.JarMetadata;
import io.github.brunoborges.jlib.common.JavaApplication;

/**
 * Utility for building JSON responses.
 */
public class JsonResponseBuilder {
    
    private static final JsonParserInterface jsonParser = JsonParserFactory.getDefaultParser();

    /**
     * Builds JSON for applications list.
     */
    public static String buildAppsListJson(Iterable<JavaApplication> applications) {
        StringBuilder json = new StringBuilder();
        json.append("{\"applications\":[");
        boolean first = true;
        for (JavaApplication app : applications) {
            if (!first)
                json.append(",");
            json.append("{")
                    .append("\"appId\":\"").append(app.appId).append("\",")
                    .append("\"name\":\"").append(app.name == null ? "" : jsonParser.escapeJson(app.name)).append("\",")
                    .append("\"commandLine\":\"").append(jsonParser.escapeJson(app.commandLine)).append("\",")
                    .append("\"jdkVersion\":\"").append(app.jdkVersion).append("\",")
                    .append("\"jdkVendor\":\"").append(app.jdkVendor).append("\",")
                    .append("\"firstSeen\":\"").append(app.firstSeen).append("\",")
                    .append("\"lastUpdated\":\"").append(app.lastUpdated).append("\",")
                    .append("\"jarCount\":").append(app.jars.size())
                    .append("}");
            first = false;
        }
        json.append("]}");
        return json.toString();
    }

    /**
     * Builds JSON for application details.
     */
    public static String buildAppDetailsJson(JavaApplication app) {
                StringBuilder sb = new StringBuilder();
                sb.append("{")
                    .append("\"appId\":\"").append(app.appId).append("\",")
                    .append("\"name\":\"").append(app.name == null ? "" : jsonParser.escapeJson(app.name)).append("\",")
                    .append("\"description\":\"").append(app.description == null ? "" : jsonParser.escapeJson(app.description)).append("\",")
                    .append("\"commandLine\":\"").append(jsonParser.escapeJson(app.commandLine)).append("\",")
                    .append("\"jdkVersion\":\"").append(app.jdkVersion).append("\",")
                    .append("\"jdkVendor\":\"").append(app.jdkVendor).append("\",")
                    .append("\"jdkPath\":\"").append(jsonParser.escapeJson(app.jdkPath)).append("\",")
                    .append("\"firstSeen\":\"").append(app.firstSeen).append("\",")
                    .append("\"lastUpdated\":\"").append(app.lastUpdated).append("\",")
                    .append("\"jarCount\":").append(app.jars.size()).append(",")
                    .append("\"tags\":[").append(buildTagsArray(app)).append("]");
                // Inline minimal jars array for details with manifest attributes so client doesn't need a second call.
                sb.append(",\"jars\":[");
                boolean first = true;
                for (JarMetadata jar : app.jars.values()) {
                        if (!first) sb.append(',');
                        sb.append('{')
                            .append("\"path\":\"").append(jsonParser.escapeJson(jar.fullPath)).append("\",")
                            .append("\"fileName\":\"").append(jsonParser.escapeJson(jar.fileName)).append("\",")
                            .append("\"size\":").append(jar.size).append(',')
                            .append("\"checksum\":\"").append(jsonParser.escapeJson(jar.sha256Hash)).append("\",")
                            .append("\"loaded\":").append(jar.isLoaded()).append(',')
                            .append("\"lastAccessed\":\"").append(jar.getLastAccessed()).append("\"");
                        if (jar.getManifestAttributes() != null && !jar.getManifestAttributes().isEmpty()) {
                                sb.append(",\"manifest\":{");
                                boolean firstAttr = true;
                                for (var e : jar.getManifestAttributes().entrySet()) {
                                        if (!firstAttr) sb.append(',');
                                        sb.append("\"").append(jsonParser.escapeJson(e.getKey())).append("\":\"")
                                            .append(jsonParser.escapeJson(e.getValue())).append("\"");
                                        firstAttr = false;
                                }
                                sb.append('}');
                        }
                        sb.append('}');
                        first = false;
                }
                sb.append(']');
                sb.append('}');
                return sb.toString();
    }

    private static String buildTagsArray(JavaApplication app) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String tag : app.tags) {
            if (!first) sb.append(",");
            sb.append("\"").append(jsonParser.escapeJson(tag)).append("\"");
            first = false;
        }
        return sb.toString();
    }

    /**
     * Builds JSON for JARs list.
     */
    public static String buildJarsListJson(JavaApplication app) {
        StringBuilder json = new StringBuilder();
        json.append("{\"jars\":[");
        boolean first = true;
        for (JarMetadata jar : app.jars.values()) {
            if (!first)
                json.append(",");
            json.append("{")
                    .append("\"path\":\"").append(jsonParser.escapeJson(jar.fullPath)).append("\",")
                    .append("\"fileName\":\"").append(jar.fileName).append("\",")
                    .append("\"size\":").append(jar.size).append(",")
                    .append("\"checksum\":\"").append(jar.sha256Hash).append("\",")
                    .append("\"loaded\":").append(jar.isLoaded()).append(",")
                    .append("\"lastAccessed\":\"").append(jar.getLastAccessed()).append("\"");
            if (jar.getManifestAttributes() != null && !jar.getManifestAttributes().isEmpty()) {
                json.append(",\"manifest\":{");
                boolean firstAttr = true;
                for (var e : jar.getManifestAttributes().entrySet()) {
                    if (!firstAttr) json.append(',');
                    json.append("\"").append(jsonParser.escapeJson(e.getKey())).append("\":\"")
                        .append(jsonParser.escapeJson(e.getValue())).append("\"");
                    firstAttr = false;
                }
                json.append("}");
            }
            json.append("}");
            first = false;
        }
        json.append("]}");
        return json.toString();
    }

    /**
     * Builds JSON for health check.
     */
    public static String buildHealthJson(int applicationCount) {
        return "{\"status\":\"healthy\",\"applications\":" + applicationCount + "}";
    }
}
