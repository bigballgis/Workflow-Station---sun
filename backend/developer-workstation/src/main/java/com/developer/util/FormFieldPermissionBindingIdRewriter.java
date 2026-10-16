package com.developer.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Permission keys use the persisted bindingId:fieldName contract; ordinary field keys are unchanged. */
public final class FormFieldPermissionBindingIdRewriter {
    private FormFieldPermissionBindingIdRewriter() { }

    public static Map<String, String> remap(Map<String, String> source, Map<Long, Long> bindings) {
        if (source == null) return null;
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> permission : source.entrySet()) {
            String key = permission.getKey();
            for (Map.Entry<Long, Long> binding : bindings.entrySet()) {
                String prefix = binding.getKey() + ":";
                if (key.startsWith(prefix)) {
                    key = binding.getValue() + ":" + key.substring(prefix.length());
                    break;
                }
            }
            if (result.containsKey(key)) throw new IllegalArgumentException("Field permission binding remap is ambiguous");
            result.put(key, permission.getValue());
        }
        return result;
    }
}
