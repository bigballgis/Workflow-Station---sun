package com.portal.component;

import com.portal.exception.PortalException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Supported pure field rules for the first batch membership workspace. */
final class SubTableBatchFormRules {
    private SubTableBatchFormRules() { }

    static Map<String, Map<?, ?>> fields(Map<?, ?> form) {
        try {
            com.platform.common.form.BatchFormControlContract.validate(form.get("rule"));
            com.platform.common.form.BatchFormControlContract.validateOptions(form.get("options"));
        }
        catch (IllegalArgumentException error) { fail(error.getMessage()); }
        Map<String, Map<?, ?>> fields = new LinkedHashMap<>();
        collect(form.get("rule"), fields);
        return fields;
    }

    private static void collect(Object value, Map<String, Map<?, ?>> fields) {
        if (value instanceof List<?> list) { list.forEach(item -> collect(item, fields)); return; }
        if (!(value instanceof Map<?, ?> rule)) return;
        if (rule.get("field") instanceof String name && !name.isBlank() && !"miAssignment".equals(rule.get("type"))) {
            if (fields.put(name, rule) != null) fail("Batch field is duplicated: " + name);
        }
        collect(rule.get("children"), fields);
    }

    static void validateRow(Map<?, ?> form, Map<?, ?> row) {
        for (Map.Entry<String, Map<?, ?>> entry : fields(form).entrySet()) {
            String name = entry.getKey();
            Map<?, ?> rule = entry.getValue();
            if (Boolean.TRUE.equals(rule.get("hidden")) || Boolean.FALSE.equals(rule.get("display"))) continue;
            Object value = row.get(name);
            Map<?, ?> props = map(rule.get("props"));
            if (Boolean.TRUE.equals(rule.get("required")) && empty(value)) fail("Batch field is required: " + name);
            if (rule.get("validate") instanceof List<?> validators) {
                for (Object raw : validators) validateBounds(name, value, map(raw));
            }
            if (empty(value)) continue;
            if ("lookup".equals(rule.get("type"))) { BatchLookupValue.validate(rule, value); continue; }
            validateBounds(name, value, props);
            if (value instanceof String text && props.get("maxlength") instanceof Number max && text.length() > max.intValue()) fail("Batch field is too long: " + name);
            Object options = props.containsKey("options") ? props.get("options") : rule.get("options");
            if ("select".equals(rule.get("type")) && (!(options instanceof List<?> list)
                    || list.stream().noneMatch(option -> java.util.Objects.equals(value, option instanceof Map<?, ?> item ? item.get("value") : option)))) {
                fail("Batch field option is invalid: " + name);
            }
        }
    }

    private static void validateBounds(String name, Object value, Map<?, ?> rules) {
        if (Boolean.TRUE.equals(rules.get("required")) && empty(value)) fail("Batch field is required: " + name);
        if (empty(value)) return;
        validateType(name, value, rules.get("type"));
        BigDecimal magnitude = value instanceof Number ? new BigDecimal(value.toString())
                : value instanceof String text ? BigDecimal.valueOf(text.length()) : null;
        if (magnitude == null) return;
        if (rules.get("min") instanceof Number min && magnitude.compareTo(new BigDecimal(min.toString())) < 0) fail("Batch field is below minimum: " + name);
        if (rules.get("max") instanceof Number max && magnitude.compareTo(new BigDecimal(max.toString())) > 0) fail("Batch field exceeds maximum: " + name);
        if (rules.get("len") instanceof Number length && magnitude.compareTo(new BigDecimal(length.toString())) != 0) fail("Batch field length is invalid: " + name);
    }

    private static void validateType(String name, Object value, Object type) {
        if (type == null) return;
        boolean valid = switch (String.valueOf(type)) {
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Number && new BigDecimal(value.toString()).stripTrailingZeros().scale() <= 0;
            case "boolean" -> value instanceof Boolean;
            case "date" -> validDate(value);
            default -> false;
        };
        if (!valid) fail("Batch field validator type is invalid: " + name);
    }

    private static boolean validDate(Object value) {
        if (!(value instanceof String text)) return false;
        try { java.time.LocalDate.parse(text); return true; }
        catch (java.time.format.DateTimeParseException error) { return false; }
    }

    private static boolean empty(Object value) { return value == null || "".equals(value); }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static void fail(String message) { throw new PortalException("400", message); }
}
