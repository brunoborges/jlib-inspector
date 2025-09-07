package io.github.brunoborges.jlib.agent;

import io.github.brunoborges.jlib.common.JarMetadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Unit tests for JarInventory.
 */
@DisplayName("JarInventory Tests")
class JarInventoryTest {

    private JarInventory jarInventory;

    @BeforeEach
    void setUp() {
        jarInventory = new JarInventory();
    }

    @Test
    @DisplayName("Should start with empty inventory")
    void shouldStartWithEmptyInventory() {
        Collection<JarMetadata> jars = jarInventory.snapshot();
        assertTrue(jars.isEmpty());
    }

    @Test
    @DisplayName("Should register and retrieve JAR by path")
    void shouldRegisterAndRetrieveJarByPath() {
        String jarPath = "/path/to/test.jar";
        
        JarMetadata jar = jarInventory.registerDeclared(jarPath, 1000L, null);
        
        assertNotNull(jar);
        assertEquals(jarPath, jar.fullPath);
        assertEquals("test.jar", jar.fileName);
        assertFalse(jar.isLoaded());
        assertEquals(1000L, jar.size);
    }

    @Test
    @DisplayName("Should mark JAR as loaded")
    void shouldMarkJarAsLoaded() {
        String jarPath = "/path/to/test.jar";
        
        jarInventory.registerDeclared(jarPath, 1000L, null);
        jarInventory.markLoaded(jarPath);
        
        // Should be marked as loaded now
        Collection<JarMetadata> jars = jarInventory.snapshot();
        JarMetadata loadedJar = jars.stream()
            .filter(j -> j.fullPath.equals(jarPath))
            .findFirst()
            .orElse(null);
        
        assertNotNull(loadedJar);
        assertTrue(loadedJar.isLoaded());
    }

    @Test
    @DisplayName("Should handle nested JAR paths")
    void shouldHandleNestedJarPaths() {
        String nestedPath = "outer.jar!/BOOT-INF/lib/inner.jar";
        
        JarMetadata jar = jarInventory.registerDeclared(nestedPath, 1000L, null);
        
        assertNotNull(jar);
        assertEquals(nestedPath, jar.fullPath);
        assertEquals("inner.jar", jar.fileName);
        assertTrue(jar.isNested());
        assertEquals("outer.jar", jar.getContainerJarPath());
    }

    @Test
    @DisplayName("Should not duplicate JARs")
    void shouldNotDuplicateJars() {
        String jarPath = "/path/to/test.jar";
        
        jarInventory.registerDeclared(jarPath, 1000L, null);
        jarInventory.registerDeclared(jarPath, 2000L, null); // Register same JAR again
        
        Collection<JarMetadata> jars = jarInventory.snapshot();
        assertEquals(1, jars.size());
        
        // Should keep the first registration
        JarMetadata jar = jars.iterator().next();
        assertEquals(1000L, jar.size);
    }

    @Test
    @DisplayName("Should compute hash for existing file")
    void shouldComputeHashForExistingFile(@TempDir Path tempDir) throws IOException {
        // Create a temporary JAR file
        File tempJar = tempDir.resolve("test.jar").toFile();
        try (FileOutputStream fos = new FileOutputStream(tempJar)) {
            fos.write("test content".getBytes());
        }
        
        JarInventory.HashSupplier hashSupplier = JarInventory.fileHashSupplier(tempJar);
        JarMetadata jar = jarInventory.registerDeclared(tempJar.getAbsolutePath(), tempJar.length(), hashSupplier);
        
        assertNotNull(jar);
        assertNotEquals("?", jar.sha256Hash); // Should have computed hash
        assertEquals(tempJar.length(), jar.size);
    }

    @Test
    @DisplayName("Should handle missing hash supplier gracefully")
    void shouldHandleMissingHashSupplierGracefully() {
        String jarPath = "/path/to/test.jar";
        
        JarMetadata jar = jarInventory.registerDeclared(jarPath, -1L, null);
        
        assertNotNull(jar);
        assertEquals("?", jar.sha256Hash); // Hash unavailable
        assertEquals(-1L, jar.size); // Size unknown
    }

    @Test
    @DisplayName("Should extract filename correctly from various paths")
    void shouldExtractFilenameCorrectlyFromVariousPaths() {
        String[] testCases = {
            "/simple/path.jar", "path.jar",
            "/complex/path/with/subdirs/file.jar", "file.jar",
            "relative/path.jar", "path.jar",
            "nested.jar!/inner.jar", "inner.jar",
            "outer.jar!/BOOT-INF/lib/deep-nested.jar", "deep-nested.jar",
            "C:\\Windows\\Path\\file.jar", "file.jar"
        };
        
        for (int i = 0; i < testCases.length; i += 2) {
            String path = testCases[i];
            String expectedFilename = testCases[i + 1];
            
            JarMetadata jar = jarInventory.registerDeclared(path, 1000L, null);
            
            assertEquals(expectedFilename, jar.fileName, "Failed for path: " + path);
        }
    }

    @Test
    @DisplayName("Should handle concurrent access safely")
    void shouldHandleConcurrentAccessSafely() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        
        // Start multiple threads adding different JARs
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                jarInventory.registerDeclared("/path/to/jar" + index + ".jar", 1000L, null);
                jarInventory.markLoaded("/path/to/jar" + index + ".jar");
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Should have all JARs
        Collection<JarMetadata> jars = jarInventory.snapshot();
        assertEquals(threadCount, jars.size());
        
        // All should be marked as loaded
        for (JarMetadata jar : jars) {
            assertTrue(jar.isLoaded());
        }
    }

    @Test
    @DisplayName("Should handle marking non-existent JAR as loaded")
    void shouldHandleMarkingNonExistentJarAsLoaded() {
        // Should create the JAR if it doesn't exist
        jarInventory.markLoaded("/non/existent.jar");
        
        Collection<JarMetadata> jars = jarInventory.snapshot();
        assertEquals(1, jars.size());
        
        JarMetadata jar = jars.iterator().next();
        assertEquals("/non/existent.jar", jar.fullPath);
        assertTrue(jar.isLoaded());
        assertEquals(-1L, jar.size);
        assertEquals("?", jar.sha256Hash);
    }

    @Test
    @DisplayName("Should maintain JAR metadata consistency")
    void shouldMaintainJarMetadataConsistency() {
        String jarPath = "/path/to/consistent.jar";
        
        JarMetadata jar1 = jarInventory.registerDeclared(jarPath, 1000L, null);
        jarInventory.markLoaded(jarPath);
        
        Collection<JarMetadata> jars = jarInventory.snapshot();
        JarMetadata jar2 = jars.stream()
            .filter(j -> j.fullPath.equals(jarPath))
            .findFirst()
            .orElse(null);
        
        // Should be same instance
        assertSame(jar2, jar1);
        assertTrue(jar2.isLoaded());
    }

    @Test
    @DisplayName("Should support various JAR file extensions")
    void shouldSupportVariousJarFileExtensions() {
        String[] extensions = {".jar", ".war", ".ear", ".zip"};
        
        for (String ext : extensions) {
            String path = "/path/to/archive" + ext;
            JarMetadata jar = jarInventory.registerDeclared(path, 1000L, null);
            
            assertNotNull(jar);
            assertEquals("archive" + ext, jar.fileName);
        }
        
        Collection<JarMetadata> jars = jarInventory.snapshot();
        assertEquals(extensions.length, jars.size());
    }

    @Test
    @DisplayName("Should handle null jar path gracefully")
    void shouldHandleNullJarPathGracefully() {
        // Should not throw exception
        jarInventory.markLoaded(null);
        
        // Should still be empty
        assertTrue(jarInventory.snapshot().isEmpty());
    }
}
