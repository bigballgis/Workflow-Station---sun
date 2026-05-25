package com.developer.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 form configJson 中以 bindingId 为键的 Map 字段重写为新 bindingId（clone / import 共用）。
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
