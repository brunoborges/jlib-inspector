package io.github.brunoborges.jlib.server;

import io.github.brunoborges.jlib.common.JavaApplication;
import io.github.brunoborges.jlib.server.service.ApplicationService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unit tests for ApplicationService.
 */
@DisplayName("ApplicationService Tests")
class ApplicationServiceTest {

    private ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new ApplicationService();
    }

    @Test
    @DisplayName("Should create new application when not exists")
    void shouldCreateNewApplicationWhenNotExists() {
        String appId = "test-app-123";
        String commandLine = "java -jar myapp.jar";
        String jdkVersion = "17.0.2";
        String jdkVendor = "Eclipse Adoptium";
        String jdkPath = "/usr/lib/jvm/java-17";

        JavaApplication app = applicationService.getOrCreateApplication(appId, commandLine, jdkVersion, jdkVendor, jdkPath);

        assertNotNull(app);
        assertEquals(appId, app.appId);
        assertEquals(commandLine, app.commandLine);
        assertEquals(jdkVersion, app.jdkVersion);
        assertEquals(jdkVendor, app.jdkVendor);
        assertEquals(jdkPath, app.jdkPath);
    }

    @Test
    @DisplayName("Should return existing application when already exists")
    void shouldReturnExistingApplicationWhenAlreadyExists() {
        String appId = "existing-app";
        
        // Create application first time
        JavaApplication app1 = applicationService.getOrCreateApplication(appId, "java -jar app.jar", "17", "OpenJDK", "/java");
        
        // Get same application with different parameters (should ignore new params)
        JavaApplication app2 = applicationService.getOrCreateApplication(appId, "java -jar different.jar", "11", "Oracle", "/different");

        assertSame(app1, app2);
        assertEquals("java -jar app.jar", app2.commandLine); // Original command line
        assertEquals("17", app2.jdkVersion); // Original JDK version
    }

    @Test
    @DisplayName("Should get application by ID")
    void shouldGetApplicationById() {
        String appId = "test-app";
        
        // Create application
        JavaApplication created = applicationService.getOrCreateApplication(appId, "java -jar test.jar", "17", "OpenJDK", "/java");
        
        // Get by ID
        JavaApplication retrieved = applicationService.getApplication(appId);

        assertSame(retrieved, created);
    }

    @Test
    @DisplayName("Should return null for non-existent application")
    void shouldReturnNullForNonExistentApplication() {
        JavaApplication app = applicationService.getApplication("non-existent");
        assertNull(app);
    }

    @Test
    @DisplayName("Should return all applications")
    void shouldReturnAllApplications() {
        // Initially empty
        Collection<JavaApplication> apps = applicationService.getAllApplications();
        assertTrue(apps.isEmpty());

        // Add some applications
        applicationService.getOrCreateApplication("app1", "java -jar app1.jar", "17", "OpenJDK", "/java");
        applicationService.getOrCreateApplication("app2", "java -jar app2.jar", "11", "Oracle", "/oracle");
        applicationService.getOrCreateApplication("app3", "java -jar app3.jar", "8", "AdoptOpenJDK", "/adopt");

        apps = applicationService.getAllApplications();
        assertEquals(3, apps.size());

        // Verify all apps are present
        Set<String> appIds = apps.stream().map(app -> app.appId).collect(Collectors.toSet());
        assertEquals(Set.of("app1", "app2", "app3"), appIds);
    }

    @Test
    @DisplayName("Should return application count")
    void shouldReturnApplicationCount() {
        assertEquals(0, applicationService.getApplicationCount());

        applicationService.getOrCreateApplication("app1", "java -jar app1.jar", "17", "OpenJDK", "/java");
        assertEquals(1, applicationService.getApplicationCount());

        applicationService.getOrCreateApplication("app2", "java -jar app2.jar", "11", "Oracle", "/oracle");
        assertEquals(2, applicationService.getApplicationCount());

        // Adding same app should not increase count
        applicationService.getOrCreateApplication("app1", "java -jar different.jar", "11", "Different", "/different");
        assertEquals(2, applicationService.getApplicationCount());
    }

    @Test
    @DisplayName("Should handle concurrent access safely")
    void shouldHandleConcurrentAccessSafely() throws InterruptedException {
        int threadCount = 10;
        String appId = "concurrent-app";
        Thread[] threads = new Thread[threadCount];
        JavaApplication[] results = new JavaApplication[threadCount];

        // Start multiple threads trying to create the same application
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = applicationService.getOrCreateApplication(
                    appId, "java -jar concurrent.jar", "17", "OpenJDK", "/java");
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // All threads should have received the same instance
        for (int i = 1; i < threadCount; i++) {
            assertSame(results[0], results[i]);
        }

        // Should have only one application
        assertEquals(1, applicationService.getApplicationCount());
    }

    @Test
    @DisplayName("Should handle null and empty values gracefully")
    void shouldHandleNullAndEmptyValuesGracefully() {
        // ConcurrentHashMap doesn't allow null keys, so null appId should throw NPE
        assertThrows(NullPointerException.class, () -> 
            applicationService.getOrCreateApplication(null, null, null, null, null));
        
        // Empty string should work fine
        JavaApplication app2 = applicationService.getOrCreateApplication("", "", "", "", "");
        assertNotNull(app2);
        assertTrue(app2.appId.isEmpty());
        
        // Null queries should also throw NPE
        assertThrows(NullPointerException.class, () -> applicationService.getApplication(null));
        
        assertSame(app2, applicationService.getApplication(""));
        assertEquals(1, applicationService.getApplicationCount());
    }

    @Test
    @DisplayName("Should preserve application data integrity")
    void shouldPreserveApplicationDataIntegrity() {
        String appId = "data-integrity-test";
        JavaApplication app = applicationService.getOrCreateApplication(
            appId, "java -jar test.jar", "17", "OpenJDK", "/java");

        // Modify application data
        app.lastUpdated = java.time.Instant.now();
        app.jars.put("/test.jar", new io.github.brunoborges.jlib.common.JarMetadata(
            "/test.jar", "test.jar", 1000L, "hash123"));

        // Retrieve again - should be same instance with modifications
        JavaApplication retrieved = applicationService.getApplication(appId);
        assertSame(app, retrieved);
        assertEquals(1, retrieved.jars.size());
        assertNotNull(retrieved.jars.get("/test.jar"));
    }
}
