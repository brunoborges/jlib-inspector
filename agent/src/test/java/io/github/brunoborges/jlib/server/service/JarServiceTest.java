package io.github.brunoborges.jlib.server.service;

import io.github.brunoborges.jlib.common.JarMetadata;
import io.github.brunoborges.jlib.common.JavaApplication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JarService.
 */
@DisplayName("JarService Tests")
class JarServiceTest {

    private JarService jarService;
    private JavaApplication testApp;

    @BeforeEach
    void setUp() {
        jarService = new JarService();
        testApp = new JavaApplication("test-app", "java -jar test.jar", "17", "OpenJDK", "/java");
    }

    @Test
    @DisplayName("Should process valid JAR updates")
    void shouldProcessValidJarUpdates() {
        String jarsData = "[" +
            "{\"path\":\"/path/to/jar1.jar\",\"fileName\":\"jar1.jar\",\"size\":1000,\"checksum\":\"hash1\",\"loaded\":true}," +
            "{\"path\":\"/path/to/jar2.jar\",\"fileName\":\"jar2.jar\",\"size\":2000,\"checksum\":\"hash2\",\"loaded\":false}" +
            "]";

        jarService.processJarUpdates(testApp, jarsData);

        assertEquals(2, testApp.jars.size());
        
        JarMetadata jar1 = testApp.jars.get("/path/to/jar1.jar");
        assertNotNull(jar1);
        assertEquals("jar1.jar", jar1.fileName);
        assertEquals(1000L, jar1.size);
        assertEquals("hash1", jar1.sha256Hash);
        assertTrue(jar1.isLoaded());

        JarMetadata jar2 = testApp.jars.get("/path/to/jar2.jar");
        assertNotNull(jar2);
        assertEquals("jar2.jar", jar2.fileName);
        assertEquals(2000L, jar2.size);
        assertEquals("hash2", jar2.sha256Hash);
        assertFalse(jar2.isLoaded());
    }

    @Test
    @DisplayName("Should handle empty JAR array")
    void shouldHandleEmptyJarArray() {
        String jarsData = "[]";

        jarService.processJarUpdates(testApp, jarsData);

        assertTrue(testApp.jars.isEmpty());
    }

    @Test
    @DisplayName("Should ignore malformed JSON data")
    void shouldIgnoreMalformedJsonData() {
        String malformedData = "{not a valid json array}";

        // Should not throw exception
        jarService.processJarUpdates(testApp, malformedData);

        assertTrue(testApp.jars.isEmpty());
    }

    @Test
    @DisplayName("Should handle JAR entries without required fields")
    void shouldHandleJarEntriesWithoutRequiredFields() {
        String jarsData = "[" +
            "{\"fileName\":\"incomplete.jar\",\"size\":1000}," + // Missing path
            "{\"path\":\"/valid/path.jar\",\"fileName\":\"valid.jar\",\"size\":2000,\"checksum\":\"hash\",\"loaded\":true}" +
            "]";

        jarService.processJarUpdates(testApp, jarsData);

        // Should only process the valid entry
        assertEquals(1, testApp.jars.size());
        assertNotNull(testApp.jars.get("/valid/path.jar"));
    }

    @Test
    @DisplayName("Should handle nested JAR paths")
    void shouldHandleNestedJarPaths() {
        String jarsData = "[" +
            "{\"path\":\"outer.jar!/BOOT-INF/lib/inner.jar\",\"fileName\":\"inner.jar\",\"size\":1000,\"checksum\":\"hash1\",\"loaded\":true}" +
            "]";

        jarService.processJarUpdates(testApp, jarsData);

        assertEquals(1, testApp.jars.size());
        
        JarMetadata nestedJar = testApp.jars.get("outer.jar!/BOOT-INF/lib/inner.jar");
        assertNotNull(nestedJar);
        assertTrue(nestedJar.isNested());
        assertEquals("outer.jar", nestedJar.getContainerJarPath());
        assertEquals("BOOT-INF/lib/inner.jar", nestedJar.getInnerPath());
    }

    @Test
    @DisplayName("Should update existing JARs")
    void shouldUpdateExistingJars() {
        // Add initial JAR
        String initialData = "[{\"path\":\"/test.jar\",\"fileName\":\"test.jar\",\"size\":1000,\"checksum\":\"hash1\",\"loaded\":false}]";
        jarService.processJarUpdates(testApp, initialData);

        assertEquals(1, testApp.jars.size());
        JarMetadata initialJar = testApp.jars.get("/test.jar");
        assertFalse(initialJar.isLoaded());

        // Update the same JAR
        String updatedData = "[{\"path\":\"/test.jar\",\"fileName\":\"test.jar\",\"size\":1100,\"checksum\":\"hash2\",\"loaded\":true}]";
        jarService.processJarUpdates(testApp, updatedData);

        assertEquals(1, testApp.jars.size());
        JarMetadata updatedJar = testApp.jars.get("/test.jar");
        assertEquals(1100L, updatedJar.size);
        assertEquals("hash2", updatedJar.sha256Hash);
        assertTrue(updatedJar.isLoaded());
    }

    @Test
    @DisplayName("Should handle missing optional fields gracefully")
    void shouldHandleMissingOptionalFieldsGracefully() {
        String jarsData = "[" +
            "{\"path\":\"/minimal.jar\",\"fileName\":\"minimal.jar\"}" + // Only required fields
            "]";

        jarService.processJarUpdates(testApp, jarsData);

        assertEquals(1, testApp.jars.size());
        
        JarMetadata jar = testApp.jars.get("/minimal.jar");
        assertNotNull(jar);
        assertEquals("minimal.jar", jar.fileName);
        assertEquals(0L, jar.size); // Default value
        assertNull(jar.sha256Hash); // Default for missing checksum
        assertFalse(jar.isLoaded()); // Default value
    }

    @Test
    @DisplayName("Should handle large JAR datasets")
    void shouldHandleLargeJarDatasets() {
        StringBuilder jarsDataBuilder = new StringBuilder("[");
        
        // Create 100 JAR entries
        for (int i = 0; i < 100; i++) {
            if (i > 0) jarsDataBuilder.append(",");
            jarsDataBuilder.append(String.format(
                "{\"path\":\"/path/to/jar%d.jar\",\"fileName\":\"jar%d.jar\",\"size\":%d,\"checksum\":\"hash%d\",\"loaded\":%s}",
                i, i, 1000 + i, i, i % 2 == 0));
        }
        jarsDataBuilder.append("]");

        jarService.processJarUpdates(testApp, jarsDataBuilder.toString());

        assertEquals(100, testApp.jars.size());
        
        // Verify some random entries
        JarMetadata jar0 = testApp.jars.get("/path/to/jar0.jar");
        assertTrue(jar0.isLoaded()); // 0 % 2 == 0
        
        JarMetadata jar1 = testApp.jars.get("/path/to/jar1.jar");
        assertFalse(jar1.isLoaded()); // 1 % 2 != 0
        
        JarMetadata jar99 = testApp.jars.get("/path/to/jar99.jar");
        assertEquals(1099L, jar99.size);
    }

    @Test
    @DisplayName("Should update application lastUpdated timestamp")
    void shouldUpdateApplicationLastUpdatedTimestamp() throws InterruptedException {
        java.time.Instant originalLastUpdated = testApp.lastUpdated;
        
        Thread.sleep(10); // Ensure time difference
        
        String jarsData = "[{\"path\":\"/test.jar\",\"fileName\":\"test.jar\",\"size\":1000,\"checksum\":\"hash\",\"loaded\":true}]";
        jarService.processJarUpdates(testApp, jarsData);

        assertTrue(testApp.lastUpdated.compareTo(originalLastUpdated) > 0);
    }

    @Test
    @DisplayName("Should handle escaped JSON strings in paths")
    void shouldHandleEscapedJsonStringsInPaths() {
        String jarsData = "[" +
            "{\"path\":\"/path/with \\\"quotes\\\"/test.jar\",\"fileName\":\"test.jar\",\"size\":1000,\"checksum\":\"hash\",\"loaded\":true}" +
            "]";

        jarService.processJarUpdates(testApp, jarsData);

        assertEquals(1, testApp.jars.size());
        // The parser should handle the escaped quotes correctly
        assertTrue(testApp.jars.keySet().stream().anyMatch(path -> path.contains("quotes")));
    }
}
