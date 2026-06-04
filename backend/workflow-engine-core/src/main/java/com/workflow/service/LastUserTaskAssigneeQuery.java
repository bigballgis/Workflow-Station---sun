package com.workflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Queries the assignee of the "physically most recent completed user task" for a
 * given process instance, for use by the {@code LAST_TASK_ASSIGNEE} anchor.
 * <p>Only counts {@link HistoricTaskInstance} (user tasks), excluding service tasks
 * and others.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LastUserTaskAssigneeQuery {

    private final HistoryService historyService;

    /**
     * @param processInstanceId process instance ID
     * @return assignee of the most recent completed user task, or empty if none
     */
    public Optional<String> findLastCompletedUserTaskAssignee(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return Optional.empty();
        }
        try {
            List<HistoricTaskInstance> list = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(processInstanceId.trim())
                    .finished()
                    .orderByHistoricTaskInstanceEndTime()
                    .desc()
                    .list();
            for (HistoricTaskInstance hti : list) {
                if (hti == null) {
                    continue;
                }
                String assignee = hti.getAssignee();
                if (assignee != null && !assignee.isBlank()) {
                    return Optional.of(assignee.trim());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to query last completed user task assignee for instance {}: {}",
                    processInstanceId, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Last user who completed a specific user-task activity in this process instance
     * (e.g. Case Submission after rollback).
     */
    public Optional<String> findLastCompletedAssigneeForActivity(String processInstanceId,
                                                                 String taskDefinitionKey) {
        if (processInstanceId == null || processInstanceId.isBlank()
                || taskDefinitionKey == null || taskDefinitionKey.isBlank()) {
            return Optional.empty();
        }
        try {
            List<HistoricTaskInstance> list = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(processInstanceId.trim())
                    .taskDefinitionKey(taskDefinitionKey.trim())
                    .finished()
                    .orderByHistoricTaskInstanceEndTime()
                    .desc()
                    .list();
            for (HistoricTaskInstance hti : list) {
                if (hti == null) {
                    continue;
                }
                String assignee = hti.getAssignee();
                if (assignee != null && !assignee.isBlank()) {
                    return Optional.of(assignee.trim());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to query last assignee for activity {} in instance {}: {}",
                    taskDefinitionKey, processInstanceId, e.getMessage());
        }
        return Optional.empty();
    }
}
