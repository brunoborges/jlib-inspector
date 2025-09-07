package io.github.brunoborges.jlib.json;

import io.github.brunoborges.jlib.json.JsonParser;
import io.github.brunoborges.jlib.json.JsonParserInterface;
import io.github.brunoborges.jlib.json.JsonParserFactory;
import io.github.brunoborges.jlib.json.OrgJsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonParserFactory.
 */
@DisplayName("JsonParserFactory Tests")
class JsonParserFactoryTest {

    @BeforeEach
    void setUp() {
        // Clear cache before each test
        JsonParserFactory.clearCache();
    }

    @AfterEach
    void tearDown() {
        // Reset to default configuration after each test
        JsonParserFactory.setDefaultParserType(JsonParserFactory.ParserType.AUTO);
        JsonParserFactory.clearCache();
    }

    @Test
    @DisplayName("Should detect org.json availability correctly")
    void shouldDetectOrgJsonAvailability() {
        boolean isAvailable = JsonParserFactory.isOrgJsonAvailable();
        assertTrue(isAvailable); // org.json should be available in our test environment
    }

    @Test
    @DisplayName("Should return default parser type as AUTO")
    void shouldReturnDefaultParserTypeAsAuto() {
        assertEquals(JsonParserFactory.ParserType.AUTO, JsonParserFactory.getConfiguredParserType());
    }

    @Test
    @DisplayName("Should create built-in parser when requested")
    void shouldCreateBuiltinParser() {
        JsonParserInterface parser = JsonParserFactory.createParser(JsonParserFactory.ParserType.BUILTIN);
        
        assertTrue(parser instanceof JsonParser);
    }

    @Test
    @DisplayName("Should create org.json parser when available and requested")
    void shouldCreateOrgJsonParser() {
        JsonParserInterface parser = JsonParserFactory.createParser(JsonParserFactory.ParserType.ORG_JSON);
        
        if (JsonParserFactory.isOrgJsonAvailable()) {
            assertTrue(parser instanceof OrgJsonParser);
        } else {
            // Should fallback to built-in parser
            assertTrue(parser instanceof JsonParser);
        }
    }

    @Test
    @DisplayName("Should auto-select best available parser")
    void shouldAutoSelectBestParser() {
        JsonParserInterface parser = JsonParserFactory.createParser(JsonParserFactory.ParserType.AUTO);
        
        if (JsonParserFactory.isOrgJsonAvailable()) {
            assertTrue(parser instanceof OrgJsonParser);
        } else {
            assertTrue(parser instanceof JsonParser);
        }
    }

    @Test
    @DisplayName("Should return same instance for default parser calls")
    void shouldReturnSameInstanceForDefaultParser() {
        JsonParserInterface parser1 = JsonParserFactory.getDefaultParser();
        JsonParserInterface parser2 = JsonParserFactory.getDefaultParser();
        
        assertSame(parser1, parser2);
    }

    @Test
    @DisplayName("Should update default parser type")
    void shouldUpdateDefaultParserType() {
        JsonParserFactory.setDefaultParserType(JsonParserFactory.ParserType.BUILTIN);
        assertEquals(JsonParserFactory.ParserType.BUILTIN, JsonParserFactory.getConfiguredParserType());
        
        JsonParserInterface parser = JsonParserFactory.getDefaultParser();
        assertTrue(parser instanceof JsonParser);
    }

    @Test
    @DisplayName("Should clear cache and create new instance")
    void shouldClearCacheAndCreateNewInstance() {
        JsonParserInterface parser1 = JsonParserFactory.getDefaultParser();
        JsonParserFactory.clearCache();
        JsonParserInterface parser2 = JsonParserFactory.getDefaultParser();
        
        // Different instances after cache clear
        assertNotSame(parser1, parser2);
        // But same class
        assertEquals(parser1.getClass(), parser2.getClass());
    }

    @Test
    @DisplayName("Should change parser type and clear cache automatically")
    void shouldChangeParserTypeAndClearCache() {
        JsonParserInterface parser1 = JsonParserFactory.getDefaultParser();
        
        // Change parser type
        JsonParserFactory.setDefaultParserType(JsonParserFactory.ParserType.BUILTIN);
        JsonParserInterface parser2 = JsonParserFactory.getDefaultParser();
        
        assertTrue(parser2 instanceof JsonParser);
        assertNotSame(parser1, parser2);
    }
}
