package com.platform.common.form;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Batch field capabilities shared by design saves, package imports and runtime capture. */
public final class BatchFormControlContract {
    private static final Set<String> FIELDS = Set.of("input", "text", "number", "inputNumber", "switch", "select",
            "date", "datePicker", "datetime", "lookup");
    private static final Set<String> VALIDATORS = Set.of("required", "type", "min", "max", "len", "message", "trigger");
    private static final Set<String> VALIDATOR_TYPES = Set.of("string", "number", "integer", "boolean", "date");
    private BatchFormControlContract() { }

    /** Reject unsupported field and component-event configurations before publication. */
    public static void validate(Object value) {
        if (value instanceof List<?> list) { list.forEach(BatchFormControlContract::validate); return; }
        if (!(value instanceof Map<?, ?> rule)) return;
        Object field = rule.get("field");
        if (field instanceof String name && !name.isBlank() && !"miAssignment".equals(rule.get("type"))
                && !FIELDS.contains(String.valueOf(rule.get("type")))) {
            throw new IllegalArgumentException("Batch field control is unsupported: " + name);
        }
        for (String key : List.of("on", "_on", "hook", "_hook")) validateEvents(rule.get(key), key, field);
        if (rule.get("validate") instanceof List<?> validators) {
            for (Object raw : validators) {
                if (!(raw instanceof Map<?, ?> validator) || !VALIDATORS.containsAll(validator.keySet())) {
                    throw new IllegalArgumentException("Batch field validator is unsupported: " + field);
                }
                if (validator.get("type") != null && !VALIDATOR_TYPES.contains(validator.get("type"))) {
                    throw new IllegalArgumentException("Batch field validator type is unsupported: " + field);
                }
            }
        }
        validate(rule.get("children"));
    }

    /** Batch supports row-local field changes; executable form lifecycle events are excluded. */
    public static void validateOptions(Object raw) {
        if (!(raw instanceof Map<?, ?> options)) return;
        for (String key : List.of("onChange", "onCreated", "onMounted", "onReload", "beforeSubmit", "onSubmit", "onReset", "beforeFetch")) {
            if (!isEmptyEvent(options.get(key))) throw new IllegalArgumentException("Batch form events are unsupported: " + key + ". Use a field change event.");
        }
    }

    private static void validateEvents(Object raw, String kind, Object field) {
        if (raw == null) return;
        if (!(raw instanceof Map<?, ?> events)) throw new IllegalArgumentException("Invalid Batch events: " + field);
        for (var event : events.entrySet()) {
            if (isEmptyEvent(event.getValue())) continue;
            if (!("on".equals(kind) || "_on".equals(kind)) || !"change".equals(event.getKey())
                    || !(event.getValue() instanceof String script)
                    || !(script.startsWith("$FNX:") || script.startsWith("[[FORM-CREATE-PREFIX-"))) {
                throw new IllegalArgumentException("Batch supports only field change scripts: " + field + " (" + event.getKey() + ")");
            }
        }
    }

    /** Recognize only empty handlers and the exact historical non-executable adapter. */
    public static boolean isEmptyEvent(Object value) {
        if (value == null) return true;
        if (!(value instanceof String text)) return false;
        String source = text.strip();
        // Migration: exact form-create injected adapter emitted by older published catalogs.
        return source.isEmpty() || "$FNX:".equals(source)
                || source.matches("\\[\\[FORM-CREATE-PREFIX-function [^(){}]*\\([^(){}]*\\)\\s*\\{\\s*}-FORM-CREATE-SUFFIX]]")
                || source.equals("$FNX:\nfor(var s=a.getInjectData(n,r),u=arguments.length,c=new Array(u),d=0;d<u;d++)"
                + "c[d]=arguments[d];return s.args=[].concat(c),c.unshift(s),o.apply(this,c)");
    }
}
