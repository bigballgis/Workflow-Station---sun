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
 * 多实例子流程状态监控控制器
 * 
 * 提供多实例子流程执行状态查询接口
 * 从 Flowable 运行时数据和 ExtendedTaskInfo 中聚合子任务信息
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
@Tag(name = "多实例状态监控", description = "多实例子流程执行状态查询API")
public class MultiInstanceStatusController {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final TaskService taskService;
    private final ExtendedTaskInfoRepository extendedTaskInfoRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final BpmnActionParser bpmnActionParser;

    /**
     * 查询多实例子流程执行状态
     * 
     * GET /api/v1/workflow/multi-instance/{processInstanceId}/status
     * 
     * @param processInstanceId 流程实例ID
     * @return 多实例执行状态响应
     */
    @GetMapping("/{processInstanceId}/status")
    @Operation(summary = "查询多实例执行状态", description = "返回指定流程实例中多实例子流程的执行状态，包括总实例数、已完成数、进行中数、各子任务详情")
    public ResponseEntity<ApiResponse<MultiInstanceStatusResponse>> getStatus(
            @Parameter(description = "流程实例ID")
            @PathVariable String processInstanceId) {
        
        try {
            log.info("查询多实例执行状态，流程实例ID: {}", processInstanceId);
            
            // 1. 查询流程实例中的多实例执行（查找包含 multiInstanceLoopCharacteristics 的活动）
            List<Execution> executions = runtimeService.createExecutionQuery()
                    .processInstanceId(processInstanceId)
                    .list();
            
            // 2. 查找多实例父执行（通过变量判断；流程已结束时不存在 runtime execution，将退化为基于 ExtendedTaskInfo 构造响应）
            Execution multiInstanceExecution = null;
            for (Execution execution : executions) {
                Map<String, Object> variables = runtimeService.getVariables(execution.getId());
                if (variables.containsKey("nrOfInstances")) {
                    multiInstanceExecution = execution;
                    break;
                }
            }

            // 3. 从 Flowable 变量中获取多实例统计信息（如果存在 runtime execution）
            Integer nrOfInstances = null;
            Integer nrOfCompletedInstances = null;
            Integer nrOfActiveInstances = null;
            if (multiInstanceExecution != null) {
                Map<String, Object> miVariables = runtimeService.getVariables(multiInstanceExecution.getId());
                nrOfInstances = (Integer) miVariables.get("nrOfInstances");
                nrOfCompletedInstances = (Integer) miVariables.get("nrOfCompletedInstances");
                nrOfActiveInstances = (Integer) miVariables.get("nrOfActiveInstances");
            }

            // 4. 查询流程实例的所有扩展任务信息
            List<ExtendedTaskInfo> allTaskInfos = extendedTaskInfoRepository
                    .findByProcessInstanceIdAndIsDeletedFalse(processInstanceId);

            // 5. 多实例进度：multiInstance 标记，或扩展里已有 subTableRowId（预分配处理人路径曾漏写 multiInstance）
            List<ExtendedTaskInfo> multiInstanceTasks = allTaskInfos.stream()
                    .filter(this::isIncludedInMultiInstanceStatus)
                    .collect(Collectors.toList());

            // 5b. 流程已结束且无运行时 execution 时，扩展表常被软删除；门户聚合若无记录会误判子表 MI 状态。
            //     对已结束的流程实例补查「含已删除」的扩展行以重建 MI 任务列表。
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
                log.warn("流程实例 {} 中未找到多实例执行或历史多实例任务", processInstanceId);
                return ResponseEntity.ok(ApiResponse.error(
                        "MULTI_INSTANCE_NOT_FOUND",
                        "流程实例中未找到多实例子流程"
                ));
            }
            
            // 6. 构建子任务详情列表
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

            // 6b. 运行时仍在途的 MI 内 UserTask，若扩展表未建记录（认领前候选人、路径遗漏等），
            // wf_extended-only 聚合会把该行误判为已全部完成 → 门户 Current Step 显示 end。
            appendMissingRuntimeMultiInstanceTasks(processInstanceId, taskDetails);
            
            // 7. 统计已取消的实例数
            long cancelledCount = multiInstanceTasks.stream()
                    .filter(t -> "CANCELLED".equals(t.getStatus()))
                    .count();
            
            // 8. 确定多实例状态
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

            // 9. 获取多实例活动信息
            String activityId = multiInstanceExecution != null ? multiInstanceExecution.getActivityId() : null;
            String activityName = activityId != null ? getActivityName(processInstanceId, activityId) : null;
            
            // 10. 获取开始和完成时间
            LocalDateTime startedTime = getMultiInstanceStartTime(multiInstanceTasks);
            LocalDateTime completedTime = "COMPLETED".equals(status) ? getMultiInstanceCompletedTime(multiInstanceTasks) : null;
            
            // 11. 构建响应
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
            
            log.info("多实例执行状态查询成功，流程实例ID: {}, 总实例数: {}, 已完成: {}, 进行中: {}",
                    processInstanceId, response.getTotalInstances(), response.getCompletedInstances(), response.getActiveInstances());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("查询多实例执行状态失败，流程实例ID: {}", processInstanceId, e);
            return ResponseEntity.ok(ApiResponse.error(
                    "QUERY_FAILED",
                    "查询多实例执行状态失败: " + e.getMessage()
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
     * 将 Flowable 运行时仍存在、但 {@link ExtendedTaskInfo} 未覆盖的 MI UserTask 并入 tasks，
     * 供门户按子表行解析 Current Step / 状态（与队列里用户实际看到的节点一致）。
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
     * 是否纳入多实例状态聚合（MI 标记或参与者子表行 id）
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
     * 判断任务是否为多实例子任务
     */
    private boolean isMultiInstanceTask(ExtendedTaskInfo taskInfo) {
        if (taskInfo.getExtendedProperties() == null) {
            return false;
        }
        
        try {
            Map<String, Object> extProps = parseExtendedProperties(taskInfo.getExtendedProperties());
            return Boolean.TRUE.equals(extProps.get("multiInstance"));
        } catch (Exception e) {
            log.warn("解析扩展属性失败，任务ID: {}", taskInfo.getTaskId(), e);
            return false;
        }
    }
    
    /**
     * 解析扩展属性 JSON
     */
    private Map<String, Object> parseExtendedProperties(String extendedProperties) {
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(extendedProperties, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析扩展属性 JSON 失败: {}", extendedProperties, e);
            return new HashMap<>();
        }
    }
    
    /**
     * 获取用户姓名（简化实现，实际应调用用户服务）
     */
    private String getUserName(String userId) {
        if (userId == null) {
            return null;
        }
        // TODO: 调用用户服务获取真实姓名
        return "用户-" + userId;
    }
    
    /**
     * 确定多实例状态
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
     * 获取活动名称
     */
    private String getActivityName(String processInstanceId, String activityId) {
        try {
            // 从流程定义中获取活动名称
            // 简化实现：返回活动ID
            return activityId;
        } catch (Exception e) {
            log.warn("获取活动名称失败，流程实例ID: {}, 活动ID: {}", processInstanceId, activityId, e);
            return activityId;
        }
    }
    
    /**
     * 获取多实例开始时间（取最早的子任务创建时间）
     */
    private LocalDateTime getMultiInstanceStartTime(List<ExtendedTaskInfo> tasks) {
        return tasks.stream()
                .map(ExtendedTaskInfo::getCreatedTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }
    
    /**
     * 获取多实例完成时间（取最晚的子任务完成时间）
     */
    private LocalDateTime getMultiInstanceCompletedTime(List<ExtendedTaskInfo> tasks) {
        return tasks.stream()
                .map(ExtendedTaskInfo::getCompletedTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }
    
    /**
     * 查询主任务子表数据（用于实时同步）
     * 
     * GET /api/v1/workflow/tasks/{taskId}/sub-table-data/all
     * 
     * @param taskId 主任务ID
     * @return 子表数据列表（含 assignee、status）
     */
    @GetMapping("/tasks/{taskId}/sub-table-data/all")
    @Operation(summary = "查询主任务子表数据", description = "查询子表所有数据行（含 assignee、status），用于主任务表单实时同步")
    public ResponseEntity<ApiResponse<SubTableDataResponse>> getSubTableData(
            @Parameter(description = "主任务ID")
            @PathVariable String taskId) {
        
        try {
            log.info("查询主任务子表数据，任务ID: {}", taskId);
            
            // 1. 查询任务信息
            Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
            if (task == null) {
                log.warn("任务不存在: {}", taskId);
                return ResponseEntity.ok(ApiResponse.error(
                        "TASK_NOT_FOUND",
                        "任务不存在"
                ));
            }
            
            // 2. 获取流程实例ID
            String processInstanceId = task.getProcessInstanceId();
            
            // 3. 从流程变量中获取子表配置信息
            Map<String, Object> processVariables = runtimeService.getVariables(processInstanceId);
            
            // 查找多实例集合变量（格式：multiInstance_{subTableName}_collection）
            String collectionVariableName = null;
            String subTableName = null;
            for (String varName : processVariables.keySet()) {
                if (varName.startsWith("multiInstance_") && varName.endsWith("_collection")) {
                    collectionVariableName = varName;
                    // 提取子表名称：multiInstance_{subTableName}_collection
                    subTableName = varName.substring("multiInstance_".length(), 
                            varName.length() - "_collection".length());
                    break;
                }
            }
            
            if (subTableName == null) {
                log.warn("未找到多实例集合变量，流程实例ID: {}", processInstanceId);
                return ResponseEntity.ok(ApiResponse.error(
                        "MULTI_INSTANCE_CONFIG_NOT_FOUND",
                        "未找到多实例配置信息"
                ));
            }
            if (!subTableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                log.warn("非法子表名（跳过查询）: {}", subTableName);
                return ResponseEntity.ok(ApiResponse.error(
                        "INVALID_SUBTABLE_NAME",
                        "子表配置无效"
                ));
            }
            List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, subTableName);
            String pkWhere = SubTableRowKeySupport.buildPkWhereClause(pkCols);

            // 4. 获取集合变量（元素含 rowKey 和/或 rowId）
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> collectionData =
                    (List<Map<String, Object>>) processVariables.get(collectionVariableName);
            
            if (collectionData == null || collectionData.isEmpty()) {
                log.warn("多实例集合变量为空，流程实例ID: {}", processInstanceId);
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
                    log.warn("子表行不存在: table={}, rowKey={}", subTableName, rk);
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
            
            log.info("查询主任务子表数据成功，任务ID: {}, 子表: {}, 数据行数: {}",
                    taskId, subTableName, rows.size());
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("查询主任务子表数据失败，任务ID: {}", taskId, e);
            return ResponseEntity.ok(ApiResponse.error(
                    "QUERY_FAILED",
                    "查询子表数据失败: " + e.getMessage()
            ));
        }
    }
}
