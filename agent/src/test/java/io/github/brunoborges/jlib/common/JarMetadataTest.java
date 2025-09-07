package io.github.brunoborges.jlib.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

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

        assertEquals(fullPath, jar.fullPath);
        assertEquals(fileName, jar.fileName);
        assertEquals(size, jar.size);
        assertEquals(hash, jar.sha256Hash);
        assertFalse(jar.isLoaded());
        assertNotNull(jar.firstSeen);
        assertNotNull(jar.getLastAccessed());
    }

    @Test
    @DisplayName("Should handle nested JAR paths")
    void shouldHandleNestedJarPaths() {
        String nestedPath = "outer.jar!/BOOT-INF/lib/inner.jar";
        String fileName = "inner.jar";

        JarMetadata jar = new JarMetadata(nestedPath, fileName, 1000L, "hash123");

        assertEquals(nestedPath, jar.fullPath);
        assertEquals(fileName, jar.fileName);
    }

    @Test
    @DisplayName("Should handle unknown size and hash")
    void shouldHandleUnknownSizeAndHash() {
        JarMetadata jar = new JarMetadata("/path/to/unknown.jar", "unknown.jar", -1L, "?");

        assertEquals(-1L, jar.size);
        assertEquals("?", jar.sha256Hash);
    }

    @Test
    @DisplayName("Should track loaded state thread-safely")
    void shouldTrackLoadedStateThreadSafely() {
        JarMetadata jar = new JarMetadata("/path/to/test.jar", "test.jar", 1000L, "hash");

        assertFalse(jar.isLoaded());

        jar.markLoaded();
        assertTrue(jar.isLoaded());

        // Should remain true
        jar.markLoaded();
        assertTrue(jar.isLoaded());
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

        assertTrue(updatedAccessed.compareTo(initialAccessed) > 0);
    }

    @Test
    @DisplayName("Should create formatted string representation")
    void shouldCreateFormattedStringRepresentation() {
        JarMetadata jar = new JarMetadata("/path/to/example.jar", "example.jar", 12345L, "hash123");

        String result = jar.toString();

        assertTrue(result.contains("example.jar"));
        assertTrue(result.contains("12345"));
        assertTrue(result.contains("hash123"));
        assertTrue(result.contains("loaded=false"));
    }

    @Test
    @DisplayName("Should show loaded state in string representation")
    void shouldShowLoadedStateInStringRepresentation() {
        JarMetadata jar = new JarMetadata("/path/to/example.jar", "example.jar", 12345L, "hash123");
        jar.markLoaded();

        String result = jar.toString();

        assertTrue(result.contains("loaded=true"));
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void shouldHandleNullValuesGracefully() {
        // These should not throw exceptions
        JarMetadata jar = new JarMetadata(null, null, 0L, null);

        assertNull(jar.fullPath);
        assertNull(jar.fileName);
        assertNull(jar.sha256Hash);
        assertEquals(0L, jar.size);
    }

    @Test
    @DisplayName("Should maintain temporal ordering")
    void shouldMaintainTemporalOrdering() {
        JarMetadata jar = new JarMetadata("/path/to/test.jar", "test.jar", 1000L, "hash");

        Instant firstSeen = jar.firstSeen;
        Instant lastAccessed = jar.getLastAccessed();

        // Initially, first seen should be before or equal to last accessed
        assertTrue(firstSeen.compareTo(lastAccessed) <= 0);

        jar.markLoaded();
        Instant newLastAccessed = jar.getLastAccessed();

        // After loading, last accessed should be after or equal to original
        assertTrue(newLastAccessed.compareTo(lastAccessed) >= 0);
        assertEquals(firstSeen, jar.firstSeen); // First seen should not change
    }

    @Test
    @DisplayName("Should handle empty strings")
    void shouldHandleEmptyStrings() {
        JarMetadata jar = new JarMetadata("", "", 0L, "");

        assertTrue(jar.fullPath.isEmpty());
        assertTrue(jar.fileName.isEmpty());
        assertTrue(jar.sha256Hash.isEmpty());
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
            assertEquals(path, jar.fullPath);
            assertFalse(jar.isLoaded());
        }
    }

    @Test
    @DisplayName("Should detect nested JAR correctly")
    void shouldDetectNestedJarCorrectly() {
        JarMetadata nestedJar = new JarMetadata("outer.jar!/BOOT-INF/lib/inner.jar", "inner.jar", 1000L, "hash");
        JarMetadata topLevelJar = new JarMetadata("/path/to/toplevel.jar", "toplevel.jar", 2000L, "hash2");

        assertTrue(nestedJar.isNested());
        assertFalse(nestedJar.isTopLevel());
        
        assertFalse(topLevelJar.isNested());
        assertTrue(topLevelJar.isTopLevel());
    }

    @Test
    @DisplayName("Should extract container JAR path correctly")
    void shouldExtractContainerJarPathCorrectly() {
        JarMetadata nestedJar = new JarMetadata("outer.jar!/BOOT-INF/lib/inner.jar", "inner.jar", 1000L, "hash");
        JarMetadata topLevelJar = new JarMetadata("/path/to/toplevel.jar", "toplevel.jar", 2000L, "hash2");

        assertEquals("outer.jar", nestedJar.getContainerJarPath());
        assertNull(topLevelJar.getContainerJarPath());
    }

    @Test
    @DisplayName("Should extract inner path correctly")
    void shouldExtractInnerPathCorrectly() {
        JarMetadata nestedJar = new JarMetadata("outer.jar!/BOOT-INF/lib/inner.jar", "inner.jar", 1000L, "hash");
        JarMetadata topLevelJar = new JarMetadata("/path/to/toplevel.jar", "toplevel.jar", 2000L, "hash2");

        assertEquals("BOOT-INF/lib/inner.jar", nestedJar.getInnerPath());
        assertEquals("/path/to/toplevel.jar", topLevelJar.getInnerPath());
    }

    @Test
    @DisplayName("Should support equality based on full path")
    void shouldSupportEqualityBasedOnFullPath() {
        JarMetadata jar1 = new JarMetadata("/path/to/same.jar", "same.jar", 1000L, "hash1");
        JarMetadata jar2 = new JarMetadata("/path/to/same.jar", "same.jar", 2000L, "hash2");
        JarMetadata jar3 = new JarMetadata("/path/to/different.jar", "different.jar", 1000L, "hash1");

        assertEquals(jar2, jar1); // Same path, different size/hash
        assertNotEquals(jar3, jar1); // Different path
        
        assertEquals(jar2.hashCode(), jar1.hashCode());
        assertNotEquals(jar3.hashCode(), jar1.hashCode());
    }

    @Test
    @DisplayName("Should create with specific timestamps")
    void shouldCreateWithSpecificTimestamps() {
        Instant specificFirstSeen = Instant.parse("2023-01-01T10:00:00Z");
        Instant specificLastAccessed = Instant.parse("2023-01-01T11:00:00Z");

        JarMetadata jar = new JarMetadata("/path/to/test.jar", "test.jar", 1000L, "hash", 
                                        specificFirstSeen, specificLastAccessed, true);

        assertEquals(specificFirstSeen, jar.firstSeen);
        assertEquals(specificLastAccessed, jar.getLastAccessed());
        assertTrue(jar.isLoaded());
    }

    @Test
    @DisplayName("Should update last accessed time manually")
    void shouldUpdateLastAccessedTimeManually() throws InterruptedException {
        JarMetadata jar = new JarMetadata("/path/to/test.jar", "test.jar", 1000L, "hash");
        Instant originalLastAccessed = jar.getLastAccessed();

        Thread.sleep(10);
        jar.updateLastAccessed();

        assertTrue(jar.getLastAccessed().compareTo(originalLastAccessed) > 0);
    }
}
