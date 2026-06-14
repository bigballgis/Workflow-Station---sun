package com.workflow.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pure helpers for normalizing Flowable assignee user-id values extracted from process variables,
 * element-variable rows and BPMN expressions. Extracted verbatim from {@link TaskAssignmentListener};
 * behavior is unchanged.
 */
@Slf4j
final class AssigneeUserIdNormalizer {

    /** Flowable ACT_RU/HI identity link USER_ID_/GROUP_ID_ columns are varchar(255). */
    static final int FLOWABLE_IDENTITY_USER_ID_MAX = 255;

    private static final ObjectMapper USER_REF_OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private AssigneeUserIdNormalizer() {
    }

    static String extractUserIdFromRefMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        for (String k : new String[]{"id", "userId", "user_id", "value"}) {
            Object v = map.get(k);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    static String normalizeFlowableUserIdValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String id;
        if (raw instanceof Map<?, ?> m) {
            id = extractUserIdFromRefMap(m);
        } else if (raw instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isFinite(d) && Math.floor(d) == d) {
                id = String.valueOf(n.longValue());
            } else {
                id = n.toString();
            }
        } else {
            id = extractUserIdFromString(String.valueOf(raw));
        }
        if (id == null) {
            return null;
        }
        String t = id.trim();
        if (t.isEmpty() || "null".equalsIgnoreCase(t)) {
            return null;
        }
        if (t.length() > FLOWABLE_IDENTITY_USER_ID_MAX) {
            log.warn("ELEMENT_VARIABLE: skip assignee id longer than {} chars (Flowable identity link limit)",
                    FLOWABLE_IDENTITY_USER_ID_MAX);
            return null;
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    static String extractUserIdFromString(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return value;
        }
        String mapLikeId = extractUserIdFromMapLikeString(value);
        if (mapLikeId != null) {
            return mapLikeId;
        }
        if (value.startsWith("\"")) {
            try {
                Object parsed = USER_REF_OBJECT_MAPPER.readValue(value, Object.class);
                if (parsed instanceof String parsedString) {
                    return extractUserIdFromString(parsedString);
                }
            } catch (Exception ignored) {
                // Not a JSON string literal; try the generic UUID fallback below.
            }
        }
        if (value.startsWith("{") || value.startsWith("[")) {
            try {
                Object parsed = USER_REF_OBJECT_MAPPER.readValue(value, Object.class);
                if (parsed instanceof Map<?, ?> map) {
                    String id = extractUserIdFromRefMap(map);
                    return id != null ? id : value;
                }
                if (parsed instanceof List<?> list && !list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof Map<?, ?> map) {
                        String id = extractUserIdFromRefMap(map);
                        return id != null ? id : value;
                    }
                    return first != null ? String.valueOf(first).trim() : value;
                }
            } catch (Exception ignored) {
                // Not JSON, keep the original string.
            }
        }
        Matcher matcher = UUID_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group();
        }
        return value;
    }

    static String extractUserIdFromMapLikeString(String value) {
        if (value == null || !value.startsWith("{") || !value.endsWith("}") || !value.contains("=")) {
            return null;
        }
        for (String key : new String[]{"id", "userId", "user_id", "value"}) {
            Matcher matcher = Pattern.compile("(?i)(^|[,\\{]\\s*)" + Pattern.quote(key) + "\\s*=\\s*([^,}]+)")
                    .matcher(value);
            if (matcher.find()) {
                String id = matcher.group(2).trim();
                return id.isEmpty() || "null".equalsIgnoreCase(id) ? null : id;
            }
        }
        return null;
    }

    static List<String> sanitizeFlowableUserIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return ids;
        }
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String t = id.trim();
            if (t.length() > FLOWABLE_IDENTITY_USER_ID_MAX) {
                log.warn("ASSIGNEE_FROM_VARIABLE: skip user id longer than {} chars (Flowable identity link limit)",
                        FLOWABLE_IDENTITY_USER_ID_MAX);
                continue;
            }
            out.add(t);
        }
        return out;
    }

    static List<String> splitUserList(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }
}
