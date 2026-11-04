package com.portal.component;

import com.portal.exception.PortalException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Supported pure field rules for the first batch membership workspace. */
final class SubTableBatchFormRules {
    // Same email grammar as async-validator 4.2.5 used by ordinary forms.
    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("^(([^<>()\\[\\]\\\\.,;:\\s@\"]+(\\.[^<>()\\[\\]\\\\.,;:\\s@\"]+)*)|(\".+\"))@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}])|(([a-zA-Z\\-0-9\\u00A0-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFEF]+\\.)+[a-zA-Z\\u00A0-\\uD7FF\\uF900-\\uFDCF\\uFDF0-\\uFFEF]{2,}))$");
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
            if ((requiredFlag(rule.get("required")) || requiredFlag(rule.get("$required"))) && empty(value)) fail("Batch field is required: " + name);
            if (rule.get("validate") instanceof List<?> validators) {
                for (Object raw : validators) {
                    Map<?, ?> validator = map(raw);
                    validateEntry(name, value, validator);
                }
            }
            if (empty(value)) continue;
            if ("lookup".equals(rule.get("type"))) { BatchLookupValue.validate(rule, value); continue; }
            validateBounds(name, value, props);
            Object max = props.containsKey("maxlength") ? props.get("maxlength") : props.get("maxLength");
            if (value instanceof String text && max != null && !max.toString().isBlank()) {
                double length;
                try { length = Double.parseDouble(max.toString()); }
                catch (NumberFormatException error) { throw new PortalException("400", "Batch field maximum length is invalid: " + name); }
                if (Double.isFinite(length) && length > 0 && text.length() > length) fail("Batch field is too long: " + name);
            }
            // Options are presentation state and can change through api.setOptions. An explicit enum
            // validator is the declarative constraint; assignment authorization has its own guard.
            if ("select".equals(rule.get("type")) && Boolean.TRUE.equals(props.get("multiple")) && !(value instanceof List<?>)) {
                fail("Batch multiselect requires a list: " + name);
            }
            if (List.of("date", "datePicker", "datetime").contains(rule.get("type")) && String.valueOf(props.get("type")).endsWith("range")
                    && (!(value instanceof List<?> dates) || dates.size() != 2 || dates.stream().anyMatch(SubTableBatchFormRules::empty))) {
                fail("Batch date range requires two values: " + name);
            }
        }
    }

    private static void validateEntry(String name, Object value, Map<?, ?> rule) {
        Object mode = rule.get("mode");
        if ((Boolean.TRUE.equals(rule.get("required")) || "required".equals(mode)) && empty(value)) fail(message(rule, "Batch field is required: " + name));
        if (empty(value)) return;
        boolean numericMode = List.of("min", "max", "integer", "number").contains(String.valueOf(mode));
        Object checked = value;
        if (numericMode && value instanceof String text) {
            try { checked = new BigDecimal(text.trim()); }
            catch (NumberFormatException error) { fail(message(rule, "Batch field requires a number: " + name)); }
        }
        Object type = numericMode ? ("integer".equals(mode) ? "integer" : "number")
                : List.of("email", "url", "enum").contains(String.valueOf(mode)) ? mode : rule.get("type");
        try {
            validateType(name, checked, type);
            validateBounds(name, checked, rule);
            if (value instanceof String text) {
                if (rule.get("maxLen") instanceof Number max && text.length() > max.intValue()) fail("Batch field is too long: " + name);
                if (rule.get("minLen") instanceof Number min && text.length() < min.intValue()) fail("Batch field is too short: " + name);
                if (Boolean.TRUE.equals(rule.get("whitespace")) && text.isBlank()) fail("Batch field cannot be blank: " + name);
                if (rule.get("pattern") instanceof String pattern && !java.util.regex.Pattern.compile(pattern).matcher(text).find()) fail("Batch field pattern is invalid: " + name);
            }
            if (rule.get("enum") instanceof List<?> allowed && allowed.stream().noneMatch(item -> equalValue(item, value))) fail("Batch field option is invalid: " + name);
        } catch (PortalException error) {
            fail(message(rule, error.getMessage()));
        }
    }

    private static String message(Map<?, ?> rule, String fallback) { return rule.get("message") instanceof String text && !text.isBlank() ? text : fallback; }
    private static boolean equalValue(Object left, Object right) {
        return left instanceof Number && right instanceof Number
                ? new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())) == 0 : java.util.Objects.equals(left, right);
    }

    private static void validateBounds(String name, Object value, Map<?, ?> rules) {
        if (Boolean.TRUE.equals(rules.get("required")) && empty(value)) fail("Batch field is required: " + name);
        if (empty(value)) return;
        BigDecimal magnitude = value instanceof Number ? new BigDecimal(value.toString())
                : value instanceof String text ? BigDecimal.valueOf(text.length())
                : value instanceof List<?> items ? BigDecimal.valueOf(items.size()) : null;
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
            case "float" -> value instanceof Number;
            case "integer" -> value instanceof Number && new BigDecimal(value.toString()).stripTrailingZeros().scale() <= 0;
            case "boolean" -> value instanceof Boolean;
            case "date" -> validDate(value);
            case "array" -> value instanceof List<?>;
            case "object" -> value instanceof Map<?, ?>;
            case "email" -> value instanceof String text && text.length() <= 320 && EMAIL.matcher(text).matches();
            case "url" -> value instanceof String text && validUrl(text);
            case "hex" -> value instanceof String text && text.matches("(?:#|0[xX])?[0-9a-fA-F]+");
            case "enum" -> true; // Membership is checked against the rule's explicit enum below.
            default -> false;
        };
        if (!valid) fail("Batch field validator type is invalid: " + name);
    }

    private static boolean validDate(Object value) {
        if (value instanceof Number number) return Double.isFinite(number.doubleValue());
        if (!(value instanceof String text)) return false;
        try { java.time.LocalDate.parse(text); return true; }
        catch (java.time.format.DateTimeParseException dateError) {
            try { java.time.LocalDateTime.parse(text.replace(' ', 'T')); return true; }
            catch (java.time.format.DateTimeParseException timeError) {
                try { java.time.OffsetDateTime.parse(text.replace(' ', 'T')); return true; }
                catch (java.time.format.DateTimeParseException offsetError) { return false; }
            }
        }
    }

    private static boolean validUrl(String text) {
        try { java.net.URI uri = java.net.URI.create(text); return uri.getHost() != null && List.of("http", "https", "ftp").contains(uri.getScheme()); }
        catch (IllegalArgumentException error) { return false; }
    }

    private static boolean empty(Object value) { return value == null || "".equals(value) || value instanceof List<?> list && list.isEmpty(); }
    private static boolean requiredFlag(Object value) { return Boolean.TRUE.equals(value) || value instanceof String text && !text.isBlank(); }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static void fail(String message) { throw new PortalException("400", message); }
}
