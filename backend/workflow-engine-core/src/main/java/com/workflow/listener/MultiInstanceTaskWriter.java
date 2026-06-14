package com.workflow.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.workflow.component.BpmnActionParser;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.repository.ExtendedTaskInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.workflow.listener.AssigneeUserIdNormalizer.normalizeFlowableUserIdValue;
import static com.workflow.listener.UserTaskExtensionPropertyReader.getExtensionProperty;

/**
 * Writes multi-instance (MI) sub-task metadata: {@code wf_extended_task_info} rows and sub-table task-progress
 * columns, plus the ELEMENT_VARIABLE assignment path. Extracted verbatim from {@link TaskAssignmentListener}.
 * <p>
 * All Flowable services and repositories are read from the owning {@link TaskAssignmentListener} so that
 * unit tests injecting mocks into the listener's fields (via reflection, no Spring context) continue to work
 * unchanged. This collaborator therefore holds no injected state of its own.
 */
@Slf4j
@Component
class MultiInstanceTaskWriter {

    private static final Pattern SAFE_SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * Flowable often creates MI inner userTasks with assignee already set (BPMN expression).
     * The early return in {@code TaskAssignmentListener.handleTaskCreated} previously skipped ExtendedTaskInfo
     * creation, so multi-instance status / initiator sub-table progress only saw completed predecessor tasks.
     */
    void ensureMultiInstanceExtendedTaskForPreassignedTask(TaskAssignmentListener owner, TaskEntity task,
            String taskId, String processInstanceId, String processDefinitionId, String taskDefinitionKey,
            String assigneeOverride) {
        RuntimeService runtimeService = owner.runtimeService();
        RepositoryService repositoryService = owner.repositoryService();
        BpmnActionParser bpmnActionParser = owner.bpmnActionParser();
        JdbcTemplate jdbcTemplate = owner.jdbcTemplate();
        ExtendedTaskInfoRepository extendedTaskInfoRepository = owner.extendedTaskInfoRepository();
        ObjectMapper objectMapper = owner.objectMapper();

        if (taskDefinitionKey == null || processDefinitionId == null) {
            return;
        }
        String miScopeTable = bpmnActionParser.getMultiInstanceSubProcessSubTableName(
                processDefinitionId, taskDefinitionKey);
        if (miScopeTable == null || miScopeTable.isBlank()) {
            return;
        }
        String executionId = task.getExecutionId();
        if (executionId == null) {
            return;
        }
        Object currentItemObj = runtimeService.getVariable(executionId, "currentItem");
        if (currentItemObj == null) {
            currentItemObj = runtimeService.getVariable(executionId, "_currentItem");
        }
        if (!(currentItemObj instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> currentItem = (Map<String, Object>) currentItemObj;

        String subTableId = null;
        String subTableName = null;
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        if (bpmnModel != null) {
            FlowElement flowElement = bpmnModel.getFlowElement(taskDefinitionKey);
            if (flowElement instanceof UserTask userTask) {
                subTableId = getExtensionProperty(userTask, "subTableId");
                subTableName = getExtensionProperty(userTask, "subTableName");
            }
        }
        subTableId = firstNonBlank(subTableId,
                bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey, "subTableId"));
        subTableName = firstNonBlank(subTableName,
                bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                        "subTableName"));
        subTableName = firstNonBlank(subTableName,
                bpmnActionParser.getMultiInstanceSubProcessSubTableName(processDefinitionId, taskDefinitionKey));
        if (subTableName == null || subTableName.isBlank()) {
            subTableName = miScopeTable;
        }

        List<String> pkColsPre;
        Map<String, Object> rowKeyPre;
        try {
            pkColsPre = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate,
                    requireSafeIdentifier(subTableName));
            rowKeyPre = SubTableRowKeySupport.rowKeyFromCurrentItem(currentItem, pkColsPre);
        } catch (Exception e) {
            log.debug("ensureMI preassigned: skip task {}, could not resolve row key: {}", taskId, e.getMessage());
            return;
        }
        if (rowKeyPre == null) {
            return;
        }
        final List<String> pkCols = pkColsPre;
        final Map<String, Object> rowKey = rowKeyPre;
        Long subTableRowVersion = extractLong(currentItem.get("rowVersion"));

        String assigneeId = assigneeOverride != null && !assigneeOverride.isBlank()
                ? normalizeFlowableUserIdValue(assigneeOverride.trim())
                : normalizeFlowableUserIdValue(task.getAssignee());
        if (assigneeId == null || assigneeId.isBlank()) {
            return;
        }

        String[] progressCols = resolveMiProgressColumnNames(owner, processDefinitionId, taskDefinitionKey);
        Map<String, Object> extendedProps = new HashMap<>();
        extendedProps.put("multiInstance", true);
        extendedProps.put("subTableRowKey", rowKey);
        if (pkCols.size() == 1 && rowKey.get(pkCols.get(0)) instanceof Number) {
            extendedProps.put("subTableRowId", ((Number) rowKey.get(pkCols.get(0))).longValue());
        }
        if (subTableRowVersion != null) {
            extendedProps.put("subTableRowVersion", subTableRowVersion);
        }
        if (subTableId != null) {
            extendedProps.put("subTableId", subTableId);
        }
        extendedProps.put("subTableName", subTableName);
        extendedProps.put("miTaskStatusField", progressCols[0]);
        extendedProps.put("miTaskCurrentNodeField", progressCols[1]);

        Optional<ExtendedTaskInfo> existingOpt = extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(taskId);
        Map<String, Object> merged = new HashMap<>();
        if (existingOpt.isPresent() && existingOpt.get().getExtendedProperties() != null
                && !existingOpt.get().getExtendedProperties().isBlank()) {
            try {
                Map<String, Object> cur = objectMapper.readValue(
                        existingOpt.get().getExtendedProperties(),
                        new TypeReference<Map<String, Object>>() {});
                if (cur != null) {
                    merged.putAll(cur);
                }
            } catch (Exception e) {
                log.debug("ensureMI preassigned: reset extended JSON for task {}: {}", taskId, e.getMessage());
            }
        }
        merged.putAll(extendedProps);

        String extendedPropertiesJson;
        try {
            extendedPropertiesJson = objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            log.error("Failed to serialize extendedProperties for preassigned MI task {}: {}", taskId, e.getMessage());
            return;
        }

        if (existingOpt.isPresent()) {
            ExtendedTaskInfo ext = existingOpt.get();
            ext.setExtendedProperties(extendedPropertiesJson);
            ext.setTaskName(task.getName());
            ext.setTaskDefinitionKey(taskDefinitionKey);
            ext.setProcessDefinitionId(processDefinitionId);
            if (!"COMPLETED".equalsIgnoreCase(ext.getStatus()) && !"CANCELLED".equalsIgnoreCase(ext.getStatus())) {
                ext.setStatus("ASSIGNED");
            }
            ext.setAssignmentTarget(assigneeId);
            extendedTaskInfoRepository.save(ext);
        } else {
            ExtendedTaskInfo extInfo = ExtendedTaskInfo.builder()
                    .taskId(taskId)
                    .processInstanceId(processInstanceId)
                    .processDefinitionId(processDefinitionId)
                    .taskDefinitionKey(taskDefinitionKey)
                    .taskName(task.getName())
                    .assignmentType(AssignmentType.USER)
                    .assignmentTarget(assigneeId)
                    .status("ASSIGNED")
                    .createdTime(LocalDateTime.now())
                    .extendedProperties(extendedPropertiesJson)
                    .build();
            extendedTaskInfoRepository.save(extInfo);
        }
        updateSubTableTaskProgress(owner, subTableName, rowKey, task.getName(), progressCols[0], progressCols[1]);
        log.info("Ensured ExtendedTaskInfo for preassigned MI task {}: rowKey={}, subTable={}",
                taskId, rowKey, subTableName);
    }

    void handleElementVariableAssignment(TaskAssignmentListener owner, TaskEntity task, String taskId,
            String processInstanceId, String processDefinitionId, String taskDefinitionKey) {
        RuntimeService runtimeService = owner.runtimeService();
        RepositoryService repositoryService = owner.repositoryService();
        BpmnActionParser bpmnActionParser = owner.bpmnActionParser();
        JdbcTemplate jdbcTemplate = owner.jdbcTemplate();
        ExtendedTaskInfoRepository extendedTaskInfoRepository = owner.extendedTaskInfoRepository();
        TaskService taskService = owner.taskService();
        ObjectMapper objectMapper = owner.objectMapper();

        try {
            log.info("Handling ELEMENT_VARIABLE assignment for task {}", taskId);

            String executionId = task.getExecutionId();
            Object currentItemObj = runtimeService.getVariable(executionId, "currentItem");
            if (currentItemObj == null) {
                currentItemObj = runtimeService.getVariable(executionId, "_currentItem");
            }

            if (currentItemObj == null) {
                log.warn("currentItem variable is null for task {}, task will remain CREATED", taskId);
                return;
            }

            if (!(currentItemObj instanceof Map)) {
                log.warn("currentItem variable is not a Map for task {}, task will remain CREATED", taskId);
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> currentItem = (Map<String, Object>) currentItemObj;

            Object rowVersionObj = currentItem.get("rowVersion");
            Long subTableRowVersion = null;
            if (rowVersionObj != null) {
                if (rowVersionObj instanceof Number) {
                    subTableRowVersion = ((Number) rowVersionObj).longValue();
                } else {
                    try {
                        subTableRowVersion = Long.parseLong(String.valueOf(rowVersionObj));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid rowVersion format in currentItem for task {}: {}", taskId, rowVersionObj);
                    }
                }
            }

            String subTableId = null;
            String subTableName = null;
            String assigneeFieldFromBpmn = null;

            if (processDefinitionId != null && taskDefinitionKey != null) {
                BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
                if (bpmnModel != null) {
                    FlowElement flowElement = bpmnModel.getFlowElement(taskDefinitionKey);
                    if (flowElement instanceof UserTask userTask) {
                        subTableId = getExtensionProperty(userTask, "subTableId");
                        subTableName = getExtensionProperty(userTask, "subTableName");
                        assigneeFieldFromBpmn = getExtensionProperty(userTask, "assigneeField");
                    }
                }
                // Flowable's in-memory BpmnModel can miss designer custom properties. Keep this
                // aligned with TaskManagerComponent orphan repair, which reads the deployed XML.
                subTableId = firstNonBlank(subTableId,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "subTableId"));
                subTableName = firstNonBlank(subTableName,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "subTableName"));
                // Later userTasks in the same MI subprocess often omit subTableName — inherit from sibling nodes.
                subTableName = firstNonBlank(subTableName,
                        bpmnActionParser.getMultiInstanceSubProcessSubTableName(processDefinitionId, taskDefinitionKey));
                assigneeFieldFromBpmn = firstNonBlank(assigneeFieldFromBpmn,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "assigneeField"));
            }

            if (subTableName == null || subTableName.isBlank()) {
                log.warn("subTableName missing for MI ELEMENT_VARIABLE task {}, task will remain CREATED", taskId);
                return;
            }
            List<String> pkColsEv;
            Map<String, Object> rowKeyEv;
            try {
                pkColsEv = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate,
                        requireSafeIdentifier(subTableName));
                rowKeyEv = SubTableRowKeySupport.rowKeyFromCurrentItem(currentItem, pkColsEv);
            } catch (Exception e) {
                log.warn("Could not resolve row key for task {}: {}", taskId, e.getMessage());
                return;
            }
            if (rowKeyEv == null) {
                log.warn("currentItem missing rowKey / PK values for task {}, task will remain CREATED", taskId);
                return;
            }
            final List<String> pkCols = pkColsEv;
            final Map<String, Object> rowKey = rowKeyEv;

            // Align with portal buildParticipantsCollection and sub-table column names:
            // prefer BPMN assigneeField, then assigneeId, then assignee_user_id
            Object assigneeIdObj = null;
            if (assigneeFieldFromBpmn != null && !assigneeFieldFromBpmn.isBlank()) {
                assigneeIdObj = currentItem.get(assigneeFieldFromBpmn.trim());
            }
            if (assigneeIdObj == null) {
                assigneeIdObj = currentItem.get("assigneeId");
            }
            if (assigneeIdObj == null) {
                assigneeIdObj = currentItem.get("assignee_user_id");
            }
            if (assigneeIdObj == null) {
                log.warn("No assignee in currentItem for task {} (tried assigneeField={}, assigneeId, assignee_user_id); task will remain CREATED",
                        taskId, assigneeFieldFromBpmn);
                return;
            }

            String assigneeId = normalizeFlowableUserIdValue(assigneeIdObj);
            if (assigneeId == null || assigneeId.isBlank()) {
                log.warn("ELEMENT_VARIABLE: cannot normalize assignee id from currentItem for task {} (assigneeField={}, rawType={})",
                        taskId, assigneeFieldFromBpmn,
                        assigneeIdObj != null ? assigneeIdObj.getClass().getSimpleName() : "null");
                return;
            }

            try {
                taskService.setAssignee(taskId, assigneeId);
                log.info("Task {} assigned to user {} via ELEMENT_VARIABLE", taskId, assigneeId);
                owner.notifyNewTask(assigneeId, taskId, task.getName(), processInstanceId);
            } catch (Exception e) {
                log.warn("Failed to set assignee {} for task {}: {}, task will remain CREATED",
                        assigneeId, taskId, e.getMessage());
                return;
            }

            Map<String, Object> extendedProps = new HashMap<>();
            extendedProps.put("multiInstance", true);
            extendedProps.put("subTableRowKey", rowKey);
            if (pkCols.size() == 1 && rowKey.get(pkCols.get(0)) instanceof Number) {
                extendedProps.put("subTableRowId", ((Number) rowKey.get(pkCols.get(0))).longValue());
            }
            if (subTableRowVersion != null) {
                extendedProps.put("subTableRowVersion", subTableRowVersion);
            }
            if (subTableId != null) {
                extendedProps.put("subTableId", subTableId);
            }
            if (subTableName != null) {
                extendedProps.put("subTableName", subTableName);
            }

            String[] progressCols = resolveMiProgressColumnNames(owner, processDefinitionId, taskDefinitionKey);
            extendedProps.put("miTaskStatusField", progressCols[0]);
            extendedProps.put("miTaskCurrentNodeField", progressCols[1]);

            String extendedPropertiesJson;
            try {
                extendedPropertiesJson = objectMapper.writeValueAsString(extendedProps);
            } catch (Exception e) {
                log.error("Failed to serialize extendedProperties for task {}: {}", taskId, e.getMessage());
                extendedPropertiesJson = "{}";
            }

            try {
                ExtendedTaskInfo extInfo = ExtendedTaskInfo.builder()
                        .taskId(taskId)
                        .processInstanceId(processInstanceId)
                        .processDefinitionId(processDefinitionId)
                        .taskDefinitionKey(taskDefinitionKey)
                        .taskName(task.getName())
                        .assignmentType(AssignmentType.USER)
                        .assignmentTarget(assigneeId)
                        .status("ASSIGNED")
                        .createdTime(LocalDateTime.now())
                        .extendedProperties(extendedPropertiesJson)
                        .build();

                extendedTaskInfoRepository.save(extInfo);
                updateSubTableTaskProgress(owner, subTableName, rowKey, task.getName(), progressCols[0], progressCols[1]);
                log.info("Created ExtendedTaskInfo for multi-instance task {}: assignee={}, rowKey={}",
                        taskId, assigneeId, rowKey);
            } catch (Exception e) {
                log.error("Failed to save ExtendedTaskInfo for task {}: {}", taskId, e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("Error handling ELEMENT_VARIABLE assignment for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    /**
     * Column names come from SubProcess BPMN extensions {@code miTaskStatusField} / {@code miTaskCurrentNodeField}
     * (designer) with defaults {@code task_status} / {@code task_current_node}.
     */
    void updateSubTableTaskProgress(TaskAssignmentListener owner, String subTableName, Map<String, Object> rowKey,
            String taskName, String statusColumn, String currentNodeColumn) {
        JdbcTemplate jdbcTemplate = owner.jdbcTemplate();
        if (subTableName == null || rowKey == null || rowKey.isEmpty()) {
            return;
        }
        try {
            String tableName = requireSafeIdentifier(subTableName);
            List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, tableName);
            String pkWhere = SubTableRowKeySupport.buildPkWhereClause(pkCols);
            Object[] pkArgs = SubTableRowKeySupport.orderedPkParams(pkCols, rowKey);
            String statusCol = requireSafeIdentifier(statusColumn);
            String nodeCol = requireSafeIdentifier(currentNodeColumn);
            boolean hasTaskStatus = columnExists(jdbcTemplate, tableName, statusCol);
            boolean hasTaskCurrentNode = columnExists(jdbcTemplate, tableName, nodeCol);
            if (!hasTaskStatus && !hasTaskCurrentNode) {
                return;
            }

            StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
            List<Object> params = new ArrayList<>();
            if (hasTaskStatus) {
                sql.append(statusCol).append(" = ?, ");
                params.add("IN_PROGRESS");
            }
            if (hasTaskCurrentNode) {
                sql.append(nodeCol).append(" = ?, ");
                params.add(taskName);
            }
            sql.setLength(sql.length() - 2);
            sql.append(" WHERE ").append(pkWhere);
            params.addAll(Arrays.asList(pkArgs));
            jdbcTemplate.update(sql.toString(), params.toArray());
        } catch (Exception e) {
            log.debug("Skipped updating sub-table task progress for {} / rowKey={}: {}",
                    subTableName, rowKey, e.getMessage());
        }
    }

    String[] resolveMiProgressColumnNames(TaskAssignmentListener owner, String processDefinitionId,
            String taskDefinitionKey) {
        BpmnActionParser bpmnActionParser = owner.bpmnActionParser();
        String statusDefault = "task_status";
        String nodeDefault = "task_current_node";
        if (processDefinitionId == null || processDefinitionId.isBlank()
                || taskDefinitionKey == null || taskDefinitionKey.isBlank()) {
            return new String[] { statusDefault, nodeDefault };
        }
        String st = bpmnActionParser.getMultiInstanceSubProcessExtensionPropertyValue(
                processDefinitionId, taskDefinitionKey, "miTaskStatusField");
        String nd = bpmnActionParser.getMultiInstanceSubProcessExtensionPropertyValue(
                processDefinitionId, taskDefinitionKey, "miTaskCurrentNodeField");
        return new String[] { safeSqlColumnName(st, statusDefault), safeSqlColumnName(nd, nodeDefault) };
    }

    void updateCurrentItemProgress(TaskAssignmentListener owner, Map<String, Object> processVariables,
            String processDefinitionId, String taskDefinitionKey, String taskName) {
        BpmnActionParser bpmnActionParser = owner.bpmnActionParser();
        JdbcTemplate jdbcTemplate = owner.jdbcTemplate();
        if (processVariables == null || processDefinitionId == null || taskDefinitionKey == null) {
            return;
        }
        Object currentItemObj = processVariables.get("currentItem");
        if (currentItemObj == null) {
            currentItemObj = processVariables.get("_currentItem");
        }
        if (!(currentItemObj instanceof Map<?, ?> currentItemRaw)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> currentItem = (Map<String, Object>) currentItemRaw;

        String subTableName = firstNonBlank(
                bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey, "subTableName"),
                bpmnActionParser.getMultiInstanceSubProcessSubTableName(processDefinitionId, taskDefinitionKey)
        );
        if (subTableName == null || subTableName.isBlank()) {
            return;
        }
        Map<String, Object> rowKey;
        try {
            List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate,
                    requireSafeIdentifier(subTableName));
            rowKey = SubTableRowKeySupport.rowKeyFromCurrentItem(currentItem, pkCols);
        } catch (Exception e) {
            return;
        }
        if (rowKey == null) {
            return;
        }

        String[] cols = resolveMiProgressColumnNames(owner, processDefinitionId, taskDefinitionKey);
        updateSubTableTaskProgress(owner, subTableName, rowKey, taskName, cols[0], cols[1]);
    }

    private static String safeSqlColumnName(String candidate, String defaultName) {
        if (candidate == null || candidate.isBlank()) {
            return defaultName;
        }
        String t = candidate.trim();
        return SAFE_SQL_IDENTIFIER.matcher(t).matches() ? t : defaultName;
    }

    private static Long extractLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String requireSafeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid sub-table name");
        }
        return identifier;
    }

    private static boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName);
        return count != null && count > 0;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
