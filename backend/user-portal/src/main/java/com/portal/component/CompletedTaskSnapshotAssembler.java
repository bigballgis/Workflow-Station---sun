package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.util.SubTableNestingSanitizer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Builds completed-task {@code fieldValues}: the Task/Process form field subset plus a
 * canonical {@code __subTables__} freeze (numeric binding-id slices only).
 *
 * <p>Empty {@code fieldPermissions} is not “no form”. Alias keys (table name / case variants)
 * stay out of the snapshot so JSONB does not copy the 4–5× fan-out from live variables
 * (issue 1397). No form → empty map, including no {@code __subTables__}.
 */
final class CompletedTaskSnapshotAssembler {

    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };

    private CompletedTaskSnapshotAssembler() {
    }

    static Map<String, Object> assembleFieldValues(
            Map<String, Object> mergedVariables,
            Set<String> snapshotKeys,
            boolean formResolved,
            TaskFormFieldMapper fieldMapper,
            ObjectMapper objectMapper) {
        if (!formResolved || mergedVariables == null) {
            return new HashMap<>();
        }
        Map<String, Object> fieldValues = snapshotKeys == null || snapshotKeys.isEmpty()
                ? new HashMap<>()
                : fieldMapper.extractFieldSubset(mergedVariables, snapshotKeys);
        attachCanonicalSubTables(fieldValues, mergedVariables, objectMapper);
        return fieldValues;
    }

    @SuppressWarnings("unchecked")
    private static void attachCanonicalSubTables(
            Map<String, Object> fieldValues,
            Map<String, Object> mergedVariables,
            ObjectMapper objectMapper) {
        Object raw = mergedVariables.get("__subTables__");
        if (!(raw instanceof Map<?, ?> live) || live.isEmpty()) {
            return;
        }
        Map<String, Object> copy = copyJsonMap(objectMapper, live);
        if (copy.isEmpty()) {
            return;
        }
        Map<String, Object> canonical = MiSubTableVariableSupport.canonicalizeSubTablesAliasKeys(copy);
        Map<String, Object> bag = new HashMap<>();
        bag.put("__subTables__", canonical);
        SubTableNestingSanitizer.stripDeepNestedSubTables(bag);
        Object frozen = bag.get("__subTables__");
        if (frozen instanceof Map<?, ?> frozenMap && !frozenMap.isEmpty()) {
            fieldValues.put("__subTables__", frozenMap);
        }
    }

    private static Map<String, Object> copyJsonMap(ObjectMapper objectMapper, Map<?, ?> source) {
        if (objectMapper == null) {
            return stringKeyedCopy(source);
        }
        try {
            Map<String, Object> copied = objectMapper.convertValue(source, JSON_MAP);
            return copied != null ? copied : new HashMap<>();
        } catch (IllegalArgumentException ex) {
            return stringKeyedCopy(source);
        }
    }

    private static Map<String, Object> stringKeyedCopy(Map<?, ?> source) {
        Map<String, Object> out = new HashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                out.put(String.valueOf(key), value);
            }
        });
        return out;
    }
}
