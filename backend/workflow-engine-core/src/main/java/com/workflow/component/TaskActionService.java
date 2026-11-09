package com.workflow.component;

import com.workflow.dto.request.TaskClaimRequest;
import com.workflow.dto.request.TaskDelegationRequest;
import com.workflow.dto.response.TaskAssignmentResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.service.UserPermissionService;

import com.platform.messaging.support.NotificationDispatchHelper;
import com.platform.common.i18n.I18nService;

import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.task.api.Task;
import org.flowable.engine.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Task action operations: delegate, claim, unclaim, transfer.
 * Extracted from TaskManagerComponent.
 */
@Slf4j
@Component
@Transactional
public class TaskActionService {

    @Autowired
    private TaskService taskService;

    @Autowired
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;

    @Autowired
    private UserPermissionService userPermissionService;

    @Autowired
    private NotificationDispatchHelper notificationDispatchHelper;

    @Autowired
    private I18nService i18nService;

    @Autowired
    private TaskClaimSupport taskClaimSupport;

    // ==================== Public Action Methods ====================

    public TaskAssignmentResult delegateTask(String taskId, TaskDelegationRequest request) {
        try {
            validateTaskDelegationRequest(request);

            Task flowableTask = taskService.createTaskQuery()
                    .taskId(taskId)
                    .singleResult();
            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                                "taskId", "Task not found", taskId)));
            }
            if (!StringUtils.hasText(flowableTask.getAssignee())) {
                throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                                "taskId", "Unclaimed pool tasks cannot be delegated", taskId)));
            }

            ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId)
                .orElseThrow(() -> new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "This task cannot be delegated", taskId))));

            requireHolder(flowableTask, request.getDelegatedBy(), "delegate");

            if (extendedTaskInfo.isCompleted()) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task already completed, cannot delegate", taskId)));
            }

            applyDelegateTarget(extendedTaskInfo, request);
            extendedTaskInfo = extendedTaskInfoRepository.save(extendedTaskInfo);
            publishTaskDelegationEvent(extendedTaskInfo, request);

            String targetLabel = request.isBuRoleTarget()
                    ? request.getDelegatedBuCode() + "/" + request.getDelegatedRoleCode()
                    : request.getDelegatedTo();
            return TaskAssignmentResult.success(
                taskId,
                AssignmentType.USER,
                targetLabel,
                request.getDelegatedBy(),
                "Task delegated successfully");

        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_DELEGATION_ERROR",
                "Task delegation failed: " + e.getMessage(), e);
        }
    }

    private void applyDelegateTarget(ExtendedTaskInfo extendedTaskInfo, TaskDelegationRequest request) {
        String reason = request.getEffectiveDelegationReason();
        if (request.isBuRoleTarget()) {
            extendedTaskInfo.delegateTaskToBuRole(
                    request.getDelegatedBuCode().trim(),
                    request.getDelegatedRoleCode().trim(),
                    request.getDelegatedBy(),
                    reason);
            return;
        }
        extendedTaskInfo.delegateTask(
                request.getDelegatedTo(),
                request.getDelegatedBy(),
                reason);
    }

    public TaskAssignmentResult claimTask(String taskId, TaskClaimRequest request) {
        validateTaskClaimRequest(request);
        return taskClaimSupport.claimTask(taskId, request);
    }

    public TaskAssignmentResult unclaimTask(String taskId, String userId) {
        validateUserId(userId);
        return taskClaimSupport.unclaimTask(taskId, userId);
    }

    public TaskAssignmentResult reassignClaim(String taskId, String operatorUserId, String targetUserId) {
        validateUserId(operatorUserId);
        validateUserId(targetUserId);
        return taskClaimSupport.reassignClaim(taskId, operatorUserId, targetUserId);
    }

    public TaskAssignmentResult transferTask(String taskId, String fromUserId, String toUserId, String reason) {
        try {
            validateUserId(fromUserId);
            validateUserId(toUserId);

            Task flowableTask = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

            if (flowableTask == null) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "taskId", "Task not found", taskId)));
            }

            boolean held = StringUtils.hasText(flowableTask.getAssignee());
            if (held) {
                requireHolder(flowableTask, fromUserId, "transfer");
            }

            Optional<ExtendedTaskInfo> extendedTaskInfoOpt = extendedTaskInfoRepository
                .findByTaskIdAndIsDeletedFalse(taskId);

            if (extendedTaskInfoOpt.isPresent()) {
                ExtendedTaskInfo extendedTaskInfo = extendedTaskInfoOpt.get();

                if (extendedTaskInfo.isCompleted()) {
                    throw new WorkflowValidationException(Collections.singletonList(
                        new WorkflowValidationException.ValidationError(
                            "taskId", "Task already completed, cannot transfer", taskId)));
                }

                if (!held) {
                    validateCompletePermission(extendedTaskInfo, fromUserId);
                }

                extendedTaskInfo.setAssignmentType(AssignmentType.USER);
                extendedTaskInfo.setAssignmentTarget(toUserId);
                extendedTaskInfo.setClaimedBy(null);
                extendedTaskInfo.setClaimedTime(null);
                extendedTaskInfo.clearDelegation();
                extendedTaskInfo.updateStatus("ASSIGNED", fromUserId);
                extendedTaskInfoRepository.save(extendedTaskInfo);
            }

            String processInstanceId = flowableTask.getProcessInstanceId();
            String previousActor = Authentication.getAuthenticatedUserId();
            try {
                Authentication.setAuthenticatedUserId(fromUserId);
                taskService.setAssignee(taskId, toUserId);
                taskService.addComment(taskId, processInstanceId, "transfer",
                        reason != null && !reason.isBlank() ? reason : "");
            } finally {
                Authentication.setAuthenticatedUserId(previousActor);
            }

            String taskLabel = flowableTask.getName() != null ? flowableTask.getName() : taskId;
            String reasonText = reason != null && !reason.isBlank()
                    ? i18nService.getMessage("workflow.notification.transfer_reason", reason)
                    : "";
            notificationDispatchHelper.publishToUserAfterCommit(
                    toUserId,
                    "TASK",
                    i18nService.getMessage("workflow.notification.transferred_title"),
                    i18nService.getMessage("workflow.notification.transferred_body",
                            fromUserId, taskLabel, reasonText).trim(),
                    taskLink(taskId),
                    "workflow-engine");

            return TaskAssignmentResult.success(
                taskId,
                AssignmentType.USER,
                toUserId,
                fromUserId,
                "Task transferred successfully");

        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowBusinessException("TASK_TRANSFER_ERROR",
                "Task transfer failed: " + e.getMessage(), e);
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Platform access model D-60: a task someone holds is handed on or delegated by its holder only (completing it is
     * also open to a delegate in effect, see {@code TaskCompletionService}); candidates and pool members have no say.
     */
    private void requireHolder(Task task, String userId, String action) {
        if (!engineActorMatchesPortalUser(task.getAssignee(), userId)) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "userId", "This task is held by another user: only its holder can " + action + " it", userId)));
        }
    }

    void validateCompletePermission(ExtendedTaskInfo task, String userId) {
        String currentAssignee = task.getCurrentAssignee();

        if (currentAssignee != null && !currentAssignee.isBlank()) {
            if (!engineActorMatchesPortalUser(currentAssignee, userId)) {
                throw new WorkflowValidationException(Collections.singletonList(
                    new WorkflowValidationException.ValidationError(
                        "userId", "User does not have permission to complete this task", userId)));
            }
            return;
        }

        boolean hasPermission = userPermissionService.hasTaskPermission(
                userId,
                task.getAssignmentType(),
                task.getAssignmentTarget());

        if (!hasPermission) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "userId", "User does not have permission to complete this task", userId)));
        }
    }

    boolean engineActorMatchesPortalUser(String engineSideActor, String portalUserId) {
        return taskClaimSupport.engineActorMatchesPortalUser(engineSideActor, portalUserId);
    }

    private void validateTaskDelegationRequest(TaskDelegationRequest request) {
        if (request == null) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", "Request parameters cannot be empty", null)));
        }
        if (!request.isValid()) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", request.getValidationError(), null)));
        }
    }

    private void validateTaskClaimRequest(TaskClaimRequest request) {
        if (request == null) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", "Request parameters cannot be empty", null)));
        }
        if (!request.isValid()) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "request", request.getValidationError(), null)));
        }
    }

    private void validateUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new WorkflowValidationException(Collections.singletonList(
                new WorkflowValidationException.ValidationError(
                    "userId", "User ID cannot be empty", userId)));
        }
    }

    // ==================== Event Publishing ====================

    private void publishTaskDelegationEvent(ExtendedTaskInfo task, TaskDelegationRequest request) {
        log.info("Task delegation event: taskId={}, delegatedTo={}, delegatedBy={}",
                task.getTaskId(), request.getDelegatedTo(), request.getDelegatedBy());
        if (!request.shouldSendNotification()) {
            return;
        }
        String label = task.getTaskName() != null ? task.getTaskName() : task.getTaskId();
        notificationDispatchHelper.publishToUserAfterCommit(
                request.getDelegatedTo(),
                "TASK",
                i18nService.getMessage("workflow.notification.delegated_title"),
                i18nService.getMessage("workflow.notification.delegated_body",
                        request.getDelegatedBy(),
                        label,
                        request.getEffectiveDelegationReason() != null
                                ? " " + i18nService.getMessage("workflow.notification.delegation_reason", request.getEffectiveDelegationReason())
                                : "").trim(),
                taskLink(task.getTaskId()),
                "workflow-engine");
    }

    static String taskLink(String taskId) {
        return "/tasks/" + taskId;
    }
}
