package io.github.brunoborges.jlib.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;

/**
 * Unit tests for JarMetadata.
 */
@DisplayName("JarMetadata Tests")
class JarMetadataTest {

    @Test
    @DisplayName("Should create JAR metadata with all fields")
    void shouldCreateJarMetadataWithAllFields() {
        String fullPath = "/path/to/example.jar";
        String fileName = "example.jar";
        long size = 12345L;
        String hash = "abc123def456";

        JarMetadata jar = new JarMetadata(fullPath, fileName, size, hash);

        assertThat(jar.fullPath).isEqualTo(fullPath);
        assertThat(jar.fileName).isEqualTo(fileName);
        assertThat(jar.size).isEqualTo(size);
        assertThat(jar.sha256Hash).isEqualTo(hash);
        assertThat(jar.isLoaded()).isFalse();
        assertThat(jar.firstSeen).isNotNull();
        assertThat(jar.getLastAccessed()).isNotNull();
    }

    @Test
    @DisplayName("Should handle nested JAR paths")
    void shouldHandleNestedJarPaths() {
        String nestedPath = "outer.jar!/BOOT-INF/lib/inner.jar";
        String fileName = "inner.jar";

        JarMetadata jar = new JarMetadata(nestedPath, fileName, 1000L, "hash123");

        assertThat(jar.fullPath).isEqualTo(nestedPath);
        assertThat(jar.fileName).isEqualTo(fileName);
    }

    @Test
    @DisplayName("Should handle unknown size and hash")
    void shouldHandleUnknownSizeAndHash() {
        JarMetadata jar = new JarMetadata("/path/to/unknown.jar", "unknown.jar", -1L, "?");

        assertThat(jar.size).isEqualTo(-1L);
        assertThat(jar.sha256Hash).isEqualTo("?");
    }

    @Test
    @DisplayName("Should track loaded state thread-safely")
    void shouldTrackLoadedStateThreadSafely() {
        JarMetadata jar = new JarMetadata("/path/to/test.jar", "test.jar", 1000L, "hash");

        assertThat(jar.isLoaded()).isFalse();

        jar.markLoaded();
        assertThat(jar.isLoaded()).isTrue();

        // Should remain true
        jar.markLoaded();
        assertThat(jar.isLoaded()).isTrue();
    }

    @Test
    @DisplayName("Should update last accessed time when marked as loaded")
    void shouldUpdateLastAccessedWhenMarkedAsLoaded() throws InterruptedException {
        JarMetadata jar = new JarMetadata("/path/to/test.jar", "test.jar", 1000L, "hash");
        Instant initialAccessed = jar.getLastAccessed();

        // Wait a small amount to ensure time difference
        Thread.sleep(10);

        jar.markLoaded();
        Instant updatedAccessed = jar.getLastAccessed();

        assertThat(updatedAccessed).isAfter(initialAccessed);
    }

    @Test
    @DisplayName("Should create formatted string representation")
    void shouldCreateFormattedStringRepresentation() {
        JarMetadata jar = new JarMetadata("/path/to/example.jar", "example.jar", 12345L, "hash123");

        String result = jar.toString();

        assertThat(result)
            .contains("example.jar")
            .contains("12345")
            .contains("hash123")
            .contains("loaded=false");
    }

    @Test
    @DisplayName("Should show loaded state in string representation")
    void shouldShowLoadedStateInStringRepresentation() {
        JarMetadata jar = new JarMetadata("/path/to/example.jar", "example.jar", 12345L, "hash123");
        jar.markLoaded();

        String result = jar.toString();

        assertThat(result).contains("loaded=true");
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void shouldHandleNullValuesGracefully() {
        // These should not throw exceptions
        JarMetadata jar = new JarMetadata(null, null, 0L, null);

        assertThat(jar.fullPath).isNull();
        assertThat(jar.fileName).isNull();
        assertThat(jar.sha256Hash).isNull();
        assertThat(jar.size).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should maintain temporal ordering")
    void shouldMaintainTemporalOrdering() {
        JarMetadata jar = new JarMetadata("/path/to/test.jar", "test.jar", 1000L, "hash");

        Instant firstSeen = jar.firstSeen;
        Instant lastAccessed = jar.getLastAccessed();

        // Initially, first seen should be before or equal to last accessed
        assertThat(firstSeen).isBeforeOrEqualTo(lastAccessed);

        jar.markLoaded();
        Instant newLastAccessed = jar.getLastAccessed();

        // After loading, last accessed should be after or equal to original
        assertThat(newLastAccessed).isAfterOrEqualTo(lastAccessed);
        assertThat(jar.firstSeen).isEqualTo(firstSeen); // First seen should not change
    }

    @Test
    @DisplayName("Should handle empty strings")
    void shouldHandleEmptyStrings() {
        JarMetadata jar = new JarMetadata("", "", 0L, "");

        assertThat(jar.fullPath).isEmpty();
        assertThat(jar.fileName).isEmpty();
        assertThat(jar.sha256Hash).isEmpty();
    }

    @Test
    @DisplayName("Should work with typical JAR paths")
    void shouldWorkWithTypicalJarPaths() {
        // Test various typical JAR path formats
        String[] testPaths = {
            "/usr/lib/java/example.jar",
            "C:\\Program Files\\Java\\lib\\example.jar",
            "./lib/example.jar",
            "../dependencies/example.jar",
            "file:/path/to/example.jar",
            "jar:file:/path/outer.jar!/inner.jar"
        };

        for (String path : testPaths) {
            JarMetadata jar = new JarMetadata(path, "example.jar", 1000L, "hash");
            assertThat(jar.fullPath).isEqualTo(path);
            assertThat(jar.isLoaded()).isFalse();
        }
    }

    @Test
    @DisplayName("Should detect nested JAR correctly")
    void shouldDetectNestedJarCorrectly() {
        JarMetadata nestedJar = new JarMetadata("outer.jar!/BOOT-INF/lib/inner.jar", "inner.jar", 1000L, "hash");
        JarMetadata topLevelJar = new JarMetadata("/path/to/toplevel.jar", "toplevel.jar", 2000L, "hash2");

        assertThat(nestedJar.isNested()).isTrue();
        assertThat(nestedJar.isTopLevel()).isFalse();
        
        assertThat(topLevelJar.isNested()).isFalse();
        assertThat(topLevelJar.isTopLevel()).isTrue();
    }

    @Test
    @DisplayName("Should extract container JAR path correctly")
    void shouldExtractContainerJarPathCorrectly() {
        JarMetadata nestedJar = new JarMetadata("outer.jar!/BOOT-INF/lib/inner.jar", "inner.jar", 1000L, "hash");
        JarMetadata topLevelJar = new JarMetadata("/path/to/toplevel.jar", "toplevel.jar", 2000L, "hash2");

        assertThat(nestedJar.getContainerJarPath()).isEqualTo("outer.jar");
        assertThat(topLevelJar.getContainerJarPath()).isNull();
    }

    @Test
    @DisplayName("Should extract inner path correctly")
    void shouldExtractInnerPathCorrectly() {
        JarMetadata nestedJar = new JarMetadata("outer.jar!/BOOT-INF/lib/inner.jar", "inner.jar", 1000L, "hash");
        JarMetadata topLevelJar = new JarMetadata("/path/to/toplevel.jar", "toplevel.jar", 2000L, "hash2");

        assertThat(nestedJar.getInnerPath()).isEqualTo("BOOT-INF/lib/inner.jar");
        assertThat(topLevelJar.getInnerPath()).isEqualTo("/path/to/toplevel.jar");
    }

    @Test
    @DisplayName("Should support equality based on full path")
    void shouldSupportEqualityBasedOnFullPath() {
        JarMetadata jar1 = new JarMetadata("/path/to/same.jar", "same.jar", 1000L, "hash1");
        JarMetadata jar2 = new JarMetadata("/path/to/same.jar", "same.jar", 2000L, "hash2");
        JarMetadata jar3 = new JarMetadata("/path/to/different.jar", "different.jar", 1000L, "hash1");

        assertThat(jar1).isEqualTo(jar2); // Same path, different size/hash
        assertThat(jar1).isNotEqualTo(jar3); // Different path
        
        assertThat(jar1.hashCode()).isEqualTo(jar2.hashCode());
        assertThat(jar1.hashCode()).isNotEqualTo(jar3.hashCode());
    }

    @Test
    @DisplayName("Should create with specific timestamps")
    void shouldCreateWithSpecificTimestamps() {
        Instant specificFirstSeen = Instant.parse("2023-01-01T10:00:00Z");
        Instant specificLastAccessed = Instant.parse("2023-01-01T11:00:00Z");

        JarMetadata jar = new JarMetadata("/path/to/test.jar", "test.jar", 1000L, "hash", 
                                        specificFirstSeen, specificLastAccessed, true);

        assertThat(jar.firstSeen).isEqualTo(specificFirstSeen);
        assertThat(jar.getLastAccessed()).isEqualTo(specificLastAccessed);
        assertThat(jar.isLoaded()).isTrue();
    }

    @Test
    @DisplayName("Should update last accessed time manually")
    void shouldUpdateLastAccessedTimeManually() throws InterruptedException {
        JarMetadata jar = new JarMetadata("/path/to/test.jar", "test.jar", 1000L, "hash");
        Instant originalLastAccessed = jar.getLastAccessed();

        Thread.sleep(10);
        jar.updateLastAccessed();

        assertThat(jar.getLastAccessed()).isAfter(originalLastAccessed);
    }
}
