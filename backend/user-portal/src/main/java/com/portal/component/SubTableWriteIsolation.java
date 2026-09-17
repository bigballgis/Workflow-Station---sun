package com.portal.component;

import com.portal.dto.SubTableBindingScope;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared write isolation for Task Save, Task Complete, Process Start, and
 * Return_To_Requester Process Form updates.
 *
 * <p>Storage stays table-keyed. MI submissions merge only the current row into the
 * persisted baseline; binding scopes then run {@link SubTableBindingScopeGuard}.
 * Empty scopes keep the V1 table-keyed path. Start uses an empty baseline.
 * Process Form updates use the process instance variables as baseline.
 */
@Component
@RequiredArgsConstructor
class SubTableWriteIsolation {

    private final MiSubTaskSubTableRowMerger merger;
    private final SubTableBindingScopeGuard guard;

    static final String EMPTIED_KEYS_FIELD = "emptiedSubTableKeys";
    static final String SCOPES_FIELD = "subTableBindingScopes";

    static void stripTransportMetadata(Map<String, Object> variables) {
        if (variables == null) {
            return;
        }
        variables.remove(EMPTIED_KEYS_FIELD);
        variables.remove(SCOPES_FIELD);
    }

    static Set<String> normalizeEmptiedKeys(List<String> declared) {
        if (declared == null || declared.isEmpty()) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (String k : declared) {
            if (k != null && !k.trim().isEmpty()) {
                keys.add(k.trim());
            }
        }
        return keys;
    }

    record Request(
            Map<String, Object> formData,
            Map<String, Object> inbound,
            Map<String, Object> baselineVariables,
            List<String> emptiedSubTableKeys,
            List<SubTableBindingScope> scopes,
            String functionUnitCode) {
    }

    /**
     * Isolates {@code inbound.__subTables__} against baseline variables.
     * {@code formData} must still carry {@code _currentItem} for MI detection.
     */
    @SuppressWarnings("unchecked")
    void apply(Request request) {
        if (request == null || request.inbound() == null
                || !(request.inbound().get("__subTables__") instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> inbound = request.inbound();
        Map<String, Object> submitted = (Map<String, Object>) inbound.get("__subTables__");
        Map<String, Object> baselineVars = request.baselineVariables();
        Map<String, Object> baseline = baselineVars != null
                && baselineVars.get("__subTables__") instanceof Map<?, ?>
                ? (Map<String, Object>) baselineVars.get("__subTables__")
                : Map.of();
        List<SubTableBindingScope> bindingScopes =
                request.scopes() == null ? List.of() : List.copyOf(request.scopes());
        if (merger.isMiSubTaskSubmission(request.formData())) {
            Map<String, Object> rowKey = merger.resolveCurrentItemRowKey(request.formData());
            merger.requireResolvedRowKey(rowKey);
            submitted = merger.mergeCurrentRowOnly(
                    submitted, baseline, rowKey,
                    normalizeEmptiedKeys(request.emptiedSubTableKeys()), bindingScopes,
                    request.functionUnitCode());
            inbound.put("__subTables__", submitted);
        }
        if (bindingScopes.isEmpty()) {
            return;
        }
        Map<String, Object> guarded = submitted instanceof LinkedHashMap
                ? submitted
                : new LinkedHashMap<>(submitted);
        guard.assertAndApply(bindingScopes, request.functionUnitCode(), request.formData(),
                guarded, baseline);
        inbound.put("__subTables__", guarded);
    }
}
