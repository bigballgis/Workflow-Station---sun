package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compares change-history values by lookup identity, not raw Java equality.
 * Hydrated dropdown/status/stage rows arrive as maps (or JSON strings) while
 * the submitted form still sends the display scalar; those are the same edit.
 */
final class ChangeHistoryValueEquality {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> IDENTITY_KEYS = List.of(
            "id", "value", "dropdown_name", "status_name", "stage_name",
            "label", "name", "displayName", "display_name");
    private static final List<String> DISPLAY_KEYS = List.of(
            "dropdown_name", "status_name", "stage_name", "label", "name",
            "displayName", "display_name");

    private ChangeHistoryValueEquality() {
    }

    static boolean equal(Object oldValue, Object newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return true;
        }
        Set<String> oldTokens = identityTokens(oldValue);
        Set<String> newTokens = identityTokens(newValue);
        if (oldTokens.isEmpty() || newTokens.isEmpty()) {
            return false;
        }
        for (String token : newTokens) {
            if (oldTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    static String displayLabel(Object value) {
        Object unwrapped = unwrap(value);
        if (!(unwrapped instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        for (String key : DISPLAY_KEYS) {
            String token = scalarToken(map.get(key));
            if (token != null) {
                return token;
            }
        }
        return scalarToken(map.get("id"));
    }

    private static Set<String> identityTokens(Object value) {
        Object unwrapped = unwrap(value);
        if (unwrapped == null) {
            return Set.of();
        }
        if (unwrapped instanceof Map<?, ?> map) {
            Set<String> tokens = new LinkedHashSet<>();
            for (String key : IDENTITY_KEYS) {
                String token = scalarToken(map.get(key));
                if (token != null) {
                    tokens.add(token);
                }
            }
            return tokens;
        }
        String scalar = scalarToken(unwrapped);
        return scalar == null ? Set.of() : Set.of(scalar);
    }

    private static Object unwrap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            return value;
        }
        if (!(value instanceof String raw)) {
            return value;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            Map<String, Object> parsed = parseObject(trimmed);
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        return trimmed;
    }

    private static String scalarToken(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof Collection<?>) {
            return null;
        }
        if (value instanceof Boolean) {
            return null;
        }
        String token = String.valueOf(value).trim();
        return token.isEmpty() ? null : token;
    }

    private static Map<String, Object> parseObject(String json) {
        try {
            return JSON.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            // FALLBACK(ux): malformed JSON stays an opaque scalar so equality
            // does not invent a lookup match.
            return Map.of();
        }
    }
}
