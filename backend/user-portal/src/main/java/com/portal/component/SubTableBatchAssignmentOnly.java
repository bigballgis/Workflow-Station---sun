package com.portal.component;

import com.portal.dto.SubTableBatchChange;
import com.portal.exception.PortalException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validates declared assignment targets and identifies existing assignment-only rows. */
final class SubTableBatchAssignmentOnly {
    private SubTableBatchAssignmentOnly() { }

    static boolean validate(Map<?, ?> control, List<?> fields, Map<?, ?> original, Map<?, ?> row,
                            List<SubTableBatchChange> changes) {
        if (changes == null || changes.isEmpty()) return false;
        Map<?, ?> design = requiredMap(control.get("rowDesign"));
        Map<?, ?> binding = requiredMap(design.get("binding"));
        if (!(binding.get("primaryKeyFields") instanceof List<?> keys) || keys.isEmpty()) throw invalid();
        var intents = changes.stream().filter(change -> "ASSIGN".equals(change.getOperation())
                && Objects.equals(control.get("batchControlId"), change.getBatchControlId())
                && String.valueOf(control.get("bindingId")).equals(change.getBindingId().trim()))
                .flatMap(change -> change.getRowPatches().stream()).filter(patch -> matches(patch, row, keys)).toList();
        if (intents.isEmpty()) return false;
        if (intents.size() != 1) throw invalid();
        Map<?, ?> config = requiredMap(control.get("assignmentConfig"));
        Set<Object> assignmentFields = new HashSet<>();
        for (String property : List.of("assigneeField", "roleField", "buField")) {
            if (config.get(property) instanceof String name && !name.isBlank()) assignmentFields.add(name);
        }
        Set<Object> changed = original == null ? new HashSet<>() : differences(original, row);
        Set<Object> known = new HashSet<>();
        for (Object raw : fields) known.add(requiredMap(raw).get("fieldName"));
        // The person picker hydrates this platform display label after selection. An explicitly
        // declared business column with the same name remains subject to ordinary field checks.
        if (!known.contains("assignee_display_name")) changed.remove("assignee_display_name");
        if (!known.containsAll(changed)) throw new PortalException("403", "Batch assignment changes an unknown field: "
                + changed.stream().filter(name -> !known.contains(name)).toList());
        if (!known.containsAll(assignmentFields)) throw invalid();
        SubTableBatchChangeGuard.validateAssignmentRow(config, row, intents.get(0).getAssignment());
        // Validate all target fields as well as mode-clearing values: an unchanged malformed
        // assignee must not masquerade as empty when switching to a role target.
        List<?> targetFields = fields.stream().filter(raw -> assignmentFields.contains(requiredMap(raw).get("fieldName"))).toList();
        if (targetFields.stream().anyMatch(raw -> protectedField(requiredMap(raw)))) throw invalid();
        SubTableBatchEditedRowGuard.validateMetadata(targetFields, row);
        return original != null && !changed.isEmpty() && assignmentFields.containsAll(changed);
    }

    private static boolean matches(SubTableBatchChange.RowPatch patch, Map<?, ?> row, List<?> keys) {
        if (patch.getRowKey() == null || !patch.getRowKey().keySet().equals(new HashSet<>(keys))) throw invalid();
        return keys.stream().allMatch(key -> Objects.equals(String.valueOf(patch.getRowKey().get(key)), String.valueOf(row.get(key))));
    }

    private static Set<Object> differences(Map<?, ?> original, Map<?, ?> row) {
        Set<Object> names = new HashSet<>(original.keySet());
        names.addAll(row.keySet());
        names.removeIf(name -> original.containsKey(name) == row.containsKey(name) && Objects.equals(original.get(name), row.get(name)));
        // Version claims and server-generated increments remain owned by the binding scope guard.
        names.remove(SubTableBindingScopeGuard.ROW_VERSION_FIELD);
        names.removeIf(name -> com.platform.common.audit.SystemAuditFields.isAuditField(String.valueOf(name)));
        return names;
    }

    private static boolean protectedField(Map<?, ?> field) {
        return Boolean.TRUE.equals(field.get("isPrimaryKey")) || Boolean.TRUE.equals(field.get("isForeignKey"))
                || Boolean.TRUE.equals(field.get("isComputed"));
    }

    private static Map<?, ?> requiredMap(Object value) {
        if (value instanceof Map<?, ?> map) return map;
        throw invalid();
    }

    private static PortalException invalid() { return new PortalException("409", "Pure batch assignment context is invalid"); }
}
