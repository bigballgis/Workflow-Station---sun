package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.exception.PortalException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Expands the published sparse permission contract over a finite, frozen field definition. */
final class SubTableBatchPublishedPermissions {
    private SubTableBatchPublishedPermissions() { }

    static Map<String, String> capture(Map<String, Object> form, String bindingId, List<?> fields, ObjectMapper mapper) {
        Map<?, ?> permissions = publishedPermissions(form, mapper);
        if (permissions.entrySet().stream().anyMatch(entry -> !(entry.getKey() instanceof String)
                || !("EDITABLE".equals(entry.getValue()) || "READONLY".equals(entry.getValue())))) {
            throw invalid("Published batch field permissions are invalid");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Object raw : fields) {
            if (!(raw instanceof Map<?, ?> field) || !(field.get("fieldName") instanceof String name) || name.isBlank()) {
                throw invalid("Published batch field identity is invalid");
            }
            boolean protectedField = Boolean.TRUE.equals(field.get("isForeignKey")) || Boolean.TRUE.equals(field.get("isComputed"))
                    || (Boolean.TRUE.equals(field.get("isPrimaryKey")) && !manualKey(field));
            String mode = protectedField || "READONLY".equals(permissions.get(bindingId + ":" + name)) ? "READONLY" : "EDITABLE";
            if (result.putIfAbsent(name, mode) != null) throw invalid("Published batch field identity is duplicated");
        }
        return result;
    }

    private static Map<?, ?> publishedPermissions(Map<String, Object> form, ObjectMapper mapper) {
        // Admin content transport unwraps configJson and carries the published authority separately.
        if (form.containsKey("publishedFieldPermissions")) {
            if (form.get("publishedFieldPermissions") instanceof Map<?, ?> permissions) return permissions;
            throw invalid("Published batch field permissions are invalid");
        }
        if (!(form.get("data") instanceof String data)) throw invalid("Published batch field permissions are missing");
        Map<String, Object> wrapper;
        try { wrapper = mapper.readValue(data, new TypeReference<Map<String, Object>>() { }); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw invalid("Published batch field permissions are invalid"); }
        if (wrapper.get("fieldPermissions") instanceof Map<?, ?> permissions) return permissions;
        throw invalid("Published batch field permissions are missing");
    }

    static void validateAddedValues(Map<?, ?> design, Map<?, ?> row) {
        if (!(design.get("fieldWriteModes") instanceof Map<?, ?> modes)
                || !(design.get("fields") instanceof List<?> fields)) throw invalid("Frozen batch field permissions are missing");
        for (Object raw : fields) {
            if (!(raw instanceof Map<?, ?> field) || !(field.get("fieldName") instanceof String name)) throw invalid("Frozen batch field identity is invalid");
            Object mode = modes.get(name);
            if (!("EDITABLE".equals(mode) || "READONLY".equals(mode))) throw invalid("Frozen batch field permissions are incomplete");
            Object value = row.get(name);
            // PK/FK are separately checked/prepared by the identity and ownership guards, not business editors.
            if (Boolean.TRUE.equals(field.get("isPrimaryKey")) && !manualKey(field)) continue;
            if (Boolean.TRUE.equals(field.get("isForeignKey")) || Boolean.TRUE.equals(field.get("isComputed"))) continue;
            if ("READONLY".equals(mode) && value != null && !"".equals(value) && !declaredDefault(field, value)) {
                throw new PortalException("403", "Batch add cannot set a read-only field");
            }
        }
    }

    static void validateEditedFields(Map<?, ?> design, List<String> names) {
        if (!(design.get("fieldWriteModes") instanceof Map<?, ?> modes)
                || !(design.get("fields") instanceof List<?> fields)) throw invalid("Frozen batch field permissions are missing");
        for (String name : names) {
            Map<?, ?> field = fields.stream().filter(raw -> raw instanceof Map<?, ?> value && name.equals(value.get("fieldName")))
                    .map(raw -> (Map<?, ?>) raw).findFirst().orElseThrow(() -> invalid("Frozen batch field identity is missing"));
            if (!"EDITABLE".equals(modes.get(name)) || Boolean.TRUE.equals(field.get("isPrimaryKey"))
                    || Boolean.TRUE.equals(field.get("isForeignKey")) || Boolean.TRUE.equals(field.get("isComputed"))) {
                throw new PortalException("403", "Batch edit cannot set a read-only or protected field");
            }
        }
    }

    private static boolean manualKey(Map<?, ?> field) {
        return field.get("pkGenerationJson") instanceof Map<?, ?> generation && "manual".equals(generation.get("strategy"));
    }

    /** Only literal metadata defaults qualify; expressions/SQL functions are never evaluated. */
    static boolean declaredDefault(Map<?, ?> field, Object value) {
        Object configured = field.get("defaultValue");
        if (configured == null) return false;
        if (value instanceof Number && List.of("INTEGER", "INT", "BIGINT", "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE").contains(field.get("dataType"))) {
            try { return new BigDecimal(String.valueOf(configured)).compareTo(new BigDecimal(String.valueOf(value))) == 0; }
            catch (NumberFormatException invalidLiteral) { return false; }
        }
        if (value instanceof Boolean flag && "BOOLEAN".equals(field.get("dataType"))) {
            return configured.equals(value) || (flag ? List.of("true", "1") : List.of("false", "0")).contains(String.valueOf(configured));
        }
        return Objects.equals(configured, value);
    }
    private static PortalException invalid(String message) { return new PortalException("409", message); }
}
