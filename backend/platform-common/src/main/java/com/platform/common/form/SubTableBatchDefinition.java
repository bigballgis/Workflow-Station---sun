package com.platform.common.form;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure version/identity contract shared by publication, package import and runtime capture. */
public final class SubTableBatchDefinition {
    public static final int SCHEMA_VERSION = 1;
    private static final List<String> FLAGS = List.of(
            "allowBatchAdd", "allowBatchEdit", "allowBatchDelete", "allowBatchAssign");

    private SubTableBatchDefinition() { }

    public static void validate(Object rawConfig) {
        // Legacy forms without this optional enhancement retain their existing contract.
        if (rawConfig == null) return;
        if (!(rawConfig instanceof Map<?, ?> config)) throw invalid("configJson must be an object");
        Object rawForms = config.get("batchForms");
        if (config.containsKey("batchForms") && !(rawForms instanceof Map<?, ?>)) {
            throw invalid("batchForms must be an object");
        }
        Map<?, ?> forms = rawForms instanceof Map<?, ?> map ? map : Map.of();
        for (Map.Entry<?, ?> entry : forms.entrySet()) {
            bindingId(entry.getKey());
            validateStoredForm(entry.getValue());
        }
        visit(config.get("rule"), forms, new HashSet<>());
    }

    public static void validateForm(Object rawForm, boolean assign) {
        Map<?, ?> form = validateStoredForm(rawForm);
        List<?> rules = (List<?>) form.get("rule");
        if (rules.isEmpty()) throw invalid("Batch form rule is required");
        if (assign && !containsAssignment(rules)) throw invalid("Batch Assign requires an miAssignment component");
    }

    private static Map<?, ?> validateStoredForm(Object rawForm) {
        if (!(rawForm instanceof Map<?, ?> form)) throw invalid("batchForms definition is missing");
        Object version = form.get("schemaVersion");
        if (!(version instanceof Number) || !versionOne(version)) throw invalid("Unsupported batch form schemaVersion");
        if (!(form.get("rule") instanceof List<?> rules)) throw invalid("Batch form rule must be an array");
        if (rules.stream().anyMatch(rule -> !(rule instanceof Map<?, ?>))) throw invalid("Batch form rule must contain objects");
        if (form.containsKey("options") && !(form.get("options") instanceof Map<?, ?>)) throw invalid("Batch form options must be an object");
        return form;
    }

    private static boolean versionOne(Object version) {
        try { return new BigDecimal(version.toString()).compareTo(BigDecimal.ONE) == 0; }
        catch (NumberFormatException error) { return false; }
    }

    private static void visit(Object value, Map<?, ?> forms, Set<String> ids) {
        if (value instanceof List<?> list) { list.forEach(child -> visit(child, forms, ids)); return; }
        if (!(value instanceof Map<?, ?> rule)) return;
        if ("subTable".equals(rule.get("type")) && rule.get("props") instanceof Map<?, ?> props
                && FLAGS.stream().anyMatch(flag -> Boolean.TRUE.equals(props.get(flag)))) {
            Object rawId = rule.get("batchControlId");
            if (!(rawId instanceof String id) || id.isBlank()) throw invalid("batchControlId is required");
            if (!ids.add(id)) throw invalid("Duplicate batchControlId");
            String binding = bindingId(props.get("_bindingId"));
            validateForm(forms.get(binding), Boolean.TRUE.equals(props.get("allowBatchAssign")));
        }
        rule.values().stream().filter(child -> child instanceof List<?> || child instanceof Map<?, ?>)
                .forEach(child -> visit(child, forms, ids));
    }

    private static String bindingId(Object raw) {
        if (raw == null) throw invalid("Batch _bindingId is required");
        String value = raw.toString();
        if (!value.matches("[1-9][0-9]*")) throw invalid("Batch binding ID must be a positive integer");
        try { Long.parseLong(value); }
        catch (NumberFormatException error) { throw invalid("Batch binding ID is out of range"); }
        return value;
    }

    private static boolean containsAssignment(Object value) {
        if (value instanceof List<?> list) return list.stream().anyMatch(SubTableBatchDefinition::containsAssignment);
        if (!(value instanceof Map<?, ?> rule)) return false;
        if ("miAssignment".equals(rule.get("type"))) return true;
        return rule.values().stream().filter(child -> child instanceof List<?> || child instanceof Map<?, ?>)
                .anyMatch(SubTableBatchDefinition::containsAssignment);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid batch definition: " + message);
    }
}
