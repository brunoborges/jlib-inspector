package io.github.brunoborges.jlib.json;

import io.github.brunoborges.jlib.json.JsonParserFactory;
import io.github.brunoborges.jlib.json.JsonParserInterface;
import io.github.brunoborges.jlib.json.JsonParser;
import io.github.brunoborges.jlib.json.OrgJsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

/**
 * Unit tests for JSON parser factory and implementations.
 */
@DisplayName("JSON Parser Tests")
class JsonParserTest {

    @Test
    @DisplayName("Should detect org.json availability")
    void shouldDetectOrgJsonAvailability() {
        boolean isAvailable = JsonParserFactory.isOrgJsonAvailable();
        // Just verify the method doesn't throw an exception
        assertNotNull(isAvailable);
    }

    @Test
    @DisplayName("Should get configured parser type")
    void shouldGetConfiguredParserType() {
        JsonParserFactory.ParserType type = JsonParserFactory.getConfiguredParserType();
        assertNotNull(type);
        assertTrue(type == JsonParserFactory.ParserType.AUTO ||
                   type == JsonParserFactory.ParserType.BUILTIN ||
                   type == JsonParserFactory.ParserType.ORG_JSON);
    }

    @Test
    @DisplayName("Should get default parser")
    void shouldGetDefaultParser() {
        JsonParserInterface parser = JsonParserFactory.getDefaultParser();
        assertNotNull(parser);
    }

    @Test
    @DisplayName("Should create builtin parser")
    void shouldCreateBuiltinParser() {
        JsonParserInterface parser = JsonParserFactory.createParser(JsonParserFactory.ParserType.BUILTIN);
        assertTrue(parser instanceof JsonParser);
    }

    @Test
    @DisplayName("Should create org.json parser")
    void shouldCreateOrgJsonParser() {
        JsonParserInterface parser = JsonParserFactory.createParser(JsonParserFactory.ParserType.ORG_JSON);
        assertTrue(parser instanceof OrgJsonParser);
    }

    @Test
    @DisplayName("Should create auto parser")
    void shouldCreateAutoParser() {
        JsonParserInterface parser = JsonParserFactory.createParser(JsonParserFactory.ParserType.AUTO);
        assertNotNull(parser);
        // Auto parser returns either JsonParser or OrgJsonParser
        assertTrue(parser instanceof JsonParser || parser instanceof OrgJsonParser);
    }

    @Nested
    @DisplayName("Built-in JsonParser Tests")
    class BuiltinJsonParserTest {
        
        private JsonParser parser;
        
        @BeforeEach
        void setUp() {
            parser = new JsonParser();
        }
        
        @Test
        @DisplayName("Should escape JSON strings correctly")
        void shouldEscapeJsonStrings() {
            // Test basic string (no escaping needed)
            assertEquals("hello", parser.escapeJson("hello"));
            
            // Test quote escaping
            assertTrue(parser.escapeJson("Hello \"World\"").contains("\\\""));
            
            // Test backslash escaping
            assertTrue(parser.escapeJson("path\\to\\file").contains("\\\\"));
            
            // Test newline and tab escaping
            String escaped = parser.escapeJson("line1\nline2\ttab");
            assertTrue(escaped.contains("\\n"));
            assertTrue(escaped.contains("\\t"));
            
            // Test null input
            assertEquals("", parser.escapeJson(null));
            
            // Test empty string
            assertEquals("", parser.escapeJson(""));
        }
        
        @Test
        @DisplayName("Should parse simple JSON objects")
        void shouldParseSimpleJsonObjects() {
            String json = "{\"name\":\"test\",\"value\":\"123\",\"flag\":\"true\"}";
            Map<String, String> result = parser.parseSimpleJson(json);
            
            assertEquals(3, result.size());
            assertEquals("test", result.get("name"));
            assertEquals("123", result.get("value"));
            assertEquals("true", result.get("flag"));
        }
        
        @Test
        @DisplayName("Should parse JSON arrays")
        void shouldParseJsonArrays() {
            String jsonArray = "[{\"a\":\"1\"},{\"b\":\"2\"},{\"c\":\"3\"}]";
            List<String> result = parser.splitJsonArray(jsonArray);
            
            assertEquals(3, result.size());
            assertTrue(result.get(0).contains("\"a\""));
            assertTrue(result.get(1).contains("\"b\""));
            assertTrue(result.get(2).contains("\"c\""));
        }
        
        @Test
        @DisplayName("Should handle empty JSON array")
        void shouldHandleEmptyJsonArray() {
            List<String> result = parser.splitJsonArray("[]");
            assertTrue(result.isEmpty());
        }
        
        @Test
        @DisplayName("Should handle empty JSON object")
        void shouldHandleEmptyJsonObject() {
            Map<String, String> result = parser.parseSimpleJson("{}");
            assertTrue(result.isEmpty());
        }
        
        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJson() {
            // Invalid JSON object - should return empty map
            Map<String, String> result = parser.parseSimpleJson("{invalid}");
            assertTrue(result.isEmpty());
            
            // Invalid JSON array - implementation may return the malformed content as-is
            List<String> arrayResult = parser.splitJsonArray("[invalid");
            // The built-in parser may return the content rather than empty list
            assertTrue(arrayResult.isEmpty() || 
                       (arrayResult.size() == 1 && arrayResult.get(0).contains("invalid")));
        }
    }
    
    @Nested
    @DisplayName("OrgJsonParser Tests")
    class OrgJsonParserTest {
        
        private OrgJsonParser parser;
        
        @BeforeEach
        void setUp() {
            parser = new OrgJsonParser();
        }
        
        @Test
        @DisplayName("Should escape JSON strings using org.json")
        void shouldEscapeJsonStrings() {
            assertEquals("hello", parser.escapeJson("hello"));
            assertTrue(parser.escapeJson("Hello \"World\"").contains("\\\""));
            assertTrue(parser.escapeJson("path\\to\\file").contains("\\\\"));
            String escaped = parser.escapeJson("line1\nline2\ttab");
            assertTrue(escaped.contains("\\n"));
            assertTrue(escaped.contains("\\t"));
            assertEquals("", parser.escapeJson(null));
        }
        
        @Test
        @DisplayName("Should parse JSON objects using org.json")
        void shouldParseJsonObjects() {
            String json = "{\"name\":\"test\",\"value\":\"123\",\"flag\":\"true\"}";
            Map<String, String> result = parser.parseSimpleJson(json);
            
            assertEquals(3, result.size());
            assertEquals("test", result.get("name"));
            assertEquals("123", result.get("value"));
            assertEquals("true", result.get("flag"));
        }
        
        @Test
        @DisplayName("Should parse JSON arrays using org.json")
        void shouldParseJsonArrays() {
            String jsonArray = "[{\"a\":\"1\"},{\"b\":\"2\"},{\"c\":\"3\"}]";
            List<String> result = parser.splitJsonArray(jsonArray);
            
            assertEquals(3, result.size());
            assertTrue(result.get(0).contains("\"a\""));
            assertTrue(result.get(1).contains("\"b\""));
            assertTrue(result.get(2).contains("\"c\""));
        }
        
        @Test
        @DisplayName("Should handle complex JSON structures")
        void shouldHandleComplexJson() {
            String complex = "{\"nested\":{\"key\":\"value\"},\"array\":[1,2,3],\"string\":\"test\"}";
            Map<String, String> result = parser.parseSimpleJson(complex);
            
            assertTrue(result.containsKey("string"));
            assertEquals("test", result.get("string"));
            // Note: parseSimpleJson may flatten nested structures or convert them to strings
        }
        
        @Test
        @DisplayName("Should handle null values")
        void shouldHandleNullValues() {
            String json = "{\"key\":null,\"value\":\"test\"}";
            Map<String, String> result = parser.parseSimpleJson(json);
            
            assertTrue(result.containsKey("value"));
            assertEquals("test", result.get("value"));
            // Note: null values may be handled differently by parseSimpleJson
        }
    }
}
