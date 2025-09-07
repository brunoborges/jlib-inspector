package io.github.brunoborges.jlib.agent;

import io.github.brunoborges.jlib.common.JarMetadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Integration tests for JLib Inspector Agent components.
 */
@DisplayName("JLib Inspector Integration Tests")
class JLibInspectorIntegrationTest {

    private JarInventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new JarInventory();
    }

    @Test
    @DisplayName("Should handle complete JAR lifecycle from registration to reporting")
    void shouldHandleCompleteJarLifecycleFromRegistrationToReporting(@TempDir Path tempDir) throws IOException {
        // Create test JAR files
        File jar1 = createTestJar(tempDir, "app.jar", "Main application JAR");
        File jar2 = createTestJar(tempDir, "lib.jar", "Library JAR");
        
        // Register JARs in inventory (simulating classpath discovery)
        JarInventory.HashSupplier hash1 = JarInventory.fileHashSupplier(jar1);
        JarInventory.HashSupplier hash2 = JarInventory.fileHashSupplier(jar2);
        
        JarMetadata appJar = inventory.registerDeclared(jar1.getAbsolutePath(), jar1.length(), hash1);
        JarMetadata libJar = inventory.registerDeclared(jar2.getAbsolutePath(), jar2.length(), hash2);
        
        // Verify initial state
        assertThat(appJar.isLoaded()).isFalse();
        assertThat(libJar.isLoaded()).isFalse();
        
        // Mark one JAR as loaded (simulating class loading)
        inventory.markLoaded(jar1.getAbsolutePath());
        
        // Verify loaded state
        assertThat(appJar.isLoaded()).isTrue();
        assertThat(libJar.isLoaded()).isFalse();
        
        // Get snapshot and verify contents
        Collection<JarMetadata> snapshot = inventory.snapshot();
        assertThat(snapshot).hasSize(2);
        
        // Verify hash computation worked
        assertThat(appJar.sha256Hash).isNotEqualTo("?");
        assertThat(libJar.sha256Hash).isNotEqualTo("?");
        assertThat(appJar.sha256Hash).isNotEqualTo(libJar.sha256Hash);
    }

    @Test
    @DisplayName("Should integrate with server client for data transmission")
    void shouldIntegrateWithServerClientForDataTransmission() throws Exception {
        // Create server client
        JLibServerClient client = new JLibServerClient("localhost", 8080);
        
        // Add some test data to inventory
        inventory.registerDeclared("/test/app.jar", 1024L, null);
        inventory.registerDeclared("/test/lib.jar", 2048L, null);
        inventory.markLoaded("/test/app.jar");
        
        // Build JSON using server client (via reflection to test private method)
        java.lang.reflect.Method buildJsonMethod = JLibServerClient.class.getDeclaredMethod("buildApplicationJson", JarInventory.class);
        buildJsonMethod.setAccessible(true);
        
        String json = (String) buildJsonMethod.invoke(client, inventory);
        
        // Verify JSON contains expected data
        assertThat(json).contains("\"path\":\"/test/app.jar\"");
        assertThat(json).contains("\"path\":\"/test/lib.jar\"");
        assertThat(json).contains("\"loaded\":true");
        assertThat(json).contains("\"loaded\":false");
        assertThat(json).contains("\"jdkVersion\":");
        assertThat(json).contains("\"commandLine\":");
    }

    @Test
    @DisplayName("Should handle nested JAR scenarios end-to-end")
    void shouldHandleNestedJarScenariosEndToEnd() {
        // Simulate Spring Boot fat JAR scenario
        String fatJarPath = "/app/myapp.jar";
        String nestedLib1 = "myapp.jar!/BOOT-INF/lib/spring-core.jar";
        String nestedLib2 = "myapp.jar!/BOOT-INF/lib/spring-context.jar";
        
        // Register the main JAR and nested libraries
        inventory.registerDeclared(fatJarPath, 10485760L, null); // 10MB main JAR
        inventory.registerDeclared(nestedLib1, 1048576L, null);  // 1MB nested JAR
        inventory.registerDeclared(nestedLib2, 2097152L, null);  // 2MB nested JAR
        
        // Mark main JAR and one nested JAR as loaded
        inventory.markLoaded(fatJarPath);
        inventory.markLoaded(nestedLib1);
        
        Collection<JarMetadata> snapshot = inventory.snapshot();
        assertThat(snapshot).hasSize(3);
        
        // Verify nested JAR properties
        JarMetadata springCore = snapshot.stream()
            .filter(jar -> jar.fullPath.equals(nestedLib1))
            .findFirst()
            .orElse(null);
            
        assertThat(springCore).isNotNull();
        assertThat(springCore.fileName).isEqualTo("spring-core.jar");
        assertThat(springCore.isNested()).isTrue();
        assertThat(springCore.getContainerJarPath()).isEqualTo("myapp.jar");
        assertThat(springCore.isLoaded()).isTrue();
        
        // Verify unloaded nested JAR
        JarMetadata springContext = snapshot.stream()
            .filter(jar -> jar.fullPath.equals(nestedLib2))
            .findFirst()
            .orElse(null);
            
        assertThat(springContext).isNotNull();
        assertThat(springContext.isLoaded()).isFalse();
    }

    @Test
    @DisplayName("Should handle concurrent access scenarios")
    void shouldHandleConcurrentAccessScenarios() throws InterruptedException {
        int threadCount = 5;
        int jarsPerThread = 10;
        Thread[] threads = new Thread[threadCount];
        
        // Create multiple threads that register and load JARs concurrently
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int j = 0; j < jarsPerThread; j++) {
                    String jarPath = "/thread" + threadId + "/jar" + j + ".jar";
                    inventory.registerDeclared(jarPath, 1000L + j, null);
                    
                    // Mark every other JAR as loaded
                    if (j % 2 == 0) {
                        inventory.markLoaded(jarPath);
                    }
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify final state
        Collection<JarMetadata> snapshot = inventory.snapshot();
        assertThat(snapshot).hasSize(threadCount * jarsPerThread);
        
        // Count loaded vs unloaded JARs
        long loadedCount = snapshot.stream().mapToLong(jar -> jar.isLoaded() ? 1 : 0).sum();
        long unloadedCount = snapshot.stream().mapToLong(jar -> jar.isLoaded() ? 0 : 1).sum();
        
        assertThat(loadedCount).isEqualTo(threadCount * (jarsPerThread / 2));
        assertThat(unloadedCount).isEqualTo(threadCount * (jarsPerThread / 2));
    }

    @Test
    @DisplayName("Should handle edge cases in JAR path processing")
    void shouldHandleEdgeCasesInJarPathProcessing() {
        // Test various edge cases for JAR paths
        String[] edgeCasePaths = {
            "C:\\Windows\\System32\\app.jar",                    // Windows path
            "/usr/local/lib/app with spaces.jar",               // Spaces in name
            "/path/to/app-1.0-SNAPSHOT.jar",                   // Version numbers
            "nested.jar!/META-INF/lib/deep-nested.jar",        // Deeply nested
            "/path/with/unicode/café.jar",                     // Unicode characters
            "/very/long/path/that/goes/on/and/on/app.jar",     // Long paths
            ".jar",                                             // Edge case: just extension
            "/path/without/extension",                          // No extension
            ""                                                  // Empty path
        };
        
        for (String path : edgeCasePaths) {
            if (path.isEmpty()) {
                // Empty paths should be handled gracefully
                inventory.markLoaded(path);
                continue;
            }
            
            JarMetadata jar = inventory.registerDeclared(path, 1000L, null);
            
            if (jar != null) {
                assertThat(jar.fullPath).isEqualTo(path);
                
                // Verify filename extraction works for valid paths
                if (path.contains("/") || path.contains("\\")) {
                    assertThat(jar.fileName).isNotEmpty();
                }
            }
        }
    }

    @Test
    @DisplayName("Should maintain data consistency across operations")
    void shouldMaintainDataConsistencyAcrossOperations() {
        String jarPath = "/consistency/test.jar";
        
        // Register JAR
        JarMetadata jar1 = inventory.registerDeclared(jarPath, 1000L, null);
        assertThat(jar1).isNotNull();
        assertThat(jar1.isLoaded()).isFalse();
        
        // Get snapshot and verify consistency
        Collection<JarMetadata> snapshot1 = inventory.snapshot();
        JarMetadata jarFromSnapshot = snapshot1.stream()
            .filter(j -> j.fullPath.equals(jarPath))
            .findFirst()
            .orElse(null);
        
        assertThat(jarFromSnapshot).isSameAs(jar1);
        
        // Mark as loaded
        inventory.markLoaded(jarPath);
        assertThat(jar1.isLoaded()).isTrue();
        
        // Verify snapshot still contains same instance
        Collection<JarMetadata> snapshot2 = inventory.snapshot();
        JarMetadata jarFromSnapshot2 = snapshot2.stream()
            .filter(j -> j.fullPath.equals(jarPath))
            .findFirst()
            .orElse(null);
        
        assertThat(jarFromSnapshot2).isSameAs(jar1);
        assertThat(jarFromSnapshot2.isLoaded()).isTrue();
    }

    /**
     * Helper method to create a test JAR file with specific content.
     */
    private File createTestJar(Path tempDir, String filename, String content) throws IOException {
        File jarFile = tempDir.resolve(filename).toFile();
        
        try (FileOutputStream fos = new FileOutputStream(jarFile)) {
            fos.write(content.getBytes());
        }
        
        return jarFile;
    }
}
