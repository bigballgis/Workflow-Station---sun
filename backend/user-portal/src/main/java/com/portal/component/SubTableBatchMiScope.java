package com.portal.component;

import com.portal.exception.PortalException;
import java.util.Map;

/** Resolves one collection parent from immutable binding metadata and persisted server state. */
final class SubTableBatchMiScope {
    private SubTableBatchMiScope() { }
    record Scope(String foreignKey, String parentIdentity) {
        boolean includes(Map<?, ?> row) {
            Object value = row.get(foreignKey);
            if (!(value instanceof String || value instanceof Number) || String.valueOf(value).isBlank()) throw invalid();
            return parentIdentity.equals(String.valueOf(value));
        }
    }

    static Scope resolve(Map<?, ?> control, Map<String, Object> baseline) {
        if (!(control.get("rowDesign") instanceof Map<?, ?> design) || control.get("bindingId") == null) throw invalid();
        var binding = SubTableBatchScopeDesign.binding(design, String.valueOf(control.get("bindingId")));
        // Never interpret every sibling parent as the active parent. Nested/composite context needs an explicit authority contract.
        if (!"MAIN".equals(binding.refType()) || binding.refPk().size() != 1) throw invalid();
        Object value = baseline.get(binding.refPk().get(0));
        if (!(value instanceof String || value instanceof Number) || String.valueOf(value).isBlank()) throw invalid();
        return new Scope(binding.filterField(), String.valueOf(value));
    }
    private static PortalException invalid() {
        return new PortalException("409", "Frozen assignment parent scope is missing or ambiguous");
    }
}
