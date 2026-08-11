package com.storyplatform.monetization.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Reads the JSON blob on a payment method.
 *
 * <p>The fields differ per method (a bank needs an account number, PayPal a
 * link), so the column is free-form JSON rather than a fixed set of columns.
 */
final class JsonConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonConfig() {
    }

    static Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = MAPPER.readValue(json, Map.class);
            return parsed;
        } catch (Exception exception) {
            // One malformed row must not break the whole payment screen.
            return Map.of();
        }
    }
}
