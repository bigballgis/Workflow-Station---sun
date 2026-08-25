package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskInfo;
import com.portal.exception.PortalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Sub-table row processor assignment (MI sub-process prerequisite): assigns a row to a user via the
 * workflow engine, identified by numeric rowId or designer primary-key {@code rowKey}.
 * Extracted from {@link TaskProcessComponent} (which keeps same-name public forwards).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubTableRowAssignmentComponent {

    private final WorkflowEngineClient workflowEngineClient;
    private final TaskPermissionEvaluator taskPermissionEvaluator;

    /** Lazy: breaks cycle with {@link TaskProcessComponent} (auto-claim and task loading stay on the facade). */
    @Lazy
    @Autowired
    private TaskProcessComponent taskProcessComponent;

    /**
     * Assigns sub-table row processor (MI sub-process prerequisite) via {@link WorkflowEngineClient}.
     */
    @Transactional
    public Map<String, Object> assignSubTableRow(String taskId, Long rowId, String assigneeId, String userId) {
        return assignSubTableRow(taskId, rowId, null, assigneeId, userId, null);
    }

    @Transactional
    public Map<String, Object> assignSubTableRow(String taskId, Long rowId, String assigneeId, String userId,
                                                 String portalUsername) {
        return assignSubTableRow(taskId, rowId, null, assigneeId, userId, portalUsername);
    }

    @Transactional
    public Map<String, Object> assignSubTableRow(String taskId, Long rowId, Map<String, Object> rowKey, String assigneeId,
                                                 String userId, String portalUsername) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        TaskInfo task = taskProcessComponent.getTaskOrThrow(taskId);
        if (TaskPermissionEvaluator.isTaskAlreadyClosedInEngineView(task)) {
            throw new PortalException("409",
                    "This task is no longer active (it may already be completed). Please refresh your todo list.");
        }
        if (!taskPermissionEvaluator.canProcessTask(task, userId, portalUsername)) {
            throw new PortalException("403", "You do not have permission to process this task");
        }

        boolean poolStyleSt = "VIRTUAL_GROUP".equals(task.getAssignmentType()) || "CANDIDATE_USERS".equals(task.getAssignmentType())
                || "DEPT_ROLE".equals(task.getAssignmentType());
        boolean noAssigneeSt = task.getAssignee() == null || task.getAssignee().isEmpty();
        if (poolStyleSt && noAssigneeSt && !TaskPermissionEvaluator.isEmptyAssignmentPool(task)) {
            log.info("Auto-claiming pool task {} (type {}) for sub-table assign by user {}",
                    taskId, task.getAssignmentType(), userId);
            taskProcessComponent.claimTask(taskId, userId, portalUsername);
        }

        long pathRowId = rowId != null ? rowId : 0L;
        Optional<Map<String, Object>> result = workflowEngineClient.assignSubTableRow(taskId, pathRowId, assigneeId, rowKey);
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to assign sub-table row: " + taskId);
        }

        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            // Engine AssignSubTableRowResponse failure in errorMessage; ApiResponse errors in message / error.message
            Object msgObj = data.get("message");
            if (msgObj == null || String.valueOf(msgObj).isBlank()) {
                msgObj = data.get("errorMessage");
            }
            String message = msgObj != null && !String.valueOf(msgObj).isBlank()
                    ? String.valueOf(msgObj) : "Assignment failed";
            throw new PortalException("400", message);
        }
        return data;
    }
}
