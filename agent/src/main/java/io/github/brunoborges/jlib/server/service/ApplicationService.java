package io.github.brunoborges.jlib.server.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.brunoborges.jlib.common.JavaApplication;

import java.util.Collection;

/**
 * Service for managing Java application data.
 */
public class ApplicationService {

    /** In-memory storage for tracked Java applications */
    private final Map<String, JavaApplication> applications = new ConcurrentHashMap<>();

    /**
     * Gets or creates an application.
     */
    public JavaApplication getOrCreateApplication(String appId, String commandLine,
            String jdkVersion, String jdkVendor, String jdkPath) {
        return applications.computeIfAbsent(appId,
                id -> new JavaApplication(id, commandLine, jdkVersion, jdkVendor, jdkPath));
    }

    /**
     * Gets an application by ID.
     */
    public JavaApplication getApplication(String appId) {
        return applications.get(appId);
    }

    /**
     * Gets all applications.
     */
    public Collection<JavaApplication> getAllApplications() {
        return applications.values();
    }

    /**
     * Gets the number of tracked applications.
     */
    public int getApplicationCount() {
        return applications.size();
    }
}
