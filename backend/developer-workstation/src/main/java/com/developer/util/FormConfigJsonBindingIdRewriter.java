package com.developer.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 form configJson 中以 bindingId 为键的 Map 字段重写为新 bindingId（clone / import 共用）。
 * 同时重写画布 rule 树中 subTable 占位符的 {@code _bindingId}，避免 export/import/clone 后绑定丢失。
 */
public final class FormConfigJsonBindingIdRewriter {

    private static final String[] BINDING_KEYED_FIELDS = {
            "subForms",
            "subListViews",
            "relationViews",
            "subTablePortalViews"
    };

    private FormConfigJsonBindingIdRewriter() {
    }

    public static void remapBindingIds(Map<String, Object> configJson, Map<Long, Long> bindingIdMapping) {
        if (configJson == null || bindingIdMapping == null || bindingIdMapping.isEmpty()) {
            return;
        }
        for (String fieldName : BINDING_KEYED_FIELDS) {
            remapMapKeys(configJson, fieldName, bindingIdMapping);
        }
        remapRuleBindingIds(configJson.get("rule"), bindingIdMapping);
        remapSubFormRules(configJson.get("subForms"), bindingIdMapping);
        remapSubListViewColumnBindingRefs(configJson.get("subListViews"), bindingIdMapping);
    }

    @SuppressWarnings("unchecked")
    private static void remapSubFormRules(Object subFormsObj, Map<Long, Long> bindingIdMapping) {
        if (!(subFormsObj instanceof Map<?, ?> subForms)) {
            return;
        }
        for (Object entryVal : subForms.values()) {
            if (entryVal instanceof Map<?, ?> subFormMap) {
                remapRuleBindingIds(subFormMap.get("rule"), bindingIdMapping);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void remapSubListViewColumnBindingRefs(Object subListViewsObj, Map<Long, Long> bindingIdMapping) {
        if (!(subListViewsObj instanceof Map<?, ?> subListViews)) {
            return;
        }
        for (Object entryVal : subListViews.values()) {
            if (!(entryVal instanceof Map<?, ?> entry)) {
                continue;
            }
            Object columns = entry.get("columns");
            if (!(columns instanceof List<?> colList)) {
                continue;
            }
            for (Object col : colList) {
                if (!(col instanceof Map<?, ?> colMapRaw)) {
                    continue;
                }
                Map<String, Object> colMap = (Map<String, Object>) colMapRaw;
                remapBindingIdField(colMap, "boundSubTableBindingId", bindingIdMapping);
                remapNegativeComponentId(colMap, "componentId", bindingIdMapping);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void remapRuleBindingIds(Object ruleObj, Map<Long, Long> bindingIdMapping) {
        if (!(ruleObj instanceof List<?> rules)) {
            return;
        }
        for (Object item : rules) {
            if (!(item instanceof Map<?, ?> nodeRaw)) {
                continue;
            }
            Map<String, Object> node = (Map<String, Object>) nodeRaw;
            if ("subTable".equals(node.get("type"))) {
                remapBindingIdField(node, "_bindingId", bindingIdMapping);
                Object props = node.get("props");
                if (props instanceof Map<?, ?> propsRaw) {
                    Map<String, Object> propsMap = (Map<String, Object>) propsRaw;
                    remapBindingIdField(propsMap, "_bindingId", bindingIdMapping);
                    remapBindingIdField(propsMap, "bindingId", bindingIdMapping);
                }
            }
            Object children = node.get("children");
            if (children instanceof List<?>) {
                remapRuleBindingIds(children, bindingIdMapping);
            }
        }
    }

    private static void remapBindingIdField(Map<String, Object> container, String fieldName,
                                            Map<Long, Long> bindingIdMapping) {
        Object raw = container.get(fieldName);
        Long remapped = remapBindingIdValue(raw, bindingIdMapping);
        if (remapped != null && !remapped.equals(asLong(raw))) {
            container.put(fieldName, remapped);
        }
    }

    private static void remapNegativeComponentId(Map<String, Object> container, String fieldName,
                                                 Map<Long, Long> bindingIdMapping) {
        Object raw = container.get(fieldName);
        if (!(raw instanceof Number number)) {
            return;
        }
        long value = number.longValue();
        if (value >= 0) {
            return;
        }
        long oldBindingId = Math.abs(value);
        Long newBindingId = bindingIdMapping.get(oldBindingId);
        if (newBindingId != null) {
            container.put(fieldName, -newBindingId);
        }
    }

    private static Long remapBindingIdValue(Object raw, Map<Long, Long> bindingIdMapping) {
        Long oldId = asLong(raw);
        if (oldId == null) {
            return null;
        }
        return bindingIdMapping.getOrDefault(oldId, oldId);
    }

    private static Long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String str && !str.isBlank()) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void remapMapKeys(Map<String, Object> configJson, String fieldName,
                                     Map<Long, Long> bindingIdMapping) {
        Object fieldValue = configJson.get(fieldName);
        if (!(fieldValue instanceof Map<?, ?> oldMapRaw)) {
            return;
        }
        Map<String, Object> oldMap = (Map<String, Object>) oldMapRaw;
        Map<String, Object> newMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
            try {
                Long oldId = Long.parseLong(entry.getKey());
                Long newId = bindingIdMapping.get(oldId);
                if (newId != null) {
                    newMap.put(String.valueOf(newId), entry.getValue());
                } else {
                    newMap.put(entry.getKey(), entry.getValue());
                }
            } catch (NumberFormatException e) {
                newMap.put(entry.getKey(), entry.getValue());
            }
        }
        configJson.put(fieldName, newMap);
    }
}
