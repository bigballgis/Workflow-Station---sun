package com.workflow.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Process Monitor Performance Calculator
 *
 * Computes process performance metrics: average/max/min duration, success rate,
 * task wait time, throughput and resource utilization. Extracted from
 * {@link ProcessMonitorComponent} as a pure structural refactor; behavior is preserved verbatim.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessMonitorPerformanceCalculator {

    private final HistoryService historyService;

    /**
     * Get average process execution time
     */
    public Double getAverageProcessDuration(String processDefinitionKey, Date startTime, Date endTime) {
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
    public Long getMaxProcessDuration(String processDefinitionKey, Date startTime, Date endTime) {
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
    public Long getMinProcessDuration(String processDefinitionKey, Date startTime, Date endTime) {
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
    public Double getProcessSuccessRate(String processDefinitionKey, Date startTime, Date endTime) {
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
    public Double getAverageTaskWaitTime(String processDefinitionKey, Date startTime, Date endTime) {
        // Simplified implementation; should calculate time from task creation to processing start
        return 0.0;
    }

    /**
     * Get system throughput
     */
    public Double getThroughputPerHour(String processDefinitionKey, Date startTime, Date endTime) {
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
    public Map<String, Double> getResourceUtilization() {
        Map<String, Double> utilization = new HashMap<>();

        // Simplified implementation; should get from system monitoring in production
        utilization.put("cpu", 65.5);
        utilization.put("memory", 72.3);
        utilization.put("disk", 45.8);
        utilization.put("network", 23.1);

        return utilization;
    }
}
