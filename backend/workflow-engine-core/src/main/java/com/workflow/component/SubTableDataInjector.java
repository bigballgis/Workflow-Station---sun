package com.workflow.component;

import com.platform.common.i18n.I18nService;
import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.platform.common.jdbc.SqlIdentifiers;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sub-table data injector.
 *
 * Collection variable elements include {@code rowKey} (full primary key, supports composite PK);
 * additionally keeps {@code rowId} for single numeric PK compatibility with legacy logic.
 */
@Slf4j
@Component
public class SubTableDataInjector {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private I18nService i18nService;

    /**
     * Whether a physical base table with this name exists in the current schema
     * (JSON-only workflows typically do not have tables matching sub-table design names).
     */
    public boolean physicalTableExistsInCurrentSchema(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return false;
        }
        String t = tableName.trim();
        if (!t.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return false;
        }
        try {
            Integer n = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*)::int FROM information_schema.tables
                            WHERE table_schema = current_schema()
                              AND table_type = 'BASE TABLE'
                              AND lower(table_name) = lower(?)
                            """,
                    Integer.class,
                    t);
            return n != null && n > 0;
        } catch (Exception e) {
            log.debug("physicalTableExistsInCurrentSchema failed for {}: {}", tableName, e.getMessage());
            return false;
        }
    }

    public void injectSubTableData(
            String processInstanceId,
            String subTableName,
            String foreignKeyField,
            Long mainRecordId,
            String assigneeField,
            String collectionVariableName) {

        log.info("Starting sub-table data injection: processInstanceId={}, subTableName={}, mainRecordId={}",
                processInstanceId, subTableName, mainRecordId);

        List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, subTableName);

        List<Map<String, Object>> subTableRows = querySubTableData(
                subTableName, foreignKeyField, mainRecordId, assigneeField, pkCols);

        if (subTableRows.isEmpty()) {
            throw new WorkflowValidationException(
                    i18nService.getMessage("workflow.subtable.empty_data_source")
            );
        }

        log.debug("Found {} sub-table data rows", subTableRows.size());

        List<Map<String, Object>> collectionVariable = buildCollectionVariable(
                subTableRows, assigneeField, pkCols);

        String variableName = collectionVariableName != null
                ? collectionVariableName
                : String.format("multiInstance_%s_collection", subTableName);

        runtimeService.setVariable(processInstanceId, variableName, collectionVariable);

        log.info("Sub-table data injection successful: processInstanceId={}, variableName={}, rowCount={}",
                processInstanceId, variableName, collectionVariable.size());
    }

    private List<Map<String, Object>> querySubTableData(
            String subTableName,
            String foreignKeyField,
            Long mainRecordId,
            String assigneeField,
            List<String> pkCols) {

        try {
            String pkSelect = PostgresPhysicalTablePrimaryKeys.commaJoinedPkSelect(pkCols);
            String sql = String.format(
                    "SELECT %s, %s, row_version FROM %s WHERE %s = ?",
                    pkSelect,
                    SqlIdentifiers.requireIdentifier(assigneeField),
                    SqlIdentifiers.requireQualifiedName(subTableName),
                    SqlIdentifiers.requireIdentifier(foreignKeyField)
            );

            log.debug("Executing sub-table query: sql={}, mainRecordId={}", sql, mainRecordId);

            return jdbcTemplate.queryForList(sql, mainRecordId);
        } catch (Exception e) {
            log.error("Sub-table data query failed: subTableName={}, mainRecordId={}",
                    subTableName, mainRecordId, e);

            if (e.getMessage() != null
                    && (e.getMessage().contains("does not exist")
                    || e.getMessage().contains("doesn't exist")
                    || e.getMessage().contains("relation") && e.getMessage().contains("does not exist"))) {
                throw new WorkflowBusinessException(
                        "SUBTABLE_NOT_FOUND",
                        i18nService.getMessage("workflow.subtable.not_found", subTableName),
                        e
                );
            }

            throw new WorkflowBusinessException(
                    "SUBTABLE_QUERY_FAILED",
                    i18nService.getMessage("workflow.subtable.query_error", e.getMessage()),
                    e
            );
        }
    }

    private List<Map<String, Object>> buildCollectionVariable(
            List<Map<String, Object>> subTableRows,
            String assigneeField,
            List<String> pkCols) {

        List<Map<String, Object>> collectionVariable = new ArrayList<>();
        List<Integer> emptyAssigneeRows = new ArrayList<>();

        for (int i = 0; i < subTableRows.size(); i++) {
            Map<String, Object> row = subTableRows.get(i);

            Map<String, Object> rowKey = new LinkedHashMap<>();
            for (String col : pkCols) {
                rowKey.put(col, row.get(col));
            }
            if (!SubTableRowKeySupport.isComplete(pkCols, rowKey)) {
                throw new WorkflowValidationException(i18nService.getMessage("workflow.subtable.null_pk"));
            }

            Object assigneeObj = row.get(assigneeField);
            Object rowVersionObj = row.get("row_version");

            if (assigneeObj == null || assigneeObj.toString().trim().isEmpty()) {
                emptyAssigneeRows.add(i + 1);
                continue;
            }

            Map<String, Object> element = new HashMap<>();
            element.put("rowKey", rowKey);
            if (pkCols.size() == 1) {
                Object only = rowKey.get(pkCols.get(0));
                if (only instanceof Number) {
                    element.put("rowId", ((Number) only).longValue());
                }
            }
            element.put("assigneeId", assigneeObj.toString());
            element.put("rowVersion", rowVersionObj != null ? ((Number) rowVersionObj).longValue() : 1L);

            collectionVariable.add(element);

            log.debug("Building collection variable element: rowKey={}, assigneeId={}, rowVersion={}",
                    rowKey, element.get("assigneeId"), element.get("rowVersion"));
        }

        if (!emptyAssigneeRows.isEmpty()) {
            String rowNumbers = String.join(", ",
                    emptyAssigneeRows.stream().map(String::valueOf).toArray(String[]::new));
            throw new WorkflowValidationException(
                    i18nService.getMessage("workflow.subtable.missing_assignee", rowNumbers, assigneeField)
            );
        }

        return collectionVariable;
    }
}
