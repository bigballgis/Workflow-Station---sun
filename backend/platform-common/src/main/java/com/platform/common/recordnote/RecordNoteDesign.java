package com.platform.common.recordnote;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Portable Record Note identifiers and opt-in notification configuration in form configJson. */
public final class RecordNoteDesign {
    private RecordNoteDesign() {}

    /** Preserve identifiers on save/import/rollback; copies explicitly request fresh identifiers. */
    public static void prepare(Map<String, Object> config, boolean copy) {
        if (config == null || notes(config).isEmpty()) return;
        Object stored = config.get("recordNoteFormKey");
        String formKey = !copy && stored instanceof String key && !key.isBlank()
                ? key : UUID.randomUUID().toString();
        config.put("recordNoteFormKey", formKey);
        java.util.Set<Object> ids = new java.util.HashSet<>();
        for (Map<String, Object> note : notes(config)) {
            Map<String, Object> props = properties(note);
            props.put("formKey", formKey);
            if (copy || !(props.get("controlId") instanceof String id) || id.isBlank() || ids.contains(id)) {
                props.put("controlId", UUID.randomUUID().toString());
            }
            ids.add(props.get("controlId"));
            validate(props, null);
        }
    }

    /** Validate enabled references against the actual deployment's BPMN node keys. */
    public static void validate(Map<String, Object> props, Set<String> nodeKeys) {
        Object raw = props.get("attachmentNotification");
        if (!(raw instanceof Map<?, ?> policy) || !Boolean.TRUE.equals(policy.get("enabled"))) return;
        if (!Boolean.TRUE.equals(props.getOrDefault("allowAttachment", true))) {
            throw new IllegalArgumentException("Attachment notifications require attachments to be enabled");
        }
        if (!(policy.get("targetNodeKeys") instanceof List<?> keys) || keys.isEmpty()
                || keys.size() > 50) {
            throw new IllegalArgumentException("Select between 1 and 50 notification target task nodes");
        }
        for (Object key : keys) {
            if (!(key instanceof String value) || value.isBlank() || value.length() > 255
                    || (nodeKeys != null && !nodeKeys.contains(value))) {
                throw new IllegalArgumentException("Unresolved Record Note notification target");
            }
        }
    }

    /** Find controls inside rules, layout children, and inline/sub-form configurations. */
    public static List<Map<String, Object>> notes(Object config) {
        List<Map<String, Object>> result = new ArrayList<>();
        collect(config, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void collect(Object value, List<Map<String, Object>> result) {
        if (value instanceof Map<?, ?> map) {
            if ("recordNote".equals(map.get("type"))) result.add((Map<String, Object>) map);
            for (Object nested : map.values()) collect(nested, result);
        } else if (value instanceof List<?> list) {
            for (Object nested : list) collect(nested, result);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> note) {
        if (note.get("props") instanceof Map<?, ?> props) return (Map<String, Object>) props;
        Map<String, Object> props = new java.util.LinkedHashMap<>();
        note.put("props", props);
        return props;
    }
}
