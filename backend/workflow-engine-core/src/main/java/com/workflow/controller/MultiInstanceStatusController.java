package com.workflow.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.MultiInstanceStatusResponse;
import com.workflow.dto.response.SubTableDataResponse;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.component.BpmnActionParser;
import com.workflow.repository.ExtendedTaskInfoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-instance subprocess status monitoring controller.
 * 
 * Provides query APIs for multi-instance subprocess execution status.
 * Aggregates sub-task information from Flowable runtime data and ExtendedTaskInfo.
 * 
 * **Validates: Requirements 7.1, 7.2**
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflow/multi-instance")
@RequiredArgsConstructor
@Tag(name = "Multi-Instance Status Monitor", description = "Multi-instance subprocess execution status query API")
public class MultiInstanceStatusController {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final TaskService taskService;
    private final ExtendedTaskInfoRepository extendedTaskInfoRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final BpmnActionParser bpmnActionParser;

    /**
     * Query multi-instance subprocess execution status.
     * 
     * GET /api/v1/workflow/multi-instance/{processInstanceId}/status
     * 
     * @param processInstanceId process instance ID
     * @return multi-instance execution status response
     */
    @GetMapping("/{processInstanceId}/status")
    @Operation(summary = "Query multi-instance execution status", description = "Returns the execution status of multi-instance subprocesses in the specified process instance, including total, completed, active, and sub-task details")
    public ResponseEntity<ApiResponse<MultiInstanceStatusResponse>> getStatus(
            @Parameter(description = "Process instance ID")
            @PathVariable String processInstanceId) {
        
        try {
            log.info("Querying multi-instance execution status, processInstanceId: {}", processInstanceId);
            
            // 1. Query multi-instance executions in the process instance (find activities containing multiInstanceLoopCharacteristics)
            List<Execution> executions = runtimeService.createExecutionQuery()
                    .processInstanceId(processInstanceId)
                    .list();
            
            // 2. Find multi-instance parent execution (by variable; when process has ended there is no runtime execution, fall back to constructing response from ExtendedTaskInfo)
            Execution multiInstanceExecution = null;
            for (Execution execution : executions) {
                Map<String, Object> variables = runtimeService.getVariables(execution.getId());
                if (variables.containsKey("nrOfInstances")) {
                    multiInstanceExecution = execution;
                    break;
                }
            }

            // 3. Get multi-instance statistics from Flowable variables (if runtime execution exists)
            Integer nrOfInstances = null;
            Integer nrOfCompletedInstances = null;
            Integer nrOfActiveInstances = null;
            if (multiInstanceExecution != null) {
                Map<String, Object> miVariables = runtimeService.getVariables(multiInstanceExecution.getId());
                nrOfInstances = (Integer) miVariables.get("nrOfInstances");
                nrOfCompletedInstances = (Integer) miVariables.get("nrOfCompletedInstances");
                nrOfActiveInstances = (Integer) miVariables.get("nrOfActiveInstances");
            }

            // 4. Query all extended task info for the process instance
            List<ExtendedTaskInfo> allTaskInfos = extendedTaskInfoRepository
                    .findByProcessInstanceIdAndIsDeletedFalse(processInstanceId);

            // 5. Multi-instance progress: multiInstance flag, or already has subTableRowId in extensions (pre-assigned handler path missed multiInstance)
            List<ExtendedTaskInfo> multiInstanceTasks = allTaskInfos.stream()
                    .filter(this::isIncludedInMultiInstanceStatus)
                    .collect(Collectors.toList());

            // 5b. When process has ended and no runtime execution, wf_extended rows are often soft-deleted;
            //     portal aggregation would misjudge sub-table MI status if there are no records.
            //     For ended process instances, re-query including soft-deleted rows to rebuild MI task list.
            if (multiInstanceTasks.isEmpty() && multiInstanceExecution == null) {
                HistoricProcessInstance hip = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .singleResult();
                if (hip != null && hip.getEndTime() != null) {
                    allTaskInfos = extendedTaskInfoRepository.findAllByProcessInstanceId(processInstanceId);
                    multiInstanceTasks = allTaskInfos.stream()
                            .filter(this::isIncludedInMultiInstanceStatus)
                            .collect(Collectors.toList());
                }
            }

            if (multiInstanceTasks.isEmpty() && multiInstanceExecution == null) {
                log.warn("No multi-instance execution or historical multi-instance tasks found for process instance: {}", processInstanceId);
                return ResponseEntity.ok(ApiResponse.error(
                        "MULTI_INSTANCE_NOT_FOUND",
                        "No multi-instance subprocess found in process instance"
                ));
            }
            
            // 6. Build sub-task detail list
            List<MultiInstanceStatusResponse.SubTaskDetail> taskDetails = new ArrayList<>();
            for (ExtendedTaskInfo taskInfo : multiInstanceTasks) {
                Map<String, Object> extProps = parseExtendedProperties(taskInfo.getExtendedProperties());
                String subTableName = extProps.get("subTableName") != null ? String.valueOf(extProps.get("subTableName")).trim() : null;
                if (subTableName == null || subTableName.isBlank()) {
                    subTableName = bpmnActionParser.getMultiInstanceSubProcessSubTableName(
                            taskInfo.getProcessDefinitionId(), taskInfo.getTaskDefinitionKey());
                }
                List<String> pkCols = tryResolvePkColumns(subTableName);
                Map<String, Object> subTableRowKey = null;
                Long subTableRowId = null;
                if (pkCols != null) {
                    subTableRowKey = SubTableRowKeySupport.rowKeyFromExtendedProps(extProps, pkCols);
                    if (subTableRowKey == null && pkCols.size() == 1) {
                        subTableRowId = parseSubTableRowId(extProps.get("subTableRowId"));
                        if (subTableRowId != null) {
                            subTableRowKey = new LinkedHashMap<>(Map.of(pkCols.get(0), subTableRowId));
                        }
                    } else if (subTableRowKey != null && pkCols.size() == 1) {
                        Object v = subTableRowKey.get(pkCols.get(0));
                        if (v instanceof Number n) {
                            subTableRowId = n.longValue();
                        }
                    }
                } else {
                    subTableRowId = parseSubTableRowId(extProps.get("subTableRowId"));
                }
                String miTaskStatusField = extProps.get("miTaskStatusField") != null ? String.valueOf(extProps.get("miTaskStatusField")) : null;
                String miTaskCurrentNodeField = extProps.get("miTaskCurrentNodeField") != null ? String.valueOf(extProps.get("miTaskCurrentNodeField")) : null;
                
                MultiInstanceStatusResponse.SubTaskDetail detail = MultiInstanceStatusResponse.SubTaskDetail.builder()
                        .taskId(taskInfo.getTaskId())
                        .taskName(taskInfo.getTaskName())
                        .taskDefinitionKey(taskInfo.getTaskDefinitionKey())
                        .assignee(taskInfo.getAssignmentTarget())
                        .assigneeName(getUserName(taskInfo.getAssignmentTarget()))
                        .status(taskInfo.getStatus())
                        .subTableRowId(subTableRowId)
                        .subTableRowKey(subTableRowKey)
                        .subTableName(subTableName)
                        .miTaskStatusField(miTaskStatusField)
                        .miTaskCurrentNodeField(miTaskCurrentNodeField)
                        .createdTime(taskInfo.getCreatedTime())
                        .completedTime(taskInfo.getCompletedTime())
                        .completedBy(taskInfo.getCompletedBy())
                        .completedByName(taskInfo.getCompletedBy() != null ? getUserName(taskInfo.getCompletedBy()) : null)
                        .build();
                
                taskDetails.add(detail);
            }

            // 6b. MI UserTasks still in-flight at runtime that lack wf_extended records (pre-claim candidates, path gaps, etc.)
            // wf_extended-only aggregation would misjudge the row as fully completed → portal Current Step shows "end".
            appendMissingRuntimeMultiInstanceTasks(processInstanceId, taskDetails);
            
            // 7. Count cancelled instances
            long cancelledCount = multiInstanceTasks.stream()
                    .filter(t -> "CANCELLED".equals(t.getStatus()))
                    .count();
            
            // 8. Determine multi-instance status
            // Runtime variables missing (process completed) → derive from ExtendedTaskInfo aggregate
            if (nrOfInstances == null && !multiInstanceTasks.isEmpty()) {
                nrOfInstances = multiInstanceTasks.size();
                nrOfCompletedInstances = (int) multiInstanceTasks.stream()
                        .filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus())).count();
                nrOfActiveInstances = (int) multiInstanceTasks.stream()
                        .filter(t -> {
                            String s = t.getStatus();
                            return s != null && !"COMPLETED".equalsIgnoreCase(s) && !"CANCELLED".equalsIgnoreCase(s);
                        }).count();
            }
            String status = determineMultiInstanceStatus(nrOfInstances, nrOfCompletedInstances, cancelledCount);

            // 9. Get multi-instance activity info
            String activityId = multiInstanceExecution != null ? multiInstanceExecution.getActivityId() : null;
            String activityName = activityId != null ? getActivityName(processInstanceId, activityId) : null;
            
            // 10. Get start and completion times
            LocalDateTime startedTime = getMultiInstanceStartTime(multiInstanceTasks);
            LocalDateTime completedTime = "COMPLETED".equals(status) ? getMultiInstanceCompletedTime(multiInstanceTasks) : null;
            
            // 11. Build response
            MultiInstanceStatusResponse response = MultiInstanceStatusResponse.builder()
                    .processInstanceId(processInstanceId)
                    .multiInstanceActivityId(activityId)
                    .multiInstanceActivityName(activityName)
                    .totalInstances(nrOfInstances != null ? nrOfInstances : multiInstanceTasks.size())
                    .completedInstances(nrOfCompletedInstances != null ? nrOfCompletedInstances : 0)
                    .activeInstances(nrOfActiveInstances != null ? nrOfActiveInstances : 0)
                    .cancelledInstances((int) cancelledCount)
                    .status(status)
                    .startedTime(startedTime)
                    .completedTime(completedTime)
                    .tasks(taskDetails)
                    .build();
            
            log.info("Multi-instance execution status query succeeded, processInstanceId: {}, total: {}, completed: {}, active: {}",
                    processInstanceId, response.getTotalInstances(), response.getCompletedInstances(), response.getActiveInstances());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("Failed to query multi-instance execution status, processInstanceId: {}", processInstanceId, e);
            return ResponseEntity.ok(ApiResponse.error(
                    "QUERY_FAILED",
                    "Failed to query multi-instance execution status: " + e.getMessage()
            ));
        }
    }
    
    private static Long parseSubTableRowId(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Merge MI UserTasks that exist at Flowable runtime but are not covered by
     * {@link ExtendedTaskInfo} into the tasks list, so the portal can resolve
     * Current Step / status per sub-table row (matching what users actually see in the queue).
     */
    private void appendMissingRuntimeMultiInstanceTasks(String processInstanceId,
            List<MultiInstanceStatusResponse.SubTaskDetail> taskDetails) {
        if (taskService == null || processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }
        Set<String> knownIds = taskDetails.stream()
                .map(MultiInstanceStatusResponse.SubTaskDetail::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        TaskQuery tq = taskService.createTaskQuery().processInstanceId(processInstanceId);
        List<Task> running = tq.list();
        for (Task task : running) {
            if (task == null || task.getId() == null || knownIds.contains(task.getId())) {
                continue;
            }
            String pdId = task.getProcessDefinitionId();
            String defKey = task.getTaskDefinitionKey();
            if (pdId == null || defKey == null) {
                continue;
            }
            String miScopeTable = bpmnActionParser.getMultiInstanceSubProcessSubTableName(pdId, defKey);
            if (miScopeTable == null || miScopeTable.isBlank()) {
                continue;
            }
            String execId = task.getExecutionId();
            if (execId == null) {
                continue;
            }
            String subTableName = firstNonBlankTrimmed(
                    bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "subTableName"),
                    miScopeTable);
            Long rowId = null;
            Map<String, Object> rowKey = null;
            List<String> pkCols = tryResolvePkColumns(subTableName);
            if (pkCols != null) {
                rowKey = parseRowKeyFromExecution(execId, pkCols);
                if (rowKey != null && pkCols.size() == 1 && rowKey.get(pkCols.get(0)) instanceof Number) {
                    rowId = ((Number) rowKey.get(pkCols.get(0))).longValue();
                }
            }
            if (rowKey == null) {
                continue;
            }
            String miTaskStatusField = bpmnActionParser.getMultiInstanceSubProcessExtensionPropertyValue(
                    pdId, defKey, "miTaskStatusField");
            String miTaskCurrentNodeField = bpmnActionParser.getMultiInstanceSubProcessExtensionPropertyValue(
                    pdId, defKey, "miTaskCurrentNodeField");

            Date createTime = task.getCreateTime();
            LocalDateTime createdLdt = createTime == null
                    ? null
                    : LocalDateTime.ofInstant(createTime.toInstant(), ZoneId.systemDefault());

            MultiInstanceStatusResponse.SubTaskDetail detail = MultiInstanceStatusResponse.SubTaskDetail.builder()
                    .taskId(task.getId())
                    .taskName(task.getName())
                    .taskDefinitionKey(defKey)
                    .assignee(task.getAssignee())
                    .assigneeName(getUserName(task.getAssignee()))
                    .status(runtimeRuTaskStatus(task))
                    .subTableRowId(rowId)
                    .subTableRowKey(rowKey)
                    .subTableName(subTableName)
                    .miTaskStatusField(miTaskStatusField)
                    .miTaskCurrentNodeField(miTaskCurrentNodeField)
                    .createdTime(createdLdt)
                    .completedTime(null)
                    .completedBy(null)
                    .completedByName(null)
                    .build();
            taskDetails.add(detail);
            knownIds.add(task.getId());
        }
    }

    private List<String> tryResolvePkColumns(String subTableName) {
        if (jdbcTemplate == null) {
            return null;
        }
        if (subTableName == null || !subTableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return null;
        }
        try {
            return PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, subTableName);
        } catch (Exception e) {
            log.debug("MI status/sub-table: could not resolve PK columns for {}: {}", subTableName, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> parseRowKeyFromExecution(String executionId, List<String> pkCols) {
        Object currentItemObj = runtimeService.getVariable(executionId, "currentItem");
        if (currentItemObj == null) {
            currentItemObj = runtimeService.getVariable(executionId, "_currentItem");
        }
        if (!(currentItemObj instanceof Map<?, ?>)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) currentItemObj;
        return SubTableRowKeySupport.rowKeyFromCurrentItem(typed, pkCols);
    }

    private static String runtimeRuTaskStatus(Task task) {
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            return "ASSIGNED";
        }
        return "CREATED";
    }

    private static String firstNonBlankTrimmed(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    /**
     * Whether to include in multi-instance status aggregation (MI flag or participant sub-table row id).
     */
    private boolean isIncludedInMultiInstanceStatus(ExtendedTaskInfo taskInfo) {
        return isMultiInstanceTask(taskInfo) || hasParticipantSubTableRow(taskInfo);
    }

    private boolean hasParticipantSubTableRow(ExtendedTaskInfo taskInfo) {
        if (taskInfo.getExtendedProperties() == null || taskInfo.getExtendedProperties().isBlank()) {
            return false;
        }
        try {
            Map<String, Object> p = parseExtendedProperties(taskInfo.getExtendedProperties());
            if (p.get("subTableRowKey") != null) {
                return true;
            }
            return p.get("subTableRowId") != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Determine if a task is a multi-instance sub-task.
     */
    private boolean isMultiInstanceTask(ExtendedTaskInfo taskInfo) {
        if (taskInfo.getExtendedProperties() == null) {
            return false;
        }
        
        try {
            Map<String, Object> extProps = parseExtendedProperties(taskInfo.getExtendedProperties());
            return Boolean.TRUE.equals(extProps.get("multiInstance"));
        } catch (Exception e) {
            log.warn("Failed to parse extended properties, taskId: {}", taskInfo.getTaskId(), e);
            return false;
        }
    }
    
    /**
     * Parse extended properties JSON.
     */
    private Map<String, Object> parseExtendedProperties(String extendedProperties) {
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(extendedProperties, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse extended properties JSON: {}", extendedProperties, e);
            return new HashMap<>();
        }
    }
    
    /**
     * Get user display name (simplified implementation; should call user service in production).
     */
    private String getUserName(String userId) {
        if (userId == null) {
            return null;
        }
        // TODO: Call user service to get real name
        return "User-" + userId;
    }
    
    /**
     * Determine multi-instance status.
     */
    private String determineMultiInstanceStatus(Integer nrOfInstances, Integer nrOfCompletedInstances, long cancelledCount) {
        if (nrOfInstances == null || nrOfCompletedInstances == null) {
            return "ACTIVE";
        }
        
        if (nrOfCompletedInstances.equals(nrOfInstances)) {
            return "COMPLETED";
        }
        
        if (cancelledCount > 0 && (nrOfCompletedInstances + cancelledCount) == nrOfInstances) {
            return "CANCELLED";
        }
        
        return "ACTIVE";
    }
    
    /**
     * Get activity display name.
     */
    private String getActivityName(String processInstanceId, String activityId) {
        try {
            // Get activity name from process definition
            // Simplified implementation: return activity ID
            return activityId;
        } catch (Exception e) {
            log.warn("Failed to get activity name, processInstanceId: {}, activityId: {}", processInstanceId, activityId, e);
            return activityId;
        }
    }
    
    /**
     * Get multi-instance start time (earliest sub-task creation time).
     */
    private LocalDateTime getMultiInstanceStartTime(List<ExtendedTaskInfo> tasks) {
        return tasks.stream()
                .map(ExtendedTaskInfo::getCreatedTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }
    
    /**
     * Get multi-instance completion time (latest sub-task completion time).
     */
    private LocalDateTime getMultiInstanceCompletedTime(List<ExtendedTaskInfo> tasks) {
        return tasks.stream()
                .map(ExtendedTaskInfo::getCompletedTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
    
    /**
     * Query main task sub-table data (for real-time sync).
     * 
     * GET /api/v1/workflow/tasks/{taskId}/sub-table-data/all
     * 
     * @param taskId main task ID
     * @return sub-table data list (with assignee, status)
     */
    @GetMapping("/tasks/{taskId}/sub-table-data/all")
    @Operation(summary = "Query main task sub-table data", description = "Query all sub-table data rows (with assignee, status) for main task form real-time sync")
    public ResponseEntity<ApiResponse<SubTableDataResponse>> getSubTableData(
            @Parameter(description = "Main task ID")
            @PathVariable String taskId) {
        
        try {
            log.info("Querying main task sub-table data, taskId: {}", taskId);
            
            // 1. Query task info
            Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
            if (task == null) {
                log.warn("Task not found: {}", taskId);
                return ResponseEntity.ok(ApiResponse.error(
                        "TASK_NOT_FOUND",
                        "Task not found"
                ));
            }
            
            // 2. Get process instance ID
            String processInstanceId = task.getProcessInstanceId();
            
            // 3. Get sub-table config from process variables
            Map<String, Object> processVariables = runtimeService.getVariables(processInstanceId);
            
            // Find multi-instance collection variable (format: multiInstance_{subTableName}_collection)
            String collectionVariableName = null;
            String subTableName = null;
            for (String varName : processVariables.keySet()) {
                if (varName.startsWith("multiInstance_") && varName.endsWith("_collection")) {
                    collectionVariableName = varName;
                    // Extract sub-table name: multiInstance_{subTableName}_collection
                    subTableName = varName.substring("multiInstance_".length(), 
                            varName.length() - "_collection".length());
                    break;
                }
            }
            
            if (subTableName == null) {
                log.warn("Multi-instance collection variable not found, processInstanceId: {}", processInstanceId);
                return ResponseEntity.ok(ApiResponse.error(
                        "MULTI_INSTANCE_CONFIG_NOT_FOUND",
                        "Multi-instance configuration not found"
                ));
            }
            if (!subTableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                log.warn("Invalid sub-table name (skipping query): {}", subTableName);
                return ResponseEntity.ok(ApiResponse.error(
                        "INVALID_SUBTABLE_NAME",
                        "Invalid sub-table configuration"
                ));
            }
            List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, subTableName);
            String pkWhere = SubTableRowKeySupport.buildPkWhereClause(pkCols);

            // 4. Get collection variable (elements contain rowKey and/or rowId)
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> collectionData =
                    (List<Map<String, Object>>) processVariables.get(collectionVariableName);
            
            if (collectionData == null || collectionData.isEmpty()) {
                log.warn("Multi-instance collection variable is empty, processInstanceId: {}", processInstanceId);
                return ResponseEntity.ok(ApiResponse.success(
                        SubTableDataResponse.builder()
                                .taskId(taskId)
                                .subTableName(subTableName)
                                .rows(Collections.emptyList())
                                .build()
                ));
            }
            
            List<Map<String, Object>> resolvedRowKeys = new ArrayList<>();
            for (Map<String, Object> item : collectionData) {
                Map<String, Object> rk = SubTableRowKeySupport.rowKeyFromCurrentItem(item, pkCols);
                if (rk != null) {
                    resolvedRowKeys.add(rk);
                }
            }
            
            List<ExtendedTaskInfo> allTaskInfos = extendedTaskInfoRepository
                    .findByProcessInstanceIdAndIsDeletedFalse(processInstanceId);
            
            Map<String, ExtendedTaskInfo> rowKeyToTaskInfo = new HashMap<>();
            for (ExtendedTaskInfo taskInfo : allTaskInfos) {
                if (!isMultiInstanceTask(taskInfo)) {
                    continue;
                }
                Map<String, Object> extProps = parseExtendedProperties(taskInfo.getExtendedProperties());
                Map<String, Object> rk = SubTableRowKeySupport.rowKeyFromExtendedProps(extProps, pkCols);
                if (rk != null) {
                    rowKeyToTaskInfo.put(SubTableRowKeySupport.canonicalRowKeyString(pkCols, rk), taskInfo);
                }
            }
            
            List<SubTableDataResponse.SubTableRow> rows = new ArrayList<>();
            String selectSql = "SELECT * FROM " + subTableName + " WHERE " + pkWhere;
            for (Map<String, Object> rk : resolvedRowKeys) {
                Map<String, Object> rowData;
                try {
                    rowData = jdbcTemplate.queryForMap(selectSql, SubTableRowKeySupport.orderedPkParams(pkCols, rk));
                } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                    log.warn("Sub-table row not found: table={}, rowKey={}", subTableName, rk);
                    continue;
                }
                String canon = SubTableRowKeySupport.canonicalRowKeyString(pkCols, rk);
                ExtendedTaskInfo taskInfo = rowKeyToTaskInfo.get(canon);
                
                String assignee = null;
                String assigneeName = null;
                String status = "PENDING";
                
                if (taskInfo != null) {
                    assignee = taskInfo.getAssignmentTarget();
                    assigneeName = getUserName(assignee);
                    status = taskInfo.getStatus();
                } else {
                    for (Map.Entry<String, Object> entry : rowData.entrySet()) {
                        String fieldName = entry.getKey().toLowerCase();
                        if (fieldName.contains("assignee") || fieldName.contains("handler")) {
                            Object value = entry.getValue();
                            if (value != null) {
                                assignee = value.toString();
                                assigneeName = getUserName(assignee);
                            }
                            break;
                        }
                    }
                }
                
                Long legacyId = null;
                if (pkCols.size() == 1 && rk.get(pkCols.get(0)) instanceof Number) {
                    legacyId = ((Number) rk.get(pkCols.get(0))).longValue();
                }
                
                SubTableDataResponse.SubTableRow row = SubTableDataResponse.SubTableRow.builder()
                        .id(legacyId)
                        .rowKey(new LinkedHashMap<>(rk))
                        .data(rowData)
                        .assignee(assignee)
                        .assigneeName(assigneeName)
                        .status(status)
                        .build();
                
                rows.add(row);
            }
            
            SubTableDataResponse response = SubTableDataResponse.builder()
                    .taskId(taskId)
                    .subTableName(subTableName)
                    .rows(rows)
                    .build();
            
            log.info("Main task sub-table data query succeeded, taskId: {}, subTable: {}, row count: {}",
                    taskId, subTableName, rows.size());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("Failed to query main task sub-table data, taskId: {}", taskId, e);
            return ResponseEntity.ok(ApiResponse.error(
                    "QUERY_FAILED",
                    "Failed to query sub-table data: " + e.getMessage()
            ));
        }
    }
}
