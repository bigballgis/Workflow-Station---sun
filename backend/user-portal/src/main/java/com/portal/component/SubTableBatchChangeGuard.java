package com.portal.component;

import com.portal.dto.SubTableBatchChange;
import com.portal.exception.PortalException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies the batch transport declaration against the server-owned instance
 * snapshot.  It deliberately does not consult the current DW form definition:
 * a catalog overwrite must not grant a running instance a new batch capability.
 */
final class SubTableBatchChangeGuard {

    private SubTableBatchChangeGuard() {
    }

    static void validate(Map<String, Object> instanceVariables,
                         Map<String, Object> formDefinition,
                         List<SubTableBatchChange> changes,
                         com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        if (changes == null || changes.isEmpty()) return;
        Object sourceId = formDefinition == null ? null : formDefinition.get("sourceId");
        Map<String, Object> controls = SubTableBatchRuntimeSnapshot.projectForForm(
                instanceVariables, sourceId, objectMapper);
        if (controls.isEmpty()) {
            throw forbidden("Batch operations are not enabled for this task form");
        }
        for (SubTableBatchChange change : changes) {
            validateChange(change, controls, Map.of());
        }
    }

    static void validateFieldPermissions(List<SubTableBatchChange> changes,
                                         Map<String, String> fieldPermissions) {
        if (changes == null || changes.isEmpty() || fieldPermissions == null || fieldPermissions.isEmpty()) return;
        for (SubTableBatchChange change : changes) {
            if (change == null || blank(change.getBindingId()) || change.getRowPatches() == null) continue;
            for (SubTableBatchChange.RowPatch patch : change.getRowPatches()) {
                if (patch == null || patch.getFields() == null) continue;
                for (String field : patch.getFields()) {
                    if ("READONLY".equals(fieldPermissions.get(change.getBindingId().trim() + ":" + field))) {
                        throw forbidden("Batch patch contains a read-only field");
                    }
                }
            }
        }
    }

    /**
     * Verifies that each batch declaration still addresses a concrete canonical
     * row after the normal scope guard has merged the submitted slice.  The
     * client-side display identity is not used as authority: {@code rowKey} is
     * the configured PK/platform identity that the existing scope protocol
     * already understands.
     */
    static void validateSubmittedRows(List<SubTableBatchChange> changes,
                                      List<com.portal.dto.SubTableBindingScope> scopes,
                                      Map<String, Object> submitted) {
        if (changes == null || changes.isEmpty()) return;
        if (submitted == null) throw forbidden("Batch submit has no canonical sub-table data");
        Map<String, Object> tables = map(submitted.get("__subTables__"));
        for (SubTableBatchChange change : changes) {
            String storeKey = storeKeyFor(change, scopes);
            List<Map<String, Object>> rows = rows(tables.get(storeKey));
            for (SubTableBatchChange.RowPatch patch : change.getRowPatches()) {
                if (patch.getRowKey() == null || patch.getRowKey().isEmpty()) {
                    throw forbidden("Batch row patch is missing its configured identity");
                }
                Map<String, Object> row = findByKey(rows, patch.getRowKey());
                if (row == null) throw forbidden("Batch row patch does not match a submitted canonical row");
                for (String field : patch.getFields()) {
                    if (!row.containsKey(field)) {
                        throw forbidden("Batch row patch field is absent from the submitted canonical row");
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateChange(SubTableBatchChange change, Map<String, Object> controls,
                                       Map<String, String> fieldPermissions) {
        if (change == null || blank(change.getBatchControlId()) || blank(change.getBindingId())) {
            throw forbidden("Batch operation is missing its frozen control identity");
        }
        Object rawControl = controls.get(change.getBatchControlId());
        if (!(rawControl instanceof Map<?, ?> map)) {
            throw forbidden("Batch control is not available for this task form");
        }
        Map<String, Object> control = (Map<String, Object>) map;
        if (!change.getBindingId().trim().equals(String.valueOf(control.get("bindingId")))) {
            throw forbidden("Batch control does not belong to the submitted binding");
        }
        if (!"EDIT".equals(change.getOperation())) {
            throw forbidden("Unsupported batch operation");
        }
        Map<String, Object> capabilities = map(control.get("capabilities"));
        Map<String, Object> originalCrud = map(control.get("originalCrud"));
        if (!Boolean.TRUE.equals(capabilities.get("allowBatchEdit"))
                || Boolean.FALSE.equals(originalCrud.get("allowEdit"))) {
            throw forbidden("Batch edit is not enabled for this control");
        }
        Set<String> allowedFields = batchFormFields(map(control.get("batchForm")));
        if (allowedFields.isEmpty()) {
            throw forbidden("Batch form has no supported editable fields");
        }
        if (change.getRowPatches() == null || change.getRowPatches().isEmpty()) {
            throw forbidden("Batch edit declaration has no row patches");
        }
        for (SubTableBatchChange.RowPatch patch : change.getRowPatches()) {
            if (patch == null || blank(patch.getRowId()) || patch.getRowKey() == null || patch.getRowKey().isEmpty()
                    || patch.getFields() == null || patch.getFields().isEmpty()) {
                throw forbidden("Invalid batch row patch");
            }
            for (String field : patch.getFields()) {
                if (blank(field) || !allowedFields.contains(field)) {
                    throw forbidden("Batch patch contains a field outside the frozen batch form");
                }
                if ("READONLY".equals(fieldPermissions.get(change.getBindingId().trim() + ":" + field))) {
                    throw forbidden("Batch patch contains a read-only field");
                }
            }
        }
    }

    private static String storeKeyFor(SubTableBatchChange change,
                                      List<com.portal.dto.SubTableBindingScope> scopes) {
        if (scopes == null) throw forbidden("Batch submit is missing binding scope metadata");
        List<String> keys = scopes.stream()
                .filter(Objects::nonNull)
                .filter(scope -> change.getBindingId().trim().equals(scope.getBindingId()))
                .map(com.portal.dto.SubTableBindingScope::getStoreKey)
                .filter(Objects::nonNull)
                .distinct().toList();
        if (keys.size() != 1) throw forbidden("Batch submit has no unambiguous canonical binding scope");
        return keys.get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) out.add((Map<String, Object>) map);
        }
        return out;
    }

    private static Map<String, Object> findByKey(List<Map<String, Object>> rows, Map<String, Object> key) {
        for (Map<String, Object> row : rows) {
            boolean match = key.entrySet().stream().allMatch(entry ->
                    Objects.equals(String.valueOf(row.get(entry.getKey())), String.valueOf(entry.getValue())));
            if (match) return row;
        }
        return null;
    }

    private static Set<String> batchFormFields(Map<String, Object> batchForm) {
        if (!(batchForm.get("schemaVersion") instanceof Number version) || version.intValue() != 1) {
            return Set.of();
        }
        Set<String> fields = new HashSet<>();
        collectFields(batchForm.get("rule"), fields);
        return fields;
    }

    @SuppressWarnings("unchecked")
    private static void collectFields(Object value, Set<String> fields) {
        if (value instanceof List<?> list) {
            for (Object item : list) collectFields(item, fields);
            return;
        }
        if (!(value instanceof Map<?, ?> raw)) return;
        Map<String, Object> rule = (Map<String, Object>) raw;
        Object field = rule.get("field");
        if (field instanceof String name && !name.isBlank()) fields.add(name.trim());
        for (Object child : new ArrayList<>(rule.values())) collectFields(child, fields);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static PortalException forbidden(String message) {
        return new PortalException("403", message);
    }
}
