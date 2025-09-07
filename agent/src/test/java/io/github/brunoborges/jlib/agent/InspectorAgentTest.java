package io.github.brunoborges.jlib.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

/**
 * Unit tests for InspectorAgent.
 */
@DisplayName("InspectorAgent Tests")
class InspectorAgentTest {

    @BeforeEach
    void setUp() throws Exception {
        // Reset static state for each test
        resetAgentState();
    }

    @Test
    @DisplayName("Should parse server arguments correctly for localhost")
    void shouldParseServerArgumentsCorrectlyForLocalhost() throws Exception {
        // Use reflection to access the private method
        Method parseArgsMethod = InspectorAgent.class.getDeclaredMethod("parseArguments", String.class);
        parseArgsMethod.setAccessible(true);
        
        // Test localhost with port
        parseArgsMethod.invoke(null, "server:8080");
        
        // Verify serverClient was created (check through reflection)
        Field serverClientField = InspectorAgent.class.getDeclaredField("serverClient");
        serverClientField.setAccessible(true);
        Object serverClient = serverClientField.get(null);
        
        assertThat(serverClient).isNotNull();
    }

    @Test
    @DisplayName("Should parse server arguments correctly for remote host")
    void shouldParseServerArgumentsCorrectlyForRemoteHost() throws Exception {
        // Use reflection to access the private method
        Method parseArgsMethod = InspectorAgent.class.getDeclaredMethod("parseArguments", String.class);
        parseArgsMethod.setAccessible(true);
        
        // Test remote host with port
        parseArgsMethod.invoke(null, "server:example.com:9090");
        
        // Verify serverClient was created
        Field serverClientField = InspectorAgent.class.getDeclaredField("serverClient");
        serverClientField.setAccessible(true);
        Object serverClient = serverClientField.get(null);
        
        assertThat(serverClient).isNotNull();
    }

    @Test
    @DisplayName("Should handle null arguments gracefully")
    void shouldHandleNullArgumentsGracefully() throws Exception {
        // Use reflection to access the private method
        Method parseArgsMethod = InspectorAgent.class.getDeclaredMethod("parseArguments", String.class);
        parseArgsMethod.setAccessible(true);
        
        // Test null arguments
        parseArgsMethod.invoke(null, (String) null);
        
        // Verify serverClient remains null
        Field serverClientField = InspectorAgent.class.getDeclaredField("serverClient");
        serverClientField.setAccessible(true);
        Object serverClient = serverClientField.get(null);
        
        assertThat(serverClient).isNull();
    }

    @Test
    @DisplayName("Should handle empty arguments gracefully")
    void shouldHandleEmptyArgumentsGracefully() throws Exception {
        // Use reflection to access the private method
        Method parseArgsMethod = InspectorAgent.class.getDeclaredMethod("parseArguments", String.class);
        parseArgsMethod.setAccessible(true);
        
        // Test empty arguments
        parseArgsMethod.invoke(null, "");
        parseArgsMethod.invoke(null, "   ");
        
        // Verify serverClient remains null
        Field serverClientField = InspectorAgent.class.getDeclaredField("serverClient");
        serverClientField.setAccessible(true);
        Object serverClient = serverClientField.get(null);
        
        assertThat(serverClient).isNull();
    }

    @Test
    @DisplayName("Should handle invalid server arguments gracefully")
    void shouldHandleInvalidServerArgumentsGracefully() throws Exception {
        // Use reflection to access the private method
        Method parseArgsMethod = InspectorAgent.class.getDeclaredMethod("parseArguments", String.class);
        parseArgsMethod.setAccessible(true);
        
        // Test invalid server specifications
        parseArgsMethod.invoke(null, "server:invalid:port:extra");
        parseArgsMethod.invoke(null, "notserver:8080");
        
        // Should not throw exception and serverClient should remain null
        Field serverClientField = InspectorAgent.class.getDeclaredField("serverClient");
        serverClientField.setAccessible(true);
        Object serverClient = serverClientField.get(null);
        
        assertThat(serverClient).isNull();
    }

    @Test
    @DisplayName("Should normalize file URLs correctly")
    void shouldNormalizeFileUrlsCorrectly() throws Exception {
        // Use reflection to access the private method
        Method normalizeMethod = InspectorAgent.class.getDeclaredMethod("normalizeLocation", String.class);
        normalizeMethod.setAccessible(true);
        
        // Test JAR file URL
        String result1 = (String) normalizeMethod.invoke(null, "file:/path/to/lib.jar");
        assertThat(result1).isEqualTo("file:/path/to/lib.jar");
        
        // Test JAR file URL with nested path
        String result2 = (String) normalizeMethod.invoke(null, "file:/path/to/lib.jar!/some/class");
        assertThat(result2).isEqualTo("file:/path/to/lib.jar");
        
        // Test directory URL
        String result3 = (String) normalizeMethod.invoke(null, "file:/path/to/classes");
        assertThat(result3).isEqualTo("file:/path/to/classes/");
        
        // Test directory URL already ending with slash
        String result4 = (String) normalizeMethod.invoke(null, "file:/path/to/classes/");
        assertThat(result4).isEqualTo("file:/path/to/classes/");
        
        // Test null input
        String result5 = (String) normalizeMethod.invoke(null, (String) null);
        assertThat(result5).isNull();
        
        // Test non-file URL
        String result6 = (String) normalizeMethod.invoke(null, "http://example.com/resource");
        assertThat(result6).isEqualTo("http://example.com/resource");
    }

    @Test
    @DisplayName("Should handle jar-in-jar URLs correctly")
    void shouldHandleJarInJarUrlsCorrectly() throws Exception {
        // Use reflection to access the private method
        Method normalizeMethod = InspectorAgent.class.getDeclaredMethod("normalizeLocation", String.class);
        normalizeMethod.setAccessible(true);
        
        // Test nested JAR URLs
        String nestedJar = "jar:file:/path/to/outer.jar!/BOOT-INF/lib/inner.jar!/some/class";
        String result = (String) normalizeMethod.invoke(null, nestedJar);
        assertThat(result).isEqualTo("jar:file:/path/to/outer.jar");
    }

    @Test
    @DisplayName("Should handle various URL formats")
    void shouldHandleVariousUrlFormats() throws Exception {
        // Use reflection to access the private method
        Method normalizeMethod = InspectorAgent.class.getDeclaredMethod("normalizeLocation", String.class);
        normalizeMethod.setAccessible(true);
        
        // Test various URL formats
        String[] testUrls = {
            "file:/C:/Program Files/app.jar",
            "file:/usr/local/lib/library.jar",
            "jar:file:/path/to/app.jar!/",
            "file:/path/to/build/classes/",
            "/absolute/path/without/protocol.jar",
            "relative/path/lib.jar"
        };
        
        for (String url : testUrls) {
            String result = (String) normalizeMethod.invoke(null, url);
            assertThat(result).isNotNull();
            // Just ensure no exceptions are thrown and we get some result
        }
    }

    @Test
    @DisplayName("Should handle numeric port parsing")
    void shouldHandleNumericPortParsing() throws Exception {
        // Use reflection to access the private method
        Method parseArgsMethod = InspectorAgent.class.getDeclaredMethod("parseArguments", String.class);
        parseArgsMethod.setAccessible(true);
        
        // Test valid numeric ports
        parseArgsMethod.invoke(null, "server:8080");
        
        Field serverClientField = InspectorAgent.class.getDeclaredField("serverClient");
        serverClientField.setAccessible(true);
        Object serverClient = serverClientField.get(null);
        assertThat(serverClient).isNotNull();
        
        // Reset for next test
        resetAgentState();
        
        // Test edge case ports
        parseArgsMethod.invoke(null, "server:1");
        serverClient = serverClientField.get(null);
        assertThat(serverClient).isNotNull();
        
        resetAgentState();
        
        parseArgsMethod.invoke(null, "server:65535");
        serverClient = serverClientField.get(null);
        assertThat(serverClient).isNotNull();
    }

    @Test
    @DisplayName("Should handle invalid port numbers gracefully")
    void shouldHandleInvalidPortNumbersGracefully() throws Exception {
        // Use reflection to access the private method
        Method parseArgsMethod = InspectorAgent.class.getDeclaredMethod("parseArguments", String.class);
        parseArgsMethod.setAccessible(true);
        
        // Test invalid port numbers (should not throw but also not create client)
        try {
            parseArgsMethod.invoke(null, "server:notanumber");
        } catch (Exception e) {
            // Exception is expected due to NumberFormatException, but should be handled
            assertThat(e.getCause()).isInstanceOf(NumberFormatException.class);
        }
        
        // Verify serverClient remains null after invalid port
        Field serverClientField = InspectorAgent.class.getDeclaredField("serverClient");
        serverClientField.setAccessible(true);
        Object serverClient = serverClientField.get(null);
        assertThat(serverClient).isNull();
    }

    /**
     * Helper method to reset static state in InspectorAgent for test isolation.
     */
    private void resetAgentState() throws Exception {
        Field serverClientField = InspectorAgent.class.getDeclaredField("serverClient");
        serverClientField.setAccessible(true);
        serverClientField.set(null, null);
        
        Field applicationIdField = InspectorAgent.class.getDeclaredField("applicationId");
        applicationIdField.setAccessible(true);
        applicationIdField.set(null, null);
    }
}
