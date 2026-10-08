package com.portal.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.common.list.ListColumnMeta;
import com.portal.dto.MainTableViewPortalDtos.MainTableViewFieldColumn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Select-like view columns designed to show option labels instead of stored values.
 *
 * <p>Options come from the static {@code options} array of the form widget bound to the field
 * (select / radio / checkbox share the {@code {label, value}} shape). Cell values are mapped for
 * display only; the row payload keeps the stored value.
 */
public final class MainTableViewSelectDisplay {

    public static final String VALUE = "value";
    public static final String LABEL = "label";

    private MainTableViewSelectDisplay() {}

    public static boolean showsLabel(String selectDisplay) {
        return LABEL.equalsIgnoreCase(selectDisplay);
    }

    /**
     * The value a cell shows for {@code column}. A stored value that is not among the options —
     * an option removed after the row was written, a free-typed value — is shown as is.
     */
    public static Object display(MainTableViewFieldColumn column, Object value) {
        if (value == null || !showsLabel(column.selectDisplay())
                || column.selectOptions() == null || column.selectOptions().isEmpty()) {
            return value;
        }
        if (value instanceof Collection<?> many) {
            List<Object> out = new ArrayList<>(many.size());
            for (Object one : many) {
                out.add(labelOf(column.selectOptions(), one));
            }
            return out;
        }
        return labelOf(column.selectOptions(), value);
    }

    private static Object labelOf(List<ListColumnMeta.Option> options, Object value) {
        if (value == null) {
            return null;
        }
        String key = String.valueOf(value);
        for (ListColumnMeta.Option option : options) {
            if (key.equals(option.value())) {
                return option.label();
            }
        }
        // FALLBACK(ux): a stored value outside the current options is still real data; show it as is.
        return value;
    }

    /**
     * The parts of one form config that render the table behind {@code bindingId}: the top-level
     * {@code rule} belongs to the form's PRIMARY binding, and {@code subForms[bindingId]} holds the
     * widgets of that sub-table binding. Scoping this way keeps a {@code status} widget on a SUB
     * form from being mistaken for the MAIN table's {@code status} column.
     */
    public static void collectOptionsForBinding(JsonNode config, long bindingId, boolean primaryBinding,
                                                Map<String, List<ListColumnMeta.Option>> out) {
        if (config == null) {
            return;
        }
        if (primaryBinding) {
            collectStaticOptions(config.get("rule"), out);
        }
        collectStaticOptions(config.path("subForms").get(String.valueOf(bindingId)), out);
    }

    /**
     * Records {@code field → options} for every widget node in a form config tree that binds a
     * field and declares a non-empty static {@code options} array. First declaration per field wins.
     */
    public static void collectStaticOptions(JsonNode node, Map<String, List<ListColumnMeta.Option>> out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            String field = node.path("field").asText(null);
            JsonNode options = node.get("options");
            if (field != null && !field.isBlank() && options != null && options.isArray() && !options.isEmpty()) {
                List<ListColumnMeta.Option> parsed = new ArrayList<>(options.size());
                for (JsonNode option : options) {
                    JsonNode value = option.get("value");
                    if (value == null || value.isNull()) {
                        continue;
                    }
                    JsonNode label = option.get("label");
                    parsed.add(new ListColumnMeta.Option(
                            value.asText(),
                            label != null && !label.isNull() ? label.asText() : value.asText()));
                }
                if (!parsed.isEmpty()) {
                    out.putIfAbsent(field, List.copyOf(parsed));
                }
            }
            node.fields().forEachRemaining(e -> collectStaticOptions(e.getValue(), out));
        } else if (node.isArray()) {
            node.forEach(child -> collectStaticOptions(child, out));
        }
    }
}
