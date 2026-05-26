package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.platform.common.jdbc.SubTablePhysicalColumnResolver;
import com.platform.common.jdbc.SubTableRowKeySupport;
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
 * 多实例数据解析器
 *
 * 负责多实例子任务运行时的行键解析、行数据加载/回写与乐观锁。
 *
 * 核心职责：
 * 1. 解析当前 MI 任务关联的子表行键（来自 ExtendedTaskInfo.extendedProperties）
 * 2. 加载主表单变量（{@link #loadMainFormData}）用于上层组件按需读取
 * 3. 行数据回写使用 row_version 乐观锁，区分"被删除"与"版本冲突"
 *
 * Portal 侧 MI 子任务的表单 hydrate 由 {@code tasks/detail.vue} 主路径完成；本组件
 * 只负责行键/行数据层面的协议，不再聚合"子任务表单视图"——之前的 SubTaskFormData
 * 通道与 {@code /tasks/{taskId}/sub-task-form-data} 接口已废弃。
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
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 加载主表单数据（从流程变量中获取）
     *
     * 过滤系统变量和集合变量，只返回主表单相关的业务数据
     *
     * @param processInstanceId 流程实例 ID
     * @return 主表单数据
     */
    public Map<String, Object> loadMainFormData(String processInstanceId) {
        log.debug("加载主表单数据: processInstanceId={}", processInstanceId);
        
        // 从流程变量中获取所有变量
        Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
        
        // 过滤出主表单相关的变量（排除系统变量和集合变量）
        Map<String, Object> mainFormData = new HashMap<>();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("multiInstance_") && 
                !key.equals("currentItem") &&
                !isSystemVariable(key)) {
                mainFormData.put(key, entry.getValue());
            }
        }
        
        log.debug("主表单数据加载完成: processInstanceId={}, fieldCount={}", 
            processInstanceId, mainFormData.size());
        
        return mainFormData;
    }
    
    /**
     * 加载子表数据行（单列主键兼容：历史调用仍传 Long）。
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
        log.debug("加载子表数据行: subTableName={}, rowKey={}", subTableName, rowKey);

        try {
            String sql = String.format("SELECT * FROM %s WHERE %s", safeTable, where);
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, args);

            log.debug("子表数据行加载成功: subTableName={}", subTableName);
            return row;
        } catch (EmptyResultDataAccessException e) {
            log.warn("子表数据行不存在: subTableName={}, rowKey={}", subTableName, rowKey);
            throw new WorkflowValidationException("The associated data row no longer exists");
        } catch (Exception e) {
            log.error("加载子表数据行失败: subTableName={}, rowKey={}", subTableName, rowKey, e);
            throw new WorkflowBusinessException(
                "LOAD_SUBTABLE_ROW_FAILED",
                String.format("加载子表数据行失败: %s", e.getMessage()),
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
            log.warn("检查子表是否存在失败: subTableName={}", subTableName, e);
            return false;
        }
    }
    
    /**
     * 回写子任务表单数据到子表（含乐观锁校验）
     * 
     * 使用 row_version 实现乐观锁：
     * UPDATE ... SET row_version = row_version + 1 WHERE id = ? AND row_version = ?
     * 
     * 影响行数为 0 时区分两种情况：
     * 1. 数据行被删除：抛出 WorkflowValidationException
     * 2. row_version 不一致：抛出 OptimisticLockException
     * 
     * @param taskId 任务 ID
     * @param formData 表单数据
     * @param expectedRowVersion 期望的 row_version
     * @throws OptimisticLockException row_version 不一致时
     * @throws WorkflowValidationException 数据行已删除时
     */
    public void writeBackSubTableRow(String taskId, Map<String, Object> formData, 
                                      Long expectedRowVersion) {
        log.info("回写子表数据: taskId={}, expectedRowVersion={}", taskId, expectedRowVersion);
        
        // 1. 获取子表信息
        ExtendedTaskInfo extInfo = extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(taskId)
            .orElseThrow(() -> new WorkflowValidationException("任务不存在"));
        
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
            log.warn("数据行已被删除: subTableName={}, rowKey={}", subTableName, rowKey);
            throw new WorkflowValidationException("The associated data row no longer exists");
        }

        if (currentRowVersion == null) {
            throw new WorkflowValidationException("The associated data row no longer exists");
        }

        if (!currentRowVersion.equals(expectedRowVersion)) {
            log.warn("乐观锁冲突: subTableName={}, rowKey={}, expected={}, current={}",
                subTableName, rowKey, expectedRowVersion, currentRowVersion);
            throw new OptimisticLockException("Data has been modified, please refresh and try again");
        }

        // 3. 构建 UPDATE SQL（含乐观锁）
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

        // 4. 执行更新
        log.debug("执行子表数据回写: sql={}", updateSql);
        int updated = jdbcTemplate.update(updateSql.toString(), params.toArray());

        if (updated == 0) {
            // 再次检查是否是 row_version 不一致还是数据行被删除
            try {
                Long latestRowVersion = jdbcTemplate.queryForObject(checkSql, Long.class, pkArgs);
                if (latestRowVersion == null) {
                    throw new WorkflowValidationException("The associated data row no longer exists");
                } else {
                    log.warn("乐观锁冲突（二次检查）: subTableName={}, rowKey={}, expected={}, latest={}",
                        subTableName, rowKey, expectedRowVersion, latestRowVersion);
                    throw new OptimisticLockException("Data has been modified, please refresh and try again");
                }
            } catch (EmptyResultDataAccessException e) {
                throw new WorkflowValidationException("The associated data row no longer exists");
            }
        }

        log.info("回写子表数据成功: taskId={}, subTableName={}, rowKey={}, newVersion={}",
            taskId, subTableName, rowKey, expectedRowVersion + 1);
    }
    
    /**
     * 判断是否为系统变量
     * 
     * Flowable 多实例系统变量：
     * - nrOfInstances: 总实例数
     * - nrOfActiveInstances: 活跃实例数
     * - nrOfCompletedInstances: 已完成实例数
     * - loopCounter: 循环计数器
     * - 以下划线开头的变量
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

    // ==================== 辅助方法 ====================
    
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
     * 解析 ExtendedTaskInfo 的 extendedProperties JSON 字符串
     */
    private Map<String, Object> parseExtendedProperties(String extendedProperties) {
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(extendedProperties, Map.class);
        } catch (JsonProcessingException e) {
            log.error("解析 extendedProperties 失败: {}", extendedProperties, e);
            return new HashMap<>();
        }
    }
    
    /**
     * 从 Map 中安全获取 Long 值
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
            log.warn("无法将值转换为 Long: key={}, value={}", key, value);
            return null;
        }
    }
    
    /**
     * 从 Map 中安全获取 String 值
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * SubProcess 扩展 {@code miTaskStatusField} / {@code miTaskCurrentNodeField}，或 ExtendedTaskInfo JSON 中的同名键。
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
    
    // ==================== 内部类 ====================

    /**
     * 乐观锁异常
     */
    public static class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }
}
