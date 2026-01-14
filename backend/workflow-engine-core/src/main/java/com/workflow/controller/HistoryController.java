package com.workflow.controller;

import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.HistoryQueryResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@Tag(name = "历史数据", description = "流程和任务历史数据查询API")
public class HistoryController {

    private final HistoryService historyService;

    /**
     * 获取流程实例的任务历史
     */
    @GetMapping("/tasks")
    @Operation(summary = "获取任务历史", description = "根据流程实例ID获取任务历史")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTaskHistory(
            @Parameter(description = "流程实例ID", required = true)
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
    @Operation(summary = "获取活动历史", description = "根据流程实例ID获取活动历史")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getActivityHistory(
            @Parameter(description = "流程实例ID", required = true)
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
}
