package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.audit.SystemAuditFields;
import com.platform.common.dto.PkGenerationConfig;
import com.platform.common.fk.PrimaryKeyAllocationService;
import com.portal.dto.TaskInfo;
import com.portal.entity.ActionDefinition;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ActionDefinitionRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.UserDisplayNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Persists FORM_POPUP action submissions (e.g. "Add Remark") into their bound ACTION table.
 *
 * <p>ACTION-type table bindings ({@code dw_form_table_bindings.binding_type = 'ACTION'}) let a
 * FORM_POPUP action's form design its own fields on a dedicated Table Design table, independent
 * of the process's PRIMARY table / {@code __subTables__} variables — the row is keyed back to the
 * running request via {@code foreign_key_field} (e.g. {@code main_id}), populated with the request
 * id (the same {@code id} process variable rendered to end users as "Request ID"). This does not
 * touch process variables at all.</p>
 *
 * <p>Rows live in the unified JSON container {@code dw_table_data_rows} — Table Design defines
 * structure only and never gets a physical table per designer table name.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionFormPopupSubmitComponent {

    /** Table/column identifiers are validated against this before ever reaching SQL. */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final PrimaryKeyAllocationService primaryKeyAllocationService;

    @Lazy
    @Autowired
    private UserDisplayNameResolver userDisplayNameResolver;

    @Transactional
    public void submit(TaskInfo task, String actionId, Map<String, Object> formData, String userId) {
        ActionDefinition action = actionDefinitionRepository.findFromDwById(actionId)
                .orElseThrow(() -> new PortalException("404", "Action not found: " + actionId));
        if (!"FORM_POPUP".equals(action.getActionType())) {
            throw new PortalException("400", "Action is not a FORM_POPUP action: " + actionId);
        }

        Long formId = readConfigFormId(action.getConfigJson());
        if (formId == null) {
            throw new PortalException("400", "Action has no target formId: " + actionId);
        }

        ActionTableBinding binding = resolveActionTableBinding(formId);
        List<String> validColumns = loadFieldNames(binding.tableId());

        ProcessInstance processInstance = processInstanceRepository.findById(task.getProcessInstanceId())
                .orElseThrow(() -> new PortalException("404",
                        "Process instance not found: " + task.getProcessInstanceId()));
        String requestId = readRequestId(processInstance);

        Map<String, Object> row = new LinkedHashMap<>();
        if (formData != null) {
            for (Map.Entry<String, Object> entry : formData.entrySet()) {
                String field = entry.getKey();
                if (field == null || SystemAuditFields.isAuditField(field)) {
                    continue;
                }
                if (validColumns.contains(field)) {
                    row.put(field, scalarize(field, entry.getValue()));
                }
            }
        }

        List<String> pkValues = allocatePrimaryKey(binding.tableId(), validColumns);
        if (!pkValues.isEmpty()) {
            row.put(pkValues.get(0), pkValues.get(1));
        }

        if (binding.foreignKeyField() != null && validColumns.contains(binding.foreignKeyField())) {
            row.put(binding.foreignKeyField(), requestId);
        }

        String displayName = resolveAuditUserDisplay(userId);
        if (validColumns.contains(SystemAuditFields.CREATED_BY)) {
            row.put(SystemAuditFields.CREATED_BY, displayName);
        }
        if (validColumns.contains(SystemAuditFields.UPDATED_BY)) {
            row.put(SystemAuditFields.UPDATED_BY, displayName);
        }

        insertRow(binding.tableId(), row);
        log.info("[ActionFormPopupSubmit] inserted row into {} for action {} (task {}, request {})",
                binding.tableName(), actionId, task.getTaskId(), requestId);
    }

    private Long readConfigFormId(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> cfg = objectMapper.readValue(configJson, Map.class);
            Object formId = cfg.get("formId");
            if (formId == null) {
                return null;
            }
            return Long.valueOf(String.valueOf(formId));
        } catch (Exception e) {
            log.warn("[ActionFormPopupSubmit] failed to parse action config_json: {}", e.getMessage());
            return null;
        }
    }

    private record ActionTableBinding(Long tableId, String tableName, String foreignKeyField) {}

    private ActionTableBinding resolveActionTableBinding(Long formId) {
        record Row(Long tableId, String tableName, String foreignKeyField) {}
        Row row = jdbcTemplate.query(
                """
                SELECT t.id AS table_id, t.table_name, b.foreign_key_field
                FROM dw_form_table_bindings b
                JOIN dw_table_definitions t ON t.id = b.table_id
                WHERE b.form_id = ? AND b.binding_type = 'ACTION'
                LIMIT 1
                """,
                rs -> rs.next()
                        ? new Row(rs.getLong("table_id"), rs.getString("table_name"), rs.getString("foreign_key_field"))
                        : null,
                formId);
        if (row == null) {
            throw new PortalException("400", "No ACTION table bound to form " + formId);
        }
        assertSafeIdentifier(row.tableName());
        if (row.foreignKeyField() != null) {
            assertSafeIdentifier(row.foreignKeyField());
        }
        return new ActionTableBinding(row.tableId(), row.tableName(), row.foreignKeyField());
    }

    private List<String> loadFieldNames(Long tableId) {
        List<String> names = jdbcTemplate.queryForList(
                "SELECT field_name FROM dw_field_definitions WHERE table_id = ?",
                String.class,
                tableId);
        names.forEach(this::assertSafeIdentifier);
        return names;
    }

    private List<String> allocatePrimaryKey(Long tableId, List<String> validColumns) {
        record PkField(String fieldName, String pkJson) {}
        PkField pk = jdbcTemplate.query(
                """
                SELECT field_name, pk_generation_json::text AS pk_json
                FROM dw_field_definitions
                WHERE table_id = ? AND is_primary_key = true
                LIMIT 1
                """,
                rs -> rs.next() ? new PkField(rs.getString("field_name"), rs.getString("pk_json")) : null,
                tableId);
        if (pk == null || !validColumns.contains(pk.fieldName())) {
            return List.of();
        }
        PkGenerationConfig config = parsePkConfig(pk.pkJson());
        List<String> values = primaryKeyAllocationService.allocate(tableId, pk.fieldName(), config, 1, "");
        if (values.isEmpty()) {
            return List.of();
        }
        return List.of(pk.fieldName(), values.get(0));
    }

    private PkGenerationConfig parsePkConfig(String json) {
        if (json == null || json.isBlank()) {
            return PkGenerationConfig.builder().strategy("uuid").build();
        }
        try {
            return objectMapper.readValue(json, PkGenerationConfig.class);
        } catch (Exception e) {
            return PkGenerationConfig.builder().strategy("uuid").build();
        }
    }

    /**
     * A Lookup field's value arrives as the whole selected row (a Map), not a scalar — the
     * runtime FormRenderer stores {@code formData[field] = selectedRow} for backfill display.
     * The physical column expects a scalar, so extract the value under the field's own key
     * (a Lookup field named {@code building} selects a row that itself has a {@code building}
     * column, matching the target column 1:1 by construction — see LookupBindingSelect's
     * {@code selectedDisplayField}). Anything else (string/number/boolean/null) passes through.
     */
    private Object scalarize(String field, Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return value;
        }
        Object own = map.get(field);
        return own != null ? own : map.get("id");
    }

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

    /**
     * Table Design keeps structure only — a designer table name never has a physical table
     * (see {@code .cursor/rules/json-row-storage-no-physical-tables.mdc}). Rows go into the
     * unified JSON container {@code dw_table_data_rows} keyed by {@code table_id}, mirroring
     * {@code rt_table_data_rows} on the Relation Table side.
     *
     * <p>This used to build {@code INSERT INTO <tableName> (...)}, which threw
     * {@code relation "meeting_remark" does not exist} on every Function Unit — the feature
     * could never have worked, because the platform does not create those tables.
     *
     * <p>{@code created_at}/{@code updated_at} are real columns on the container, so they are
     * not written into {@code data}; the audit *user* fields stay in {@code data} because they
     * are designer-declared fields the form renders back.
     */
    private void insertRow(Long tableId, Map<String, Object> row) {
        if (row.isEmpty()) {
            throw new PortalException("400", "No valid fields to submit");
        }
        String rowId = UUID.randomUUID().toString();
        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(row);
        } catch (Exception e) {
            throw new PortalException("500", "Failed to serialize action row: " + e.getMessage());
        }
        jdbcTemplate.update(
                "INSERT INTO dw_table_data_rows (table_id, row_id, data, created_by, updated_by) "
                        + "VALUES (?, ?, ?::jsonb, ?, ?)",
                tableId,
                rowId,
                dataJson,
                stringOrNull(row.get(SystemAuditFields.CREATED_BY)),
                stringOrNull(row.get(SystemAuditFields.UPDATED_BY)));
    }

    private static String stringOrNull(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private void assertSafeIdentifier(String identifier) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new PortalException("400", "Unsafe identifier: " + identifier);
        }
    }

    private String resolveAuditUserDisplay(String userId) {
        UserDisplayNameResolver resolver = userDisplayNameResolver;
        if (resolver == null) {
            return userId;
        }
        try {
            String display = resolver.resolve(userId);
            return display != null && !display.isBlank() ? display : userId;
        } catch (RuntimeException ex) {
            log.debug("resolveAuditUserDisplay failed for {}: {}", userId, ex.getMessage());
            return userId;
        }
    }
}
