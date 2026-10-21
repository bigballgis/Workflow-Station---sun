package com.workflow.component;

import com.platform.common.functionunit.RuntimeVersionContext;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/** Direct Engine writes must not bypass the Portal's immutable version assignment. */
@Component
@RequiredArgsConstructor
public class RuntimeVersionWriteGuard {
    private final RuntimeService runtime;
    private final JdbcTemplate jdbc;

    public void validate(String processId, Map<String, Object> incoming) {
        Map<String, Object> existing = new HashMap<>(runtime.getVariables(processId, RuntimeVersionContext.metadataKeys()));
        if (existing.get("functionUnitId") == null) {
            var rows = jdbc.queryForList("SELECT function_unit_catalog_id, function_unit_code, function_unit_version_label "
                    + "FROM up_process_instance WHERE id=?", processId);
            if (rows.size() == 1 && rows.get(0).get("function_unit_catalog_id") != null) {
                var row = rows.get(0);
                existing.put("functionUnitId", row.get("function_unit_catalog_id"));
                existing.put("functionUnitCode", row.get("function_unit_code"));
                existing.put("functionUnitVersionLabel", row.get("function_unit_version_label"));
            }
        }
        RuntimeVersionContext.requireUnchanged(existing, incoming);
    }
}
