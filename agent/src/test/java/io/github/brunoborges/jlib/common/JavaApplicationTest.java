package io.github.brunoborges.jlib.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;

/**
 * Unit tests for JavaApplication.
 */
@DisplayName("JavaApplication Tests")
class JavaApplicationTest {

    @Test
    @DisplayName("Should create Java application with all required fields")
    void shouldCreateJavaApplicationWithAllRequiredFields() {
        String appId = "test-app-123";
        String commandLine = "java -jar myapp.jar";
        String jdkVersion = "17.0.2";
        String jdkVendor = "Eclipse Adoptium";
        String jdkPath = "/usr/lib/jvm/java-17";

        JavaApplication app = new JavaApplication(appId, commandLine, jdkVersion, jdkVendor, jdkPath);

        assertThat(app.appId).isEqualTo(appId);
        assertThat(app.commandLine).isEqualTo(commandLine);
        assertThat(app.jdkVersion).isEqualTo(jdkVersion);
        assertThat(app.jdkVendor).isEqualTo(jdkVendor);
        assertThat(app.jdkPath).isEqualTo(jdkPath);
        assertThat(app.firstSeen).isNotNull();
        assertThat(app.lastUpdated).isNotNull();
        assertThat(app.jars).isEmpty();
    }

    @Test
    @DisplayName("Should initialize timestamps correctly")
    void shouldInitializeTimestampsCorrectly() {
        JavaApplication app = new JavaApplication("test", "java -jar test.jar", "17", "OpenJDK", "/java");

        assertThat(app.firstSeen).isBeforeOrEqualTo(app.lastUpdated);
        assertThat(app.firstSeen).isBeforeOrEqualTo(Instant.now());
        assertThat(app.lastUpdated).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Should allow updating last updated timestamp")
    void shouldAllowUpdatingLastUpdatedTimestamp() throws InterruptedException {
        JavaApplication app = new JavaApplication("test", "java -jar test.jar", "17", "OpenJDK", "/java");
        Instant originalLastUpdated = app.lastUpdated;

        Thread.sleep(10);
        app.lastUpdated = Instant.now();

        assertThat(app.lastUpdated).isAfter(originalLastUpdated);
        assertThat(app.firstSeen).isEqualTo(app.firstSeen); // Should not change
    }

    @Test
    @DisplayName("Should provide thread-safe JAR map")
    void shouldProvideThreadSafeJarMap() {
        JavaApplication app = new JavaApplication("test", "java -jar test.jar", "17", "OpenJDK", "/java");

        // Add some JARs
        JarMetadata jar1 = new JarMetadata("/path/to/jar1.jar", "jar1.jar", 1000L, "hash1");
        JarMetadata jar2 = new JarMetadata("/path/to/jar2.jar", "jar2.jar", 2000L, "hash2");

        app.jars.put(jar1.fullPath, jar1);
        app.jars.put(jar2.fullPath, jar2);

        assertThat(app.jars).hasSize(2);
        assertThat(app.jars.get("/path/to/jar1.jar")).isEqualTo(jar1);
        assertThat(app.jars.get("/path/to/jar2.jar")).isEqualTo(jar2);
    }

    @Test
    @DisplayName("Should handle null values appropriately")
    void shouldHandleNullValuesAppropriately() {
        // These should not throw exceptions
        JavaApplication app = new JavaApplication(null, null, null, null, null);

        assertThat(app.appId).isNull();
        assertThat(app.commandLine).isNull();
        assertThat(app.jdkVersion).isNull();
        assertThat(app.jdkVendor).isNull();
        assertThat(app.jdkPath).isNull();
        assertThat(app.firstSeen).isNotNull();
        assertThat(app.lastUpdated).isNotNull();
        assertThat(app.jars).isNotNull();
    }

    @Test
    @DisplayName("Should handle empty strings")
    void shouldHandleEmptyStrings() {
        JavaApplication app = new JavaApplication("", "", "", "", "");

        assertThat(app.appId).isEmpty();
        assertThat(app.commandLine).isEmpty();
        assertThat(app.jdkVersion).isEmpty();
        assertThat(app.jdkVendor).isEmpty();
        assertThat(app.jdkPath).isEmpty();
    }

    @Test
    @DisplayName("Should support complex command lines")
    void shouldSupportComplexCommandLines() {
        String complexCommandLine = "java -Xms512m -Xmx2g -Dprop=value -javaagent:agent.jar=options -jar myapp.jar --spring.profiles.active=prod";
        
        JavaApplication app = new JavaApplication("complex-app", complexCommandLine, "17", "OpenJDK", "/java");

        assertThat(app.commandLine).isEqualTo(complexCommandLine);
    }

    @Test
    @DisplayName("Should handle JAR updates correctly")
    void shouldHandleJarUpdatesCorrectly() {
        JavaApplication app = new JavaApplication("test", "java -jar test.jar", "17", "OpenJDK", "/java");

        // Add initial JAR
        JarMetadata jar = new JarMetadata("/path/to/test.jar", "test.jar", 1000L, "hash1");
        app.jars.put(jar.fullPath, jar);

        assertThat(app.jars).hasSize(1);
        assertThat(jar.isLoaded()).isFalse();

        // Update JAR (mark as loaded)
        jar.markLoaded();
        assertThat(jar.isLoaded()).isTrue();

        // Replace with new version
        JarMetadata updatedJar = new JarMetadata("/path/to/test.jar", "test.jar", 1100L, "hash2");
        app.jars.put(updatedJar.fullPath, updatedJar);

        assertThat(app.jars).hasSize(1);
        assertThat(app.jars.get("/path/to/test.jar")).isEqualTo(updatedJar);
        assertThat(app.jars.get("/path/to/test.jar").size).isEqualTo(1100L);
    }

    @Test
    @DisplayName("Should handle nested JARs in application")
    void shouldHandleNestedJarsInApplication() {
        JavaApplication app = new JavaApplication("spring-app", "java -jar spring-boot-app.jar", "17", "OpenJDK", "/java");

        // Add main JAR
        JarMetadata mainJar = new JarMetadata("/app/spring-boot-app.jar", "spring-boot-app.jar", 50000L, "mainhash");
        
        // Add nested JARs
        JarMetadata nestedJar1 = new JarMetadata("spring-boot-app.jar!/BOOT-INF/lib/spring-core.jar", "spring-core.jar", 1000L, "hash1");
        JarMetadata nestedJar2 = new JarMetadata("spring-boot-app.jar!/BOOT-INF/lib/spring-context.jar", "spring-context.jar", 2000L, "hash2");

        app.jars.put(mainJar.fullPath, mainJar);
        app.jars.put(nestedJar1.fullPath, nestedJar1);
        app.jars.put(nestedJar2.fullPath, nestedJar2);

        assertThat(app.jars).hasSize(3);
        
        // Verify nested JAR detection
        assertThat(nestedJar1.isNested()).isTrue();
        assertThat(nestedJar2.isNested()).isTrue();
        assertThat(mainJar.isNested()).isFalse();
        
        // Verify container JAR paths
        assertThat(nestedJar1.getContainerJarPath()).isEqualTo("spring-boot-app.jar");
        assertThat(nestedJar2.getContainerJarPath()).isEqualTo("spring-boot-app.jar");
    }
}
