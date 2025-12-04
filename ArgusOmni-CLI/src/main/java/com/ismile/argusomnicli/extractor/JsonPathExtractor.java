package com.ismile.argusomnicli.extractor;

import com.jayway.jsonpath.JsonPath;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * JSONPath-based response extractor.
 * Follows Single Responsibility - only extracts data using JSONPath.
 */
@Component
public class JsonPathExtractor implements ResponseExtractor {

    @Override
    public Map<String, Object> extract(Object response, Map<String, String> extractConfig) throws Exception {
        Map<String, Object> extracted = new HashMap<>();

        if (extractConfig == null || extractConfig.isEmpty()) {
            return extracted;
        }

        // Convert response to JSON string if needed
        String jsonResponse = convertToJson(response);

        // Extract each configured path
        extractConfig.forEach((key, path) -> {
            try {
                Object value = JsonPath.read(jsonResponse, path);
                extracted.put(key, value);
            } catch (Exception e) {
                throw new RuntimeException("Failed to extract '" + key + "' from path: " + path, e);
            }
        });

        return extracted;
    }

    private String convertToJson(Object response) throws Exception {
        if (response instanceof String) {
            return (String) response;
        }

        // Convert object to JSON
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.writeValueAsString(response);
    }
}
