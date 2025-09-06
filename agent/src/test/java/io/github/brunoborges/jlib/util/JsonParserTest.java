package io.github.brunoborges.jlib.util;

import java.util.List;
import java.util.Map;

import io.github.brunoborges.jlib.json.JsonParserFactory;
import io.github.brunoborges.jlib.json.JsonParserInterface;

/**
 * Simple test to verify JSON parser factory pattern.
 */
public class JsonParserTest {
    
    public static void main(String[] args) {
        System.out.println("=== JSON Parser Factory Test ===");
        
        // Test factory availability detection
        System.out.println("org.json availability: " + JsonParserFactory.isOrgJsonAvailable());
        System.out.println("Configured parser type: " + JsonParserFactory.getConfiguredParserType());
        
        // Get default parser
        JsonParserInterface parser = JsonParserFactory.getDefaultParser();
        System.out.println("Default parser class: " + parser.getClass().getSimpleName());
        
        // Test JSON escaping
        String testString = "Hello \"World\"\nNew line\tTab";
        String escaped = parser.escapeJson(testString);
        System.out.println("Escaped JSON: " + escaped);
        
        // Test simple JSON parsing
        String jsonObject = "{\"name\":\"test\",\"value\":\"123\",\"flag\":\"true\"}";
        Map<String, String> parsed = parser.parseSimpleJson(jsonObject);
        System.out.println("Parsed object: " + parsed);
        
        // Test JSON array splitting
        String jsonArray = "[{\"a\":\"1\"},{\"b\":\"2\"},{\"c\":\"3\"}]";
        List<String> entries = parser.splitJsonArray(jsonArray);
        System.out.println("Array entries: " + entries.size());
        for (int i = 0; i < entries.size(); i++) {
            System.out.println("  [" + i + "]: " + entries.get(i));
        }
        
        // Test different parser types
        System.out.println("\n=== Testing Different Parser Types ===");
        
        JsonParserInterface builtinParser = JsonParserFactory.createParser(JsonParserFactory.ParserType.BUILTIN);
        System.out.println("Built-in parser: " + builtinParser.getClass().getSimpleName());
        
        JsonParserInterface orgJsonParser = JsonParserFactory.createParser(JsonParserFactory.ParserType.ORG_JSON);
        System.out.println("org.json parser: " + orgJsonParser.getClass().getSimpleName());
        
        JsonParserInterface autoParser = JsonParserFactory.createParser(JsonParserFactory.ParserType.AUTO);
        System.out.println("Auto-selected parser: " + autoParser.getClass().getSimpleName());
        
        System.out.println("\n=== Test Complete ===");
    }
}
