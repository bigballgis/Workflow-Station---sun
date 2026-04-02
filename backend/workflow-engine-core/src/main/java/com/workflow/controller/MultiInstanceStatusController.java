package com.workflow.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.MultiInstanceStatusResponse;
import com.workflow.dto.response.SubTableDataResponse;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.repository.ExtendedTaskInfoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.Execution;
import org.flowable.task.api.Task;
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
    private final TaskService taskService;
    private final ExtendedTaskInfoRepository extendedTaskInfoRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

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
            
            // 2. 查找多实例父执行（通过变量判断）
            Execution multiInstanceExecution = null;
            for (Execution execution : executions) {
                Map<String, Object> variables = runtimeService.getVariables(execution.getId());
                if (variables.containsKey("nrOfInstances")) {
                    multiInstanceExecution = execution;
                    break;
                }
            }
            
            if (multiInstanceExecution == null) {
                log.warn("流程实例 {} 中未找到多实例执行", processInstanceId);
                return ResponseEntity.ok(ApiResponse.error(
                        "MULTI_INSTANCE_NOT_FOUND",
                        "流程实例中未找到多实例子流程"
                ));
            }
            
            // 3. 从 Flowable 变量中获取多实例统计信息
            Map<String, Object> miVariables = runtimeService.getVariables(multiInstanceExecution.getId());
            Integer nrOfInstances = (Integer) miVariables.get("nrOfInstances");
            Integer nrOfCompletedInstances = (Integer) miVariables.get("nrOfCompletedInstances");
            Integer nrOfActiveInstances = (Integer) miVariables.get("nrOfActiveInstances");
            
            // 4. 查询流程实例的所有扩展任务信息
            List<ExtendedTaskInfo> allTaskInfos = extendedTaskInfoRepository
                    .findByProcessInstanceIdAndIsDeletedFalse(processInstanceId);
            
            // 5. 过滤出多实例子任务（通过 extendedProperties 中的 multiInstance 标记）
            List<ExtendedTaskInfo> multiInstanceTasks = allTaskInfos.stream()
                    .filter(this::isMultiInstanceTask)
                    .collect(Collectors.toList());
            
            // 6. 构建子任务详情列表
            List<MultiInstanceStatusResponse.SubTaskDetail> taskDetails = new ArrayList<>();
            for (ExtendedTaskInfo taskInfo : multiInstanceTasks) {
                Map<String, Object> extProps = parseExtendedProperties(taskInfo.getExtendedProperties());
                Long subTableRowId = extProps.containsKey("subTableRowId") 
                        ? ((Number) extProps.get("subTableRowId")).longValue() 
                        : null;
                
                MultiInstanceStatusResponse.SubTaskDetail detail = MultiInstanceStatusResponse.SubTaskDetail.builder()
                        .taskId(taskInfo.getTaskId())
                        .taskName(taskInfo.getTaskName())
                        .assignee(taskInfo.getAssignmentTarget())
                        .assigneeName(getUserName(taskInfo.getAssignmentTarget()))
                        .status(taskInfo.getStatus())
                        .subTableRowId(subTableRowId)
                        .createdTime(taskInfo.getCreatedTime())
                        .completedTime(taskInfo.getCompletedTime())
                        .completedBy(taskInfo.getCompletedBy())
                        .completedByName(taskInfo.getCompletedBy() != null ? getUserName(taskInfo.getCompletedBy()) : null)
                        .build();
                
                taskDetails.add(detail);
            }
            
            // 7. 统计已取消的实例数
            long cancelledCount = multiInstanceTasks.stream()
                    .filter(t -> "CANCELLED".equals(t.getStatus()))
                    .count();
            
            // 8. 确定多实例状态
            String status = determineMultiInstanceStatus(nrOfInstances, nrOfCompletedInstances, cancelledCount);
            
            // 9. 获取多实例活动信息
            String activityId = multiInstanceExecution.getActivityId();
            String activityName = getActivityName(processInstanceId, activityId);
            
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
            
            // 4. 获取集合变量（包含所有子表行ID）
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
            
            // 5. 查询所有子表数据行
            List<Long> rowIds = collectionData.stream()
                    .map(item -> ((Number) item.get("rowId")).longValue())
                    .collect(Collectors.toList());
            
            // 构建查询SQL
            String placeholders = rowIds.stream()
                    .map(id -> "?")
                    .collect(Collectors.joining(","));
            String sql = String.format("SELECT * FROM %s WHERE id IN (%s)", subTableName, placeholders);
            
            List<Map<String, Object>> subTableRows = jdbcTemplate.queryForList(sql, rowIds.toArray());
            
            // 6. 查询所有子任务的状态信息
            List<ExtendedTaskInfo> allTaskInfos = extendedTaskInfoRepository
                    .findByProcessInstanceIdAndIsDeletedFalse(processInstanceId);
            
            // 过滤出多实例子任务
            Map<Long, ExtendedTaskInfo> rowIdToTaskInfo = new HashMap<>();
            for (ExtendedTaskInfo taskInfo : allTaskInfos) {
                if (isMultiInstanceTask(taskInfo)) {
                    Map<String, Object> extProps = parseExtendedProperties(taskInfo.getExtendedProperties());
                    Long subTableRowId = extProps.containsKey("subTableRowId") 
                            ? ((Number) extProps.get("subTableRowId")).longValue() 
                            : null;
                    if (subTableRowId != null) {
                        rowIdToTaskInfo.put(subTableRowId, taskInfo);
                    }
                }
            }
            
            // 7. 构建响应数据
            List<SubTableDataResponse.SubTableRow> rows = new ArrayList<>();
            for (Map<String, Object> rowData : subTableRows) {
                Long rowId = ((Number) rowData.get("id")).longValue();
                ExtendedTaskInfo taskInfo = rowIdToTaskInfo.get(rowId);
                
                String assignee = null;
                String assigneeName = null;
                String status = "PENDING"; // 默认状态
                
                if (taskInfo != null) {
                    assignee = taskInfo.getAssignmentTarget();
                    assigneeName = getUserName(assignee);
                    status = taskInfo.getStatus();
                } else {
                    // 如果没有对应的任务信息，尝试从子表数据中获取 assignee
                    // 假设子表中有 assignee 字段（根据设计文档，处理人字段名可能不同）
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
                
                SubTableDataResponse.SubTableRow row = SubTableDataResponse.SubTableRow.builder()
                        .id(rowId)
                        .data(rowData)
                        .assignee(assignee)
                        .assigneeName(assigneeName)
                        .status(status)
                        .build();
                
                rows.add(row);
            }
            
            // 8. 构建响应
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
