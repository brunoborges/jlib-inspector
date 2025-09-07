package io.github.brunoborges.jlib.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Method;

/**
 * Unit tests for JLibServerClient.
 */
@DisplayName("JLibServerClient Tests")
class JLibServerClientTest {

    private JLibServerClient client;
    private JarInventory inventory;

    @BeforeEach
    void setUp() {
        client = new JLibServerClient("localhost", 8080);
        inventory = new JarInventory();
    }

    @Test
    @DisplayName("Should create client with correct host and port")
    void shouldCreateClientWithCorrectHostAndPort() {
        JLibServerClient testClient = new JLibServerClient("example.com", 9090);
        assertThat(testClient).isNotNull();
    }

    @Test
    @DisplayName("Should build valid JSON for empty inventory")
    void shouldBuildValidJsonForEmptyInventory() throws Exception {
        // Use reflection to access the private method
        Method buildJsonMethod = JLibServerClient.class.getDeclaredMethod("buildApplicationJson", JarInventory.class);
        buildJsonMethod.setAccessible(true);
        
        String json = (String) buildJsonMethod.invoke(client, inventory);
        
        assertThat(json).isNotNull();
        assertThat(json).contains("\"commandLine\":");
        assertThat(json).contains("\"jdkVersion\":");
        assertThat(json).contains("\"jdkVendor\":");
        assertThat(json).contains("\"jdkPath\":");
        assertThat(json).contains("\"jars\":[]");
        assertThat(json).startsWith("{");
        assertThat(json).endsWith("}");
    }

    @Test
    @DisplayName("Should build valid JSON with JAR inventory")
    void shouldBuildValidJsonWithJarInventory() throws Exception {
        // Add some test JARs to inventory
        inventory.registerDeclared("/path/to/test1.jar", 1024L, null);
        inventory.registerDeclared("/path/to/test2.jar", 2048L, null);
        inventory.markLoaded("/path/to/test1.jar");
        
        // Use reflection to access the private method
        Method buildJsonMethod = JLibServerClient.class.getDeclaredMethod("buildApplicationJson", JarInventory.class);
        buildJsonMethod.setAccessible(true);
        
        String json = (String) buildJsonMethod.invoke(client, inventory);
        
        assertThat(json).isNotNull();
        assertThat(json).contains("\"path\":\"/path/to/test1.jar\"");
        assertThat(json).contains("\"path\":\"/path/to/test2.jar\"");
        assertThat(json).contains("\"fileName\":\"test1.jar\"");
        assertThat(json).contains("\"fileName\":\"test2.jar\"");
        assertThat(json).contains("\"size\":1024");
        assertThat(json).contains("\"size\":2048");
        assertThat(json).contains("\"loaded\":true");
        assertThat(json).contains("\"loaded\":false");
    }

    @Test
    @DisplayName("Should escape special characters in JSON")
    void shouldEscapeSpecialCharactersInJson() throws Exception {
        // Add JAR with special characters in path
        String specialPath = "C:\\Program Files\\My \"App\"\\lib\\test.jar";
        inventory.registerDeclared(specialPath, 1024L, null);
        
        // Use reflection to access the private method
        Method buildJsonMethod = JLibServerClient.class.getDeclaredMethod("buildApplicationJson", JarInventory.class);
        buildJsonMethod.setAccessible(true);
        
        String json = (String) buildJsonMethod.invoke(client, inventory);
        
        assertThat(json).contains("\\\\"); // Escaped backslashes
        assertThat(json).contains("\\\""); // Escaped quotes
        assertThat(json).doesNotContain("\"App\""); // Original quotes should be escaped
    }

    @Test
    @DisplayName("Should escape JSON strings correctly")
    void shouldEscapeJsonStringsCorrectly() throws Exception {
        // Use reflection to access the private method
        Method escapeJsonMethod = JLibServerClient.class.getDeclaredMethod("escapeJson", String.class);
        escapeJsonMethod.setAccessible(true);
        
        // Test various special characters
        String result1 = (String) escapeJsonMethod.invoke(null, "Path with \"quotes\"");
        assertThat(result1).isEqualTo("Path with \\\"quotes\\\"");
        
        String result2 = (String) escapeJsonMethod.invoke(null, "Path\\with\\backslashes");
        assertThat(result2).isEqualTo("Path\\\\with\\\\backslashes");
        
        String result3 = (String) escapeJsonMethod.invoke(null, "Line\nwith\nnewlines");
        assertThat(result3).isEqualTo("Line\\nwith\\nnewlines");
        
        String result4 = (String) escapeJsonMethod.invoke(null, "Tab\tseparated\tvalues");
        assertThat(result4).isEqualTo("Tab\\tseparated\\tvalues");
        
        // Test null input
        String result5 = (String) escapeJsonMethod.invoke(null, (String) null);
        assertThat(result5).isEqualTo("");
    }

    @Test
    @DisplayName("Should get command line information")
    void shouldGetCommandLineInformation() throws Exception {
        // Use reflection to access the private method
        Method getCommandLineMethod = JLibServerClient.class.getDeclaredMethod("getFullCommandLine");
        getCommandLineMethod.setAccessible(true);
        
        String commandLine = (String) getCommandLineMethod.invoke(null);
        
        assertThat(commandLine).isNotNull();
        assertThat(commandLine).isNotEmpty();
        assertThat(commandLine).contains("java");
    }

    @Test
    @DisplayName("Should handle inventory with nested JARs")
    void shouldHandleInventoryWithNestedJars() throws Exception {
        // Add nested JAR
        String nestedPath = "outer.jar!/BOOT-INF/lib/inner.jar";
        inventory.registerDeclared(nestedPath, 512L, null);
        
        // Use reflection to access the private method
        Method buildJsonMethod = JLibServerClient.class.getDeclaredMethod("buildApplicationJson", JarInventory.class);
        buildJsonMethod.setAccessible(true);
        
        String json = (String) buildJsonMethod.invoke(client, inventory);
        
        assertThat(json).contains("\"path\":\"outer.jar!/BOOT-INF/lib/inner.jar\"");
        assertThat(json).contains("\"fileName\":\"inner.jar\"");
        assertThat(json).contains("\"size\":512");
    }

    @Test
    @DisplayName("Should handle JARs with unknown checksums")
    void shouldHandleJarsWithUnknownChecksums() throws Exception {
        // Add JAR without hash supplier (checksum will be "?")
        inventory.registerDeclared("/unknown/checksum.jar", -1L, null);
        
        // Use reflection to access the private method
        Method buildJsonMethod = JLibServerClient.class.getDeclaredMethod("buildApplicationJson", JarInventory.class);
        buildJsonMethod.setAccessible(true);
        
        String json = (String) buildJsonMethod.invoke(client, inventory);
        
        assertThat(json).contains("\"checksum\":\"?\"");
        assertThat(json).contains("\"size\":-1");
    }

    @Test
    @DisplayName("Should include system properties in JSON")
    void shouldIncludeSystemPropertiesInJson() throws Exception {
        // Use reflection to access the private method
        Method buildJsonMethod = JLibServerClient.class.getDeclaredMethod("buildApplicationJson", JarInventory.class);
        buildJsonMethod.setAccessible(true);
        
        String json = (String) buildJsonMethod.invoke(client, inventory);
        
        // Check for JDK information
        String jdkVersion = System.getProperty("java.version");
        
        assertThat(json).contains("\"jdkVersion\":\"" + jdkVersion + "\"");
        assertThat(json).contains("\"jdkVendor\":");
        assertThat(json).contains("\"jdkPath\":");
    }

    @Test
    @DisplayName("Should handle multiple JARs with different states")
    void shouldHandleMultipleJarsWithDifferentStates() throws Exception {
        // Add multiple JARs with different loading states
        inventory.registerDeclared("/loaded/jar1.jar", 1000L, null);
        inventory.registerDeclared("/not-loaded/jar2.jar", 2000L, null);
        inventory.registerDeclared("/also-loaded/jar3.jar", 3000L, null);
        
        inventory.markLoaded("/loaded/jar1.jar");
        inventory.markLoaded("/also-loaded/jar3.jar");
        
        // Use reflection to access the private method
        Method buildJsonMethod = JLibServerClient.class.getDeclaredMethod("buildApplicationJson", JarInventory.class);
        buildJsonMethod.setAccessible(true);
        
        String json = (String) buildJsonMethod.invoke(client, inventory);
        
        // Count JAR entries in JSON
        assertThat(json).contains("jar1.jar");
        assertThat(json).contains("jar2.jar");
        assertThat(json).contains("jar3.jar");
        
        // Check for proper JSON array structure
        int jarArrayStart = json.indexOf("\"jars\":[");
        int jarArrayEnd = json.indexOf("]", jarArrayStart);
        assertThat(jarArrayStart).isGreaterThan(-1);
        assertThat(jarArrayEnd).isGreaterThan(jarArrayStart);
        
        String jarSection = json.substring(jarArrayStart, jarArrayEnd + 1);
        // Should contain 3 JAR objects
        int jarObjectCount = jarSection.split("\\{").length - 1;
        assertThat(jarObjectCount).isEqualTo(3);
    }

    @Test
    @DisplayName("Should create valid JSON structure")
    void shouldCreateValidJsonStructure() throws Exception {
        // Add some test data
        inventory.registerDeclared("/test.jar", 1000L, null);
        inventory.markLoaded("/test.jar");
        
        // Use reflection to access the private method
        Method buildJsonMethod = JLibServerClient.class.getDeclaredMethod("buildApplicationJson", JarInventory.class);
        buildJsonMethod.setAccessible(true);
        
        String json = (String) buildJsonMethod.invoke(client, inventory);
        
        // Verify JSON structure
        assertThat(json).startsWith("{");
        assertThat(json).endsWith("}");
        
        // Count braces to ensure they're balanced
        long openBraces = json.chars().filter(ch -> ch == '{').count();
        long closeBraces = json.chars().filter(ch -> ch == '}').count();
        assertThat(openBraces).isEqualTo(closeBraces);
        
        // Count brackets to ensure they're balanced  
        long openBrackets = json.chars().filter(ch -> ch == '[').count();
        long closeBrackets = json.chars().filter(ch -> ch == ']').count();
        assertThat(openBrackets).isEqualTo(closeBrackets);
    }
}
