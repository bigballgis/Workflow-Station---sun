package com.workflow.controller;

import com.workflow.component.TaskManagerComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller-layer support for assembling process instance flow history.
 *
 * <p>Shapes Flowable historic activity/task/comment data into the portal UI timeline payload.
 * Includes completed Send Email {@code serviceTask} rows ({@code operationType=SEND}, operator
 * {@code system}). Reads through {@link HistoryService}/{@link TaskService}/{@link RepositoryService}
 * and resolves display names via {@link TaskManagerComponent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TaskHistoryAssembler {

    private static final String SEND_EMAIL_DELEGATE = "${sendEmailTaskDelegate}";
    private static final String SYSTEM_OPERATOR = "system";

    private final HistoryService historyService;
    private final TaskService taskService;
    private final TaskManagerComponent taskManagerComponent;
    private final RepositoryService repositoryService;

    /**
     * Assemble the complete flow-history timeline for a process instance, including synthetic
     * TRANSFER entries and resolved operator display names.
     */
    List<Map<String, Object>> assembleProcessInstanceHistory(String processInstanceId) {
        log.info("Getting process instance history for: {}", processInstanceId);

        // Load historic activity instances for the process
        List<HistoricActivityInstance> activities = historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(processInstanceId)
            .orderByHistoricActivityInstanceStartTime().asc()
            .list();

        // Load task history for deleteReason
        List<HistoricTaskInstance> tasks = historyService
            .createHistoricTaskInstanceQuery()
            .processInstanceId(processInstanceId)
            .list();

        // Map taskId -> deleteReason
        Map<String, String> taskDeleteReasons = tasks.stream()
            .filter(task -> task.getDeleteReason() != null)
            .collect(Collectors.toMap(
                HistoricTaskInstance::getId,
                HistoricTaskInstance::getDeleteReason,
                (existing, replacement) -> existing
            ));

        // Build taskId → comment mapping from Flowable's native comment system (ACT_HI_COMMENT).
        // For each task, take the latest comment message.
        // Transfer-typed comments are tracked separately so we can inject synthetic TRANSFER entries.
        Map<String, String> taskComments = new HashMap<>();
        Map<String, String> taskReturnComments = new HashMap<>();
        Map<String, String> taskDraftComments = new HashMap<>();
        // Same runtime userTask keeps one taskId across multiple transfers; keep every transfer comment.
        Map<String, List<Comment>> taskTransferCommentsByTaskId = new HashMap<>();
        try {
            List<Comment> allComments = taskService.getProcessInstanceComments(processInstanceId);
            if (allComments != null) {
                for (Comment c : allComments) {
                    if (c.getTaskId() == null) continue;
                    if ("return".equals(c.getType())) {
                        if (c.getFullMessage() != null && !c.getFullMessage().isBlank()) {
                            String msg = c.getFullMessage();
                            if (isDraftReturnCommentMessage(msg)) {
                                taskDraftComments.put(c.getTaskId(), msg);
                            } else {
                                taskReturnComments.put(c.getTaskId(), msg);
                            }
                        }
                    } else if ("draft".equals(c.getType())) {
                        if (c.getFullMessage() != null && !c.getFullMessage().isBlank()) {
                            taskDraftComments.put(c.getTaskId(), c.getFullMessage());
                        }
                    } else if ("transfer".equals(c.getType())) {
                        taskTransferCommentsByTaskId.computeIfAbsent(c.getTaskId(), k -> new ArrayList<>()).add(c);
                    } else if (c.getFullMessage() != null && !c.getFullMessage().isBlank()) {
                        taskComments.put(c.getTaskId(), c.getFullMessage());
                    }
                }
                for (List<Comment> transferComments : taskTransferCommentsByTaskId.values()) {
                    transferComments.sort(Comparator.comparing(Comment::getTime, Comparator.nullsLast(Comparator.naturalOrder())));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load Flowable comments for process {}: {}", processInstanceId, e.getMessage());
        }

        // Process instance info for startEvent initiator resolution
        HistoricProcessInstance processInstance = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        String processStartUserId = processInstance != null ? processInstance.getStartUserId() : null;
        String processDefinitionId = processInstance != null ? processInstance.getProcessDefinitionId() : null;
        BpmnModel bpmnModel = loadBpmnModel(processDefinitionId);
        Set<String> sendEmailActivityIds = resolveSendEmailActivityIds(bpmnModel);

        Set<String> userIdsToResolve = new LinkedHashSet<>();
        if (processStartUserId != null && !processStartUserId.isBlank()) {
            userIdsToResolve.add(processStartUserId.trim());
        }
        for (HistoricActivityInstance activity : activities) {
            String assignee = activity.getAssignee();
            if (assignee != null && !assignee.isBlank()) {
                userIdsToResolve.add(assignee.trim());
            }
        }
        for (List<Comment> transferComments : taskTransferCommentsByTaskId.values()) {
            for (Comment tc : transferComments) {
                String transferUserId = tc.getUserId();
                if (transferUserId != null && !transferUserId.isBlank()) {
                    userIdsToResolve.add(transferUserId.trim());
                }
            }
        }
        long __tNames = System.nanoTime();
        Map<String, String> displayNamesByUserId = taskManagerComponent.resolveUserDisplayNames(userIdsToResolve);
        log.info("[PERF] processInstanceHistory.resolveDisplayNames {} distinct users took {} ms",
                userIdsToResolve.size(), (System.nanoTime() - __tNames) / 1_000_000L);

        // Shape response for the portal UI
        List<Map<String, Object>> historyList = activities.stream()
            .filter(activity -> shouldIncludeInHistory(activity, sendEmailActivityIds, bpmnModel))
            .map(activity -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", activity.getId());
                item.put("taskId", activity.getTaskId());
                item.put("taskName", activity.getActivityName());
                item.put("activityId", activity.getActivityId());
                item.put("activityName", activity.getActivityName());
                item.put("activityType", activity.getActivityType());

                // Derive operation type from activity type and deleteReason
                String activityType = activity.getActivityType();
                boolean sendEmailTask = isCompletedSendEmailTask(activity, sendEmailActivityIds, bpmnModel);
                String operationType = "PENDING";
                if (activity.getEndTime() != null) {
                    if ("startEvent".equals(activityType)) {
                        operationType = "SUBMIT";
                    } else if ("exclusiveGateway".equals(activityType) ||
                               "parallelGateway".equals(activityType) ||
                               "inclusiveGateway".equals(activityType)) {
                        operationType = "GATEWAY";
                    } else if (sendEmailTask) {
                        operationType = "SEND";
                    } else if ("userTask".equals(activityType)) {
                        String taskIdForActivity = activity.getTaskId();
                        if (taskIdForActivity != null && taskDraftComments.containsKey(taskIdForActivity)) {
                            operationType = "DRAFT";
                        } else if (taskIdForActivity != null && taskReturnComments.containsKey(taskIdForActivity)) {
                            operationType = "RETURN";
                        } else {
                            // Use deleteReason to distinguish APPROVE vs REJECT vs rollback
                            String deleteReason = taskDeleteReasons.get(taskIdForActivity);
                            if (deleteReason != null) {
                                String dr = deleteReason.toLowerCase(java.util.Locale.ROOT);
                                if (isFlowableReturnDeleteReason(dr)) {
                                    operationType = "RETURN";
                                } else if (dr.contains("rejected") || dr.contains("reject")) {
                                    operationType = "REJECT";
                                } else if (dr.contains("approved") || dr.contains("approve")) {
                                    operationType = "APPROVE";
                                } else if (dr.contains("transfer")) {
                                    operationType = "TRANSFER";
                                } else if (dr.contains("delegate")) {
                                    operationType = "DELEGATE";
                                } else {
                                    operationType = "APPROVE";
                                }
                            } else {
                                operationType = "APPROVE";
                            }
                        }
                    } else {
                        operationType = "APPROVE";
                    }
                }
                item.put("operationType", operationType);

                if (sendEmailTask) {
                    item.put("operatorId", SYSTEM_OPERATOR);
                    item.put("operatorName", SYSTEM_OPERATOR);
                } else {
                    // startEvent has no assignee in Flowable; use process initiator
                    String assignee = activity.getAssignee();
                    if ((assignee == null || assignee.isEmpty()) && "startEvent".equals(activityType)) {
                        assignee = processStartUserId;
                    }
                    item.put("operatorId", assignee);

                    String operatorName = assignee;
                    if (assignee != null && !assignee.isEmpty()) {
                        operatorName = displayNamesByUserId.getOrDefault(assignee.trim(), assignee);
                    } else if ("startEvent".equals(activityType) && processStartUserId != null) {
                        operatorName = displayNamesByUserId.getOrDefault(processStartUserId.trim(), processStartUserId);
                    }
                    item.put("operatorName", operatorName);
                }

                item.put("operationTime", activity.getEndTime() != null ?
                    activity.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString() :
                    activity.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString());

                // Prefer return/transfer typed comments, then generic comment; avoid raw deleteReason for RETURN
                String taskId = activity.getTaskId();
                String comment = null;
                if (taskId != null) {
                    comment = taskDraftComments.get(taskId);
                    if (comment == null) {
                        comment = taskReturnComments.get(taskId);
                    }
                    if (comment == null) {
                        comment = taskComments.get(taskId);
                    }
                    if (comment == null && !"RETURN".equals(operationType) && !"DRAFT".equals(operationType)
                            && taskDeleteReasons.containsKey(taskId)) {
                        comment = taskDeleteReasons.get(taskId);
                    }
                }
                item.put("comment", comment);
                item.put("duration", activity.getDurationInMillis());

                return item;
            })
            .collect(Collectors.toList());

        // Inject synthetic TRANSFER entries for tasks that have transfer comments.
        // Each transfer entry appears right before the corresponding task entry so the
        // timeline shows: ... → TRANSFER (by originator) → PENDING (current assignee).
        int transferCommentCount = taskTransferCommentsByTaskId.values().stream().mapToInt(List::size).sum();
        if (transferCommentCount > 0) {
            List<Map<String, Object>> enrichedList = new ArrayList<>(historyList.size() + transferCommentCount);
            for (Map<String, Object> item : historyList) {
                String tid = (String) item.get("taskId");
                List<Comment> transferComments = tid != null ? taskTransferCommentsByTaskId.get(tid) : null;
                if (transferComments != null) {
                    for (Comment tc : transferComments) {
                        Map<String, Object> transferItem = new HashMap<>();
                        String commentId = tc.getId() != null ? tc.getId() : "noid";
                        transferItem.put("id", item.get("id") + "_transfer_" + commentId);
                        transferItem.put("taskId", tid);
                        transferItem.put("taskName", item.get("taskName"));
                        transferItem.put("activityId", item.get("activityId"));
                        transferItem.put("activityName", item.get("activityName"));
                        transferItem.put("activityType", "userTask");
                        transferItem.put("operationType", "TRANSFER");

                        String transferUserId = tc.getUserId();
                        transferItem.put("operatorId", transferUserId);
                        String transferOperatorName = transferUserId;
                        if (transferUserId != null && !transferUserId.isEmpty()) {
                            transferOperatorName = displayNamesByUserId.getOrDefault(
                                    transferUserId.trim(), transferUserId);
                        }
                        transferItem.put("operatorName", transferOperatorName);

                        transferItem.put("operationTime", tc.getTime() != null
                                ? tc.getTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().toString()
                                : item.get("operationTime"));
                        String reason = tc.getFullMessage();
                        transferItem.put("comment", reason != null && !reason.isBlank() ? reason : null);
                        transferItem.put("duration", null);

                        enrichedList.add(transferItem);
                    }
                }
                enrichedList.add(item);
            }
            historyList = enrichedList;
        }

        return historyList;
    }

    private BpmnModel loadBpmnModel(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            return null;
        }
        try {
            return repositoryService.getBpmnModel(processDefinitionId);
        } catch (Exception e) {
            log.error("Failed to load BPMN model for processDefinition {}: {}",
                    processDefinitionId, e.getMessage());
            return null;
        }
    }

    /**
     * Collect activity IDs of Send Email service tasks from the process definition BPMN
     * (all processes, including nested subprocess containers).
     */
    private Set<String> resolveSendEmailActivityIds(BpmnModel model) {
        if (model == null || model.getProcesses() == null || model.getProcesses().isEmpty()) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (Process process : model.getProcesses()) {
            for (ServiceTask serviceTask : process.findFlowElementsOfType(ServiceTask.class, true)) {
                if (SEND_EMAIL_DELEGATE.equals(serviceTask.getImplementation())) {
                    ids.add(serviceTask.getId());
                }
            }
        }
        return ids;
    }

    private static boolean shouldIncludeInHistory(
            HistoricActivityInstance activity, Set<String> sendEmailActivityIds, BpmnModel bpmnModel) {
        String type = activity.getActivityType();
        if ("userTask".equals(type)
                || "manualTask".equals(type)
                || "scriptTask".equals(type)
                || "businessRuleTask".equals(type)
                || "receiveTask".equals(type)
                || "startEvent".equals(type)
                || "endEvent".equals(type)
                || "exclusiveGateway".equals(type)
                || "parallelGateway".equals(type)
                || "inclusiveGateway".equals(type)) {
            return true;
        }
        return isCompletedSendEmailTask(activity, sendEmailActivityIds, bpmnModel);
    }

    private static boolean isCompletedSendEmailTask(
            HistoricActivityInstance activity, Set<String> sendEmailActivityIds, BpmnModel bpmnModel) {
        if (!"serviceTask".equals(activity.getActivityType())
                || activity.getEndTime() == null
                || activity.getActivityId() == null) {
            return false;
        }
        if (sendEmailActivityIds.contains(activity.getActivityId())) {
            return true;
        }
        if (bpmnModel == null) {
            return false;
        }
        FlowElement flowElement = bpmnModel.getFlowElement(activity.getActivityId());
        return flowElement instanceof ServiceTask serviceTask
                && SEND_EMAIL_DELEGATE.equals(serviceTask.getImplementation());
    }

    /** Flowable changeActivityState sets deleteReason like "Change activity to Activity_xxx". */
    private static boolean isFlowableReturnDeleteReason(String deleteReasonLower) {
        if (deleteReasonLower == null || deleteReasonLower.isBlank()) {
            return false;
        }
        return deleteReasonLower.contains("change activity")
                || deleteReasonLower.contains("changeactivity")
                || deleteReasonLower.contains("rollback")
                || deleteReasonLower.contains("returned to");
    }

    /** Draft returns use comment prefix when Flowable stores type as {@code return}. */
    private static boolean isDraftReturnCommentMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String trimmed = message.trim();
        return trimmed.regionMatches(true, 0, "Drafted to ", 0, "Drafted to ".length());
    }
}
