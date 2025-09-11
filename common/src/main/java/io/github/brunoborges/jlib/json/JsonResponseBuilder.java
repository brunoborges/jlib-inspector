package io.github.brunoborges.jlib.json;

import io.github.brunoborges.jlib.common.JarMetadata;
import io.github.brunoborges.jlib.common.JavaApplication;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Utility for building JSON responses.
 */
public class JsonResponseBuilder {
    
    // All JSON generation handled directly by org.json.

    /**
     * Builds JSON for applications list.
     */
    public static String buildAppsListJson(Iterable<JavaApplication> applications) {
        JSONArray appsArray = new JSONArray();
        for (JavaApplication app : applications) {
            JSONObject o = new JSONObject();
            o.put("appId", app.appId);
            o.put("name", app.name == null ? "" : app.name);
            o.put("commandLine", app.commandLine);
            o.put("jdkVersion", app.jdkVersion);
            o.put("jdkVendor", app.jdkVendor);
            o.put("firstSeen", app.firstSeen.toString());
            o.put("lastUpdated", app.lastUpdated.toString());
            o.put("jarCount", app.jars.size());
            appsArray.put(o);
        }
        return new JSONObject().put("applications", appsArray).toString();
    }

    /**
     * Builds JSON for application details.
     */
    public static String buildAppDetailsJson(JavaApplication app) {
        JSONObject root = new JSONObject();
        root.put("appId", app.appId);
        root.put("name", app.name == null ? "" : app.name);
        root.put("description", app.description == null ? "" : app.description);
        root.put("commandLine", app.commandLine);
        root.put("jdkVersion", app.jdkVersion);
        root.put("jdkVendor", app.jdkVendor);
        root.put("jdkPath", app.jdkPath);
        root.put("firstSeen", app.firstSeen.toString());
        root.put("lastUpdated", app.lastUpdated.toString());
        root.put("jarCount", app.jars.size());
        JSONArray tags = new JSONArray();
        for (String tag : app.tags) {
            tags.put(tag);
        }
        root.put("tags", tags);
        JSONArray jars = new JSONArray();
        for (JarMetadata jar : app.jars.values()) {
            JSONObject jo = new JSONObject();
            jo.put("path", jar.fullPath);
            jo.put("fileName", jar.fileName);
            jo.put("size", jar.size);
            jo.put("checksum", jar.sha256Hash);
            jo.put("jarId", jar.getJarId());
            jo.put("loaded", jar.isLoaded());
            jo.put("lastAccessed", jar.getLastAccessed().toString());
            jars.put(jo);
        }
        root.put("jars", jars);
        return root.toString();
    }

    // Legacy helper removed (replaced by direct JSONArray building).

    /**
     * Builds JSON for JARs list.
     */
    public static String buildJarsListJson(JavaApplication app) {
        JSONArray jars = new JSONArray();
        for (JarMetadata jar : app.jars.values()) {
            JSONObject jo = new JSONObject();
            jo.put("path", jar.fullPath);
            jo.put("fileName", jar.fileName);
            jo.put("size", jar.size);
            jo.put("checksum", jar.sha256Hash);
            jo.put("jarId", jar.getJarId());
            jo.put("loaded", jar.isLoaded());
            jo.put("lastAccessed", jar.getLastAccessed().toString());
            jars.put(jo);
        }
        return new JSONObject().put("jars", jars).toString();
    }

    /**
     * Builds JSON for health check.
     */
    public static String buildHealthJson(int applicationCount) {
        return new JSONObject().put("status", "healthy").put("applications", applicationCount).toString();
    }
}
