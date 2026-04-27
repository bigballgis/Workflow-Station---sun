package com.workflow.controller;

import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.HistoryQueryResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 历史数据控制器
 * 
 * 提供流程和任务历史数据查询的RESTful API接口
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
@Tag(name = "History Data", description = "Process and task history data query API")
public class HistoryController {

    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    
    // 缓存流程定义名称，避免重复查询
    private final Map<String, String> processDefinitionNameCache = new ConcurrentHashMap<>();
    
    /**
     * 获取用户已处理的任务列表
     */
    @GetMapping("/completed-tasks")
    @Operation(summary = "Get user completed tasks", description = "Query user completed task history list")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCompletedTasks(
            @Parameter(description = "User ID", required = true)
            @RequestParam("userId") String userId,
            @Parameter(description = "Page number, starting from 0")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "Keyword search")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "Start time")
            @RequestParam(value = "startTime", required = false) String startTime,
            @Parameter(description = "End time")
            @RequestParam(value = "endTime", required = false) String endTime) {
        
        log.info("Getting completed tasks for user: {}, page: {}, size: {}", userId, page, size);
        
        // 构建查询
        var query = historyService.createHistoricTaskInstanceQuery()
            .taskAssignee(userId)
            .finished()
            .orderByHistoricTaskInstanceEndTime().desc();
        
        // 应用关键词搜索
        if (keyword != null && !keyword.isEmpty()) {
            query.taskNameLike("%" + keyword + "%");
        }
        
        // 应用时间范围
        if (startTime != null && !startTime.isEmpty()) {
            try {
                java.time.LocalDateTime start = java.time.LocalDateTime.parse(startTime);
                query.taskCompletedAfter(java.util.Date.from(start.atZone(ZoneId.systemDefault()).toInstant()));
            } catch (Exception e) {
                log.warn("Invalid startTime format: {}", startTime);
            }
        }
        if (endTime != null && !endTime.isEmpty()) {
            try {
                java.time.LocalDateTime end = java.time.LocalDateTime.parse(endTime);
                query.taskCompletedBefore(java.util.Date.from(end.atZone(ZoneId.systemDefault()).toInstant()));
            } catch (Exception e) {
                log.warn("Invalid endTime format: {}", endTime);
            }
        }
        
        // 获取总数
        long totalCount = query.count();
        
        // 分页查询
        List<HistoricTaskInstance> tasks = query.listPage(page * size, size);
        
        // 转换为前端期望的格式
        List<Map<String, Object>> taskList = tasks.stream()
            .map(task -> {
                Map<String, Object> item = new HashMap<>();
                item.put("taskId", task.getId());
                item.put("taskName", task.getName());
                item.put("taskDescription", task.getDescription());
                item.put("processInstanceId", task.getProcessInstanceId());
                item.put("processDefinitionId", task.getProcessDefinitionId());
                item.put("processDefinitionKey", extractProcessDefinitionKey(task.getProcessDefinitionId()));
                item.put("processDefinitionName", getProcessDefinitionName(task.getProcessDefinitionId()));
                item.put("assignee", task.getAssignee());
                item.put("owner", task.getOwner());
                item.put("startTime", task.getStartTime() != null ? 
                    task.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString() : null);
                item.put("endTime", task.getEndTime() != null ? 
                    task.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString() : null);
                item.put("durationInMillis", task.getDurationInMillis());
                item.put("deleteReason", task.getDeleteReason());
                // 根据 deleteReason 判断操作类型
                String action = "completed";
                if (task.getDeleteReason() != null) {
                    if (task.getDeleteReason().contains("approved") || task.getDeleteReason().contains("APPROVED")) {
                        action = "approved";
                    } else if (task.getDeleteReason().contains("rejected") || task.getDeleteReason().contains("REJECTED")) {
                        action = "rejected";
                    } else if (task.getDeleteReason().contains("transfer") || task.getDeleteReason().contains("TRANSFER")) {
                        action = "transferred";
                    } else if (task.getDeleteReason().contains("delegate") || task.getDeleteReason().contains("DELEGATE")) {
                        action = "delegated";
                    }
                }
                item.put("action", action);
                return item;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("content", taskList);
        result.put("page", page);
        result.put("size", size);
        result.put("totalElements", totalCount);
        result.put("totalPages", (totalCount + size - 1) / size);
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * 从 processDefinitionId 中提取 processDefinitionKey
     * Flowable 7.0 可能只返回 UUID，此时查询 repositoryService 获取真实 key
     */
    private String extractProcessDefinitionKey(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        // 标准格式: key:version:uuid
        int colonIndex = processDefinitionId.indexOf(':');
        if (colonIndex > 0) {
            return processDefinitionId.substring(0, colonIndex);
        }
        // Flowable 7.0 可能仅返回 UUID，查询 repositoryService 获取真实 key
        try {
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
            if (pd != null) {
                return pd.getKey();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve process definition key for ID {}: {}", processDefinitionId, e.getMessage());
        }
        return processDefinitionId;
    }
    
    /**
     * 获取流程定义名称（带缓存）
     */
    private String getProcessDefinitionName(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        
        return processDefinitionNameCache.computeIfAbsent(processDefinitionId, id -> {
            try {
                ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(id)
                    .singleResult();
                if (processDefinition != null) {
                    if (processDefinition.getName() != null) {
                        return processDefinition.getName();
                    }
                    // 用 key 作为名称 fallback
                    return processDefinition.getKey();
                }
            } catch (Exception e) {
                log.warn("Failed to get process definition name for {}: {}", id, e.getMessage());
            }
            // 如果查询失败，返回 key 部分
            return extractProcessDefinitionKey(id);
        });
    }

    /**
     * 获取流程实例的任务历史
     */
    @GetMapping("/tasks")
    @Operation(summary = "Get task history", description = "Get task history by process instance ID")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTaskHistory(
            @Parameter(description = "Process instance ID", required = true)
            @RequestParam("processInstanceId") String processInstanceId) {
        
        log.info("Getting task history for process instance: {}", processInstanceId);
        
        // 查询历史任务实例
        List<HistoricTaskInstance> tasks = historyService
            .createHistoricTaskInstanceQuery()
            .processInstanceId(processInstanceId)
            .orderByHistoricTaskInstanceStartTime().asc()
            .list();
        
        // 查询历史活动实例（包括开始和结束事件）
        List<HistoricActivityInstance> activities = historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(processInstanceId)
            .orderByHistoricActivityInstanceStartTime().asc()
            .list();
        
        // 转换任务历史为前端期望的格式
        List<Map<String, Object>> taskInstances = tasks.stream()
            .map(task -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", task.getId());
                item.put("taskId", task.getId());
                item.put("name", task.getName());
                item.put("activityId", task.getTaskDefinitionKey());
                item.put("activityName", task.getName());
                item.put("activityType", "userTask");
                item.put("assignee", task.getAssignee());
                item.put("owner", task.getOwner());
                item.put("startTime", task.getStartTime() != null ? 
                    task.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString() : null);
                item.put("endTime", task.getEndTime() != null ? 
                    task.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString() : null);
                item.put("durationInMillis", task.getDurationInMillis());
                item.put("deleteReason", task.getDeleteReason());
                item.put("processInstanceId", task.getProcessInstanceId());
                item.put("processDefinitionId", task.getProcessDefinitionId());
                return item;
            })
            .collect(Collectors.toList());
        
        // 转换活动历史
        List<Map<String, Object>> activityInstances = activities.stream()
            .map(activity -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", activity.getId());
                item.put("activityId", activity.getActivityId());
                item.put("activityName", activity.getActivityName());
                item.put("activityType", activity.getActivityType());
                item.put("assignee", activity.getAssignee());
                item.put("taskId", activity.getTaskId());
                item.put("startTime", activity.getStartTime() != null ? 
                    activity.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString() : null);
                item.put("endTime", activity.getEndTime() != null ? 
                    activity.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString() : null);
                item.put("durationInMillis", activity.getDurationInMillis());
                item.put("deleteReason", activity.getDeleteReason());
                item.put("processInstanceId", activity.getProcessInstanceId());
                item.put("processDefinitionId", activity.getProcessDefinitionId());
                return item;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("taskInstances", taskInstances);
        result.put("activityInstances", activityInstances);
        result.put("totalCount", tasks.size());
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * 获取流程实例的活动历史
     */
    @GetMapping("/activities")
    @Operation(summary = "Get activity history", description = "Get activity history by process instance ID")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getActivityHistory(
            @Parameter(description = "Process instance ID", required = true)
            @RequestParam("processInstanceId") String processInstanceId) {
        
        log.info("Getting activity history for process instance: {}", processInstanceId);
        
        List<HistoricActivityInstance> activities = historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(processInstanceId)
            .orderByHistoricActivityInstanceStartTime().asc()
            .list();
        
        List<Map<String, Object>> result = activities.stream()
            .map(activity -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", activity.getId());
                item.put("activityId", activity.getActivityId());
                item.put("activityName", activity.getActivityName());
                item.put("activityType", activity.getActivityType());
                item.put("assignee", activity.getAssignee());
                item.put("taskId", activity.getTaskId());
                item.put("startTime", activity.getStartTime() != null ? 
                    activity.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString() : null);
                item.put("endTime", activity.getEndTime() != null ? 
                    activity.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString() : null);
                item.put("durationInMillis", activity.getDurationInMillis());
                return item;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * 获取用户流程统计数据
     */
    @GetMapping("/process-statistics")
    @Operation(summary = "Get user process statistics", description = "Query process statistics initiated by user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProcessStatistics(
            @Parameter(description = "User ID", required = true)
            @RequestParam("userId") String userId) {
        
        log.info("Getting process statistics for user: {}", userId);
        
        // 查询用户发起的所有流程实例
        var allProcessInstances = historyService.createHistoricProcessInstanceQuery()
            .startedBy(userId)
            .list();
        
        long initiatedCount = allProcessInstances.size();
        
        // 进行中的流程
        long inProgressCount = allProcessInstances.stream()
            .filter(pi -> pi.getEndTime() == null)
            .count();
        
        // 本月完成的流程
        java.time.LocalDateTime startOfMonth = java.time.LocalDateTime.now()
            .withDayOfMonth(1)
            .withHour(0).withMinute(0).withSecond(0).withNano(0);
        java.util.Date startOfMonthDate = java.util.Date.from(
            startOfMonth.atZone(ZoneId.systemDefault()).toInstant());
        
        long completedThisMonthCount = allProcessInstances.stream()
            .filter(pi -> pi.getEndTime() != null && pi.getEndTime().after(startOfMonthDate))
            .count();
        
        // 计算通过率（已完成且未被拒绝的流程 / 已完成的流程）
        long completedCount = allProcessInstances.stream()
            .filter(pi -> pi.getEndTime() != null)
            .count();
        
        // 被拒绝的流程（deleteReason 包含 rejected）
        long rejectedCount = allProcessInstances.stream()
            .filter(pi -> pi.getEndTime() != null && pi.getDeleteReason() != null 
                && (pi.getDeleteReason().toLowerCase().contains("reject") 
                    || pi.getDeleteReason().toLowerCase().contains("denied")))
            .count();
        
        double approvalRate = completedCount > 0 
            ? (double)(completedCount - rejectedCount) / completedCount 
            : 1.0;
        
        // 流程类型分布
        Map<String, Long> typeDistribution = allProcessInstances.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                pi -> getProcessDefinitionName(pi.getProcessDefinitionId()),
                java.util.stream.Collectors.counting()
            ));
        
        Map<String, Object> result = new HashMap<>();
        result.put("initiatedCount", initiatedCount);
        result.put("inProgressCount", inProgressCount);
        result.put("completedThisMonthCount", completedThisMonthCount);
        result.put("approvalRate", approvalRate);
        result.put("typeDistribution", typeDistribution);
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
