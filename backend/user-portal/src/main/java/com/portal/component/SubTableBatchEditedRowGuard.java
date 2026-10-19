package com.portal.component;

import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;
import com.portal.dto.SubTableBatchChange;
import com.portal.exception.PortalException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates complete merged EDIT rows, not just the values declared in the field patch. */
final class SubTableBatchEditedRowGuard {
    private SubTableBatchEditedRowGuard() { }

    static void validate(SubTableBatchAddedRowGuard.Request request) {
        if (request.changes() == null || request.changes().stream().noneMatch(change -> "EDIT".equals(change.getOperation()))) return;
        Map<String, Object> controls = SubTableBatchRuntimeSnapshot.projectForForm(request.variables(),
                request.form().get("sourceId"), request.mapper());
        Map<?, ?> tables = map(request.submitted().get("__subTables__"));
        for (SubTableBatchChange change : request.changes()) {
            if (!"EDIT".equals(change.getOperation())) continue;
            Map<?, ?> control = map(controls.get(change.getBatchControlId()));
            Map<?, ?> design = map(control.get("rowDesign"));
            if (!(design.get("fields") instanceof List<?> fields) || fields.isEmpty()
                    || !(control.get("batchForm") instanceof Map<?, ?> form)) throw invalid("Frozen batch edit field rules are missing");
            String store = request.scopes().stream().filter(scope -> change.getBindingId().equals(scope.getBindingId()))
                    .map(com.portal.dto.SubTableBindingScope::getStoreKey).findFirst()
                    .orElseThrow(() -> invalid("Batch edit binding scope is missing"));
            if (!(tables.get(store) instanceof List<?> rows)) throw invalid("Batch edit canonical rows are missing");
            for (SubTableBatchChange.RowPatch patch : change.getRowPatches()) {
                Map<?, ?> row = rows.stream().map(SubTableBatchEditedRowGuard::map)
                        .filter(value -> patch.getRowKey().entrySet().stream().allMatch(entry ->
                                Objects.equals(String.valueOf(value.get(entry.getKey())), String.valueOf(entry.getValue()))))
                        .findFirst().orElseThrow(() -> invalid("Batch edited row is missing"));
                validateRow(fields, form, row);
            }
        }
    }

    private static void validateRow(List<?> fields, Map<?, ?> form, Map<?, ?> row) {
        validateMetadata(fields, row);
        try { SubTableBatchFormRules.validateRow(form, row); }
        catch (PortalException error) { throw invalid(error.getMessage()); }
    }

    /** Shared by ordinary writes on enhanced instances; does not apply the batch form's column/rule restrictions. */
    static void validateMetadata(List<?> fields, Map<?, ?> row) {
        try {
            for (Object raw : fields) {
                if (!(raw instanceof Map<?, ?> field) || !(field.get("fieldName") instanceof String name) || name.isBlank()) {
                    throw invalid("Frozen batch edit field identity is invalid");
                }
                SubTableBatchAddedRowGuard.validateField(field, row);
            }
        } catch (PortalException error) {
            throw invalid(error.getMessage());
        }
    }

    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_FIELD_INVALID, message); }
}
