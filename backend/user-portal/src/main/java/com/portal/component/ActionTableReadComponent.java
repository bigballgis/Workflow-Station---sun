package com.portal.component;

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
 * This component queries the physical table directly, scoped by requestId, on every call.
 *
 * <p>Binding metadata (which tables are ACTION-bound, their physical table name and
 * foreign_key_field) is read from the (safely cacheable) FU content design payload — only the
 * row data itself is fetched fresh each time.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionTableReadComponent {

    private final JdbcTemplate jdbcTemplate;
    private final ProcessInstanceRepository processInstanceRepository;

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

    private List<Map<String, Object>> queryRows(ActionBindingRef binding, String requestId) {
        String tableName = SqlIdentifiers.requireIdentifier(binding.tableName());
        String fkColumn = SqlIdentifiers.requireIdentifier(binding.foreignKeyField());
        String sql = "SELECT * FROM " + tableName + " WHERE " + fkColumn + " = ? ORDER BY created_at ASC";
        return jdbcTemplate.queryForList(sql, requestId);
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
