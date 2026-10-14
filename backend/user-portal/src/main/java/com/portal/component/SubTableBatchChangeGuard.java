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
        Set<Map.Entry<String, Map<String, Object>>> affected = new HashSet<>();
        for (SubTableBatchChange change : changes) {
            validateChange(change, controls, Map.of());
            for (SubTableBatchChange.RowPatch patch : change.getRowPatches()) {
                affected.add(Map.entry(change.getBindingId(), patch.getRowKey()));
            }
            if (affected.size() > 200) throw forbidden("Batch submit exceeds 200 affected rows");
        }
    }

    static void validateFieldPermissions(List<SubTableBatchChange> changes,
                                         Map<String, String> fieldPermissions) {
        if (changes == null || changes.isEmpty() || fieldPermissions == null || fieldPermissions.isEmpty()) return;
        for (SubTableBatchChange change : changes) {
            if (change == null || blank(change.getBindingId()) || change.getRowPatches() == null) continue;
            // ADD carries initial values, including frozen readonly defaults. The full canonical
            // row is checked by AddedRowGuard and TaskPermissions; it is not an edit permission grant.
            if ("ADD".equals(change.getOperation())) continue;
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
                if ("DELETE".equals(change.getOperation())) {
                    if (row != null) throw forbidden("Batch deleted row still exists in the submitted canonical store");
                    requireVersionedDeletion(change, patch, scopes);
                    continue;
                }
                if (row == null) throw forbidden("Batch row patch does not match a submitted canonical row");
                for (String field : patch.getFields() == null ? List.<String>of() : patch.getFields()) {
                    if (!row.containsKey(field)) {
                        throw forbidden("Batch row patch field is absent from the submitted canonical row");
                    }
                }
            }
        }
    }

    private static void requireVersionedDeletion(SubTableBatchChange change, SubTableBatchChange.RowPatch patch,
                                                  List<com.portal.dto.SubTableBindingScope> scopes) {
        List<Map<String, Object>> deletions = scopes.stream().filter(Objects::nonNull)
                .filter(scope -> change.getBindingId().equals(scope.getBindingId()))
                .filter(scope -> scope.getDeletedRows() != null)
                .flatMap(scope -> scope.getDeletedRows().stream()).toList();
        Map<String, Object> deletion = findByKey(deletions, patch.getRowKey());
        if (deletion == null || !(deletion.get("_wsRowVersion") instanceof Number)) {
            throw forbidden("Batch delete requires an explicit versioned deletion claim");
        }
    }

    /**
     * Assignment intent is not authoritative by itself.  The canonical row
     * submitted through the normal save path must contain exactly the frozen
     * assignment fields represented by that intent; otherwise a forged intent
     * could make an unrelated field mutation look like a valid batch assign.
     */
    static void validateSubmittedAssignments(Map<String, Object> instanceVariables,
                                             Map<String, Object> formDefinition,
                                             List<SubTableBatchChange> changes,
                                             List<com.portal.dto.SubTableBindingScope> scopes,
                                             Map<String, Object> submitted,
                                             com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        if (changes == null || changes.stream().noneMatch(change -> change != null && "ASSIGN".equals(change.getOperation()))) return;
        Object sourceId = formDefinition == null ? null : formDefinition.get("sourceId");
        Map<String, Object> controls = SubTableBatchRuntimeSnapshot.projectForForm(instanceVariables, sourceId, objectMapper);
        Map<String, Object> tables = map(submitted == null ? null : submitted.get("__subTables__"));
        for (SubTableBatchChange change : changes) {
            if (change == null || !"ASSIGN".equals(change.getOperation())) continue;
            Map<String, Object> control = map(controls.get(change.getBatchControlId()));
            Map<String, Object> config = map(control.get("assignmentConfig"));
            String assigneeField = text(config.get("assigneeField"));
            String roleField = text(config.get("roleField"));
            String buField = text(config.get("buField"));
            if (assigneeField.isEmpty() && roleField.isEmpty()) throw forbidden("Batch assignment has no frozen field mapping");
            List<Map<String, Object>> rows = rows(tables.get(storeKeyFor(change, scopes)));
            for (SubTableBatchChange.RowPatch patch : change.getRowPatches()) {
                Map<String, Object> row = findByKey(rows, patch.getRowKey());
                if (row == null) throw forbidden("Batch assignment row does not match a submitted canonical row");
                SubTableBatchChange.AssignmentIntent intent = patch.getAssignment();
                if (intent == null) throw forbidden("Batch assignment row has no intent");
                if ("PERSON".equals(intent.getMode())) {
                    if (!same(row.get(assigneeField), intent.getAssigneeId()) || !empty(row.get(roleField)) || !empty(row.get(buField))) {
                        throw forbidden("Batch assignment canonical fields do not match the person intent");
                    }
                } else if ("ROLE".equals(intent.getMode())) {
                    if (!same(row.get(roleField), intent.getRoleCode()) || !empty(row.get(assigneeField))
                            || (!buField.isEmpty() && !same(row.get(buField), intent.getBusinessUnitCode()))) {
                        throw forbidden("Batch assignment canonical fields do not match the role intent");
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
        if (!"EDIT".equals(change.getOperation()) && !"ASSIGN".equals(change.getOperation())
                && !"DELETE".equals(change.getOperation()) && !"ADD".equals(change.getOperation())) {
            throw forbidden("Unsupported batch operation");
        }
        Map<String, Object> capabilities = map(control.get("capabilities"));
        Map<String, Object> originalCrud = map(control.get("originalCrud"));
        if (change.getRowPatches() == null || change.getRowPatches().isEmpty() || change.getRowPatches().size() > 200) {
            throw forbidden("Batch operation requires between 1 and 200 row patches");
        }
        if ("ADD".equals(change.getOperation()) || "DELETE".equals(change.getOperation())) {
            validateMembershipIdentity(change, map(control.get("rowDesign")));
        }
        if (!"DELETE".equals(change.getOperation()) && !"ADD".equals(change.getOperation())
                && Boolean.FALSE.equals(originalCrud.get("allowEdit"))) {
            throw forbidden("Batch operation is not enabled for this control");
        }
        if ("EDIT".equals(change.getOperation()) && !Boolean.TRUE.equals(capabilities.get("allowBatchEdit"))) {
            throw forbidden("Batch edit is not enabled for this control");
        }
        if ("ASSIGN".equals(change.getOperation())) {
            validateAssignment(change, control, capabilities);
            return;
        }
        if ("DELETE".equals(change.getOperation())) {
            validateDelete(change, capabilities, originalCrud);
            return;
        }
        if ("ADD".equals(change.getOperation())) {
            validateAdd(change, capabilities, originalCrud, map(control.get("batchForm")));
            return;
        }
        validateEdit(change, control, fieldPermissions);
    }

    private static void validateEdit(SubTableBatchChange change, Map<String, Object> control,
                                     Map<String, String> fieldPermissions) {
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
            if (control.get("rowDesign") instanceof Map<?, ?> design) {
                SubTableBatchPublishedPermissions.validateEditedFields(design, patch.getFields());
            }
        }
    }

    private static void validateMembershipIdentity(SubTableBatchChange change, Map<String, Object> design) {
        Map<String, Object> binding = map(design.get("binding"));
        if (!"SUB".equals(binding.get("bindingType")) || !"EDITABLE".equals(binding.get("bindingMode"))
                || !"structuralFk".equals(binding.get("bindingLinkMode"))) {
            throw forbidden("Batch membership operations require an editable structural business binding");
        }
        if (!(binding.get("primaryKeyFields") instanceof List<?> keys) || keys.isEmpty()) {
            throw forbidden("Batch membership operations require frozen row identity fields");
        }
        Set<Map<String, Object>> seen = new HashSet<>();
        for (SubTableBatchChange.RowPatch patch : change.getRowPatches()) {
            if (patch == null || patch.getRowKey() == null || !patch.getRowKey().keySet().equals(new HashSet<>(keys))
                    || patch.getRowKey().values().stream().anyMatch(value -> value == null || String.valueOf(value).isBlank())
                    || !seen.add(patch.getRowKey())) {
                throw forbidden("Batch membership row identity is missing, duplicated or not configured");
            }
        }
    }

    private static void validateDelete(SubTableBatchChange change, Map<String, Object> capabilities,
                                       Map<String, Object> originalCrud) {
        if (!Boolean.TRUE.equals(capabilities.get("allowBatchDelete"))
                || Boolean.FALSE.equals(originalCrud.get("allowDelete"))) {
            throw forbidden("Batch delete is not enabled for this control");
        }
        if (change.getRowPatches() == null || change.getRowPatches().isEmpty()) {
            throw forbidden("Batch delete declaration has no row patches");
        }
        for (SubTableBatchChange.RowPatch patch : change.getRowPatches()) {
            if (patch == null || blank(patch.getRowId()) || patch.getRowKey() == null || patch.getRowKey().isEmpty()
                    || (patch.getFields() != null && !patch.getFields().isEmpty()) || patch.getAssignment() != null) {
                throw forbidden("Invalid batch delete row patch");
            }
        }
    }

    private static void validateAdd(SubTableBatchChange change, Map<String, Object> capabilities,
                                    Map<String, Object> originalCrud, Map<String, Object> batchForm) {
        if (!Boolean.TRUE.equals(capabilities.get("allowBatchAdd"))
                || Boolean.FALSE.equals(originalCrud.get("allowAdd"))) {
            throw forbidden("Batch add is not enabled for this control");
        }
        Set<String> allowedFields = batchFormFields(batchForm);
        if (allowedFields.isEmpty() || change.getRowPatches() == null || change.getRowPatches().isEmpty()) {
            throw forbidden("Batch add declaration has no supported row fields");
        }
        for (SubTableBatchChange.RowPatch patch : change.getRowPatches()) {
            if (patch == null || blank(patch.getRowId()) || patch.getRowKey() == null || patch.getRowKey().isEmpty() || patch.getAssignment() != null
                    || patch.getFields() == null || patch.getFields().isEmpty()) {
                throw forbidden("Invalid batch add row patch");
            }
            for (String field : patch.getFields()) {
                if (blank(field) || !allowedFields.contains(field)) {
                    throw forbidden("Batch add contains a field outside the frozen batch form");
                }
            }
        }
    }

    private static void validateAssignment(SubTableBatchChange change, Map<String, Object> control,
                                           Map<String, Object> capabilities) {
        if (!Boolean.TRUE.equals(capabilities.get("allowBatchAssign"))) {
            throw forbidden("Batch assignment is not enabled for this control");
        }
        Map<String, Object> assignmentConfig = map(control.get("assignmentConfig"));
        if (blank(text(assignmentConfig.get("taskDefinitionKey")))) {
            throw forbidden("Batch assignment has no frozen BPMN task target");
        }
        if (change.getRowPatches() == null || change.getRowPatches().isEmpty()) {
            throw forbidden("Batch assignment declaration has no row patches");
        }
        for (SubTableBatchChange.RowPatch patch : change.getRowPatches()) {
            if (patch == null || blank(patch.getRowId()) || patch.getRowKey() == null || patch.getRowKey().isEmpty()
                    || patch.getAssignment() == null) {
                throw forbidden("Invalid batch assignment row patch");
            }
            SubTableBatchChange.AssignmentIntent intent = patch.getAssignment();
            if ("PERSON".equals(intent.getMode())) {
                if (!Boolean.TRUE.equals(assignmentConfig.get("allowUser")) || blank(intent.getAssigneeId())) {
                    throw forbidden("Batch assignment person target is invalid");
                }
            } else if ("ROLE".equals(intent.getMode())) {
                if (!Boolean.TRUE.equals(assignmentConfig.get("allowRole")) || blank(intent.getRoleCode())) {
                    throw forbidden("Batch assignment role target is invalid");
                }
            } else {
                throw forbidden("Batch assignment mode is invalid");
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

    private static String text(Object value) {
        return value instanceof String text ? text.trim() : "";
    }

    private static boolean same(Object actual, String expected) {
        return expected != null && expected.equals(text(actual));
    }

    private static boolean empty(Object value) {
        return text(value).isEmpty();
    }

    private static PortalException forbidden(String message) {
        return new PortalException("403", message);
    }
}
