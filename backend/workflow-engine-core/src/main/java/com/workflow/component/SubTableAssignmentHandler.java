package com.workflow.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
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
 * Sub-table row assignee handler.
 * 
 * Handles manual assignment of sub-table row assignees in multi-instance sub-process pre-tasks.
 * This is the first step of the multi-instance task distribution workflow: the pre-task handler
 * uses the Assign button to specify an assignee for each sub-table row.
 * 
 * Core responsibilities:
 * 1. Verify the task exists and the current user has permission
 * 2. Retrieve sub-table configuration (subTableName, assigneeField) from task extended properties or process definition
 * 3. Verify rowId belongs to the main table record associated with the current task
 * 4. Verify the assigneeId user exists and is not disabled
 * 5. Update the sub-table's assigneeField column
 * 6. Return the assignment result
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

    @Autowired
    private I18nService i18nService;
    
    /**
     * Assign sub-table row assignee.
     * 
     * @param taskId main task ID
     * @param rowId sub-table row ID
     * @param assigneeId assignee user ID
     * @return assignment result
     * @throws WorkflowValidationException on validation failure
     * @throws WorkflowBusinessException on business exception
     */
    public AssignmentResponse assign(String taskId, Long rowId, String assigneeId) {
        return assign(taskId, rowId, null, assigneeId);
    }

    public AssignmentResponse assign(String taskId, Long rowId, Map<String, Object> rowKey, String assigneeId) {
        log.info("Starting sub-table row assignee assignment: taskId={}, rowId={}, assigneeId={}", taskId, rowId, assigneeId);

        // 1. Verify task exists
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new WorkflowValidationException("Task not found: " + taskId);
        }

        // 2. Retrieve sub-table configuration from task extended properties or process definition
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

        log.debug("Retrieved sub-table config: subTableName={}, assigneeField={}, foreignKey={}",
            config.getSubTableName(), config.getAssigneeField(), config.getForeignKey());

        // 3. Verify the row belongs to the main table record associated with the current task
        if (!verifyRowBelongsToTask(config, resolvedRowKey, task)) {
            throw new WorkflowValidationException(
                String.format("Sub-table row %s does not belong to the main table record associated with current task",
                        resolvedRowKey)
            );
        }

        // 4. Verify assigneeId user exists and is not disabled
        validateUser(assigneeId);

        // 5. Update sub-table assigneeField
        updateSubTableAssignee(config.getSubTableName(), config.getAssigneeField(), resolvedRowKey, assigneeId);

        // 6. Get user display name and return assignment result
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

        log.info("Sub-table row assignee assignment successful: taskId={}, rowKey={}, assigneeId={}, assigneeName={}",
            taskId, resolvedRowKey, assigneeId, assigneeName);

        // 7. Publish WebSocket update notification
        publishWebSocketUpdate(taskId, resolvedRowKey, assigneeId);

        return response;
    }
    
    /**
     * Retrieve sub-table configuration from task extended properties or process definition
     */
    private SubTableConfig getSubTableConfig(Task task) {
        // Try to retrieve from ExtendedTaskInfo.extendedProperties
        Optional<ExtendedTaskInfo> extInfoOpt = extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(task.getId());
        if (extInfoOpt.isPresent() && extInfoOpt.get().getExtendedProperties() != null) {
            try {
                Map<String, Object> extProps = objectMapper.readValue(
                    extInfoOpt.get().getExtendedProperties(), 
                    new TypeReference<Map<String, Object>>() {}
                );
                
                // Check if sub-table config is present
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
                log.warn("Failed to parse ExtendedTaskInfo.extendedProperties: taskId={}", task.getId(), e);
            }
        }
        
        // Try to retrieve from process variables (as fallback)
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
            log.warn("Failed to retrieve sub-table config from process variables: taskId={}", task.getId(), e);
        }

        // 3) Multi-instance pre-tasks (e.g., "Assign Participants") often declare subTableName/assigneeField on BPMN, but process variables are not injected — read from deployed BPMN XML
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
            log.warn("Failed to read sub-table config from BPMN extensions: taskId={}", task.getId(), e);
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
     * Main table primary key: prefer {@code mainRecordId}, then fallback to {@code meeting_id} for meeting demo workflows.
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
     * Verify that the sub-table row belongs to the main table record associated with the current task
     */
    private boolean verifyRowBelongsToTask(SubTableConfig config, Map<String, Object> rowKey, Task task) {
        if (config.getForeignKey() == null || config.getMainRecordId() == null) {
            // If no foreign key info configured, skip verification (assume configuration is correct)
            log.warn("Sub-table config missing foreign key info, skipping ownership verification: taskId={}, rowKey={}", task.getId(), rowKey);
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
            log.error("Failed to verify sub-table row ownership: taskId={}, rowKey={}", task.getId(), rowKey, e);
            throw new WorkflowBusinessException(
                "SUBTABLE_VERIFICATION_FAILED",
                i18nService.getMessage("workflow.subtable.verification_error") + ": " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Verify user exists and is not disabled
     */
    private void validateUser(String assigneeId) {
        try {
            Map<String, Object> userInfo = adminCenterClient.getUserInfo(assigneeId);
            
            if (userInfo == null) {
                throw new WorkflowValidationException(
                    String.format(i18nService.getMessage("workflow.subtable.user_not_found"), assigneeId)
                );
            }
            
            // Check if user is disabled
            Boolean enabled = (Boolean) userInfo.get("enabled");
            if (enabled != null && !enabled) {
                throw new WorkflowValidationException(
                    String.format(i18nService.getMessage("workflow.subtable.user_disabled"), assigneeId)
                );
            }
            
            log.debug("User verification passed: assigneeId={}", assigneeId);
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify user: assigneeId={}", assigneeId, e);
            throw new WorkflowBusinessException(
                "USER_VALIDATION_FAILED",
                i18nService.getMessage("workflow.subtable.user_validation_error") + ": " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Update the sub-tables assigneeField column
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
                    String.format("Sub-table row does not exist or has been deleted: rowKey=%s", rowKey)
                );
            }

            log.debug("Sub-table assigneeField updated successfully: subTableName={}, rowKey={}, assigneeId={}",
                subTableName, rowKey, assigneeId);
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update sub-table assigneeField: subTableName={}, rowKey={}", subTableName, rowKey, e);
            throw new WorkflowBusinessException(
                "SUBTABLE_UPDATE_FAILED",
                i18nService.getMessage("workflow.subtable.update_error") + ": " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Get user display name
     */
    private String getUserName(String assigneeId) {
        try {
            Map<String, Object> userInfo = adminCenterClient.getUserInfo(assigneeId);
            if (userInfo != null) {
                Object name = userInfo.get("name");
                if (name != null) {
                    return name.toString();
                }
                // If no name field, try username
                Object username = userInfo.get("username");
                if (username != null) {
                    return username.toString();
                }
            }
        } catch (Exception e) {
            // FALLBACK(external): 展示名降级为 userId（含 AdminCenterUnavailableException）。
            log.warn("Failed to get user display name, using user ID: assigneeId={}", assigneeId, e);
        }

        return assigneeId; // Fallback: return user ID
    }
    
    /**
     * Publish WebSocket update notification
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
                log.debug("WebSocket update notification published: taskId={}, rowKey={}", taskId, rowKey);
            } catch (Exception e) {
                // WebSocket publish failure should not affect the main flow
                log.warn("Failed to publish WebSocket update notification: taskId={}, rowKey={}", taskId, rowKey, e);
            }
        }
    }
    
    /**
     * Sub-table configuration
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
     * Assignment response
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
