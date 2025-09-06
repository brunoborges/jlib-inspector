package io.github.brunoborges.jlib.util;

import io.github.brunoborges.jlib.server.model.JavaApplication;
import io.github.brunoborges.jlib.server.model.JarInfo;

/**
 * Utility for building JSON responses.
 */
public class JsonResponseBuilder {

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
                    .append("\"commandLine\":\"").append(JsonParser.escapeJson(app.commandLine)).append("\",")
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
        return "{" +
                "\"appId\":\"" + app.appId + "\"," +
                "\"commandLine\":\"" + JsonParser.escapeJson(app.commandLine) + "\"," +
                "\"jdkVersion\":\"" + app.jdkVersion + "\"," +
                "\"jdkVendor\":\"" + app.jdkVendor + "\"," +
                "\"jdkPath\":\"" + JsonParser.escapeJson(app.jdkPath) + "\"," +
                "\"firstSeen\":\"" + app.firstSeen + "\"," +
                "\"lastUpdated\":\"" + app.lastUpdated + "\"," +
                "\"jarCount\":" + app.jars.size() +
                "}";
    }

    /**
     * Builds JSON for JARs list.
     */
    public static String buildJarsListJson(JavaApplication app) {
        StringBuilder json = new StringBuilder();
        json.append("{\"jars\":[");
        boolean first = true;
        for (JarInfo jar : app.jars.values()) {
            if (!first)
                json.append(",");
            json.append("{")
                    .append("\"path\":\"").append(JsonParser.escapeJson(jar.jarPath)).append("\",")
                    .append("\"fileName\":\"").append(jar.fileName).append("\",")
                    .append("\"size\":").append(jar.size).append(",")
                    .append("\"checksum\":\"").append(jar.checksum).append("\",")
                    .append("\"loaded\":").append(jar.isLoaded).append(",")
                    .append("\"lastAccessed\":\"").append(jar.lastAccessed).append("\"")
                    .append("}");
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
