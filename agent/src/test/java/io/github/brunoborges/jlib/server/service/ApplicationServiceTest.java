package io.github.brunoborges.jlib.server.service;

import io.github.brunoborges.jlib.common.JavaApplication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

import java.util.Collection;

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

        assertThat(app).isNotNull();
        assertThat(app.appId).isEqualTo(appId);
        assertThat(app.commandLine).isEqualTo(commandLine);
        assertThat(app.jdkVersion).isEqualTo(jdkVersion);
        assertThat(app.jdkVendor).isEqualTo(jdkVendor);
        assertThat(app.jdkPath).isEqualTo(jdkPath);
    }

    @Test
    @DisplayName("Should return existing application when already exists")
    void shouldReturnExistingApplicationWhenAlreadyExists() {
        String appId = "existing-app";
        
        // Create application first time
        JavaApplication app1 = applicationService.getOrCreateApplication(appId, "java -jar app.jar", "17", "OpenJDK", "/java");
        
        // Get same application with different parameters (should ignore new params)
        JavaApplication app2 = applicationService.getOrCreateApplication(appId, "java -jar different.jar", "11", "Oracle", "/different");

        assertThat(app1).isSameAs(app2);
        assertThat(app2.commandLine).isEqualTo("java -jar app.jar"); // Original command line
        assertThat(app2.jdkVersion).isEqualTo("17"); // Original JDK version
    }

    @Test
    @DisplayName("Should get application by ID")
    void shouldGetApplicationById() {
        String appId = "test-app";
        
        // Create application
        JavaApplication created = applicationService.getOrCreateApplication(appId, "java -jar test.jar", "17", "OpenJDK", "/java");
        
        // Get by ID
        JavaApplication retrieved = applicationService.getApplication(appId);

        assertThat(retrieved).isSameAs(created);
    }

    @Test
    @DisplayName("Should return null for non-existent application")
    void shouldReturnNullForNonExistentApplication() {
        JavaApplication app = applicationService.getApplication("non-existent");
        assertThat(app).isNull();
    }

    @Test
    @DisplayName("Should return all applications")
    void shouldReturnAllApplications() {
        // Initially empty
        Collection<JavaApplication> apps = applicationService.getAllApplications();
        assertThat(apps).isEmpty();

        // Add some applications
        applicationService.getOrCreateApplication("app1", "java -jar app1.jar", "17", "OpenJDK", "/java");
        applicationService.getOrCreateApplication("app2", "java -jar app2.jar", "11", "Oracle", "/oracle");
        applicationService.getOrCreateApplication("app3", "java -jar app3.jar", "8", "AdoptOpenJDK", "/adopt");

        apps = applicationService.getAllApplications();
        assertThat(apps).hasSize(3);

        // Verify all apps are present
        assertThat(apps).extracting(app -> app.appId)
            .containsExactlyInAnyOrder("app1", "app2", "app3");
    }

    @Test
    @DisplayName("Should return application count")
    void shouldReturnApplicationCount() {
        assertThat(applicationService.getApplicationCount()).isEqualTo(0);

        applicationService.getOrCreateApplication("app1", "java -jar app1.jar", "17", "OpenJDK", "/java");
        assertThat(applicationService.getApplicationCount()).isEqualTo(1);

        applicationService.getOrCreateApplication("app2", "java -jar app2.jar", "11", "Oracle", "/oracle");
        assertThat(applicationService.getApplicationCount()).isEqualTo(2);

        // Adding same app should not increase count
        applicationService.getOrCreateApplication("app1", "java -jar different.jar", "11", "Different", "/different");
        assertThat(applicationService.getApplicationCount()).isEqualTo(2);
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
            assertThat(results[i]).isSameAs(results[0]);
        }

        // Should have only one application
        assertThat(applicationService.getApplicationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle null and empty values gracefully")
    void shouldHandleNullAndEmptyValuesGracefully() {
        // ConcurrentHashMap doesn't allow null keys, so null appId should throw NPE
        assertThatThrownBy(() -> 
            applicationService.getOrCreateApplication(null, null, null, null, null))
            .isInstanceOf(NullPointerException.class);
        
        // Empty string should work fine
        JavaApplication app2 = applicationService.getOrCreateApplication("", "", "", "", "");
        assertThat(app2).isNotNull();
        assertThat(app2.appId).isEmpty();
        
        // Null queries should also throw NPE
        assertThatThrownBy(() -> applicationService.getApplication(null))
            .isInstanceOf(NullPointerException.class);
        
        assertThat(applicationService.getApplication("")).isSameAs(app2);
        assertThat(applicationService.getApplicationCount()).isEqualTo(1);
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
        assertThat(retrieved).isSameAs(app);
        assertThat(retrieved.jars).hasSize(1);
        assertThat(retrieved.jars.get("/test.jar")).isNotNull();
    }
}
