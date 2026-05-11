package com.workflow.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.workflow.client.AdminCenterClient;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.messaging.SubTableUpdatePublisher;
import com.workflow.repository.ExtendedTaskInfoRepository;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 子表行处理人分配处理器
 * 
 * 负责在多实例子流程前置任务中，手动分配子表行的处理人。
 * 这是多实例任务分发流程的第一步：前置任务处理人通过 Assign 按钮为每个子表行指定处理人。
 * 
 * 核心职责：
 * 1. 验证任务存在且当前用户有权限
 * 2. 从任务扩展属性或流程定义中获取子表配置（subTableName、assigneeField）
 * 3. 验证 rowId 属于当前任务关联的主表记录
 * 4. 验证 assigneeId 对应的用户存在且未禁用
 * 5. 更新子表的 assigneeField 字段
 * 6. 返回分配结果
 */
@Slf4j
@Component
public class SubTableAssignmentHandler {
    
    @Autowired
    private TaskService taskService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    
    @Autowired
    private AdminCenterClient adminCenterClient;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private SubTableUpdatePublisher updatePublisher;

    @Autowired
    private BpmnActionParser bpmnActionParser;
    
    /**
     * 分配子表行处理人
     * 
     * @param taskId 主任务 ID
     * @param rowId 子表行 ID
     * @param assigneeId 处理人用户 ID
     * @return 分配结果
     * @throws WorkflowValidationException 验证失败时
     * @throws WorkflowBusinessException 业务异常时
     */
    public AssignmentResponse assign(String taskId, Long rowId, String assigneeId) {
        return assign(taskId, rowId, null, assigneeId);
    }

    public AssignmentResponse assign(String taskId, Long rowId, Map<String, Object> rowKey, String assigneeId) {
        log.info("开始分配子表行处理人: taskId={}, rowId={}, assigneeId={}", taskId, rowId, assigneeId);

        // 1. 验证任务存在
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new WorkflowValidationException("Task not found: " + taskId);
        }

        // 2. 从任务扩展属性或流程定义中获取子表配置
        SubTableConfig config = getSubTableConfig(task);
        if (config == null) {
            throw new WorkflowBusinessException(
                "SUBTABLE_CONFIG_NOT_FOUND",
                "Task is not configured with sub-table information, cannot assign handler"
            );
        }

        List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate,
                requireSafeIdentifier(config.getSubTableName()));
        Map<String, Object> resolvedRowKey;
        try {
            resolvedRowKey = SubTableRowKeySupport.resolveRowKeyForAssign(rowId, rowKey, pkCols);
        } catch (IllegalArgumentException e) {
            throw new WorkflowValidationException(e.getMessage());
        }
        if (resolvedRowKey == null) {
            throw new WorkflowValidationException("Could not resolve sub-table row primary key");
        }

        log.debug("获取到子表配置: subTableName={}, assigneeField={}, foreignKey={}",
            config.getSubTableName(), config.getAssigneeField(), config.getForeignKey());

        // 3. 验证 row 属于当前任务关联的主表记录
        if (!verifyRowBelongsToTask(config, resolvedRowKey, task)) {
            throw new WorkflowValidationException(
                String.format("Sub-table row %s does not belong to the main table record associated with current task",
                        resolvedRowKey)
            );
        }

        // 4. 验证 assigneeId 对应的用户存在且未禁用
        validateUser(assigneeId);

        // 5. 更新子表 assigneeField
        updateSubTableAssignee(config.getSubTableName(), config.getAssigneeField(), resolvedRowKey, assigneeId);

        // 6. 获取用户名称并返回分配结果
        String assigneeName = getUserName(assigneeId);

        Long legacyRowId = pkCols.size() == 1 && resolvedRowKey.get(pkCols.get(0)) instanceof Number
                ? ((Number) resolvedRowKey.get(pkCols.get(0))).longValue()
                : rowId;

        AssignmentResponse response = AssignmentResponse.builder()
            .success(true)
            .rowId(legacyRowId)
            .assigneeId(assigneeId)
            .assigneeName(assigneeName)
            .build();

        log.info("子表行处理人分配成功: taskId={}, rowKey={}, assigneeId={}, assigneeName={}",
            taskId, resolvedRowKey, assigneeId, assigneeName);

        // 7. 发布 WebSocket 更新通知
        publishWebSocketUpdate(taskId, resolvedRowKey, assigneeId);

        return response;
    }
    
    /**
     * 从任务扩展属性或流程定义中获取子表配置
     */
    private SubTableConfig getSubTableConfig(Task task) {
        // 尝试从 ExtendedTaskInfo 的 extendedProperties 中获取
        Optional<ExtendedTaskInfo> extInfoOpt = extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(task.getId());
        if (extInfoOpt.isPresent() && extInfoOpt.get().getExtendedProperties() != null) {
            try {
                Map<String, Object> extProps = objectMapper.readValue(
                    extInfoOpt.get().getExtendedProperties(), 
                    new TypeReference<Map<String, Object>>() {}
                );
                
                // 检查是否包含子表配置
                if (extProps.containsKey("subTableName") && extProps.containsKey("assigneeField")) {
                    Map<String, Object> variables = safeTaskVariables(task.getId());
                    Long mainFromExt = toLong(extProps.get("mainRecordId"));
                    if (mainFromExt == null) {
                        mainFromExt = resolveMainRecordId(variables);
                    }
                    return SubTableConfig.builder()
                        .subTableName((String) extProps.get("subTableName"))
                        .assigneeField((String) extProps.get("assigneeField"))
                        .foreignKey((String) extProps.get("foreignKey"))
                        .mainRecordId(mainFromExt)
                        .build();
                }
            } catch (Exception e) {
                log.warn("解析 ExtendedTaskInfo.extendedProperties 失败: taskId={}", task.getId(), e);
            }
        }
        
        // 尝试从流程变量中获取（作为备选方案）
        try {
            Map<String, Object> variables = taskService.getVariables(task.getId());
            if (variables.containsKey("subTableName") && variables.containsKey("assigneeField")) {
                return SubTableConfig.builder()
                    .subTableName((String) variables.get("subTableName"))
                    .assigneeField((String) variables.get("assigneeField"))
                    .foreignKey((String) variables.get("foreignKey"))
                    .mainRecordId(resolveMainRecordId(variables))
                    .build();
            }
        } catch (Exception e) {
            log.warn("从流程变量获取子表配置失败: taskId={}", task.getId(), e);
        }

        // 3) 多实例前置任务（如「分配参与人」）常在 BPMN 上声明 subTableName/assigneeField，但流程变量未注入 — 从已部署 BPMN XML 读取
        try {
            String pdId = task.getProcessDefinitionId();
            String defKey = task.getTaskDefinitionKey();
            if (pdId != null && !pdId.isBlank() && defKey != null && !defKey.isBlank()) {
                String subTableName = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "subTableName");
                String assigneeField = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "assigneeField");
                if (subTableName != null && !subTableName.isBlank() && assigneeField != null && !assigneeField.isBlank()) {
                    Map<String, Object> variables = safeTaskVariables(task.getId());
                    String foreignKey = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "foreignKey");
                    return SubTableConfig.builder()
                            .subTableName(subTableName.trim())
                            .assigneeField(assigneeField.trim())
                            .foreignKey(foreignKey != null && !foreignKey.isBlank() ? foreignKey.trim() : null)
                            .mainRecordId(resolveMainRecordId(variables))
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("从 BPMN 扩展读取子表配置失败: taskId={}", task.getId(), e);
        }

        return null;
    }

    private Map<String, Object> safeTaskVariables(String taskId) {
        try {
            Map<String, Object> v = taskService.getVariables(taskId);
            return v != null ? v : new HashMap<>();
        } catch (Exception e) {
            log.debug("getVariables failed for taskId={}: {}", taskId, e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 主表主键：优先 {@code mainRecordId}，其次会议演示流程的 {@code meeting_id}。
     */
    private static Long resolveMainRecordId(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        Object mr = variables.get("mainRecordId");
        if (mr == null) {
            mr = variables.get("meeting_id");
        }
        return toLong(mr);
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private String requireSafeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return identifier;
    }

    /**
     * 验证子表行是否属于当前任务关联的主表记录
     */
    private boolean verifyRowBelongsToTask(SubTableConfig config, Map<String, Object> rowKey, Task task) {
        if (config.getForeignKey() == null || config.getMainRecordId() == null) {
            // 如果没有配置外键信息，跳过验证（假设配置正确）
            log.warn("子表配置缺少外键信息，跳过归属验证: taskId={}, rowKey={}", task.getId(), rowKey);
            return true;
        }

        try {
            String subTable = requireSafeIdentifier(config.getSubTableName());
            List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, subTable);
            String fk = requireSafeIdentifier(config.getForeignKey());
            String pkWhere = SubTableRowKeySupport.buildPkWhereClause(pkCols);
            String sql = String.format(
                "SELECT COUNT(*) FROM %s WHERE %s AND %s = ?",
                subTable,
                pkWhere,
                fk
            );
            List<Object> params = new ArrayList<>(Arrays.asList(SubTableRowKeySupport.orderedPkParams(pkCols, rowKey)));
            params.add(config.getMainRecordId());

            Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                params.toArray()
            );

            return count != null && count > 0;
        } catch (Exception e) {
            log.error("验证子表行归属失败: taskId={}, rowKey={}", task.getId(), rowKey, e);
            throw new WorkflowBusinessException(
                "SUBTABLE_VERIFICATION_FAILED",
                "验证子表行归属时发生错误: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * 验证用户存在且未禁用
     */
    private void validateUser(String assigneeId) {
        try {
            Map<String, Object> userInfo = adminCenterClient.getUserInfo(assigneeId);
            
            if (userInfo == null) {
                throw new WorkflowValidationException(
                    String.format("用户不存在: %s", assigneeId)
                );
            }
            
            // 检查用户是否被禁用
            Boolean enabled = (Boolean) userInfo.get("enabled");
            if (enabled != null && !enabled) {
                throw new WorkflowValidationException(
                    String.format("用户已被禁用: %s", assigneeId)
                );
            }
            
            log.debug("用户验证通过: assigneeId={}", assigneeId);
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("验证用户失败: assigneeId={}", assigneeId, e);
            throw new WorkflowBusinessException(
                "USER_VALIDATION_FAILED",
                "验证用户时发生错误: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * 更新子表的 assigneeField 字段
     */
    private void updateSubTableAssignee(String subTableName, String assigneeField,
                                        Map<String, Object> rowKey, String assigneeId) {
        try {
            String table = requireSafeIdentifier(subTableName);
            String field = requireSafeIdentifier(assigneeField);
            List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, table);
            String pkWhere = SubTableRowKeySupport.buildPkWhereClause(pkCols);
            String sql = String.format(
                "UPDATE %s SET %s = ? WHERE %s",
                table,
                field,
                pkWhere
            );
            List<Object> params = new ArrayList<>();
            params.add(assigneeId);
            params.addAll(Arrays.asList(SubTableRowKeySupport.orderedPkParams(pkCols, rowKey)));

            int updated = jdbcTemplate.update(sql, params.toArray());

            if (updated == 0) {
                throw new WorkflowValidationException(
                    String.format("子表行不存在或已被删除: rowKey=%s", rowKey)
                );
            }

            log.debug("子表 assigneeField 更新成功: subTableName={}, rowKey={}, assigneeId={}",
                subTableName, rowKey, assigneeId);
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新子表 assigneeField 失败: subTableName={}, rowKey={}", subTableName, rowKey, e);
            throw new WorkflowBusinessException(
                "SUBTABLE_UPDATE_FAILED",
                "更新子表处理人字段时发生错误: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * 获取用户名称
     */
    private String getUserName(String assigneeId) {
        try {
            Map<String, Object> userInfo = adminCenterClient.getUserInfo(assigneeId);
            if (userInfo != null) {
                Object name = userInfo.get("name");
                if (name != null) {
                    return name.toString();
                }
                // 如果没有 name 字段，尝试 username
                Object username = userInfo.get("username");
                if (username != null) {
                    return username.toString();
                }
            }
        } catch (Exception e) {
            log.warn("获取用户名称失败，使用用户ID: assigneeId={}", assigneeId, e);
        }
        
        return assigneeId; // 降级：返回用户ID
    }
    
    /**
     * 发布 WebSocket 更新通知
     */
    private void publishWebSocketUpdate(String taskId, Map<String, Object> rowKey, String assigneeId) {
        if (updatePublisher != null) {
            try {
                Long rowId = null;
                if (rowKey != null && rowKey.size() == 1) {
                    Object v = rowKey.values().iterator().next();
                    if (v instanceof Number) {
                        rowId = ((Number) v).longValue();
                    }
                }
                updatePublisher.publishUpdate(taskId, rowId, rowKey, assigneeId, null);
                log.debug("WebSocket 更新通知已发布: taskId={}, rowKey={}", taskId, rowKey);
            } catch (Exception e) {
                // WebSocket 发布失败不应影响主流程
                log.warn("发布 WebSocket 更新通知失败: taskId={}, rowKey={}", taskId, rowKey, e);
            }
        }
    }
    
    /**
     * 子表配置
     */
    @Data
    @Builder
    private static class SubTableConfig {
        private String subTableName;
        private String assigneeField;
        private String foreignKey;
        private Long mainRecordId;
    }
    
    /**
     * 分配响应
     */
    @Data
    @Builder
    public static class AssignmentResponse {
        private boolean success;
        private Long rowId;
        private String assigneeId;
        private String assigneeName;
    }
}
