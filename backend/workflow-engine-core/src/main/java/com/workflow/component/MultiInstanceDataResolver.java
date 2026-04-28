package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 负责多实例子任务的数据加载和回写，实现数据隔离和乐观锁机制。
 * 
 * 核心职责：
 * 1. 加载子任务表单数据（主任务表单数据 + 子表数据行）
 * 2. 实现数据隔离：每个子任务只能访问自己关联的子表数据行
 * 3. 数据回写时使用乐观锁（row_version）防止并发冲突
 * 4. 区分数据行被删除和版本冲突两种错误场景
 * 
 * 数据流程：
 * - 子任务打开时：loadSubTaskFormData() 加载主表单数据（只读）+ 子表数据行（可编辑）
 * - 子任务提交时：writeBackSubTableRow() 回写数据到子表，row_version 递增
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
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 加载子任务表单数据（包含主任务表单数据和子表数据行）
     * 
     * @param taskId 任务 ID
     * @return 子任务表单完整数据
     * @throws WorkflowValidationException 任务不存在或数据行不存在时
     */
    public SubTaskFormData loadSubTaskFormData(String taskId) {
        log.info("加载子任务表单数据: taskId={}", taskId);
        
        // 1. 获取子任务的 ExtendedTaskInfo
        ExtendedTaskInfo extInfo = extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(taskId)
            .orElseThrow(() -> new WorkflowValidationException("任务不存在"));
        
        Map<String, Object> extProps = parseExtendedProperties(extInfo.getExtendedProperties());
        
        Long subTableRowId = getLongValue(extProps, "subTableRowId");
        String subTableName = getStringValue(extProps, "subTableName");
        
        if (subTableRowId == null || subTableName == null) {
            throw new WorkflowValidationException("Task is missing multi-instance configuration information");
        }
        
        // 2. 获取流程实例 ID，加载主表单数据
        String processInstanceId = extInfo.getProcessInstanceId();
        Map<String, Object> mainFormData = loadMainFormData(processInstanceId);
        
        // 3. 加载主表单字段定义
        List<FormField> mainFormFields = getMainFormFields(processInstanceId);
        
        // 4. 加载子表数据行
        Map<String, Object> subTableRowData = loadSubTableRow(subTableName, subTableRowId);
        
        // 5. 加载子表单字段定义
        List<FormField> subFormFields = getSubFormFields(subTableName);
        
        log.info("子任务表单数据加载成功: taskId={}, subTableRowId={}", taskId, subTableRowId);
        
        return SubTaskFormData.builder()
            .taskId(taskId)
            .mainFormData(mainFormData)
            .mainFormFields(mainFormFields)
            .subTableRowData(subTableRowData)
            .subFormFields(subFormFields)
            .rowVersion(getLongValue(subTableRowData, "row_version"))
            .build();
    }
    
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
     * 加载子表数据行
     * 
     * 根据 taskId 查询 ExtendedTaskInfo 获取 subTableRowId，仅加载对应数据行
     * 
     * @param subTableName 子表物理表名
     * @param rowId 子表行 ID
     * @return 子表数据行
     * @throws WorkflowValidationException 数据行不存在时
     */
    public Map<String, Object> loadSubTableRow(String subTableName, Long rowId) {
        log.debug("加载子表数据行: subTableName={}, rowId={}", subTableName, rowId);
        
        try {
            String sql = String.format("SELECT * FROM %s WHERE id = ?", subTableName);
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, rowId);
            
            log.debug("子表数据行加载成功: subTableName={}, rowId={}", subTableName, rowId);
            return row;
        } catch (EmptyResultDataAccessException e) {
            log.warn("子表数据行不存在: subTableName={}, rowId={}", subTableName, rowId);
            throw new WorkflowValidationException("The associated data row no longer exists");
        } catch (Exception e) {
            log.error("加载子表数据行失败: subTableName={}, rowId={}", subTableName, rowId, e);
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
        
        Long subTableRowId = getLongValue(extProps, "subTableRowId");
        String subTableName = getStringValue(extProps, "subTableName");
        
        if (subTableRowId == null || subTableName == null) {
            throw new WorkflowValidationException("Task is missing multi-instance configuration information");
        }
        
        // 2. 验证 row_version（先查询当前版本）
        String checkSql = String.format(
            "SELECT row_version FROM %s WHERE id = ?", subTableName);
        
        Long currentRowVersion;
        try {
            currentRowVersion = jdbcTemplate.queryForObject(checkSql, Long.class, subTableRowId);
        } catch (EmptyResultDataAccessException e) {
            log.warn("数据行已被删除: subTableName={}, rowId={}", subTableName, subTableRowId);
            throw new WorkflowValidationException("The associated data row no longer exists");
        }
        
        if (currentRowVersion == null) {
            throw new WorkflowValidationException("The associated data row no longer exists");
        }
        
        if (!currentRowVersion.equals(expectedRowVersion)) {
            log.warn("乐观锁冲突: subTableName={}, rowId={}, expected={}, current={}", 
                subTableName, subTableRowId, expectedRowVersion, currentRowVersion);
            throw new OptimisticLockException("Data has been modified, please refresh and try again");
        }
        
        // 3. 构建 UPDATE SQL（含乐观锁）
        StringBuilder updateSql = new StringBuilder(String.format("UPDATE %s SET ", subTableName));
        List<Object> params = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            if ("id".equals(entry.getKey()) || "row_version".equals(entry.getKey()) 
                    || "task_status".equals(entry.getKey())) {
                continue;
            }
            updateSql.append(entry.getKey()).append(" = ?, ");
            params.add(entry.getValue());
        }
        
        updateSql.append("task_status = 'COMPLETED', ");
        updateSql.append("row_version = row_version + 1 ");
        updateSql.append("WHERE id = ? AND row_version = ?");
        params.add(subTableRowId);
        params.add(expectedRowVersion);
        
        // 4. 执行更新
        log.debug("执行子表数据回写: sql={}", updateSql);
        int updated = jdbcTemplate.update(updateSql.toString(), params.toArray());
        
        if (updated == 0) {
            // 再次检查是否是 row_version 不一致还是数据行被删除
            try {
                Long latestRowVersion = jdbcTemplate.queryForObject(checkSql, Long.class, subTableRowId);
                if (latestRowVersion == null) {
                    throw new WorkflowValidationException("The associated data row no longer exists");
                } else {
                    log.warn("乐观锁冲突（二次检查）: subTableName={}, rowId={}, expected={}, latest={}", 
                        subTableName, subTableRowId, expectedRowVersion, latestRowVersion);
                    throw new OptimisticLockException("Data has been modified, please refresh and try again");
                }
            } catch (EmptyResultDataAccessException e) {
                throw new WorkflowValidationException("The associated data row no longer exists");
            }
        }
        
        log.info("子表数据回写成功: taskId={}, subTableName={}, rowId={}, newVersion={}", 
            taskId, subTableName, subTableRowId, expectedRowVersion + 1);
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
     * 获取主表单字段定义
     * 
     * 从流程定义或 FormDefinition 中获取主表单字段定义
     * 
     * @param processInstanceId 流程实例 ID
     * @return 主表单字段定义列表
     */
    public List<FormField> getMainFormFields(String processInstanceId) {
        // TODO: 从流程定义或 FormDefinition 中获取主表单字段定义
        // 实现细节待补充
        log.debug("获取主表单字段定义: processInstanceId={}", processInstanceId);
        return new ArrayList<>();
    }
    
    /**
     * 获取子表单字段定义
     * 
     * 从 TableDefinition 和 FieldDefinition 中获取子表字段定义
     * 
     * @param subTableName 子表物理表名
     * @return 子表单字段定义列表
     */
    public List<FormField> getSubFormFields(String subTableName) {
        // TODO: 从 TableDefinition 和 FieldDefinition 中获取子表字段定义
        // 实现细节待补充
        log.debug("获取子表单字段定义: subTableName={}", subTableName);
        return new ArrayList<>();
    }
    
    // ==================== 辅助方法 ====================
    
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
    
    // ==================== 内部类 ====================
    
    /**
     * 子任务表单数据
     */
    @lombok.Data
    @lombok.Builder
    public static class SubTaskFormData {
        private String taskId;
        
        // 主任务表单数据（只读）
        private Map<String, Object> mainFormData;
        
        // 主任务表单字段定义
        private List<FormField> mainFormFields;
        
        // 子任务表单数据（可编辑）
        private Map<String, Object> subTableRowData;
        
        // 子任务表单字段定义
        private List<FormField> subFormFields;
        
        // 乐观锁版本号
        private Long rowVersion;
    }
    
    /**
     * 表单字段定义
     */
    @lombok.Data
    @lombok.Builder
    public static class FormField {
        private String name;
        private String label;
        private String type;
        private Boolean required;
        private Boolean readonly;
    }
    
    /**
     * 乐观锁异常
     */
    public static class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }
}
