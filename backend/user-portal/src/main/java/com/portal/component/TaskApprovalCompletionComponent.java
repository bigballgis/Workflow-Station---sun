package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.ChangeHistoryContext;
import com.portal.dto.SubTableChange;
import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.util.SubTableNestingSanitizer;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Approval (APPROVE/REJECT) completion path: builds outcome variables, injects the MI collection,
 * completes the task in Flowable, then best-effort syncs portal {@link ProcessInstance} state and
 * records change history.
 * Extracted from {@link TaskProcessComponent} (which keeps the {@code completeTask} entry point and
 * its transaction boundary).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskApprovalCompletionComponent {

    private final WorkflowEngineClient workflowEngineClient;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ChangeHistoryComponent changeHistoryComponent;
    private final TaskFormComponent taskFormComponent;
    private final MiCollectionVariableBuilder miCollectionVariableBuilder;
    private final ProcessInstanceSyncComponent processInstanceSyncComponent;

    /**
     * Handles approval completion
     * Via WorkflowEngineClient calling Flowable engine
     */
    void handleApproval(TaskInfo task, TaskCompleteRequest request, String userId) {
        String taskId = task.getTaskId();
        String action = request.getAction();

        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        log.info("Using Flowable engine to complete task: {} with action: {}", taskId, action);

        // Start with variables from request if provided
        Map<String, Object> variables = new HashMap<>();
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }

        // Merge explicit form payload before locking outcome variables. Form schemas may reuse names like
        // "decision" or "approvalStatus"; if we merge formData AFTER setting those keys, user/stale values win
        // and Flowable completion fails with engine error → PortalException 500.
        if (request.getFormData() != null) {
            variables.putAll(request.getFormData());
        }

        variables.put("action", action);
        if ("APPROVE".equals(action)) {
            variables.put("decision", "yes");
            variables.put("approvalStatus", "APPROVED");
            log.info("Set decision=yes for APPROVE action");
        } else if ("REJECT".equals(action)) {
            variables.put("decision", "no");
            variables.put("approvalStatus", "REJECTED");
            log.info("Set decision=no for REJECT action");
        }
        if (request.getComment() != null && !request.getComment().isEmpty()) {
            variables.put("approverComments", request.getComment());
        }

        // Approval submit is often incremental; __subTables__ may only exist on TaskInfo (merged ProcessInstance).
        // Without merge here, injectMiCollectionFromBpmn sees no sub-table rows → empty MI collection → zero child tasks.
        miCollectionVariableBuilder.mergeSubTablesFromTaskInfoForMi(task, variables);
        Object subTablesAfterMerge = variables.get("__subTables__");
        if (!(subTablesAfterMerge instanceof Map<?, ?> subMap) || subMap.isEmpty()) {
            log.warn("[MI] After TaskInfo merge, variables have no __subTables__ (taskId={}, processInstanceId={}). "
                    + "Multi-instance injection will not be able to build row collection.",
                    task.getTaskId(), task.getProcessInstanceId());
        }

        // If MI sub-process prerequisite, read collection variable and assignee field from BPMN and build collection
        miCollectionVariableBuilder.injectMiCollectionFromBpmn(
                task.getProcessDefinitionKey(), task.getTaskDefinitionKey(), task.getProcessInstanceId(), variables);

        log.info("Variables before calling workflowEngineClient: {}", variables);

        Optional<Map<String, Object>> result = workflowEngineClient.completeTask(taskId, userId, action, variables);

        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to complete task: " + taskId);
        }

        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to complete task";
            if (TaskPermissionEvaluator.isEngineTaskInactiveMessage(message)) {
                throw new PortalException("409",
                        "The task is no longer active in the workflow engine (completed, cancelled, or superseded). "
                                + "Please refresh your todo list.");
            }
            throw new PortalException("500", message);
        }

        log.info("Task {} completed via Flowable by user {} with action {} (approvalStatus: {})",
                taskId, userId, action, variables.get("approvalStatus"));

        // Sync approval variables to local ProcessInstance for Completed Tasks / My Requests
        // Must copy into a new HashMap; in-place edit breaks Hibernate JSON dirty detection
        // Same reference makes Hibernate dirty-check think unchanged and skip UPDATE
        try {
            String syncProcessId = task.getProcessInstanceId();
            Optional<ProcessInstance> syncOpt = processInstanceRepository.findById(syncProcessId);
            if (syncOpt.isPresent()) {
                ProcessInstance syncInstance = syncOpt.get();
                Map<String, Object> existingVars = syncInstance.getVariables();
                Map<String, Object> mergedVars = new HashMap<>();
                if (existingVars != null) {
                    mergedVars.putAll(existingVars);
                }
                mergedVars.putAll(variables);
                taskFormComponent.mergeCompletedTaskSnapshotIntoVariables(
                        taskId, userId, task.getTaskDefinitionKey(), mergedVars);
                // Prevent geometric __subTables__ bloat: collapse deep nested copies to the canonical
                // one-level structure before persisting the approval write-back.
                SubTableNestingSanitizer.stripDeepNestedSubTables(mergedVars);
                syncInstance.setVariables(mergedVars);

                processInstanceRepository.save(syncInstance);

                log.info("Synced {} approval variables back to local ProcessInstance {}",
                        mergedVars.size(), syncProcessId);
            }
        } catch (PortalException e) {
            throw e;
        } catch (DataAccessException e) {
            log.warn("Data access failure syncing approval variables to local ProcessInstance (task {}): {}",
                    taskId, e.getMessage());
            throw e;
        } catch (OptimisticLockException e) {
            log.warn("Optimistic lock failure syncing approval variables (task {}): {}", taskId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Failed to sync approval variables to local ProcessInstance: {}", e.getMessage());
            ProcessInstanceSyncComponent.rethrowIfRollbackOnlyAfterCatch(e, taskId);
        }

        // Record Change_History for field and sub-table changes during approval (best-effort)
        try {
            String chProcessId = task.getProcessInstanceId();
            Optional<ProcessInstance> chOpt = processInstanceRepository.findById(chProcessId);
            if (chOpt.isPresent()) {
                ProcessInstance chInstance = chOpt.get();
                Map<String, Object> chOldVars = chInstance.getVariables() != null
                        ? new HashMap<>(chInstance.getVariables())
                        : new HashMap<>();
                // Rebuild the change payload: only the newly submitted variables (exclude system keys)
                Map<String, Object> chSubmitted = new HashMap<>(variables);
                chSubmitted.remove("action");
                chSubmitted.remove("decision");
                chSubmitted.remove("approvalStatus");
                chSubmitted.remove("approval_result");
                chSubmitted.remove("approved");
                chSubmitted.remove("approval_comment");
                if (!chSubmitted.isEmpty()) {
                    ChangeHistoryContext chContext = ChangeHistoryContext.builder()
                            .processInstanceId(chProcessId)
                            .taskInstanceId(taskId)
                            .stageId(task.getTaskDefinitionKey())
                            .userId(userId)
                            .build();
                    // Record top-level field changes
                    changeHistoryComponent.recordFieldChanges(chContext, chOldVars, chSubmitted);
                    // Record sub-table changes
                    Object chOldSubTables = chOldVars.get("__subTables__");
                    Object chNewSubTables = chSubmitted.get("__subTables__");
                    if (chNewSubTables != null) {
                        recordSubTableChangeHistory(chContext, chOldSubTables, chNewSubTables);
                    }
                }
            }
        } catch (PortalException e) {
            throw e;
        } catch (DataAccessException e) {
            log.warn("Data access failure recording change history during task completion (task {}): {}",
                    taskId, e.getMessage());
            throw e;
        } catch (OptimisticLockException e) {
            log.warn("Optimistic lock failure recording change history (task {}): {}", taskId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Failed to record change history during task completion (task {}): {}",
                    taskId, e.getMessage());
            ProcessInstanceSyncComponent.rethrowIfRollbackOnlyAfterCatch(e, taskId);
        }

        // After task completion, check for active tasks; none may mean process completed
        // Compensation when ProcessCompletionListener notification fails and portal state drifts
        try {
            String processInstanceId = task.getProcessInstanceId();

            // Check process status via workflowEngineClient
            Optional<Map<String, Object>> processStatus = workflowEngineClient.getProcessInstanceStatus(processInstanceId);
            if (processStatus.isPresent()) {
                Map<String, Object> status = processStatus.get();
                Boolean isCompleted = (Boolean) status.get("completed");

                if (Boolean.TRUE.equals(isCompleted)) {
                    log.info("Process {} is completed after task completion, syncing portal process instance", processInstanceId);

                    // Update process instance status
                    Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processInstanceId);
                    if (optInstance.isPresent()) {
                        ProcessInstance instance = optInstance.get();
                        if ("RUNNING".equals(instance.getStatus())) {
                            instance.setStatus("COMPLETED");
                            LocalDateTime finishedAt = LocalDateTime.now();
                            instance.setEndTime(finishedAt);
                            instance.setCompletedAt(finishedAt);
                            instance.setCurrentNode(null);
                            instance.setCurrentAssignee(null);
                            processInstanceRepository.save(instance);
                            log.info("Process instance {} updated to COMPLETED with currentNode: {}",
                                    processInstanceId, instance.getCurrentNode());
                        }
                    }
                } else {
                    // Process still running; try next task info
                    String nextTaskName = (String) status.get("nextTaskName");
                    String nextAssignee = (String) status.get("nextAssignee");
                    String nextCandidateUsers = (String) status.get("nextCandidateUsers");
                    if (nextTaskName != null) {
                        processInstanceSyncComponent.updateProcessInstanceAssignee(
                                processInstanceId, nextAssignee, nextCandidateUsers, nextTaskName);
                        log.info("Process {} continues with next task: {}", processInstanceId, nextTaskName);
                    } else {
                        // No next user task; may be at non-user task (e.g. end event)
                        // Try to load current activity
                        log.info("No next user task found for process {}, checking for current activity", processInstanceId);
                        Optional<Map<String, Object>> currentActivity = processInstanceSyncComponent.getCurrentActivity(processInstanceId);
                        if (currentActivity.isPresent()) {
                            String currentActivityName = (String) currentActivity.get().get("activityName");
                            String currentActivityType = (String) currentActivity.get().get("activityType");
                            log.info("Current activity for process {}: {} (type: {})",
                                    processInstanceId, currentActivityName, currentActivityType);

                            // Skip SequenceFlow: name is a condition label (e.g. Yes/No), not currentNode
                            if ("SequenceFlow".equals(currentActivityType)) {
                                log.warn("Current activity is SequenceFlow (name: {}), skipping currentNode update for process {}",
                                        currentActivityName, processInstanceId);
                            } else {
                                // Update process instance current node
                                Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processInstanceId);
                                if (optInstance.isPresent()) {
                                    ProcessInstance instance = optInstance.get();
                                    instance.setCurrentNode(currentActivityName);
                                    instance.setCurrentAssignee(null);

                                    // End event means process completed
                                    if ("endEvent".equals(currentActivityType) || "EndEvent".equals(currentActivityType)) {
                                        log.info("Current activity is end event, marking process {} as COMPLETED", processInstanceId);
                                        instance.setStatus("COMPLETED");
                                        LocalDateTime finishedAt = LocalDateTime.now();
                                        instance.setEndTime(finishedAt);
                                        instance.setCompletedAt(finishedAt);
                                        instance.setCurrentNode(null);
                                    }

                                    processInstanceRepository.save(instance);
                                    log.info("Updated process instance {} currentNode to: {}, status: {}",
                                            processInstanceId, instance.getCurrentNode(), instance.getStatus());
                                }
                            }
                        }
                    }
                }
            }
        } catch (PortalException e) {
            throw e;
        } catch (DataAccessException e) {
            log.warn("Data access failure checking process status after task completion (task {}): {}",
                    taskId, e.getMessage());
            throw e;
        } catch (OptimisticLockException e) {
            log.warn("Optimistic lock failure checking process status after task completion (task {}): {}",
                    taskId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Unexpected failure checking process status after task completion (task {}): {}",
                    taskId, e.getMessage());
            ProcessInstanceSyncComponent.rethrowIfRollbackOnlyAfterCatch(e, taskId);
        }
    }

    // ========== Sub-table change history helpers ==========

    @SuppressWarnings("unchecked")
    private void recordSubTableChangeHistory(ChangeHistoryContext context,
                                              Object oldSubTablesObj,
                                              Object newSubTablesObj) {
        if (newSubTablesObj == null) {
            return;
        }
        try {
            Map<String, Object> oldMap = oldSubTablesObj instanceof Map
                    ? (Map<String, Object>) oldSubTablesObj
                    : java.util.Collections.emptyMap();
            Map<String, Object> newMap = (Map<String, Object>) newSubTablesObj;

            for (Map.Entry<String, Object> subTableEntry : newMap.entrySet()) {
                String subTableKey = subTableEntry.getKey();
                List<Map<String, Object>> newRows = subTableEntry.getValue() instanceof List
                        ? (List<Map<String, Object>>) subTableEntry.getValue()
                        : java.util.Collections.emptyList();
                List<Map<String, Object>> oldRows = oldMap.get(subTableKey) instanceof List
                        ? (List<Map<String, Object>>) oldMap.get(subTableKey)
                        : java.util.Collections.emptyList();

                List<SubTableChange> changes = computeSubTableRowChanges(oldRows, newRows);
                if (!changes.isEmpty()) {
                    changeHistoryComponent.recordSubTableChanges(
                            context, subTableKey, changes);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to record sub-table changes during task completion: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<SubTableChange> computeSubTableRowChanges(
            List<Map<String, Object>> oldRows,
            List<Map<String, Object>> newRows) {
        List<SubTableChange> changes = new ArrayList<>();

        // Build row lookup maps by row id (fallback: id_idw, rowId, _rowKey, rowKey, first non-internal value)
        Map<Object, Map<String, Object>> oldRowMap = new HashMap<>();
        for (Map<String, Object> row : oldRows) {
            Object rowId = ChangeHistoryComponent.resolveRowIdentifier(row);
            if (rowId != null) {
                oldRowMap.put(rowId, row);
            }
        }
        Map<Object, Map<String, Object>> newRowMap = new HashMap<>();
        for (Map<String, Object> row : newRows) {
            Object rowId = ChangeHistoryComponent.resolveRowIdentifier(row);
            if (rowId != null) {
                newRowMap.put(rowId, row);
            }
        }

        // Detect ROW_ADD (in new but not in old)
        for (Map.Entry<Object, Map<String, Object>> entry : newRowMap.entrySet()) {
            Object rowId = entry.getKey();
            if (!oldRowMap.containsKey(rowId)) {
                changes.add(SubTableChange.builder()
                        .changeType("ROW_ADD")
                        .rowIdentifier(String.valueOf(rowId))
                        .oldValues(null)
                        .newValues(entry.getValue())
                        .build());
            }
        }

        // Detect ROW_DELETE (in old but not in new)
        for (Map.Entry<Object, Map<String, Object>> entry : oldRowMap.entrySet()) {
            Object rowId = entry.getKey();
            if (!newRowMap.containsKey(rowId)) {
                changes.add(SubTableChange.builder()
                        .changeType("ROW_DELETE")
                        .rowIdentifier(String.valueOf(rowId))
                        .oldValues(entry.getValue())
                        .newValues(null)
                        .build());
            }
        }

        // Detect ROW_UPDATE (in both but field values differ)
        for (Map.Entry<Object, Map<String, Object>> entry : newRowMap.entrySet()) {
            Object rowId = entry.getKey();
            Map<String, Object> oldRow = oldRowMap.get(rowId);
            if (oldRow != null) {
                Map<String, Object> newRow = entry.getValue();
                Map<String, Object> changedFields = new HashMap<>();
                Map<String, Object> oldChangedFields = new HashMap<>();
                boolean hasChanges = false;
                // Compare all fields except 'id' (the row key)
                for (Map.Entry<String, Object> field : newRow.entrySet()) {
                    if ("id".equals(field.getKey())) continue;
                    Object oldFieldVal = oldRow.get(field.getKey());
                    if (!java.util.Objects.equals(oldFieldVal, field.getValue())) {
                        changedFields.put(field.getKey(), field.getValue());
                        oldChangedFields.put(field.getKey(), oldFieldVal);
                        hasChanges = true;
                    }
                }
                if (hasChanges) {
                    changes.add(SubTableChange.builder()
                            .changeType("ROW_UPDATE")
                            .rowIdentifier(String.valueOf(rowId))
                            .oldValues(oldChangedFields)
                            .newValues(changedFields)
                            .build());
                }
            }
        }

        return changes;
    }
}
