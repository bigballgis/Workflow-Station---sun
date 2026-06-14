package com.workflow.component;

import com.workflow.dto.response.TaskListResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Task statistics queries: counts, overdue tasks, high-priority tasks.
 * Extracted from TaskManagerComponent.
 */
@Slf4j
@Component
@Transactional
public class TaskStatsService {

    @Autowired
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;

    @Lazy
    @Autowired
    private TaskQueryService taskQueryService;

    // ==================== Public Methods ====================

    public long countUserTasks(String userId) {
        try {
            validateUserId(userId);
            return extendedTaskInfoRepository.countUserTodoTasks(userId);
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_COUNT_ERROR",
                "Failed to count user tasks: " + e.getMessage(), e);
        }
    }

    public long countUserOverdueTasks(String userId) {
        try {
            validateUserId(userId);
            return extendedTaskInfoRepository.countUserOverdueTasks(userId, LocalDateTime.now());
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_COUNT_ERROR",
                "Failed to count user overdue tasks: " + e.getMessage(), e);
        }
    }

    public List<TaskListResult.TaskInfo> getOverdueTasks() {
        try {
            List<ExtendedTaskInfo> overdueTasks = extendedTaskInfoRepository
                .findOverdueTasks(LocalDateTime.now());

            return overdueTasks.stream()
                .map(taskQueryService::convertToTaskInfo)
                .toList();

        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR",
                "Failed to query overdue tasks: " + e.getMessage(), e);
        }
    }

    public List<TaskListResult.TaskInfo> getHighPriorityTasks(int minPriority) {
        try {
            List<ExtendedTaskInfo> highPriorityTasks = extendedTaskInfoRepository
                .findHighPriorityTasks(minPriority);

            return highPriorityTasks.stream()
                .map(taskQueryService::convertToTaskInfo)
                .toList();

        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_QUERY_ERROR",
                "Failed to query high priority tasks: " + e.getMessage(), e);
        }
    }

    // ==================== Private Helpers ====================

    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "userId", "User ID cannot be empty", userId)));
        }
    }
}
