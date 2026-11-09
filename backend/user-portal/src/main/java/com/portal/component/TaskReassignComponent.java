package com.portal.component;

import com.platform.common.i18n.I18nService;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskInfo;
import com.portal.enums.ChangeType;
import com.portal.exception.PortalException;
import com.portal.util.BuRolePoolTasks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Reassigning a pool task to another pool member, or taking it over (target = the caller): the BU Approver or a
 * Leader of the BU + Role while the holder has Portal entry, any pool member with entry once the holder lost it
 * (platform access model §7.3, D-07, D-17). No System Administrator exception. The engine checks both again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskReassignComponent {

    private final TaskQueryComponent taskQueryComponent;
    private final WorkflowEngineClient workflowEngineClient;
    private final TaskPermissionEvaluator taskPermissionEvaluator;
    private final ClaimForceUnclaimAnnotator claimForceUnclaimAnnotator;
    private final ProcessInstanceSyncComponent processInstanceSyncComponent;
    private final TaskAssignmentHistoryRecorder taskAssignmentHistoryRecorder;
    private final I18nService i18nService;

    @Transactional
    public TaskInfo reassign(String taskId, String userId, String targetUserId, String portalUsername) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException(
                    "Flowable engine unavailable, please check if workflow-engine-core service is running");
        }
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new PortalException("400", "Target user is required");
        }
        TaskInfo taskBefore = taskQueryComponent.getTaskById(taskId)
                .orElseThrow(() -> new PortalException("404", "Task not found: " + taskId));
        if (!BuRolePoolTasks.hasPool(taskBefore)) {
            throw new PortalException("400", "Only pool tasks can be reassigned");
        }
        if (!claimForceUnclaimAnnotator.canReassign(taskBefore, userId)) {
            throw new PortalException("403", "You are not allowed to reassign this task");
        }
        String target = targetUserId.trim();
        if (taskPermissionEvaluator.isHeldByUser(taskBefore, target, null)) {
            throw new PortalException("400", "Task is already held by the selected user");
        }
        if (taskBefore.getReassignTargetUserIds() == null || !taskBefore.getReassignTargetUserIds().contains(target)) {
            throw new PortalException("400", i18nService.getMessage("portal.reassign_target_not_allowed"));
        }
        String enginePrincipal = TaskPermissionEvaluator.resolveEnginePrincipalForWorkflow(
                taskBefore, userId, portalUsername);
        Optional<Map<String, Object>> result = workflowEngineClient.reassignClaim(taskId, enginePrincipal, target);
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to reassign task: " + taskId);
        }
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to reassign task";
            throw new PortalException("400", message);
        }
        TaskInfo task = taskQueryComponent.getTaskById(taskId)
                .orElseThrow(() -> new PortalException("404", "Task not found: " + taskId));
        processInstanceSyncComponent.updateProcessInstanceAssignee(task.getProcessInstanceId(), target, null,
                task.getTaskName(), OwnerFieldComponent.taskScopedCurrentItem(task.getVariables()));
        taskQueryComponent.invalidateMineTaskListCache();
        taskAssignmentHistoryRecorder.record(taskBefore, task, userId, ChangeType.REASSIGN, target);
        log.info("Task {} reassigned by {} to {}", taskId, userId, target);
        return task;
    }
}
