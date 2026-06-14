package com.workflow.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Process Monitor Statistics Calculator
 *
 * Computes process- and task-level statistics (grouped by definition, time, task name,
 * assignee and average processing time). Extracted from {@link ProcessMonitorComponent}
 * as a pure structural refactor; behavior is preserved verbatim.
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessMonitorStatisticsCalculator {

    private final HistoryService historyService;

    /**
     * Get statistics grouped by process definition
     */
    public Map<String, Long> getProcessDefinitionStatistics(Date startTime, Date endTime) {
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
    public Map<String, Long> getTimeStatistics(String processDefinitionKey, Date startTime, Date endTime) {
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
    public Map<String, Long> getTaskNameStatistics(String assignee, String processDefinitionKey, Date startTime, Date endTime) {
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
    public Map<String, Long> getAssigneeStatistics(String processDefinitionKey, Date startTime, Date endTime) {
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
    public Double getAverageProcessingTime(String assignee, String processDefinitionKey, Date startTime, Date endTime) {
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
}
