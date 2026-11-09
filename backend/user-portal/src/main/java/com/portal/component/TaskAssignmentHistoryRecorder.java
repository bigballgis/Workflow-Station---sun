package com.portal.component;

import com.portal.dto.TaskInfo;
import com.portal.entity.ChangeHistory;
import com.portal.enums.AssignmentChangeTypes;
import com.portal.enums.ChangeType;
import com.portal.repository.ChangeHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Writes claim / unclaim / force-unclaim / reassign rows, and the end of a single-task delegation they or a transfer
 * bring about, to {@code up_change_history}.
 * Best-effort for user actions: a failure never blocks the assignment itself. Changes the engine made on its own are
 * saved together or not at all, and a failure is thrown so the engine logs the tasks it could not record.
 */
@Slf4j
@Component
public class TaskAssignmentHistoryRecorder {

    private final ChangeHistoryRepository changeHistoryRepository;
    private final TransactionTemplate requiresNewTx;

    public TaskAssignmentHistoryRecorder(
            ChangeHistoryRepository changeHistoryRepository,
            PlatformTransactionManager transactionManager) {
        this.changeHistoryRepository = changeHistoryRepository;
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = tt;
    }

    /**
     * A claim / release / reassignment of {@code task} (as read before it). When it carried a single-task delegation
     * that {@code after} (the task read again once the engine made the change) no longer carries, a second row says
     * the delegation ended (D-59): the engine ends it, this row only reports what the engine did.
     */
    public void record(TaskInfo task, TaskInfo after, String actorUserId, ChangeType changeType,
                       String newHolderUserId) {
        if (task == null || task.getProcessInstanceId() == null || task.getProcessInstanceId().isBlank()
                || actorUserId == null || actorUserId.isBlank()
                || !AssignmentChangeTypes.isAssignmentAction(changeType)) {
            return;
        }
        var context = com.portal.dto.ChangeHistoryContext.builder().build();
        ChangeHistory record = ChangeHistory.builder()
                .batchId(context.getBatchId())
                .batchTime(context.getBatchTime())
                .processInstanceId(task.getProcessInstanceId())
                .taskInstanceId(blankToNull(task.getTaskId()))
                .stageId(blankToNull(task.getTaskDefinitionKey()))
                .userId(actorUserId)
                .timestamp(Instant.now())
                .fieldName(AssignmentChangeTypes.FIELD_NAME)
                .oldValue(blankToNull(task.getAssignee()))
                .newValue(blankToNull(newHolderUserId))
                .changeType(changeType)
                .build();
        save(record);
        recordDelegationEnded(task, after, actorUserId, record.getTimestamp(), context);
    }

    /**
     * A change of hands the change history records no row of its own for, such as a transfer: only the end of the
     * single-task delegation it brought about (D-59), when {@code task} (read before) carried one that {@code after}
     * (read again once the engine made the change) no longer carries.
     */
    public void recordDelegationEnded(TaskInfo task, TaskInfo after, String actorUserId) {
        if (task == null || task.getProcessInstanceId() == null || task.getProcessInstanceId().isBlank()
                || actorUserId == null || actorUserId.isBlank()) {
            return;
        }
        recordDelegationEnded(task, after, actorUserId, Instant.now(), com.portal.dto.ChangeHistoryContext.builder().build());
    }

    private void recordDelegationEnded(TaskInfo task, TaskInfo after, String actorUserId, Instant at,
                                       com.portal.dto.ChangeHistoryContext context) {
        String endedDelegate = delegateOf(task);
        if (endedDelegate != null && after != null && delegateOf(after) == null) {
            save(delegationEnded(task.getProcessInstanceId(), task.getTaskId(), endedDelegate, actorUserId, at, context));
        }
    }

    /** Whom {@code task}'s single-task delegation names: a user, or {@code BU / ROLE}; null when it has none. */
    private static String delegateOf(TaskInfo task) {
        if (blankToNull(task.getDelegatedTo()) != null) {
            return task.getDelegatedTo().trim();
        }
        return blankToNull(task.getDelegatedBuCode()) != null && blankToNull(task.getDelegatedRoleCode()) != null
                ? task.getDelegatedBuCode().trim() + " / " + task.getDelegatedRoleCode().trim() : null;
    }

    /** A single-task delegation ended because the task changed hands (D-59). */
    public static ChangeHistory delegationEnded(String processInstanceId, String taskId, String delegate,
                                                String actorUserId, Instant endedAt,
                                                com.portal.dto.ChangeHistoryContext context) {
        return ChangeHistory.builder()
                .batchId(context.getBatchId())
                .batchTime(context.getBatchTime())
                .processInstanceId(processInstanceId)
                .taskInstanceId(blankToNull(taskId))
                .userId(actorUserId)
                .timestamp(endedAt != null ? endedAt : Instant.now())
                .fieldName(AssignmentChangeTypes.DELEGATED_TO_FIELD)
                .oldValue(delegate)
                .changeType(ChangeType.DELEGATION_ENDED)
                .reason(AssignmentChangeTypes.REASON_HOLDER_CHANGED)
                .build();
    }

    /** Saves rows the engine reported (system changes) in one transaction; a failure is thrown, nothing is kept. */
    public void recordSystemEvents(List<ChangeHistory> rows) {
        if (!rows.isEmpty()) {
            requiresNewTx.executeWithoutResult(status -> changeHistoryRepository.saveAll(rows));
        }
    }

    /**
     * The engine released a claimed BU + Role pool task because its holder no longer holds that BU + Role (D-40):
     * a {@code FORCE_UNCLAIM} by {@code system} back to the pool, with the reason.
     */
    public static ChangeHistory poolMembershipRelease(String processInstanceId, String taskId, String previousHolderId,
                                                      Instant releasedAt) {
        var context = com.portal.dto.ChangeHistoryContext.builder().build();
        return ChangeHistory.builder()
                .batchId(context.getBatchId())
                .batchTime(context.getBatchTime())
                .processInstanceId(processInstanceId)
                .taskInstanceId(blankToNull(taskId))
                .userId(AssignmentChangeTypes.SYSTEM_ACTOR)
                .timestamp(releasedAt != null ? releasedAt : Instant.now())
                .fieldName(AssignmentChangeTypes.FIELD_NAME)
                .oldValue(blankToNull(previousHolderId))
                .changeType(ChangeType.FORCE_UNCLAIM)
                .reason(AssignmentChangeTypes.REASON_POOL_MEMBERSHIP_LOST)
                .build();
    }

    /**
     * The engine handed a task to the manager of the people who left (D-42): a {@code MANAGER_REASSIGN} by
     * {@code system} from the former holder (none for an unclaimed list) to the manager, with the reason.
     */
    public static ChangeHistory departedReassignment(String processInstanceId, String taskId, String previousHolderId,
                                                     String managerId, Instant reassignedAt) {
        var context = com.portal.dto.ChangeHistoryContext.builder().build();
        return ChangeHistory.builder()
                .batchId(context.getBatchId())
                .batchTime(context.getBatchTime())
                .processInstanceId(processInstanceId)
                .taskInstanceId(blankToNull(taskId))
                .userId(AssignmentChangeTypes.SYSTEM_ACTOR)
                .timestamp(reassignedAt != null ? reassignedAt : Instant.now())
                .fieldName(AssignmentChangeTypes.FIELD_NAME)
                .oldValue(blankToNull(previousHolderId))
                .newValue(blankToNull(managerId))
                .changeType(ChangeType.MANAGER_REASSIGN)
                .reason(AssignmentChangeTypes.REASON_ASSIGNEE_DEPARTED)
                .build();
    }

    private void save(ChangeHistory record) {
        requiresNewTx.executeWithoutResult(status -> {
            try {
                changeHistoryRepository.save(record);
            } catch (Exception e) {
                log.warn("Failed to record {} for process {}: {}",
                        record.getChangeType(), record.getProcessInstanceId(), e.getMessage());
                status.setRollbackOnly();
            }
        });
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
