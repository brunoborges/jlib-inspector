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
        assertThat(jars).isEmpty();
    }

    @Test
    @DisplayName("Should register and retrieve JAR by path")
    void shouldRegisterAndRetrieveJarByPath() {
        String jarPath = "/path/to/test.jar";
        
        JarMetadata jar = jarInventory.registerDeclared(jarPath, 1000L, null);
        
        assertThat(jar).isNotNull();
        assertThat(jar.fullPath).isEqualTo(jarPath);
        assertThat(jar.fileName).isEqualTo("test.jar");
        assertThat(jar.isLoaded()).isFalse();
        assertThat(jar.size).isEqualTo(1000L);
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
        
        assertThat(loadedJar).isNotNull();
        assertThat(loadedJar.isLoaded()).isTrue();
    }

    @Test
    @DisplayName("Should handle nested JAR paths")
    void shouldHandleNestedJarPaths() {
        String nestedPath = "outer.jar!/BOOT-INF/lib/inner.jar";
        
        JarMetadata jar = jarInventory.registerDeclared(nestedPath, 1000L, null);
        
        assertThat(jar).isNotNull();
        assertThat(jar.fullPath).isEqualTo(nestedPath);
        assertThat(jar.fileName).isEqualTo("inner.jar");
        assertThat(jar.isNested()).isTrue();
        assertThat(jar.getContainerJarPath()).isEqualTo("outer.jar");
    }

    @Test
    @DisplayName("Should not duplicate JARs")
    void shouldNotDuplicateJars() {
        String jarPath = "/path/to/test.jar";
        
        jarInventory.registerDeclared(jarPath, 1000L, null);
        jarInventory.registerDeclared(jarPath, 2000L, null); // Register same JAR again
        
        Collection<JarMetadata> jars = jarInventory.snapshot();
        assertThat(jars).hasSize(1);
        
        // Should keep the first registration
        JarMetadata jar = jars.iterator().next();
        assertThat(jar.size).isEqualTo(1000L);
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
        
        assertThat(jar).isNotNull();
        assertThat(jar.sha256Hash).isNotEqualTo("?"); // Should have computed hash
        assertThat(jar.size).isEqualTo(tempJar.length());
    }

    @Test
    @DisplayName("Should handle missing hash supplier gracefully")
    void shouldHandleMissingHashSupplierGracefully() {
        String jarPath = "/path/to/test.jar";
        
        JarMetadata jar = jarInventory.registerDeclared(jarPath, -1L, null);
        
        assertThat(jar).isNotNull();
        assertThat(jar.sha256Hash).isEqualTo("?"); // Hash unavailable
        assertThat(jar.size).isEqualTo(-1L); // Size unknown
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
            
            assertThat(jar.fileName)
                .withFailMessage("Failed for path: " + path)
                .isEqualTo(expectedFilename);
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
        assertThat(jars).hasSize(threadCount);
        
        // All should be marked as loaded
        for (JarMetadata jar : jars) {
            assertThat(jar.isLoaded()).isTrue();
        }
    }

    @Test
    @DisplayName("Should handle marking non-existent JAR as loaded")
    void shouldHandleMarkingNonExistentJarAsLoaded() {
        // Should create the JAR if it doesn't exist
        jarInventory.markLoaded("/non/existent.jar");
        
        Collection<JarMetadata> jars = jarInventory.snapshot();
        assertThat(jars).hasSize(1);
        
        JarMetadata jar = jars.iterator().next();
        assertThat(jar.fullPath).isEqualTo("/non/existent.jar");
        assertThat(jar.isLoaded()).isTrue();
        assertThat(jar.size).isEqualTo(-1L);
        assertThat(jar.sha256Hash).isEqualTo("?");
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
        assertThat(jar1).isSameAs(jar2);
        assertThat(jar2.isLoaded()).isTrue();
    }

    @Test
    @DisplayName("Should support various JAR file extensions")
    void shouldSupportVariousJarFileExtensions() {
        String[] extensions = {".jar", ".war", ".ear", ".zip"};
        
        for (String ext : extensions) {
            String path = "/path/to/archive" + ext;
            JarMetadata jar = jarInventory.registerDeclared(path, 1000L, null);
            
            assertThat(jar).isNotNull();
            assertThat(jar.fileName).isEqualTo("archive" + ext);
        }
        
        Collection<JarMetadata> jars = jarInventory.snapshot();
        assertThat(jars).hasSize(extensions.length);
    }

    @Test
    @DisplayName("Should handle null jar path gracefully")
    void shouldHandleNullJarPathGracefully() {
        // Should not throw exception
        jarInventory.markLoaded(null);
        
        // Should still be empty
        assertThat(jarInventory.snapshot()).isEmpty();
    }
}
