package com.workflow.delegate;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rows a per-row Function Unit call runs for, read when the call starts.
 *
 * <p>Referenced from deployed BPMN as {@code ${fuCallRows.of(execution, 'table')}} — the
 * collection that {@code CallActivityDataMappingCompiler} writes for a call step whose designer
 * chose "once per row of &lt;table&gt;". Reading at that moment, rather than having the portal
 * store a list up front, means the call sees the rows as they are when it is reached, whichever
 * task or call completion got the request there.
 *
 * <p>Rows come from {@code __subTables__}, keyed {@code dw:<table name>} — the same canonical key
 * the portal writes. A request with no rows in that table yields an empty list, which Flowable
 * treats as "nothing to call" and moves straight on.
 */
@Slf4j
@Component("fuCallRows")
public class CallActivityRows {

    static final String SUB_TABLES = "__subTables__";

    public List<Map<String, Object>> of(DelegateExecution execution, String tableName) {
        Object subTables = execution.getVariable(SUB_TABLES);
        if (!(subTables instanceof Map<?, ?> byTable)) {
            return new ArrayList<>();
        }
        Object rows = byTable.get("dw:" + tableName);
        if (!(rows instanceof List<?> list)) {
            return new ArrayList<>();
        }
        // Copies as plain HashMaps: each becomes a variable of its own call instance, and the
        // engine must be able to serialize it whatever map type the portal sent.
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object row : list) {
            if (row instanceof Map<?, ?> map) {
                Map<String, Object> copy = new HashMap<>();
                map.forEach((k, v) -> {
                    if (k != null) {
                        copy.put(String.valueOf(k), v);
                    }
                });
                out.add(copy);
            }
        }
        log.debug("Per-row call in process {} runs for {} row(s) of {}",
                execution.getProcessInstanceId(), out.size(), tableName);
        return out;
    }
}
