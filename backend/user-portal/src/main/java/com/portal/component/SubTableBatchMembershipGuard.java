package com.portal.component;

import com.portal.dto.SubTableBatchChange;
import com.portal.dto.SubTableBindingScope;
import com.portal.exception.PortalException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Checks Add/Delete against the saved baseline, not the client's display array. */
final class SubTableBatchMembershipGuard {
    private SubTableBatchMembershipGuard() { }

    static void validate(List<SubTableBatchChange> changes, List<SubTableBindingScope> scopes,
                         Map<String, Object> baseline) {
        if (changes == null || changes.isEmpty()) return;
        Object rawTables = baseline == null ? null : baseline.get("__subTables__");
        Map<?, ?> tables = rawTables instanceof Map<?, ?> map ? map : Map.of();
        for (SubTableBatchChange change : changes) {
            if (!"ADD".equals(change.getOperation()) && !"DELETE".equals(change.getOperation())) continue;
            List<String> keys = scopes == null ? List.of() : scopes.stream().filter(Objects::nonNull)
                    .filter(scope -> change.getBindingId().equals(scope.getBindingId()))
                    .map(SubTableBindingScope::getStoreKey).distinct().toList();
            if (keys.size() != 1) throw new PortalException("403", "Batch membership change requires a binding scope");
            Object rawRows = tables.get(keys.get(0));
            List<?> rows = rawRows instanceof List<?> list ? list : List.of();
            for (SubTableBatchChange.RowPatch patch : change.getRowPatches()) {
                Map<?, ?> saved = rows.stream().filter(raw -> raw instanceof Map<?, ?> row
                        && patch.getRowKey().entrySet().stream().allMatch(entry ->
                        Objects.equals(row.get(entry.getKey()), entry.getValue())))
                        .map(raw -> (Map<?, ?>) raw).findFirst().orElse(null);
                boolean existed = saved != null;
                if ("ADD".equals(change.getOperation()) && existed) {
                    throw new PortalException("409", "Batch added row identity already exists");
                }
                if ("DELETE".equals(change.getOperation()) && !existed) {
                    throw new PortalException("409", "Batch deleted row no longer exists");
                }
                if ("DELETE".equals(change.getOperation()) && !(saved.get("_wsRowVersion") instanceof Number)) {
                    throw new PortalException("409", "Batch deleted row has no saved row version");
                }
            }
        }
    }
}
