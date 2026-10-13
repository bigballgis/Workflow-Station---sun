package com.portal.component;

import com.portal.dto.SubTableBatchChange;
import com.portal.exception.PortalException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Overrides only batch membership bindings with their instance-frozen PK/FK scope contract. */
final class SubTableBatchScopeDesign {
    private SubTableBatchScopeDesign() { }

    static void validateFrozen(Map<?, ?> design, String bindingId) { binding(design, bindingId); }

    static Map<String, SubTableWriteDesign.Binding> overlay(Map<String, SubTableWriteDesign.Binding> current,
                                                           Map<String, Object> variables, List<SubTableBatchChange> changes) {
        if (changes == null || changes.stream().noneMatch(change -> "ADD".equals(change.getOperation()) || "DELETE".equals(change.getOperation()))) return current;
        Map<String, SubTableWriteDesign.Binding> result = new LinkedHashMap<>(current == null ? Map.of() : current);
        Map<?, ?> forms = map(map(variables.get(SubTableWriteIsolation.BATCH_RUNTIME_FIELD)).get("forms"));
        for (SubTableBatchChange change : changes) {
            if (!"ADD".equals(change.getOperation()) && !"DELETE".equals(change.getOperation())) continue;
            Map<?, ?> control = forms.values().stream().flatMap(form -> list(map(form).get("controls")).stream())
                    .map(SubTableBatchScopeDesign::map)
                    .filter(value -> change.getBatchControlId().equals(value.get("batchControlId")))
                    .findFirst().orElseThrow(() -> invalid("Frozen batch scope control is missing"));
            result.put(change.getBindingId(), binding(map(control.get("rowDesign")), change.getBindingId()));
        }
        return result;
    }

    private static SubTableWriteDesign.Binding binding(Map<?, ?> design, String id) {
        Map<?, ?> binding = map(design.get("binding"));
        List<?> fields = list(binding.get("fieldDefinitions"));
        String filter = text(binding.get("filterFkFieldName"));
        if (filter == null) filter = text(binding.get("foreignKeyField"));
        final String fieldName = filter;
        Map<?, ?> foreign = fields.stream().map(SubTableBatchScopeDesign::map)
                .filter(field -> fieldName != null && fieldName.equals(field.get("fieldName")) && Boolean.TRUE.equals(field.get("isForeignKey")))
                .findFirst().orElseThrow(() -> invalid("Frozen batch ownership FK is missing"));
        Map<?, ?> parent = map(map(design.get("parentTablesById")).get(String.valueOf(foreign.get("refTableId"))));
        String parentName = text(parent.get("tableName"));
        if (parentName == null || text(parent.get("tableType")) == null) throw invalid("Frozen batch parent scope is missing");
        List<String> fills = new ArrayList<>();
        for (Object raw : list(binding.get("fkFillSources"))) {
            String name = text(map(raw).get("fieldName"));
            if (name == null) throw invalid("Frozen batch FK fill field is missing");
            fills.add(name);
        }
        return new SubTableWriteDesign.Binding(id, "dw:" + required(binding.get("tableName")).toLowerCase(java.util.Locale.ROOT),
                filter, text(parent.get("tableType")), parentName.toLowerCase(java.util.Locale.ROOT), strings(binding.get("primaryKeyFields")),
                strings(foreign.get("refPrimaryKeyFields")), text(binding.get("bindingLinkMode")),
                fields.stream().map(SubTableBatchScopeDesign::map).filter(field -> Boolean.TRUE.equals(field.get("isForeignKey")))
                        .map(field -> required(field.get("fieldName"))).toList(), fills);
    }

    private static List<String> strings(Object value) {
        List<String> values = list(value).stream().map(SubTableBatchScopeDesign::required).toList();
        if (values.isEmpty()) throw invalid("Frozen batch primary-key fields are missing");
        return values;
    }
    private static String text(Object value) { return value instanceof String text && !text.isBlank() ? text : null; }
    private static String required(Object value) { String text = text(value); if (text == null) throw invalid("Frozen batch metadata is missing"); return text; }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static List<?> list(Object value) { return value instanceof List<?> list ? list : List.of(); }
    private static PortalException invalid(String message) { return new PortalException("409", message); }
}
