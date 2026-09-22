package com.admin.component;

import com.admin.dto.response.TableBindingDTO;
import com.admin.exception.AdminBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Catalog FORM {@code content_data} is either a legacy bare configJson string, or the full
 * Developer Workstation form JSON ({@code configJson} + {@code tableBindings}).
 *
 * <p>PROCESS/TASK/ACTION runtime freezes those snapshot bindings instead of re-querying live
 * {@code dw_form_table_bindings}. DETAIL still overlays live bindings.
 */
final class CatalogFormSnapshot {

    private CatalogFormSnapshot() {
    }

    record Payload(String configJson, List<TableBindingDTO> tableBindings, boolean freezeBindings) {
    }

    static Payload unwrap(ObjectMapper objectMapper, String contentData) {
        if (contentData == null || contentData.isBlank()) {
            return new Payload(contentData, null, false);
        }
        Object raw;
        try {
            raw = objectMapper.readValue(contentData, Object.class);
        } catch (Exception e) {
            return new Payload(contentData, null, false);
        }
        if (!(raw instanceof Map<?, ?> parsed) || !isFullFormWrapper(parsed)) {
            return new Payload(contentData, null, false);
        }
        String configJson = stringifyConfig(objectMapper, parsed.get("configJson"));
        if (!parsed.containsKey("tableBindings")) {
            return new Payload(configJson, null, false);
        }
        return new Payload(configJson, mapBindings(parsed.get("tableBindings")), true);
    }

    private static boolean isFullFormWrapper(Map<?, ?> parsed) {
        if (!parsed.containsKey("configJson")) {
            return false;
        }
        return parsed.containsKey("formName")
                || parsed.containsKey("formId")
                || parsed.containsKey("formType");
    }

    private static String stringifyConfig(ObjectMapper objectMapper, Object configJson) {
        if (configJson == null) {
            throw new AdminBusinessException("INVALID_FORM_SNAPSHOT",
                    "Catalog form is missing configJson");
        }
        if (configJson instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(configJson);
        } catch (Exception e) {
            throw new AdminBusinessException("INVALID_FORM_SNAPSHOT",
                    "Unable to serialize catalog form configJson", e);
        }
    }

    private static List<TableBindingDTO> mapBindings(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new AdminBusinessException("INVALID_FORM_SNAPSHOT",
                    "Catalog form tableBindings must be a JSON array");
        }
        List<TableBindingDTO> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new AdminBusinessException("INVALID_FORM_SNAPSHOT",
                        "Catalog form tableBindings entries must be objects");
            }
            out.add(mapBinding(map));
        }
        return out;
    }

    private static TableBindingDTO mapBinding(Map<?, ?> m) {
        String bindingType = asString(m.get("bindingType"));
        Long tableId = asLong(m.get("tableId"));
        if (tableId == null && "RELATED".equalsIgnoreCase(bindingType)) {
            tableId = asLong(m.get("relationTableId"));
        }
        return TableBindingDTO.builder()
                .bindingId(asLong(m.get("bindingId")))
                .tableId(tableId)
                .bindingType(bindingType)
                .bindingMode(asString(m.get("bindingMode")))
                .subMode(asString(m.get("subMode")))
                .foreignKeyField(asString(m.get("foreignKeyField")))
                .bindingLinkMode(asString(m.get("bindingLinkMode")))
                .filterFkFieldName(asString(m.get("filterFkFieldName")))
                .filterFkRefTableName(asString(m.get("filterFkRefTableName")))
                .filterFkRefTableId(asLong(m.get("filterFkRefTableId")))
                .fkFillSources(mapFillSources(m.get("fkFillSources")))
                .sortOrder(asInt(m.get("sortOrder")))
                .tableName(asString(m.get("tableName")))
                .tableDisplayName(asString(m.get("tableDisplayName")))
                .tableType(asString(m.get("tableType")))
                .tableDescription(asString(m.get("tableDescription")))
                .build();
    }

    private static java.util.List<com.admin.dto.response.FkFillSourceDTO> mapFillSources(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        java.util.List<com.admin.dto.response.FkFillSourceDTO> out = new java.util.ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String kind = asString(map.get("kind"));
            String fieldName = asString(map.get("fieldName"));
            Long fieldId = asLong(map.get("fieldId"));
            if (kind == null || (fieldName == null && fieldId == null)) {
                continue;
            }
            out.add(com.admin.dto.response.FkFillSourceDTO.builder()
                    .fieldId(asLong(map.get("fieldId")))
                    .fieldName(fieldName)
                    .kind(kind)
                    .ancestorBindingId(asLong(map.get("ancestorBindingId")))
                    .ancestorTableName(asString(map.get("ancestorTableName")))
                    .ancestorFilterFkFieldName(asString(map.get("ancestorFilterFkFieldName")))
                    .build());
        }
        return out.isEmpty() ? null : out;
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new AdminBusinessException("INVALID_FORM_SNAPSHOT",
                    "Catalog form binding numeric field is not a number");
        }
    }

    private static Integer asInt(Object value) {
        Long n = asLong(value);
        return n == null ? null : n.intValue();
    }
}
