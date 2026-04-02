package com.workflow.component;

import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 子表数据注入器
 * 
 * 负责在多实例子流程前置任务完成时，从子表查询数据并注入为流程变量（Collection Variable）。
 * 这是多实例任务分发流程的第二步：将子表数据转换为 Flowable 多实例所需的集合变量。
 * 
 * 核心职责：
 * 1. 从子表查询数据行（含已分配的处理人）
 * 2. 验证数据行数 > 0，否则抛出异常
 * 3. 验证所有行的 assigneeField 非空，否则抛出异常并指明行号
 * 4. 构建 List<Map<String, Object>> 集合变量（每个元素含 rowId、assigneeId、rowVersion）
 * 5. 通过 runtimeService.setVariable() 注入集合变量
 * 
 * 集合变量命名格式：multiInstance_{subTableName}_collection
 * 
 * 集合变量结构示例：
 * [
 *   { "rowId": 101, "assigneeId": "user-001", "rowVersion": 1 },
 *   { "rowId": 102, "assigneeId": "user-002", "rowVersion": 1 },
 *   { "rowId": 103, "assigneeId": "user-003", "rowVersion": 2 }
 * ]
 */
@Slf4j
@Component
public class SubTableDataInjector {
    
    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * 从子表查询数据并注入为流程变量
     * 
     * @param processInstanceId 流程实例 ID
     * @param subTableName 子表物理表名
     * @param foreignKeyField 外键字段名
     * @param mainRecordId 主表记录 ID
     * @param assigneeField 处理人字段名
     * @param collectionVariableName 集合变量名（可选，默认为 multiInstance_{subTableName}_collection）
     * @throws WorkflowValidationException 数据为空或处理人缺失时
     * @throws WorkflowBusinessException 数据库查询失败时
     */
    public void injectSubTableData(
            String processInstanceId,
            String subTableName,
            String foreignKeyField,
            Long mainRecordId,
            String assigneeField,
            String collectionVariableName) {
        
        log.info("开始注入子表数据: processInstanceId={}, subTableName={}, mainRecordId={}", 
            processInstanceId, subTableName, mainRecordId);
        
        // 1. 查询子表数据
        List<Map<String, Object>> subTableRows = querySubTableData(
            subTableName, foreignKeyField, mainRecordId, assigneeField);
        
        // 2. 验证数据行数 > 0
        if (subTableRows.isEmpty()) {
            throw new WorkflowValidationException(
                "多实例数据源为空，至少需要一条子表数据"
            );
        }
        
        log.debug("查询到 {} 条子表数据行", subTableRows.size());
        
        // 3. 验证所有行的 assigneeField 非空，并构建集合变量
        List<Map<String, Object>> collectionVariable = buildCollectionVariable(
            subTableRows, assigneeField);
        
        // 4. 注入集合变量
        String variableName = collectionVariableName != null ? 
            collectionVariableName : 
            String.format("multiInstance_%s_collection", subTableName);
        
        runtimeService.setVariable(processInstanceId, variableName, collectionVariable);
        
        log.info("子表数据注入成功: processInstanceId={}, variableName={}, rowCount={}", 
            processInstanceId, variableName, collectionVariable.size());
    }
    
    /**
     * 查询子表数据
     */
    private List<Map<String, Object>> querySubTableData(
            String subTableName,
            String foreignKeyField,
            Long mainRecordId,
            String assigneeField) {
        
        try {
            String sql = String.format(
                "SELECT id, %s, row_version FROM %s WHERE %s = ?",
                assigneeField,
                subTableName,
                foreignKeyField
            );
            
            log.debug("执行子表查询: sql={}, mainRecordId={}", sql, mainRecordId);
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, mainRecordId);
            
            return rows;
        } catch (Exception e) {
            log.error("查询子表数据失败: subTableName={}, mainRecordId={}", 
                subTableName, mainRecordId, e);
            
            // 检查是否是表不存在的错误
            if (e.getMessage() != null && 
                (e.getMessage().contains("does not exist") || 
                 e.getMessage().contains("doesn't exist") ||
                 e.getMessage().contains("relation") && e.getMessage().contains("does not exist"))) {
                throw new WorkflowBusinessException(
                    "SUBTABLE_NOT_FOUND",
                    String.format("关联的子表 %s 不存在", subTableName),
                    e
                );
            }
            
            throw new WorkflowBusinessException(
                "SUBTABLE_QUERY_FAILED",
                String.format("查询子表数据时发生错误: %s", e.getMessage()),
                e
            );
        }
    }
    
    /**
     * 构建集合变量并验证 assigneeField 非空
     */
    private List<Map<String, Object>> buildCollectionVariable(
            List<Map<String, Object>> subTableRows,
            String assigneeField) {
        
        List<Map<String, Object>> collectionVariable = new ArrayList<>();
        List<Integer> emptyAssigneeRows = new ArrayList<>();
        
        for (int i = 0; i < subTableRows.size(); i++) {
            Map<String, Object> row = subTableRows.get(i);
            
            // 获取字段值
            Object idObj = row.get("id");
            Object assigneeObj = row.get(assigneeField);
            Object rowVersionObj = row.get("row_version");
            
            // 验证 assigneeField 非空
            if (assigneeObj == null || assigneeObj.toString().trim().isEmpty()) {
                emptyAssigneeRows.add(i + 1); // 行号从 1 开始
                continue;
            }
            
            // 构建集合变量元素
            Map<String, Object> element = new HashMap<>();
            element.put("rowId", idObj != null ? ((Number) idObj).longValue() : null);
            element.put("assigneeId", assigneeObj.toString());
            element.put("rowVersion", rowVersionObj != null ? ((Number) rowVersionObj).longValue() : 1L);
            
            collectionVariable.add(element);
            
            log.debug("构建集合变量元素: rowId={}, assigneeId={}, rowVersion={}", 
                element.get("rowId"), element.get("assigneeId"), element.get("rowVersion"));
        }
        
        // 如果有 assigneeField 为空的行，抛出异常
        if (!emptyAssigneeRows.isEmpty()) {
            String rowNumbers = String.join(", ", 
                emptyAssigneeRows.stream().map(String::valueOf).toArray(String[]::new));
            throw new WorkflowValidationException(
                String.format("第 %s 行缺少处理人（%s 字段为空）", rowNumbers, assigneeField)
            );
        }
        
        return collectionVariable;
    }
}
