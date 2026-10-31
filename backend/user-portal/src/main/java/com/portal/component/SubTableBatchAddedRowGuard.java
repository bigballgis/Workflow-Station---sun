package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.dto.SubTableBatchChange;
import com.portal.dto.SubTableBindingScope;
import com.portal.exception.PortalException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates new rows using the instance's frozen table fields inside the save transaction. */
final class SubTableBatchAddedRowGuard {
    record Request(Map<String, Object> variables, Map<String, Object> form, List<SubTableBatchChange> changes,
                   List<SubTableBindingScope> scopes, Map<String, Object> submitted, ObjectMapper mapper) { }

    private SubTableBatchAddedRowGuard() { }

    static void validate(Request request) {
        if (request.changes() == null || request.changes().isEmpty()) return;
        Map<String, Object> controls = SubTableBatchRuntimeSnapshot.projectForForm(request.variables(),
                request.form().get("sourceId"), request.mapper());
        Map<?, ?> tables = map(request.submitted().get("__subTables__"));
        for (SubTableBatchChange change : request.changes()) {
            if (!"ADD".equals(change.getOperation())) continue;
            Map<?, ?> control = map(controls.get(change.getBatchControlId()));
            Map<?, ?> design = map(control.get("rowDesign"));
            Map<?, ?> binding = map(design.get("binding"));
            if (!"SUB".equals(binding.get("bindingType")) || !"EDITABLE".equals(binding.get("bindingMode"))
                    || !"structuralFk".equals(binding.get("bindingLinkMode"))) fail("Batch add binding is not editable business data");
            String key = request.scopes().stream().filter(scope -> change.getBindingId().equals(scope.getBindingId()))
                    .map(SubTableBindingScope::getStoreKey).findFirst().orElseThrow(() -> invalid("Batch add binding scope is missing"));
            List<?> rows = list(tables.get(key));
            if (binding.get("primaryKeyFields") instanceof List<?> identityFields) {
                long unique = rows.stream().map(raw -> identityFields.stream()
                        .map(field -> String.valueOf(map(raw).get(field))).toList()).distinct().count();
                if (unique != rows.size()) fail("Batch add canonical rows contain duplicate identities");
            }
            List<?> fields = list(design.get("fields"));
            if (fields.isEmpty()) fail("Batch add frozen fields are missing");
            for (SubTableBatchChange.RowPatch patch : change.getRowPatches()) {
                Map<?, ?> row = rows.stream().map(SubTableBatchAddedRowGuard::map)
                        .filter(value -> patch.getRowKey().entrySet().stream().allMatch(entry ->
                                Objects.equals(String.valueOf(value.get(entry.getKey())), String.valueOf(entry.getValue()))))
                        .findFirst().orElseThrow(() -> invalid("Batch added row is missing"));
                validateColumns(control, fields, row, patch.getRowKey());
                SubTableBatchPublishedPermissions.validateAddedValues(design, row);
                Map<String, Map<?, ?>> rules = SubTableBatchFormRules.fields(map(control.get("batchForm")));
                for (Object raw : fields) SubTableBatchEditedRowGuard.validateField(map(raw), row, rules.get(String.valueOf(map(raw).get("fieldName"))));
                SubTableBatchFormRules.validateRow(map(control.get("batchForm")), row);
            }
        }
    }

    private static void validateColumns(Map<?, ?> control, List<?> fields, Map<?, ?> row, Map<String, Object> identity) {
        java.util.Set<String> allowed = new java.util.HashSet<>(SubTableBatchFormRules.fields(map(control.get("batchForm"))).keySet());
        allowed.addAll(identity.keySet());
        allowed.add(com.platform.common.jdbc.SubTableRowIdentity.CANONICAL_FIELD);
        allowed.add("_wsRowVersion"); // Written by the existing scope guard before this validation boundary.
        for (Object raw : fields) {
            Map<?, ?> field = map(raw);
            if (Boolean.TRUE.equals(field.get("isForeignKey")) || Boolean.TRUE.equals(field.get("isPrimaryKey"))) {
                allowed.add(String.valueOf(field.get("fieldName")));
            }
            if (Boolean.TRUE.equals(field.get("isComputed"))) {
                Object value = row.get(field.get("fieldName"));
                if (value != null && !"".equals(value)) fail("Batch add cannot set computed fields");
                allowed.add(String.valueOf(field.get("fieldName")));
            }
        }
        if (!allowed.containsAll(row.keySet())) fail("Batch add contains a field outside the frozen batch form");
    }

    static void validateField(Map<?, ?> field, Map<?, ?> row) {
        String name = String.valueOf(field.get("fieldName"));
        Object value = row.get(name);
        boolean empty = value == null || "".equals(value);
        // FK presence/ownership is binding-scoped (SubTableBindingScopeGuard), not table-wide:
        // rows linked through a second FK must leave the other binding's FK empty.
        if (empty && Boolean.FALSE.equals(field.get("nullable")) && !Boolean.TRUE.equals(field.get("isComputed"))
                && !Boolean.TRUE.equals(field.get("isForeignKey"))) {
            fail("Batch row requires field " + name);
        }
        if (empty) return;
        String type = String.valueOf(field.get("dataType"));
        if (List.of("VARCHAR", "TEXT", "CHAR").contains(type) && !(value instanceof String)) fail("Batch field has invalid text type: " + name);
        if ("BOOLEAN".equals(type) && !(value instanceof Boolean)) fail("Batch field has invalid boolean type: " + name);
        if ("DATE".equals(type)) {
            try { java.time.LocalDate.parse(String.valueOf(value)); }
            catch (java.time.format.DateTimeParseException error) { throw invalid("Batch field has invalid date: " + name); }
        }
        if ("TIMESTAMP".equals(type)) {
            try { java.time.LocalDateTime.parse(String.valueOf(value).replace(' ', 'T')); }
            catch (java.time.format.DateTimeParseException error) { throw invalid("Batch field has invalid timestamp: " + name); }
        }
        if (List.of("BIGINT", "INTEGER", "INT", "DECIMAL", "NUMERIC", "DOUBLE", "FLOAT").contains(type)) {
            validateNumeric(field, value, type, name);
        }
        if (value instanceof String text && field.get("length") instanceof Number length && text.length() > length.intValue()) {
            fail("Batch field exceeds its maximum length: " + name);
        }
    }

    private static void validateNumeric(Map<?, ?> field, Object value, String type, String name) {
        // The existing PK allocation API returns strings even for sequence-backed numeric PKs.
        boolean allocatedKey = Boolean.TRUE.equals(field.get("isPrimaryKey")) && value instanceof String;
        if (!(value instanceof Number) && !allocatedKey) fail("Batch field has invalid numeric type: " + name);
        BigDecimal decimal;
        try { decimal = new BigDecimal(value.toString()); }
        catch (NumberFormatException error) { throw invalid("Batch field has invalid numeric value: " + name); }
        try {
            if ("BIGINT".equals(type)) decimal.longValueExact();
            if ("INTEGER".equals(type) || "INT".equals(type)) decimal.intValueExact();
        } catch (ArithmeticException error) { throw invalid("Batch field requires an in-range integer: " + name); }
        if (List.of("DECIMAL", "NUMERIC").contains(type) && field.get("precision") instanceof Number precision
                && field.get("scale") instanceof Number scale) {
            if (Math.max(decimal.scale(), 0) > scale.intValue()
                    || decimal.precision() - decimal.scale() > precision.intValue() - scale.intValue()) {
                fail("Batch decimal exceeds its configured precision: " + name);
            }
        }
    }

    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static List<?> list(Object value) { return value instanceof List<?> list ? list : List.of(); }
    private static void fail(String message) { throw invalid(message); }
    private static PortalException invalid(String message) { return new PortalException("400", message); }
}
