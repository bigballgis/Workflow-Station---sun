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
 * Process Monitor Component
 * 
 * Handles process instance status queries, task statistics, performance metrics calculation
 * and visualization data generation. Supports multi-dimensional filtering and pagination.
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
     * Query process instance monitoring information.
     * Supports multi-dimensional filtering and pagination.
     * 
     * @param request query request
     * @return monitoring result
     */
    @Transactional(readOnly = true)
    public ProcessMonitorResult queryProcessInstances(ProcessMonitorQueryRequest request) {
        log.info("Querying process instance monitoring info: processDefinitionKey={}, status={}", 
                request.getProcessDefinitionKey(), request.getStatus());
        
        try {
            // Validate request parameters
            validateQueryRequest(request);
            
            List<Map<String, Object>> processInstances = new ArrayList<>();
            long totalCount = 0;
            
            if ("ACTIVE".equals(request.getStatus()) || request.getStatus() == null) {
                // Query running process instances
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
                // Query historic process instances
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
            log.error("Failed to query process instance monitoring info: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("MONITOR_QUERY_FAILED", "Failed to query process instance monitoring info: " + e.getMessage());
        }
    }

    /**
     * Get process statistics
     * 
     * @param processDefinitionKey process definition key (optional)
     * @param startTime start time (optional)
     * @param endTime end time (optional)
     * @return process statistics result
     */
    @Transactional(readOnly = true)
    public ProcessStatisticsResult getProcessStatistics(String processDefinitionKey, Date startTime, Date endTime) {
        log.info("Getting process statistics: processDefinitionKey={}, startTime={}, endTime={}", 
                processDefinitionKey, startTime, endTime);
        
        try {
            // Number of running process instances
            ProcessInstanceQuery activeQuery = runtimeService.createProcessInstanceQuery();
            if (StringUtils.hasText(processDefinitionKey)) {
                activeQuery.processDefinitionKey(processDefinitionKey);
            }
            long activeCount = activeQuery.count();
            
            // Number of completed process instances
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
            
            // Number of terminated process instances
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
            
            // Statistics grouped by status
            Map<String, Long> statusStatistics = new HashMap<>();
            statusStatistics.put("ACTIVE", activeCount);
            statusStatistics.put("COMPLETED", completedCount);
            statusStatistics.put("TERMINATED", terminatedCount);
            
            // Statistics grouped by process definition
            Map<String, Long> processDefinitionStatistics = getProcessDefinitionStatistics(startTime, endTime);
            
            // Statistics grouped by time (last 7 days)
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
            log.error("Failed to get process statistics: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("STATISTICS_QUERY_FAILED", "Failed to get process statistics: " + e.getMessage());
        }
    }

    /**
     * Get task statistics
     * 
     * @param assignee task assignee (optional)
     * @param processDefinitionKey process definition key (optional)
     * @param startTime start time (optional)
     * @param endTime end time (optional)
     * @return task statistics result
     */
    @Transactional(readOnly = true)
    public TaskStatisticsResult getTaskStatistics(String assignee, String processDefinitionKey, Date startTime, Date endTime) {
        log.info("Getting task statistics: assignee={}, processDefinitionKey={}, startTime={}, endTime={}", 
                assignee, processDefinitionKey, startTime, endTime);
        
        try {
            // Number of pending tasks
            TaskQuery pendingQuery = taskService.createTaskQuery();
            if (StringUtils.hasText(assignee)) {
                pendingQuery.taskAssignee(assignee);
            }
            if (StringUtils.hasText(processDefinitionKey)) {
                pendingQuery.processDefinitionKey(processDefinitionKey);
            }
            long pendingCount = pendingQuery.count();
            
            // Number of completed tasks
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
            
            // Number of overdue tasks
            TaskQuery overdueQuery = taskService.createTaskQuery()
                    .taskDueBefore(new Date());
            if (StringUtils.hasText(assignee)) {
                overdueQuery.taskAssignee(assignee);
            }
            if (StringUtils.hasText(processDefinitionKey)) {
                overdueQuery.processDefinitionKey(processDefinitionKey);
            }
            long overdueCount = overdueQuery.count();
            
            // Statistics grouped by task name
            Map<String, Long> taskNameStatistics = getTaskNameStatistics(assignee, processDefinitionKey, startTime, endTime);
            
            // Statistics grouped by assignee
            Map<String, Long> assigneeStatistics = getAssigneeStatistics(processDefinitionKey, startTime, endTime);
            
            // Average processing time statistics
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
            log.error("Failed to get task statistics: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("TASK_STATISTICS_FAILED", "Failed to get task statistics: " + e.getMessage());
        }
    }

    /**
     * Get performance metrics
     * 
     * @param processDefinitionKey process definition key (optional)
     * @param startTime start time (optional)
     * @param endTime end time (optional)
     * @return performance metrics result
     */
    @Transactional(readOnly = true)
    public PerformanceMetricsResult getPerformanceMetrics(String processDefinitionKey, Date startTime, Date endTime) {
        log.info("Getting performance metrics: processDefinitionKey={}, startTime={}, endTime={}", 
                processDefinitionKey, startTime, endTime);
        
        try {
            // Average process execution time
            Double averageProcessDuration = getAverageProcessDuration(processDefinitionKey, startTime, endTime);
            
            // Maximum process execution time
            Long maxProcessDuration = getMaxProcessDuration(processDefinitionKey, startTime, endTime);
            
            // Minimum process execution time
            Long minProcessDuration = getMinProcessDuration(processDefinitionKey, startTime, endTime);
            
            // Process success rate
            Double processSuccessRate = getProcessSuccessRate(processDefinitionKey, startTime, endTime);
            
            // Average task wait time
            Double averageTaskWaitTime = getAverageTaskWaitTime(processDefinitionKey, startTime, endTime);
            
            // System throughput (processes per hour)
            Double throughputPerHour = getThroughputPerHour(processDefinitionKey, startTime, endTime);
            
            // Resource utilization metrics
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
            log.error("Failed to get performance metrics: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("PERFORMANCE_METRICS_FAILED", "Failed to get performance metrics: " + e.getMessage());
        }
    }

    /**
     * Get process execution visualization data
     * 
     * @param processInstanceId process instance ID
     * @return visualization data
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getProcessVisualizationData(String processInstanceId) {
        log.info("Getting process execution visualization data: processInstanceId={}", processInstanceId);
        
        try {
            Map<String, Object> visualizationData = new HashMap<>();
            
            // Get process instance info
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
                    new WorkflowValidationException.ValidationError("processInstanceId", "Process instance does not exist", processInstanceId)
                ));
            }
            
            // Basic info
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
            
            // Current active nodes
            if (processInstance != null) {
                List<String> activeActivityIds = runtimeService.getActiveActivityIds(processInstanceId);
                visualizationData.put("activeActivityIds", activeActivityIds);
            }
            
            // Completed activity nodes
            List<Map<String, Object>> completedActivities = getCompletedActivities(processInstanceId);
            visualizationData.put("completedActivities", completedActivities);
            
            // Process variables
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
            
            // Execution path
            List<Map<String, Object>> executionPath = getExecutionPath(processInstanceId);
            visualizationData.put("executionPath", executionPath);
            
            return visualizationData;
            
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get process execution visualization data: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("VISUALIZATION_DATA_FAILED", "Failed to get process execution visualization data: " + e.getMessage());
        }
    }

    // ==================== Private helper methods ====================

    /**
     * Validate query request parameters
     */
    private void validateQueryRequest(ProcessMonitorQueryRequest request) {
        List<WorkflowValidationException.ValidationError> errors = new ArrayList<>();
        
        if (request.getLimit() != null && request.getLimit() <= 0) {
            errors.add(new WorkflowValidationException.ValidationError("limit", "Page size must be greater than 0", request.getLimit()));
        }
        
        if (request.getOffset() != null && request.getOffset() < 0) {
            errors.add(new WorkflowValidationException.ValidationError("offset", "Offset must not be negative", request.getOffset()));
        }
        
        if (request.getStartTime() != null && request.getEndTime() != null && 
            request.getStartTime().after(request.getEndTime())) {
            errors.add(new WorkflowValidationException.ValidationError("timeRange", "Start time must not be after end time", null));
        }
        
        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    /**
     * Build runtime query
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
        
        // Sorting - simplified to use ID ordering only
        query.orderByProcessInstanceId();
        
        if ("DESC".equalsIgnoreCase(request.getOrderDirection())) {
            query.desc();
        } else {
            query.asc();
        }
        
        return query;
    }

    /**
     * Build history query
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
        
        // Sorting - simplified to use ID ordering only
        query.orderByProcessInstanceId();
        
        if ("DESC".equalsIgnoreCase(request.getOrderDirection())) {
            query.desc();
        } else {
            query.asc();
        }
        
        return query;
    }

    /**
     * Convert process instance info
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
     * Convert historic process instance info
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
     * Get statistics grouped by process definition
     */
    private Map<String, Long> getProcessDefinitionStatistics(Date startTime, Date endTime) {
        // Simplified implementation; should use database aggregate queries in production
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
     * Get statistics grouped by time
     */
    private Map<String, Long> getTimeStatistics(String processDefinitionKey, Date startTime, Date endTime) {
        Map<String, Long> statistics = new HashMap<>();
        
        // Simplified implementation, daily stats for last 7 days
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
     * Get statistics grouped by task name
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
     * Get statistics grouped by assignee
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
     * Get average task processing time
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
        
        return totalDuration / 1000.0; // Convert to seconds
    }

    /**
     * Get average process execution time
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
        
        return avgDuration / 1000.0; // Convert to seconds
    }

    /**
     * Get maximum process execution time
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
                .orElse(0L) / 1000; // Convert to seconds
    }

    /**
     * Get minimum process execution time
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
                .orElse(0L) / 1000; // Convert to seconds
    }

    /**
     * Get process success rate
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
     * Get average task wait time
     */
    private Double getAverageTaskWaitTime(String processDefinitionKey, Date startTime, Date endTime) {
        // Simplified implementation; should calculate time from task creation to processing start
        return 0.0;
    }

    /**
     * Get system throughput
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
     * Get resource utilization
     */
    private Map<String, Double> getResourceUtilization() {
        Map<String, Double> utilization = new HashMap<>();
        
        // Simplified implementation; should get from system monitoring in production
        utilization.put("cpu", 65.5);
        utilization.put("memory", 72.3);
        utilization.put("disk", 45.8);
        utilization.put("network", 23.1);
        
        return utilization;
    }

    /**
     * Get completed activity nodes
     */
    private List<Map<String, Object>> getCompletedActivities(String processInstanceId) {
        List<Map<String, Object>> activities = new ArrayList<>();
        
        // Simplified implementation; should query historic activity instances
        return activities;
    }

    /**
     * Get execution path
     */
    private List<Map<String, Object>> getExecutionPath(String processInstanceId) {
        List<Map<String, Object>> path = new ArrayList<>();
        
        // Simplified implementation; should analyze process execution path
        return path;
    }
}