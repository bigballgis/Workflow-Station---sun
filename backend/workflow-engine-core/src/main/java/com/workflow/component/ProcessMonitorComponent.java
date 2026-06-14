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
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Process Monitor Component
 *
 * Handles process instance status queries, task statistics, performance metrics calculation
 * and visualization data generation. Supports multi-dimensional filtering and pagination.
 *
 * <p>This class is the public facade. Helper logic is delegated to collaborator components
 * in the same package: {@link ProcessMonitorQueryBuilder} (validation/query building/conversion),
 * {@link ProcessMonitorStatisticsCalculator} (process/task statistics),
 * {@link ProcessMonitorPerformanceCalculator} (performance metrics) and
 * {@link ProcessMonitorVisualizationBuilder} (visualization data). Behavior is unchanged.
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

    @Lazy
    @Autowired
    private ProcessMonitorQueryBuilder queryBuilder;

    @Lazy
    @Autowired
    private ProcessMonitorStatisticsCalculator statisticsCalculator;

    @Lazy
    @Autowired
    private ProcessMonitorPerformanceCalculator performanceCalculator;

    @Lazy
    @Autowired
    private ProcessMonitorVisualizationBuilder visualizationBuilder;

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
            queryBuilder.validateQueryRequest(request);

            List<Map<String, Object>> processInstances = new ArrayList<>();
            long totalCount = 0;

            if ("ACTIVE".equals(request.getStatus()) || request.getStatus() == null) {
                // Query running process instances
                ProcessInstanceQuery query = queryBuilder.buildRuntimeQuery(request);
                List<ProcessInstance> activeInstances = query.listPage(
                        request.getOffset() != null ? request.getOffset() : 0,
                        request.getLimit() != null ? request.getLimit() : 20
                );

                for (ProcessInstance instance : activeInstances) {
                    processInstances.add(queryBuilder.convertToProcessInstanceInfo(instance));
                }

                totalCount += query.count();
            }

            if ("COMPLETED".equals(request.getStatus()) || "TERMINATED".equals(request.getStatus()) || request.getStatus() == null) {
                // Query historic process instances
                HistoricProcessInstanceQuery historyQuery = queryBuilder.buildHistoryQuery(request);
                List<HistoricProcessInstance> historicInstances = historyQuery.listPage(
                        request.getOffset() != null ? request.getOffset() : 0,
                        request.getLimit() != null ? request.getLimit() : 20
                );

                for (HistoricProcessInstance instance : historicInstances) {
                    processInstances.add(queryBuilder.convertToProcessInstanceInfo(instance));
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
            Map<String, Long> processDefinitionStatistics = statisticsCalculator.getProcessDefinitionStatistics(startTime, endTime);

            // Statistics grouped by time (last 7 days)
            Map<String, Long> timeStatistics = statisticsCalculator.getTimeStatistics(processDefinitionKey, startTime, endTime);

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
            Map<String, Long> taskNameStatistics = statisticsCalculator.getTaskNameStatistics(assignee, processDefinitionKey, startTime, endTime);

            // Statistics grouped by assignee
            Map<String, Long> assigneeStatistics = statisticsCalculator.getAssigneeStatistics(processDefinitionKey, startTime, endTime);

            // Average processing time statistics
            Double averageProcessingTime = statisticsCalculator.getAverageProcessingTime(assignee, processDefinitionKey, startTime, endTime);

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
            Double averageProcessDuration = performanceCalculator.getAverageProcessDuration(processDefinitionKey, startTime, endTime);

            // Maximum process execution time
            Long maxProcessDuration = performanceCalculator.getMaxProcessDuration(processDefinitionKey, startTime, endTime);

            // Minimum process execution time
            Long minProcessDuration = performanceCalculator.getMinProcessDuration(processDefinitionKey, startTime, endTime);

            // Process success rate
            Double processSuccessRate = performanceCalculator.getProcessSuccessRate(processDefinitionKey, startTime, endTime);

            // Average task wait time
            Double averageTaskWaitTime = performanceCalculator.getAverageTaskWaitTime(processDefinitionKey, startTime, endTime);

            // System throughput (processes per hour)
            Double throughputPerHour = performanceCalculator.getThroughputPerHour(processDefinitionKey, startTime, endTime);

            // Resource utilization metrics
            Map<String, Double> resourceUtilization = performanceCalculator.getResourceUtilization();

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
            List<Map<String, Object>> completedActivities = visualizationBuilder.getCompletedActivities(processInstanceId);
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
            List<Map<String, Object>> executionPath = visualizationBuilder.getExecutionPath(processInstanceId);
            visualizationData.put("executionPath", executionPath);

            return visualizationData;

        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get process execution visualization data: {}", e.getMessage(), e);
            throw new WorkflowBusinessException("VISUALIZATION_DATA_FAILED", "Failed to get process execution visualization data: " + e.getMessage());
        }
    }
}
