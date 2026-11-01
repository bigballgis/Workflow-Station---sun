package com.portal.component;

import com.portal.dto.SubTableBindingScope;
import com.portal.exception.PortalException;
import java.util.List;
import java.util.Map;

/** Restricts automatic filling; the scope guard still authorizes every resulting write. */
final class SubTableFkOwnership {
    private final Map<String, SubTableWriteDesign.Binding> bindings;
    private final List<SubTableBindingScope> scopes;
    private final Map<Long, String> stores;

    SubTableFkOwnership(Map<String, SubTableWriteDesign.Binding> bindings,
            List<SubTableBindingScope> scopes, Map<Long, String> stores) {
        this.bindings = bindings;
        this.scopes = scopes == null ? List.of() : List.copyOf(scopes);
        this.stores = stores;
        for (SubTableBindingScope scope : this.scopes) {
            SubTableWriteDesign.Binding binding = scope.getBindingId() == null ? null
                    : bindings.get(scope.getBindingId().trim());
            if (binding == null || !binding.storeKey().equals(scope.getStoreKey())) {
                throw new PortalException("403", "Binding is not part of the pinned form");
            }
        }
    }

    boolean allows(Long tableId, Map<String, Object> row, String field) {
        String store = stores.get(tableId);
        return claimedBindings(store, row).stream().anyMatch(binding -> ownsPopulatedForeignKeys(binding, row)
                && binding.allowedFillFields().stream().anyMatch(allowed -> allowed.equalsIgnoreCase(field)));
    }

    private List<SubTableWriteDesign.Binding> claimedBindings(String store, Map<String, Object> row) {
        List<SubTableWriteDesign.Binding> candidates = bindings.values().stream()
                .filter(binding -> binding.storeKey().equals(store)).toList();
        List<SubTableWriteDesign.Binding> claimed = scopes.stream()
                .filter(scope -> scope.getStoreKey().equals(store))
                .filter(scope -> scope.getRowKeys() != null && scope.getRowKeys().stream()
                        .anyMatch(key -> matches(row, key, bindings.get(scope.getBindingId().trim()).pk())))
                .map(scope -> bindings.get(scope.getBindingId().trim())).toList();
        // A sole published binding is unambiguous even for older clients without scopes.
        boolean storeIsScoped = scopes.stream().anyMatch(scope -> scope.getStoreKey().equals(store));
        return !storeIsScoped && candidates.size() == 1 ? candidates : claimed;
    }

    /** Run after every claim's filter/context/version check, before any version is bumped. */
    void assertNewRows(Map<String, Object> submitted, Map<String, Object> baseline) {
        for (String store : scopes.stream().map(SubTableBindingScope::getStoreKey).distinct().toList()) {
            List<String> keys = bindings.values().stream().filter(binding -> binding.storeKey().equals(store))
                    .findFirst().orElseThrow().pk();
            for (Map<String, Object> row : rows(submitted.get(store))) {
                if (rows(baseline.get(store)).stream().anyMatch(before -> matches(before, row, keys))) continue;
                // One fully authorized binding must own the row; never union partial FK permissions.
                if (claimedBindings(store, row).stream().noneMatch(binding -> ownsPopulatedForeignKeys(binding, row))) {
                    throw new PortalException("403", "New sub-table row contains a foreign key not owned by this binding");
                }
            }
        }
    }

    private static boolean ownsPopulatedForeignKeys(SubTableWriteDesign.Binding binding, Map<String, Object> row) {
        return binding.foreignKeyFields().stream().allMatch(field -> value(row, field) == null
                || String.valueOf(value(row, field)).isBlank()
                || binding.allowedFillFields().stream().anyMatch(allowed -> allowed.equalsIgnoreCase(field)));
    }

    private static Object value(Map<String, Object> row, String field) {
        return row.entrySet().stream().filter(entry -> field.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Object value) {
        return value instanceof List<?> list ? list.stream().filter(Map.class::isInstance)
                .map(row -> (Map<String, Object>) row).toList() : List.of();
    }

    private static boolean matches(Map<String, Object> row, Map<String, Object> key, List<String> primaryKeys) {
        if (key == null) return false;
        return SubTableBindingScopeGuard.keysMatch(
                SubTableBindingScopeGuard.rowKeyOf(key, primaryKeys),
                SubTableBindingScopeGuard.rowKeyOf(row, primaryKeys));
    }
}
