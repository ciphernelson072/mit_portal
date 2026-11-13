package com.sis.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UserPreferenceNormalizer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_THEMES = Set.of("light", "dark", "system");

    private UserPreferenceNormalizer() {
    }

    public static String normalizeSettingsJson(String existingJson, Object incomingSettingsJson, Object incomingSettingsMap) {
        Map<String, Object> merged = new LinkedHashMap<>();
        Map<String, Object> existing = asMap(existingJson);
        if (existing != null && !existing.isEmpty()) {
            merged.putAll(existing);
        }

        if (incomingSettingsJson != null) {
            Map<String, Object> parsedIncoming = asMap(incomingSettingsJson);
            if (parsedIncoming != null && !parsedIncoming.isEmpty()) {
                merged = deepMerge(merged, parsedIncoming);
            }
        }

        if (incomingSettingsMap != null) {
            Map<String, Object> nestedSettings = asMap(incomingSettingsMap);
            if (nestedSettings != null && !nestedSettings.isEmpty()) {
                merged = deepMerge(merged, nestedSettings);
            }
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(merged);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public static String normalizeTheme(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim();
        return ALLOWED_THEMES.contains(normalized) ? normalized : fallback;
    }

    public static String normalizeLanguage(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> incoming) {
        Map<String, Object> merged = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            Object currentValue = merged.get(entry.getKey());
            Object incomingValue = entry.getValue();
            if (currentValue instanceof Map<?, ?> && incomingValue instanceof Map<?, ?>) {
                merged.put(entry.getKey(), deepMerge(asMap(currentValue), asMap(incomingValue)));
            } else if (incomingValue instanceof List<?> && currentValue instanceof List<?>) {
                merged.put(entry.getKey(), mergeLists((List<?>) currentValue, (List<?>) incomingValue));
            } else {
                merged.put(entry.getKey(), incomingValue);
            }
        }
        return merged;
    }

    private static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                Object parsed = OBJECT_MAPPER.readValue(stringValue, Object.class);
                if (parsed instanceof Map<?, ?> map) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    map.forEach((key, item) -> result.put(String.valueOf(key), item));
                    return result;
                }
            } catch (Exception ignored) {
                // String payloads are allowed to be plain text for other fields, but
                // this specific normalizer only merges structured settings payloads.
            }
        }
        return new LinkedHashMap<>();
    }

    private static List<Object> mergeLists(List<?> currentValue, List<?> incomingValue) {
        if (currentValue.size() == incomingValue.size()) {
            return new ArrayList<>(incomingValue);
        }
        List<Object> merged = new ArrayList<>(currentValue);
        for (int i = 0; i < incomingValue.size(); i++) {
            Object item = incomingValue.get(i);
            if (i < merged.size()) {
                merged.set(i, item);
            } else {
                merged.add(item);
            }
        }
        return merged;
    }
}
