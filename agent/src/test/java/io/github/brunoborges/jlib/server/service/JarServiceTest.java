package io.github.brunoborges.jlib.server.service;

import io.github.brunoborges.jlib.common.JarMetadata;
import io.github.brunoborges.jlib.common.JavaApplication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

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

        assertThat(testApp.jars).hasSize(2);
        
        JarMetadata jar1 = testApp.jars.get("/path/to/jar1.jar");
        assertThat(jar1).isNotNull();
        assertThat(jar1.fileName).isEqualTo("jar1.jar");
        assertThat(jar1.size).isEqualTo(1000L);
        assertThat(jar1.sha256Hash).isEqualTo("hash1");
        assertThat(jar1.isLoaded()).isTrue();

        JarMetadata jar2 = testApp.jars.get("/path/to/jar2.jar");
        assertThat(jar2).isNotNull();
        assertThat(jar2.fileName).isEqualTo("jar2.jar");
        assertThat(jar2.size).isEqualTo(2000L);
        assertThat(jar2.sha256Hash).isEqualTo("hash2");
        assertThat(jar2.isLoaded()).isFalse();
    }

    @Test
    @DisplayName("Should handle empty JAR array")
    void shouldHandleEmptyJarArray() {
        String jarsData = "[]";

        jarService.processJarUpdates(testApp, jarsData);

        assertThat(testApp.jars).isEmpty();
    }

    @Test
    @DisplayName("Should ignore malformed JSON data")
    void shouldIgnoreMalformedJsonData() {
        String malformedData = "{not a valid json array}";

        // Should not throw exception
        jarService.processJarUpdates(testApp, malformedData);

        assertThat(testApp.jars).isEmpty();
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
        assertThat(testApp.jars).hasSize(1);
        assertThat(testApp.jars.get("/valid/path.jar")).isNotNull();
    }

    @Test
    @DisplayName("Should handle nested JAR paths")
    void shouldHandleNestedJarPaths() {
        String jarsData = "[" +
            "{\"path\":\"outer.jar!/BOOT-INF/lib/inner.jar\",\"fileName\":\"inner.jar\",\"size\":1000,\"checksum\":\"hash1\",\"loaded\":true}" +
            "]";

        jarService.processJarUpdates(testApp, jarsData);

        assertThat(testApp.jars).hasSize(1);
        
        JarMetadata nestedJar = testApp.jars.get("outer.jar!/BOOT-INF/lib/inner.jar");
        assertThat(nestedJar).isNotNull();
        assertThat(nestedJar.isNested()).isTrue();
        assertThat(nestedJar.getContainerJarPath()).isEqualTo("outer.jar");
        assertThat(nestedJar.getInnerPath()).isEqualTo("BOOT-INF/lib/inner.jar");
    }

    @Test
    @DisplayName("Should update existing JARs")
    void shouldUpdateExistingJars() {
        // Add initial JAR
        String initialData = "[{\"path\":\"/test.jar\",\"fileName\":\"test.jar\",\"size\":1000,\"checksum\":\"hash1\",\"loaded\":false}]";
        jarService.processJarUpdates(testApp, initialData);

        assertThat(testApp.jars).hasSize(1);
        JarMetadata initialJar = testApp.jars.get("/test.jar");
        assertThat(initialJar.isLoaded()).isFalse();

        // Update the same JAR
        String updatedData = "[{\"path\":\"/test.jar\",\"fileName\":\"test.jar\",\"size\":1100,\"checksum\":\"hash2\",\"loaded\":true}]";
        jarService.processJarUpdates(testApp, updatedData);

        assertThat(testApp.jars).hasSize(1);
        JarMetadata updatedJar = testApp.jars.get("/test.jar");
        assertThat(updatedJar.size).isEqualTo(1100L);
        assertThat(updatedJar.sha256Hash).isEqualTo("hash2");
        assertThat(updatedJar.isLoaded()).isTrue();
    }

    @Test
    @DisplayName("Should handle missing optional fields gracefully")
    void shouldHandleMissingOptionalFieldsGracefully() {
        String jarsData = "[" +
            "{\"path\":\"/minimal.jar\",\"fileName\":\"minimal.jar\"}" + // Only required fields
            "]";

        jarService.processJarUpdates(testApp, jarsData);

        assertThat(testApp.jars).hasSize(1);
        
        JarMetadata jar = testApp.jars.get("/minimal.jar");
        assertThat(jar).isNotNull();
        assertThat(jar.fileName).isEqualTo("minimal.jar");
        assertThat(jar.size).isEqualTo(0L); // Default value
        assertThat(jar.sha256Hash).isNull(); // Default for missing checksum
        assertThat(jar.isLoaded()).isFalse(); // Default value
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

        assertThat(testApp.jars).hasSize(100);
        
        // Verify some random entries
        JarMetadata jar0 = testApp.jars.get("/path/to/jar0.jar");
        assertThat(jar0.isLoaded()).isTrue(); // 0 % 2 == 0
        
        JarMetadata jar1 = testApp.jars.get("/path/to/jar1.jar");
        assertThat(jar1.isLoaded()).isFalse(); // 1 % 2 != 0
        
        JarMetadata jar99 = testApp.jars.get("/path/to/jar99.jar");
        assertThat(jar99.size).isEqualTo(1099L);
    }

    @Test
    @DisplayName("Should update application lastUpdated timestamp")
    void shouldUpdateApplicationLastUpdatedTimestamp() throws InterruptedException {
        java.time.Instant originalLastUpdated = testApp.lastUpdated;
        
        Thread.sleep(10); // Ensure time difference
        
        String jarsData = "[{\"path\":\"/test.jar\",\"fileName\":\"test.jar\",\"size\":1000,\"checksum\":\"hash\",\"loaded\":true}]";
        jarService.processJarUpdates(testApp, jarsData);

        assertThat(testApp.lastUpdated).isAfter(originalLastUpdated);
    }

    @Test
    @DisplayName("Should handle escaped JSON strings in paths")
    void shouldHandleEscapedJsonStringsInPaths() {
        String jarsData = "[" +
            "{\"path\":\"/path/with \\\"quotes\\\"/test.jar\",\"fileName\":\"test.jar\",\"size\":1000,\"checksum\":\"hash\",\"loaded\":true}" +
            "]";

        jarService.processJarUpdates(testApp, jarsData);

        assertThat(testApp.jars).hasSize(1);
        // The parser should handle the escaped quotes correctly
        assertThat(testApp.jars.keySet()).anyMatch(path -> path.contains("quotes"));
    }
}
