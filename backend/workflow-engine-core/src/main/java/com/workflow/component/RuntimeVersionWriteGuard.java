package com.workflow.component;

import com.platform.common.functionunit.RuntimeVersionContext;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Direct Engine writes must not bypass the Portal's immutable version assignment. */
@Component
@RequiredArgsConstructor
public class RuntimeVersionWriteGuard {
    private final RuntimeService runtime;
    private final JdbcTemplate jdbc;

    private static final List<String> PORTAL_PIN_KEYS =
            List.of("functionUnitId", "functionUnitCode", "functionUnitVersionLabel");

    public void validate(String processId, Map<String, Object> incoming) {
        Map<String, Object> existing = new HashMap<>(runtime.getVariables(processId, RuntimeVersionContext.metadataKeys()));
        if (incoming != null && PORTAL_PIN_KEYS.stream().anyMatch(key -> incoming.containsKey(key) && existing.get(key) == null)) {
            var rows = jdbc.queryForList("SELECT function_unit_catalog_id, function_unit_code, function_unit_version_label "
                    + "FROM up_process_instance WHERE id=?", processId);
            Object engineRelease = existing.get("functionUnitId");
            if (rows.size() == 1 && rows.get(0).get("function_unit_catalog_id") != null
                    && (engineRelease == null || engineRelease.equals(rows.get(0).get("function_unit_catalog_id")))) {
                // Requests started before runtime versioning hold only part of the association in the engine
                // (e.g. no version label). Fill the gaps from the Portal's pin of the same release, never over a value.
                var row = rows.get(0);
                existing.putIfAbsent("functionUnitId", row.get("function_unit_catalog_id"));
                existing.putIfAbsent("functionUnitCode", row.get("function_unit_code"));
                existing.putIfAbsent("functionUnitVersionLabel", row.get("function_unit_version_label"));
            }
        }
        RuntimeVersionContext.requireUnchanged(existing, incoming);
    }
}
