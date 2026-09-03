package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.jdbc.SqlIdentifiers;
import com.portal.dto.ActionTableRowsDTO;
import com.portal.dto.TaskInfo;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Read-only lookup of ACTION-table rows (e.g. FORM_POPUP "Meeting Remark") for Task Detail
 * read-back. Deliberately separate from {@link ProcessComponent#getFunctionUnitContent}, whose
 * result is cached per functionUnitId across every task/request of that FU — attaching per-request
 * row data there would leak one request's rows into every other request viewing the same FU.
 * This component queries the JSON row container directly, scoped by requestId, on every call.
 *
 * <p>Binding metadata (which tables are ACTION-bound, their table name and foreign_key_field) is
 * read from the (safely cacheable) FU content design payload — only the row data itself is fetched
 * fresh each time, from {@code dw_table_data_rows} (Table Design defines structure only; there is
 * no physical table per designer table name).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionTableReadComponent {

    private final JdbcTemplate jdbcTemplate;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ObjectMapper objectMapper;

    /** Lazy: same cycle-breaking rationale as SubTableEnrichmentComponent's ProcessComponent dependency. */
    @Lazy
    private final ProcessComponent processComponent;

    /**
     * Returns row data for every ACTION binding declared on the task's function unit — the
     * assignee/candidate/initiator Task Detail (To Do) path. See {@link #getActionTableRows(String)}
     * for the My Request (initiator, no taskId) path.
     */
    public List<ActionTableRowsDTO> getActionTableRows(TaskInfo task) {
        if (task == null || task.getProcessInstanceId() == null) {
            return new ArrayList<>();
        }
        return getActionTableRows(task.getProcessInstanceId());
    }

    /**
     * Returns row data for every ACTION binding declared on the request's function unit, keyed to
     * the current request via each binding's {@code foreign_key_field}. Bindings with no rows, or
     * whose foreign_key_field is not configured, are omitted (not returned as empty — the frontend
     * only renders a table for bindings actually placed on the main canvas, so omission is harmless).
     *
     * <p>Callers must perform their own access check first (this component does no authorization) —
     * see {@code TaskFormController#requireTaskFormAccess} / {@code ProcessFormController#requireProcessReadAccess}.</p>
     */
    public List<ActionTableRowsDTO> getActionTableRows(String processInstanceId) {
        List<ActionTableRowsDTO> result = new ArrayList<>();
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return result;
        }
        ProcessInstance processInstance = processInstanceRepository.findById(processInstanceId).orElse(null);
        if (processInstance == null) {
            return result;
        }
        String requestId = readRequestId(processInstance);
        if (requestId == null || requestId.isBlank()) {
            return result;
        }

        List<ActionBindingRef> actionBindings = resolveActionBindings(processInstance);
        for (ActionBindingRef binding : actionBindings) {
            if (binding.foreignKeyField() == null || binding.foreignKeyField().isBlank()) {
                continue;
            }
            try {
                List<Map<String, Object>> rows = queryRows(binding, requestId);
                if (!rows.isEmpty()) {
                    result.add(new ActionTableRowsDTO(binding.bindingId(), rows));
                }
            } catch (Exception e) {
                log.warn("[ActionTableRead] failed to load rows for binding {} ({}): {}",
                        binding.bindingId(), binding.tableName(), e.getMessage());
            }
        }
        return result;
    }

    private record ActionBindingRef(Long bindingId, String tableName, String foreignKeyField) {}

    private List<ActionBindingRef> resolveActionBindings(ProcessInstance processInstance) {
        List<ActionBindingRef> out = new ArrayList<>();
        String functionUnitRef = MiOverlaySupport.firstNonBlank(
                processInstance.getFunctionUnitCatalogId(),
                processInstance.getFunctionUnitCode(),
                processInstance.getProcessDefinitionKey()
        );
        if (functionUnitRef == null || functionUnitRef.isBlank()) {
            return out;
        }
        try {
            Map<String, Object> content = processComponent.getFunctionUnitContent(functionUnitRef);
            Object formsObj = content.get("forms");
            if (!(formsObj instanceof List<?> forms)) {
                return out;
            }
            for (Object formObj : forms) {
                if (!(formObj instanceof Map<?, ?> form)) {
                    continue;
                }
                Object bindingsObj = form.get("tableBindings");
                if (!(bindingsObj instanceof List<?> bindings)) {
                    continue;
                }
                for (Object bindingObj : bindings) {
                    if (!(bindingObj instanceof Map<?, ?> binding)) {
                        continue;
                    }
                    if (!"ACTION".equals(String.valueOf(binding.get("bindingType")))) {
                        continue;
                    }
                    Object bindingId = binding.get("bindingId");
                    Object tableName = binding.get("tableName");
                    Object foreignKeyField = binding.get("foreignKeyField");
                    if (bindingId == null || tableName == null) {
                        continue;
                    }
                    out.add(new ActionBindingRef(
                            Long.valueOf(String.valueOf(bindingId)),
                            String.valueOf(tableName),
                            foreignKeyField != null ? String.valueOf(foreignKeyField) : null
                    ));
                }
            }
        } catch (Exception e) {
            log.debug("[ActionTableRead] resolveActionBindings skipped: {}", e.getMessage());
        }
        return out;
    }

    /**
     * Rows come from the unified JSON container {@code dw_table_data_rows}, not from a physical
     * table named after the designer table — Table Design defines structure only
     * (see {@code .cursor/rules/json-row-storage-no-physical-tables.mdc}).
     *
     * <p>This used to run {@code SELECT * FROM <tableName>}, which failed with
     * {@code relation "..." does not exist} on every Function Unit. The failure was swallowed
     * into a WARN by the caller, so the Remark list simply rendered empty instead of surfacing
     * the real problem. Mirrors {@code ActionFormPopupSubmitComponent#insertRow}.
     *
     * <p>The foreign key that ties a row to its request is a designer-declared field, so it lives
     * inside {@code data} rather than as a column — it is matched with a JSONB key lookup, and the
     * field name is still identifier-validated because it is interpolated into the SQL text.
     */
    private List<Map<String, Object>> queryRows(ActionBindingRef binding, String requestId) {
        Long tableId = resolveTableId(binding.bindingId());
        if (tableId == null) {
            return List.of();
        }
        String fkField = SqlIdentifiers.requireIdentifier(binding.foreignKeyField());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT data::text AS data FROM dw_table_data_rows "
                        + "WHERE table_id = ? AND status = 'ACTIVE' AND data ->> '" + fkField + "' = ? "
                        + "ORDER BY created_at ASC, id ASC",
                tableId, requestId);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> data = readDataJson(r.get("data"));
            if (data != null) {
                out.add(data);
            }
        }
        return out;
    }

    private Long resolveTableId(Long bindingId) {
        if (bindingId == null) {
            return null;
        }
        return jdbcTemplate.query(
                "SELECT table_id FROM dw_form_table_bindings WHERE id = ?",
                rs -> rs.next() ? rs.getLong("table_id") : null,
                bindingId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDataJson(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), Map.class);
        } catch (Exception e) {
            log.debug("[ActionTableRead] skipped unreadable row data: {}", e.getMessage());
            return null;
        }
    }

    /** Mirrors {@link ActionFormPopupSubmitComponent#readRequestId} — must resolve to the same value the write path used. */
    private String readRequestId(ProcessInstance processInstance) {
        Map<String, Object> variables = processInstance.getVariables();
        if (variables == null) {
            return null;
        }
        Object requestId = variables.get("__request_id");
        if (requestId != null) {
            return String.valueOf(requestId);
        }
        Object id = variables.get("id");
        return id != null ? String.valueOf(id) : null;
    }
}
