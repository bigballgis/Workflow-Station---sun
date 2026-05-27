package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.platform.common.jdbc.SubTablePhysicalColumnResolver;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.platform.common.i18n.I18nService;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Resolves multi-instance sub-table row keys, loads/writes row data, and applies optimistic locking.
 *
 * Portal MI sub-task form hydrate is handled in tasks/detail.vue; this component owns row-key/data only.
 */
@Slf4j
@Component
public class MultiInstanceDataResolver {
    
    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;

    @Autowired
    private BpmnActionParser bpmnActionParser;

    @Autowired
    private I18nService i18nService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Load main form fields from process variables (excludes system/MI vars).
     */
    public Map<String, Object> loadMainFormData(String processInstanceId) {
        log.debug("Loading main form data: processInstanceId={}", processInstanceId);
        
        // Load all variables from the process instance
        Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
        
        // Keep business fields only (exclude system/MI collection vars)
        Map<String, Object> mainFormData = new HashMap<>();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("multiInstance_") && 
                !key.equals("currentItem") &&
                !isSystemVariable(key)) {
                mainFormData.put(key, entry.getValue());
            }
        }
        
        log.debug("Main form data loaded: processInstanceId={}, fieldCount={}", 
            processInstanceId, mainFormData.size());
        
        return mainFormData;
    }
    
    /**
     * Load a sub-table row (Long row id for single-column PK legacy callers).
     */
    public Map<String, Object> loadSubTableRow(String subTableName, Long rowId) {
        String safe = requireSafeIdentifier(subTableName);
        List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, safe);
        if (pkCols.size() != 1) {
            throw new WorkflowValidationException("Use rowKey map for composite primary key sub-tables");
        }
        return loadSubTableRow(subTableName, Map.of(pkCols.get(0), rowId));
    }

    public Map<String, Object> loadSubTableRow(String subTableName, Map<String, Object> rowKey) {
        String safeTable = requireSafeIdentifier(subTableName);
        List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, safeTable);
        if (!SubTableRowKeySupport.isComplete(pkCols, rowKey)) {
            throw new WorkflowValidationException("The associated data row no longer exists");
        }
        String where = SubTableRowKeySupport.buildPkWhereClause(pkCols);
        Object[] args = SubTableRowKeySupport.orderedPkParams(pkCols, rowKey);
        log.debug("Loading sub-table row: subTableName={}, rowKey={}", subTableName, rowKey);

        try {
            String sql = String.format("SELECT * FROM %s WHERE %s", safeTable, where);
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, args);

            log.debug("Sub-table row loaded: subTableName={}", subTableName);
            return row;
        } catch (EmptyResultDataAccessException e) {
            log.warn("Sub-table row not found: subTableName={}, rowKey={}", subTableName, rowKey);
            throw new WorkflowValidationException("The associated data row no longer exists");
        } catch (Exception e) {
            log.error("Failed to load sub-table row: subTableName={}, rowKey={}", subTableName, rowKey, e);
            throw new WorkflowBusinessException(
                "LOAD_SUBTABLE_ROW_FAILED",
                i18nService.getMessage("workflow.load_subtable_row_failed"),
                e
            );
        }
    }

    public boolean subTableExists(String subTableName) {
        if (subTableName == null || subTableName.isBlank()) {
            return false;
        }
        try {
            String resolvedRegclass = jdbcTemplate.queryForObject(
                "SELECT to_regclass(?)::text", String.class, subTableName);
            return resolvedRegclass != null && !resolvedRegclass.isBlank();
        } catch (Exception e) {
            log.warn("Failed to check sub-table existence: subTableName={}", subTableName, e);
            return false;
        }
    }
    
    /**
     * Write sub-task form data to the sub-table with row_version optimistic locking.
     */
    public void writeBackSubTableRow(String taskId, Map<String, Object> formData, 
                                      Long expectedRowVersion) {
        log.info("Writing back sub-table row: taskId={}, expectedRowVersion={}", taskId, expectedRowVersion);
        
        // 1. Resolve sub-table metadata
        ExtendedTaskInfo extInfo = extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(taskId)
            .orElseThrow(() -> new WorkflowValidationException(i18nService.getMessage("workflow.task_not_found")));
        
        Map<String, Object> extProps = parseExtendedProperties(extInfo.getExtendedProperties());

        String subTableName = getStringValue(extProps, "subTableName");
        if (subTableName == null) {
            throw new WorkflowValidationException("Task is missing multi-instance configuration information");
        }
        String safeSubTableName = requireSafeIdentifier(subTableName);
        Map<String, Object> rowKey = resolveRowKeyFromExt(extProps, safeSubTableName);
        List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, safeSubTableName);

        String statusCol = resolveMiNamedColumn(extProps, "miTaskStatusField", "miTaskStatusField", extInfo, "task_status");
        String nodeCol = resolveMiNamedColumn(extProps, "miTaskCurrentNodeField", "miTaskCurrentNodeField", extInfo, "task_current_node");

        String pkWhere = SubTableRowKeySupport.buildPkWhereClause(pkCols);
        String checkSql = String.format(
            "SELECT row_version FROM %s WHERE %s", safeSubTableName, pkWhere);

        Object[] pkArgs = SubTableRowKeySupport.orderedPkParams(pkCols, rowKey);

        Long currentRowVersion;
        try {
            currentRowVersion = jdbcTemplate.queryForObject(checkSql, Long.class, pkArgs);
        } catch (EmptyResultDataAccessException e) {
            log.warn("Sub-table row deleted: subTableName={}, rowKey={}", subTableName, rowKey);
            throw new WorkflowValidationException("The associated data row no longer exists");
        }

        if (currentRowVersion == null) {
            throw new WorkflowValidationException("The associated data row no longer exists");
        }

        if (!currentRowVersion.equals(expectedRowVersion)) {
            log.warn("Optimistic lock conflict: subTableName={}, rowKey={}, expected={}, current={}",
                subTableName, rowKey, expectedRowVersion, currentRowVersion);
            throw new OptimisticLockException("Data has been modified, please refresh and try again");
        }

        // 3. Build UPDATE with optimistic lock
        boolean hasTaskStatus = columnExists(safeSubTableName, statusCol);
        boolean hasTaskCurrentNode = columnExists(safeSubTableName, nodeCol);
        StringBuilder updateSql = new StringBuilder(String.format("UPDATE %s SET ", safeSubTableName));
        List<Object> params = new ArrayList<>();
        Set<String> pkSet = new HashSet<>(pkCols);

        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            String colName = requireSafeIdentifier(entry.getKey());
            if (pkSet.contains(colName) || "row_version".equals(colName)
                    || statusCol.equals(colName)
                    || nodeCol.equals(colName)) {
                continue;
            }
            updateSql.append(colName).append(" = ?, ");
            params.add(entry.getValue());
        }

        if (hasTaskStatus) {
            updateSql.append(statusCol).append(" = 'COMPLETED', ");
        }
        if (hasTaskCurrentNode) {
            updateSql.append(nodeCol).append(" = NULL, ");
        }
        updateSql.append("row_version = row_version + 1 ");
        updateSql.append("WHERE ").append(pkWhere).append(" AND row_version = ?");
        params.addAll(Arrays.asList(pkArgs));
        params.add(expectedRowVersion);

        // 4. Execute update
        log.debug("Executing sub-table write-back: sql={}", updateSql);
        int updated = jdbcTemplate.update(updateSql.toString(), params.toArray());

        if (updated == 0) {
            // Re-check whether row was deleted or version changed
            try {
                Long latestRowVersion = jdbcTemplate.queryForObject(checkSql, Long.class, pkArgs);
                if (latestRowVersion == null) {
                    throw new WorkflowValidationException("The associated data row no longer exists");
                } else {
                    log.warn("Optimistic lock conflict (recheck): subTableName={}, rowKey={}, expected={}, latest={}",
                        subTableName, rowKey, expectedRowVersion, latestRowVersion);
                    throw new OptimisticLockException("Data has been modified, please refresh and try again");
                }
            } catch (EmptyResultDataAccessException e) {
                throw new WorkflowValidationException("The associated data row no longer exists");
            }
        }

        log.info("Sub-table write-back succeeded: taskId={}, subTableName={}, rowKey={}, newVersion={}",
            taskId, subTableName, rowKey, expectedRowVersion + 1);
    }
    
    /**
     * Whether the variable key is a Flowable multi-instance system variable.
     */
    public boolean isSystemVariable(String key) {
        return key.equals("nrOfInstances") ||
               key.equals("nrOfActiveInstances") ||
               key.equals("nrOfCompletedInstances") ||
               key.equals("loopCounter") ||
               key.startsWith("_");
    }

    /**
     * Maps a submitted variable key (field name or designer label/description) to a column on {@code physicalColumns}.
     */
    public String resolveSubTablePhysicalColumnKey(String subTableName, String variableKey, Set<String> physicalColumns) {
        return SubTablePhysicalColumnResolver.resolvePhysicalColumnKey(jdbcTemplate, subTableName, variableKey, physicalColumns);
    }

    // ==================== Helpers ====================
    
    private Map<String, Object> resolveRowKeyFromExt(Map<String, Object> extProps, String safeSubTableName) {
        List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, safeSubTableName);
        Map<String, Object> rowKey = SubTableRowKeySupport.rowKeyFromExtendedProps(extProps, pkCols);
        if (rowKey == null) {
            throw new WorkflowValidationException("Task is missing multi-instance configuration information");
        }
        return rowKey;
    }

    /**
     * Best-effort row key for callers that only hold extended JSON (e.g. WebSocket fan-out).
     */
    public Map<String, Object> tryResolveSubTableRowKey(String subTableName, Map<String, Object> extProps) {
        if (subTableName == null || subTableName.isBlank() || extProps == null) {
            return null;
        }
        try {
            String safe = requireSafeIdentifier(subTableName);
            List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, safe);
            return SubTableRowKeySupport.rowKeyFromExtendedProps(extProps, pkCols);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse extendedProperties JSON on ExtendedTaskInfo.
     */
    private Map<String, Object> parseExtendedProperties(String extendedProperties) {
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(extendedProperties, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse extendedProperties: {}", extendedProperties, e);
            return new HashMap<>();
        }
    }
    
    /**
     * Safe Long lookup from a map.
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Cannot convert value to Long: key={}, value={}", key, value);
            return null;
        }
    }
    
    /**
     * Safe String lookup from a map.
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Resolve MI status/node column from ext props or BPMN subprocess extensions.
     */
    private String resolveMiNamedColumn(Map<String, Object> extProps, String extJsonKey, String bpmnPropertyName,
                                      ExtendedTaskInfo extInfo, String defaultName) {
        String v = getStringValue(extProps, extJsonKey);
        if (v != null && v.trim().matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return v.trim();
        }
        String pd = extInfo.getProcessDefinitionId();
        String tk = extInfo.getTaskDefinitionKey();
        if (pd != null && tk != null && bpmnActionParser != null) {
            String fromBpmn = bpmnActionParser.getMultiInstanceSubProcessExtensionPropertyValue(pd, tk, bpmnPropertyName);
            if (fromBpmn != null && fromBpmn.trim().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                return fromBpmn.trim();
            }
        }
        return defaultName;
    }

    private String requireSafeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new WorkflowValidationException("Invalid sub-table name");
        }
        return identifier;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
            Integer.class,
            tableName,
            columnName
        );
        return count != null && count > 0;
    }
    
    // ==================== Inner types ====================

    /**
     * Optimistic lock conflict
     */
    public static class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }
}
