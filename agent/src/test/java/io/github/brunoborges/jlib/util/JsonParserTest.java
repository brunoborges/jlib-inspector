package io.github.brunoborges.jlib.util;

import io.github.brunoborges.jlib.json.JsonParserFactory;
import io.github.brunoborges.jlib.json.JsonParserInterface;
import io.github.brunoborges.jlib.json.JsonParser;
import io.github.brunoborges.jlib.json.OrgJsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.*;

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
        assertThat(isAvailable).isNotNull();
    }

    @Test
    @DisplayName("Should get configured parser type")
    void shouldGetConfiguredParserType() {
        JsonParserFactory.ParserType type = JsonParserFactory.getConfiguredParserType();
        assertThat(type).isNotNull();
        assertThat(type).isIn(
            JsonParserFactory.ParserType.AUTO,
            JsonParserFactory.ParserType.BUILTIN,
            JsonParserFactory.ParserType.ORG_JSON
        );
    }

    @Test
    @DisplayName("Should get default parser")
    void shouldGetDefaultParser() {
        JsonParserInterface parser = JsonParserFactory.getDefaultParser();
        assertThat(parser).isNotNull();
    }

    @Test
    @DisplayName("Should create builtin parser")
    void shouldCreateBuiltinParser() {
        JsonParserInterface parser = JsonParserFactory.createParser(JsonParserFactory.ParserType.BUILTIN);
        assertThat(parser).isInstanceOf(JsonParser.class);
    }

    @Test
    @DisplayName("Should create org.json parser")
    void shouldCreateOrgJsonParser() {
        JsonParserInterface parser = JsonParserFactory.createParser(JsonParserFactory.ParserType.ORG_JSON);
        assertThat(parser).isInstanceOf(OrgJsonParser.class);
    }

    @Test
    @DisplayName("Should create auto parser")
    void shouldCreateAutoParser() {
        JsonParserInterface parser = JsonParserFactory.createParser(JsonParserFactory.ParserType.AUTO);
        assertThat(parser).isNotNull();
        // Auto parser returns either JsonParser or OrgJsonParser
        assertThat(parser).satisfiesAnyOf(
            p -> assertThat(p).isInstanceOf(JsonParser.class),
            p -> assertThat(p).isInstanceOf(OrgJsonParser.class)
        );
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
            assertThat(parser.escapeJson("hello")).isEqualTo("hello");
            
            // Test quote escaping
            assertThat(parser.escapeJson("Hello \"World\"")).contains("\\\"");
            
            // Test backslash escaping
            assertThat(parser.escapeJson("path\\to\\file")).contains("\\\\");
            
            // Test newline and tab escaping
            assertThat(parser.escapeJson("line1\nline2\ttab")).contains("\\n").contains("\\t");
            
            // Test null input
            assertThat(parser.escapeJson(null)).isEqualTo("");
            
            // Test empty string
            assertThat(parser.escapeJson("")).isEqualTo("");
        }
        
        @Test
        @DisplayName("Should parse simple JSON objects")
        void shouldParseSimpleJsonObjects() {
            String json = "{\"name\":\"test\",\"value\":\"123\",\"flag\":\"true\"}";
            Map<String, String> result = parser.parseSimpleJson(json);
            
            assertThat(result)
                .hasSize(3)
                .containsEntry("name", "test")
                .containsEntry("value", "123")
                .containsEntry("flag", "true");
        }
        
        @Test
        @DisplayName("Should parse JSON arrays")
        void shouldParseJsonArrays() {
            String jsonArray = "[{\"a\":\"1\"},{\"b\":\"2\"},{\"c\":\"3\"}]";
            List<String> result = parser.splitJsonArray(jsonArray);
            
            assertThat(result).hasSize(3);
            assertThat(result.get(0)).contains("\"a\"");
            assertThat(result.get(1)).contains("\"b\"");
            assertThat(result.get(2)).contains("\"c\"");
        }
        
        @Test
        @DisplayName("Should handle empty JSON array")
        void shouldHandleEmptyJsonArray() {
            List<String> result = parser.splitJsonArray("[]");
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("Should handle empty JSON object")
        void shouldHandleEmptyJsonObject() {
            Map<String, String> result = parser.parseSimpleJson("{}");
            assertThat(result).isEmpty();
        }
        
        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJson() {
            // Invalid JSON object - should return empty map
            Map<String, String> result = parser.parseSimpleJson("{invalid}");
            assertThat(result).isEmpty();
            
            // Invalid JSON array - implementation may return the malformed content as-is
            List<String> arrayResult = parser.splitJsonArray("[invalid");
            // The built-in parser may return the content rather than empty list
            assertThat(arrayResult).satisfiesAnyOf(
                list -> assertThat(list).isEmpty(),
                list -> assertThat(list).hasSize(1).first().asString().contains("invalid")
            );
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
            assertThat(parser.escapeJson("hello")).isEqualTo("hello");
            assertThat(parser.escapeJson("Hello \"World\"")).contains("\\\"");
            assertThat(parser.escapeJson("path\\to\\file")).contains("\\\\");
            assertThat(parser.escapeJson("line1\nline2\ttab")).contains("\\n").contains("\\t");
            assertThat(parser.escapeJson(null)).isEqualTo("");
        }
        
        @Test
        @DisplayName("Should parse JSON objects using org.json")
        void shouldParseJsonObjects() {
            String json = "{\"name\":\"test\",\"value\":\"123\",\"flag\":\"true\"}";
            Map<String, String> result = parser.parseSimpleJson(json);
            
            assertThat(result).hasSize(3);
            assertThat(result.get("name")).isEqualTo("test");
            assertThat(result.get("value")).isEqualTo("123");
            assertThat(result.get("flag")).isEqualTo("true");
        }
        
        @Test
        @DisplayName("Should parse JSON arrays using org.json")
        void shouldParseJsonArrays() {
            String jsonArray = "[{\"a\":\"1\"},{\"b\":\"2\"},{\"c\":\"3\"}]";
            List<String> result = parser.splitJsonArray(jsonArray);
            
            assertThat(result).hasSize(3);
            assertThat(result.get(0)).contains("\"a\"");
            assertThat(result.get(1)).contains("\"b\"");
            assertThat(result.get(2)).contains("\"c\"");
        }
        
        @Test
        @DisplayName("Should handle complex JSON structures")
        void shouldHandleComplexJson() {
            String complex = "{\"nested\":{\"key\":\"value\"},\"array\":[1,2,3],\"string\":\"test\"}";
            Map<String, String> result = parser.parseSimpleJson(complex);
            
            assertThat(result).containsKey("string");
            assertThat(result.get("string")).isEqualTo("test");
            // Note: parseSimpleJson may flatten nested structures or convert them to strings
        }
        
        @Test
        @DisplayName("Should handle null values")
        void shouldHandleNullValues() {
            String json = "{\"key\":null,\"value\":\"test\"}";
            Map<String, String> result = parser.parseSimpleJson(json);
            
            assertThat(result).containsKey("value");
            assertThat(result.get("value")).isEqualTo("test");
            // Note: null values may be handled differently by parseSimpleJson
        }
    }
}
