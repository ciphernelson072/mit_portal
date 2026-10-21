package com.sis.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPreferenceNormalizerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void mergeIncomingSettingsWithoutClobberingExistingGroups() throws Exception {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("privacy", Map.of("allowPeers", true, "allowDataCollection", false));
        existing.put("notifications", Map.of("ASSIGNMENT", true));

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("privacy", Map.of("allowInstructors", true));
        update.put("notifications", Map.of("GRADE", true, "ASSIGNMENT", false));

        String persisted = UserPreferenceNormalizer.normalizeSettingsJson(
                OBJECT_MAPPER.writeValueAsString(existing),
                null,
                update
        );

        Map<String, Object> result = OBJECT_MAPPER.readValue(persisted, new TypeReference<Map<String, Object>>() {});
        Map<String, Object> privacy = asNestedMap(result.get("privacy"));
        Map<String, Object> notifications = asNestedMap(result.get("notifications"));
        assertEquals(true, privacy.get("allowPeers"));
        assertEquals(true, privacy.get("allowInstructors"));
        assertEquals(false, notifications.get("ASSIGNMENT"));
        assertEquals(true, notifications.get("GRADE"));
    }

    @Test
    void replaceCheckboxGroupsByIndexInsteadOfAppendingStaleValues() throws Exception {
        String existing = OBJECT_MAPPER.writeValueAsString(Map.of(
                "notifications", List.of(true, true, false, true)
        ));

        String incoming = OBJECT_MAPPER.writeValueAsString(Map.of(
                "notifications", List.of(false, true, true, false)
        ));

        String persisted = UserPreferenceNormalizer.normalizeSettingsJson(existing, incoming, null);
        Map<String, Object> result = OBJECT_MAPPER.readValue(persisted, new TypeReference<Map<String, Object>>() {});
        List<?> notifications = (List<?>) result.get("notifications");
        assertEquals(List.of(false, true, true, false), notifications);
    }

    @Test
    void rejectUnknownThemeValuesAndKeepExistingTheme() {
        assertEquals("dark", UserPreferenceNormalizer.normalizeTheme("dark", "light"));
        assertEquals("light", UserPreferenceNormalizer.normalizeTheme("hacked", "light"));
    }

    @Test
    void acceptStructuredJsonPayloadsForSettings() throws Exception {
        String existing = OBJECT_MAPPER.writeValueAsString(Map.of(
                "quickLinks", List.of(true, false, true, false)
        ));

        String incoming = OBJECT_MAPPER.writeValueAsString(Map.of(
                "quickLinks", List.of(false, true, true, true),
                "privacy", Map.of("allowPeers", true)
        ));

        String persisted = UserPreferenceNormalizer.normalizeSettingsJson(existing, incoming, null);
        Map<String, Object> result = OBJECT_MAPPER.readValue(persisted, new TypeReference<Map<String, Object>>() {});
        List<?> quickLinks = (List<?>) result.get("quickLinks");
        assertEquals(4, quickLinks.size());
        assertTrue(Boolean.TRUE.equals(quickLinks.get(0)) || Boolean.FALSE.equals(quickLinks.get(0)));
        assertEquals(true, asNestedMap(result.get("privacy")).get("allowPeers"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asNestedMap(Object value) {
        return (Map<String, Object>) value;
    }
}
