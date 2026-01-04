package com.workflow.component;

import com.workflow.dto.request.ProcessMonitorQueryRequest;
import com.workflow.dto.response.ProcessMonitorResult;
import com.workflow.dto.response.ProcessStatisticsResult;
import com.workflow.dto.response.TaskStatisticsResult;
import com.workflow.dto.response.PerformanceMetricsResult;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程监控组件
 * 
 * 负责流程实例状态查询、任务统计、性能指标计算和可视化数据生成
 * 支持多维度条件过滤和分页查询
 * 
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessMonitorComponent {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final TaskService taskService;

    /**
     * 查询流程实例监控信息
     * 支持多维度条件过滤和分页查询
     * 
     * @param request 查询请求
     * @return 监控结果
     */
    @Transactional(readOnly = true)
    public ProcessMonitorResult queryProcessInstances(ProcessMonitorQueryRequest request) {
        log.info("查询流程实例监控信息: processDefinitionKey={}, status={}", 
                request.getProcessDefinitionKey(), request.getStatus());
        
        try {
            // 验证请求参数
            validateQueryRequest(request);
            
            List<Map<String, Object>> processInstances = new ArrayList<>();
            long totalCount = 0;
            
            if ("ACTIVE".equals(request.getStatus()) || request.getStatus() == null) {
                // 查询运行中的流程实例
                ProcessInstanceQuery query = buildRuntimeQuery(request);
                List<ProcessInstance> activeInstances = query.listPage(
                        request.getOffset() != null ? request.getOffset() : 0,
                        request.getLimit() != null ? request.getLimit() : 20
                );
                
                for (ProcessInstance instance : activeInstances) {
                    processInstances.add(convertToProcessInstanceInfo(instance));
                }
                
                totalCount += query.count();
            }
            
            if ("COMPLETED".equals(request.getStatus()) || "TERMINATED".equals(request.getStatus()) || request.getStatus() == null) {
                // 查询历史流程实例
                HistoricProcessInstanceQuery historyQuery = buildHistoryQuery(request);
                List<HistoricProcessInstance> historicInstances = historyQuery.listPage(
                        request.getOffset() != null ? request.getOffset() : 0,
                        request.getLimit() != null ? request.getLimit() : 20
                );
                
                for (HistoricProcessInstance instance : historicInstances) {
                    processInstances.add(convertToProcessInstanceInfo(instance));
                }
                
                totalCount += historyQuery.count();
            }
            
            return ProcessMonitorResult.builder()
                    .success(true)
                    .processInstances(processInstances)
                    .totalCount(totalCount)
                    .currentPage(request.getOffset() != null && request.getLimit() != null ? 
                               (request.getOffset() / request.getLimit()) + 1 : 1)
                    .pageSize(request.getLimit() != null ? request.getLimit() : 20)
                    .build();
                    
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询流程实例监控信息失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("MONITOR_QUERY_FAILED", "查询流程实例监控信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取流程统计信息
     * 
     * @param processDefinitionKey 流程定义键（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 流程统计结果
     */
    @Transactional(readOnly = true)
    public ProcessStatisticsResult getProcessStatistics(String processDefinitionKey, Date startTime, Date endTime) {
        log.info("获取流程统计信息: processDefinitionKey={}, startTime={}, endTime={}", 
                processDefinitionKey, startTime, endTime);
        
        try {
            // 运行中的流程实例数量
            ProcessInstanceQuery activeQuery = runtimeService.createProcessInstanceQuery();
            if (StringUtils.hasText(processDefinitionKey)) {
                activeQuery.processDefinitionKey(processDefinitionKey);
            }
            long activeCount = activeQuery.count();
            
            // 已完成的流程实例数量
            HistoricProcessInstanceQuery completedQuery = historyService.createHistoricProcessInstanceQuery()
                    .finished();
            if (StringUtils.hasText(processDefinitionKey)) {
                completedQuery.processDefinitionKey(processDefinitionKey);
            }
            if (startTime != null) {
                completedQuery.startedAfter(startTime);
            }
            if (endTime != null) {
                completedQuery.startedBefore(endTime);
            }
            long completedCount = completedQuery.count();
            
            // 已终止的流程实例数量
            HistoricProcessInstanceQuery terminatedQuery = historyService.createHistoricProcessInstanceQuery()
                    .unfinished();
            if (StringUtils.hasText(processDefinitionKey)) {
                terminatedQuery.processDefinitionKey(processDefinitionKey);
            }
            if (startTime != null) {
                terminatedQuery.startedAfter(startTime);
            }
            if (endTime != null) {
                terminatedQuery.startedBefore(endTime);
            }
            long terminatedCount = terminatedQuery.count();
            
            // 按状态分组统计
            Map<String, Long> statusStatistics = new HashMap<>();
            statusStatistics.put("ACTIVE", activeCount);
            statusStatistics.put("COMPLETED", completedCount);
            statusStatistics.put("TERMINATED", terminatedCount);
            
            // 按流程定义分组统计
            Map<String, Long> processDefinitionStatistics = getProcessDefinitionStatistics(startTime, endTime);
            
            // 按时间分组统计（最近7天）
            Map<String, Long> timeStatistics = getTimeStatistics(processDefinitionKey, startTime, endTime);
            
            return ProcessStatisticsResult.builder()
                    .totalCount(activeCount + completedCount + terminatedCount)
                    .activeCount(activeCount)
                    .completedCount(completedCount)
                    .terminatedCount(terminatedCount)
                    .statusStatistics(statusStatistics)
                    .processDefinitionStatistics(processDefinitionStatistics)
                    .timeStatistics(timeStatistics)
                    .build();
                    
        } catch (Exception e) {
            log.error("获取流程统计信息失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("STATISTICS_QUERY_FAILED", "获取流程统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务统计信息
     * 
     * @param assignee 任务分配人（可选）
     * @param processDefinitionKey 流程定义键（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 任务统计结果
     */
    @Transactional(readOnly = true)
    public TaskStatisticsResult getTaskStatistics(String assignee, String processDefinitionKey, Date startTime, Date endTime) {
        log.info("获取任务统计信息: assignee={}, processDefinitionKey={}, startTime={}, endTime={}", 
                assignee, processDefinitionKey, startTime, endTime);
        
        try {
            // 待办任务数量
            TaskQuery pendingQuery = taskService.createTaskQuery();
            if (StringUtils.hasText(assignee)) {
                pendingQuery.taskAssignee(assignee);
            }
            if (StringUtils.hasText(processDefinitionKey)) {
                pendingQuery.processDefinitionKey(processDefinitionKey);
            }
            long pendingCount = pendingQuery.count();
            
            // 已完成任务数量
            HistoricTaskInstanceQuery completedQuery = historyService.createHistoricTaskInstanceQuery()
                    .finished();
            if (StringUtils.hasText(assignee)) {
                completedQuery.taskAssignee(assignee);
            }
            if (StringUtils.hasText(processDefinitionKey)) {
                completedQuery.processDefinitionKey(processDefinitionKey);
            }
            if (startTime != null) {
                completedQuery.taskCompletedAfter(startTime);
            }
            if (endTime != null) {
                completedQuery.taskCompletedBefore(endTime);
            }
            long completedCount = completedQuery.count();
            
            // 超时任务数量
            TaskQuery overdueQuery = taskService.createTaskQuery()
                    .taskDueBefore(new Date());
            if (StringUtils.hasText(assignee)) {
                overdueQuery.taskAssignee(assignee);
            }
            if (StringUtils.hasText(processDefinitionKey)) {
                overdueQuery.processDefinitionKey(processDefinitionKey);
            }
            long overdueCount = overdueQuery.count();
            
            // 按任务名称分组统计
            Map<String, Long> taskNameStatistics = getTaskNameStatistics(assignee, processDefinitionKey, startTime, endTime);
            
            // 按分配人分组统计
            Map<String, Long> assigneeStatistics = getAssigneeStatistics(processDefinitionKey, startTime, endTime);
            
            // 平均处理时间统计
            Double averageProcessingTime = getAverageProcessingTime(assignee, processDefinitionKey, startTime, endTime);
            
            return TaskStatisticsResult.builder()
                    .totalCount(pendingCount + completedCount)
                    .pendingCount(pendingCount)
                    .completedCount(completedCount)
                    .overdueCount(overdueCount)
                    .taskNameStatistics(taskNameStatistics)
                    .assigneeStatistics(assigneeStatistics)
                    .averageProcessingTime(averageProcessingTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("获取任务统计信息失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("TASK_STATISTICS_FAILED", "获取任务统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取性能指标
     * 
     * @param processDefinitionKey 流程定义键（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 性能指标结果
     */
    @Transactional(readOnly = true)
    public PerformanceMetricsResult getPerformanceMetrics(String processDefinitionKey, Date startTime, Date endTime) {
        log.info("获取性能指标: processDefinitionKey={}, startTime={}, endTime={}", 
                processDefinitionKey, startTime, endTime);
        
        try {
            // 平均流程执行时间
            Double averageProcessDuration = getAverageProcessDuration(processDefinitionKey, startTime, endTime);
            
            // 最长流程执行时间
            Long maxProcessDuration = getMaxProcessDuration(processDefinitionKey, startTime, endTime);
            
            // 最短流程执行时间
            Long minProcessDuration = getMinProcessDuration(processDefinitionKey, startTime, endTime);
            
            // 流程成功率
            Double processSuccessRate = getProcessSuccessRate(processDefinitionKey, startTime, endTime);
            
            // 任务平均等待时间
            Double averageTaskWaitTime = getAverageTaskWaitTime(processDefinitionKey, startTime, endTime);
            
            // 系统吞吐量（每小时处理的流程数）
            Double throughputPerHour = getThroughputPerHour(processDefinitionKey, startTime, endTime);
            
            // 资源利用率指标
            Map<String, Double> resourceUtilization = getResourceUtilization();
            
            return PerformanceMetricsResult.builder()
                    .averageProcessDuration(averageProcessDuration)
                    .maxProcessDuration(maxProcessDuration)
                    .minProcessDuration(minProcessDuration)
                    .processSuccessRate(processSuccessRate)
                    .averageTaskWaitTime(averageTaskWaitTime)
                    .throughputPerHour(throughputPerHour)
                    .resourceUtilization(resourceUtilization)
                    .build();
                    
        } catch (Exception e) {
            log.error("获取性能指标失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("PERFORMANCE_METRICS_FAILED", "获取性能指标失败: " + e.getMessage());
        }
    }

    /**
     * 获取流程执行可视化数据
     * 
     * @param processInstanceId 流程实例ID
     * @return 可视化数据
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getProcessVisualizationData(String processInstanceId) {
        log.info("获取流程执行可视化数据: processInstanceId={}", processInstanceId);
        
        try {
            Map<String, Object> visualizationData = new HashMap<>();
            
            // 获取流程实例信息
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            
            HistoricProcessInstance historicProcessInstance = null;
            if (processInstance == null) {
                historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .singleResult();
            }
            
            if (processInstance == null && historicProcessInstance == null) {
                throw new WorkflowValidationException(List.of(
                    new WorkflowValidationException.ValidationError("processInstanceId", "流程实例不存在", processInstanceId)
                ));
            }
            
            // 基本信息
            visualizationData.put("processInstanceId", processInstanceId);
            visualizationData.put("processDefinitionId", processInstance != null ? 
                    processInstance.getProcessDefinitionId() : historicProcessInstance.getProcessDefinitionId());
            visualizationData.put("businessKey", processInstance != null ? 
                    processInstance.getBusinessKey() : historicProcessInstance.getBusinessKey());
            visualizationData.put("startTime", processInstance != null ? 
                    processInstance.getStartTime() : historicProcessInstance.getStartTime());
            visualizationData.put("endTime", historicProcessInstance != null ? 
                    historicProcessInstance.getEndTime() : null);
            visualizationData.put("isActive", processInstance != null);
            
            // 当前活动节点
            if (processInstance != null) {
                List<String> activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);
                visualizationData.put("activeActivityIds", activeActivityIds);
            }
            
            // 已完成的活动节点
            List<Map<String, Object>> completedActivities = getCompletedActivities(processInstanceId);
            visualizationData.put("completedActivities", completedActivities);
            
            // 流程变量
            Map<String, Object> variables = processInstance != null ?
                    runtimeService.getVariables(processInstanceId) :
                    historyService.createHistoricVariableInstanceQuery()
                            .processInstanceId(processInstanceId)
                            .list()
                            .stream()
                            .collect(Collectors.toMap(
                                    var -> var.getVariableName(),
                                    var -> var.getValue()
                            ));
            visualizationData.put("variables", variables);
            
            // 执行路径
            List<Map<String, Object>> executionPath = getExecutionPath(processInstanceId);
            visualizationData.put("executionPath", executionPath);
            
            return visualizationData;
            
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取流程执行可视化数据失败: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("VISUALIZATION_DATA_FAILED", "获取流程执行可视化数据失败: " + e.getMessage());
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 验证查询请求参数
     */
    private void validateQueryRequest(ProcessMonitorQueryRequest request) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();
        
        if (request.getLimit() != null && request.getLimit() <= 0) {
            errors.add(new WorkflowValidationException.ValidationError("limit", "分页大小必须大于0", request.getLimit()));
        }
        
        if (request.getOffset() != null && request.getOffset() < 0) {
            errors.add(new WorkflowValidationException.ValidationError("offset", "偏移量不能为负数", request.getOffset()));
        }
        
        if (request.getStartTime() != null && request.getEndTime() != null && 
            request.getStartTime().after(request.getEndTime())) {
            errors.add(new WorkflowValidationException.ValidationError("timeRange", "开始时间不能晚于结束时间", null));
        }
        
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /**
     * 构建运行时查询
     */
    private ProcessInstanceQuery buildRuntimeQuery(ProcessMonitorQueryRequest request) {
        ProcessInstanceQuery query = runtimeService.createProcessInstanceQuery();
        
        if (StringUtils.hasText(request.getProcessDefinitionKey())) {
            query.processDefinitionKey(request.getProcessDefinitionKey());
        }
        
        if (StringUtils.hasText(request.getBusinessKey())) {
            query.processInstanceBusinessKey(request.getBusinessKey());
        }
        
        if (request.getStartTime() != null) {
            query.startedAfter(request.getStartTime());
        }
        
        if (request.getEndTime() != null) {
            query.startedBefore(request.getEndTime());
        }
        
        // 排序 - 简化为只使用ID排序
        query.orderByProcessInstanceId();
        
        if ("DESC".equalsIgnoreCase(request.getOrderDirection())) {
            query.desc();
        } else {
            query.asc();
        }
        
        return query;
    }

    /**
     * 构建历史查询
     */
    private HistoricProcessInstanceQuery buildHistoryQuery(ProcessMonitorQueryRequest request) {
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();
        
        if ("COMPLETED".equals(request.getStatus())) {
            query.finished();
        } else if ("TERMINATED".equals(request.getStatus())) {
            query.unfinished();
        }
        
        if (StringUtils.hasText(request.getProcessDefinitionKey())) {
            query.processDefinitionKey(request.getProcessDefinitionKey());
        }
        
        if (StringUtils.hasText(request.getBusinessKey())) {
            query.processInstanceBusinessKey(request.getBusinessKey());
        }
        
        if (request.getStartTime() != null) {
            query.startedAfter(request.getStartTime());
        }
        
        if (request.getEndTime() != null) {
            query.startedBefore(request.getEndTime());
        }
        
        // 排序 - 简化为只使用ID排序
        query.orderByProcessInstanceId();
        
        if ("DESC".equalsIgnoreCase(request.getOrderDirection())) {
            query.desc();
        } else {
            query.asc();
        }
        
        return query;
    }

    /**
     * 转换流程实例信息
     */
    private Map<String, Object> convertToProcessInstanceInfo(ProcessInstance instance) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", instance.getId());
        info.put("processDefinitionId", instance.getProcessDefinitionId());
        info.put("processDefinitionKey", instance.getProcessDefinitionKey());
        info.put("processDefinitionName", instance.getProcessDefinitionName());
        info.put("businessKey", instance.getBusinessKey());
        info.put("startTime", instance.getStartTime());
        info.put("startUserId", instance.getStartUserId());
        info.put("status", "ACTIVE");
        info.put("suspended", instance.isSuspended());
        return info;
    }

    /**
     * 转换历史流程实例信息
     */
    private Map<String, Object> convertToProcessInstanceInfo(HistoricProcessInstance instance) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", instance.getId());
        info.put("processDefinitionId", instance.getProcessDefinitionId());
        info.put("processDefinitionKey", instance.getProcessDefinitionKey());
        info.put("processDefinitionName", instance.getProcessDefinitionName());
        info.put("businessKey", instance.getBusinessKey());
        info.put("startTime", instance.getStartTime());
        info.put("endTime", instance.getEndTime());
        info.put("startUserId", instance.getStartUserId());
        info.put("status", instance.getEndTime() != null ? "COMPLETED" : "TERMINATED");
        info.put("durationInMillis", instance.getDurationInMillis());
        info.put("deleteReason", instance.getDeleteReason());
        return info;
    }

    /**
     * 获取按流程定义分组的统计信息
     */
    private Map<String, Long> getProcessDefinitionStatistics(Date startTime, Date endTime) {
        // 简化实现，实际应该使用数据库聚合查询
        Map<String, Long> statistics = new HashMap<>();
        
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();
        if (startTime != null) {
            query.startedAfter(startTime);
        }
        if (endTime != null) {
            query.startedBefore(endTime);
        }
        
        List<HistoricProcessInstance> instances = query.list();
        Map<String, Long> counts = instances.stream()
                .collect(Collectors.groupingBy(
                        HistoricProcessInstance::getProcessDefinitionKey,
                        Collectors.counting()
                ));
        
        statistics.putAll(counts);
        return statistics;
    }

    /**
     * 获取按时间分组的统计信息
     */
    private Map<String, Long> getTimeStatistics(String processDefinitionKey, Date startTime, Date endTime) {
        Map<String, Long> statistics = new HashMap<>();
        
        // 简化实现，按天统计最近7天
        LocalDateTime now = LocalDateTime.now();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime dayStart = now.minusDays(i).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime dayEnd = dayStart.withHour(23).withMinute(59).withSecond(59);
            
            HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
                    .startedAfter(Date.from(dayStart.atZone(ZoneId.systemDefault()).toInstant()))
                    .startedBefore(Date.from(dayEnd.atZone(ZoneId.systemDefault()).toInstant()));
            
            if (StringUtils.hasText(processDefinitionKey)) {
                query.processDefinitionKey(processDefinitionKey);
            }
            
            long count = query.count();
            statistics.put(dayStart.toLocalDate().toString(), count);
        }
        
        return statistics;
    }

    /**
     * 获取按任务名称分组的统计信息
     */
    private Map<String, Long> getTaskNameStatistics(String assignee, String processDefinitionKey, Date startTime, Date endTime) {
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery();
        
        if (StringUtils.hasText(assignee)) {
            query.taskAssignee(assignee);
        }
        if (StringUtils.hasText(processDefinitionKey)) {
            query.processDefinitionKey(processDefinitionKey);
        }
        if (startTime != null) {
            query.taskCompletedAfter(startTime);
        }
        if (endTime != null) {
            query.taskCompletedBefore(endTime);
        }
        
        List<HistoricTaskInstance> tasks = query.list();
        return tasks.stream()
                .collect(Collectors.groupingBy(
                        HistoricTaskInstance::getName,
                        Collectors.counting()
                ));
    }

    /**
     * 获取按分配人分组的统计信息
     */
    private Map<String, Long> getAssigneeStatistics(String processDefinitionKey, Date startTime, Date endTime) {
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
                .finished();
        
        if (StringUtils.hasText(processDefinitionKey)) {
            query.processDefinitionKey(processDefinitionKey);
        }
        if (startTime != null) {
            query.taskCompletedAfter(startTime);
        }
        if (endTime != null) {
            query.taskCompletedBefore(endTime);
        }
        
        List<HistoricTaskInstance> tasks = query.list();
        return tasks.stream()
                .filter(task -> task.getAssignee() != null)
                .collect(Collectors.groupingBy(
                        HistoricTaskInstance::getAssignee,
                        Collectors.counting()
                ));
    }

    /**
     * 获取平均任务处理时间
     */
    private Double getAverageProcessingTime(String assignee, String processDefinitionKey, Date startTime, Date endTime) {
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
                .finished();
        
        if (StringUtils.hasText(assignee)) {
            query.taskAssignee(assignee);
        }
        if (StringUtils.hasText(processDefinitionKey)) {
            query.processDefinitionKey(processDefinitionKey);
        }
        if (startTime != null) {
            query.taskCompletedAfter(startTime);
        }
        if (endTime != null) {
            query.taskCompletedBefore(endTime);
        }
        
        List<HistoricTaskInstance> tasks = query.list();
        if (tasks.isEmpty()) {
            return 0.0;
        }
        
        double totalDuration = tasks.stream()
                .filter(task -> task.getDurationInMillis() != null)
                .mapToLong(HistoricTaskInstance::getDurationInMillis)
                .average()
                .orElse(0.0);
        
        return totalDuration / 1000.0; // 转换为秒
    }

    /**
     * 获取平均流程执行时间
     */
    private Double getAverageProcessDuration(String processDefinitionKey, Date startTime, Date endTime) {
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
                .finished();
        
        if (StringUtils.hasText(processDefinitionKey)) {
            query.processDefinitionKey(processDefinitionKey);
        }
        if (startTime != null) {
            query.startedAfter(startTime);
        }
        if (endTime != null) {
            query.startedBefore(endTime);
        }
        
        List<HistoricProcessInstance> instances = query.list();
        if (instances.isEmpty()) {
            return 0.0;
        }
        
        double avgDuration = instances.stream()
                .filter(instance -> instance.getDurationInMillis() != null)
                .mapToLong(HistoricProcessInstance::getDurationInMillis)
                .average()
                .orElse(0.0);
        
        return avgDuration / 1000.0; // 转换为秒
    }

    /**
     * 获取最长流程执行时间
     */
    private Long getMaxProcessDuration(String processDefinitionKey, Date startTime, Date endTime) {
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
                .finished();
        
        if (StringUtils.hasText(processDefinitionKey)) {
            query.processDefinitionKey(processDefinitionKey);
        }
        if (startTime != null) {
            query.startedAfter(startTime);
        }
        if (endTime != null) {
            query.startedBefore(endTime);
        }
        
        List<HistoricProcessInstance> instances = query.list();
        if (instances.isEmpty()) {
            return 0L;
        }
        
        return instances.stream()
                .filter(instance -> instance.getDurationInMillis() != null)
                .mapToLong(HistoricProcessInstance::getDurationInMillis)
                .max()
                .orElse(0L) / 1000; // 转换为秒
    }

    /**
     * 获取最短流程执行时间
     */
    private Long getMinProcessDuration(String processDefinitionKey, Date startTime, Date endTime) {
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
                .finished();
        
        if (StringUtils.hasText(processDefinitionKey)) {
            query.processDefinitionKey(processDefinitionKey);
        }
        if (startTime != null) {
            query.startedAfter(startTime);
        }
        if (endTime != null) {
            query.startedBefore(endTime);
        }
        
        List<HistoricProcessInstance> instances = query.list();
        if (instances.isEmpty()) {
            return 0L;
        }
        
        return instances.stream()
                .filter(instance -> instance.getDurationInMillis() != null)
                .mapToLong(HistoricProcessInstance::getDurationInMillis)
                .min()
                .orElse(0L) / 1000; // 转换为秒
    }

    /**
     * 获取流程成功率
     */
    private Double getProcessSuccessRate(String processDefinitionKey, Date startTime, Date endTime) {
        HistoricProcessInstanceQuery totalQuery = historyService.createHistoricProcessInstanceQuery();
        HistoricProcessInstanceQuery completedQuery = historyService.createHistoricProcessInstanceQuery()
                .finished();
        
        if (StringUtils.hasText(processDefinitionKey)) {
            totalQuery.processDefinitionKey(processDefinitionKey);
            completedQuery.processDefinitionKey(processDefinitionKey);
        }
        if (startTime != null) {
            totalQuery.startedAfter(startTime);
            completedQuery.startedAfter(startTime);
        }
        if (endTime != null) {
            totalQuery.startedBefore(endTime);
            completedQuery.startedBefore(endTime);
        }
        
        long totalCount = totalQuery.count();
        long completedCount = completedQuery.count();
        
        if (totalCount == 0) {
            return 0.0;
        }
        
        return (double) completedCount / totalCount * 100.0;
    }

    /**
     * 获取任务平均等待时间
     */
    private Double getAverageTaskWaitTime(String processDefinitionKey, Date startTime, Date endTime) {
        // 简化实现，实际应该计算任务创建到开始处理的时间
        return 0.0;
    }

    /**
     * 获取系统吞吐量
     */
    private Double getThroughputPerHour(String processDefinitionKey, Date startTime, Date endTime) {
        if (startTime == null || endTime == null) {
            return 0.0;
        }
        
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
                .startedAfter(startTime)
                .startedBefore(endTime);
        
        if (StringUtils.hasText(processDefinitionKey)) {
            query.processDefinitionKey(processDefinitionKey);
        }
        
        long count = query.count();
        long durationHours = (endTime.getTime() - startTime.getTime()) / (1000 * 60 * 60);
        
        if (durationHours == 0) {
            return 0.0;
        }
        
        return (double) count / durationHours;
    }

    /**
     * 获取资源利用率
     */
    private Map<String, Double> getResourceUtilization() {
        Map<String, Double> utilization = new HashMap<>();
        
        // 简化实现，实际应该从系统监控获取
        utilization.put("cpu", 65.5);
        utilization.put("memory", 72.3);
        utilization.put("disk", 45.8);
        utilization.put("network", 23.1);
        
        return utilization;
    }

    /**
     * 获取已完成的活动节点
     */
    private List<Map<String, Object>> getCompletedActivities(String processInstanceId) {
        List<Map<String, Object>> activities = new ArrayList<>();
        
        // 简化实现，实际应该查询历史活动实例
        return activities;
    }

    /**
     * 获取执行路径
     */
    private List<Map<String, Object>> getExecutionPath(String processInstanceId) {
        List<Map<String, Object>> path = new ArrayList<>();
        
        // 简化实现，实际应该分析流程执行路径
        return path;
    }
}