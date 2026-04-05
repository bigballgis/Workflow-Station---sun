package com.workflow.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.enums.AssigneeAnchor;
import com.workflow.enums.AssigneeType;
import com.platform.messaging.support.NotificationDispatchHelper;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.service.LastUserTaskAssigneeQuery;
import com.workflow.service.TaskAssigneeResolver;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务创建时按 BPMN 扩展属性 {@code assigneeType} 等解析处理人。
 * <p>语义见 {@code .kiro/docs/assignee-type-convergence.md}。</p>
 */
@Slf4j
@Component
public class TaskAssignmentListener implements FlowableEventListener {

    @Autowired
    @Lazy
    private TaskAssigneeResolver taskAssigneeResolver;

    @Autowired
    @Lazy
    private LastUserTaskAssigneeQuery lastUserTaskAssigneeQuery;

    @Autowired
    @Lazy
    private TaskService taskService;

    @Autowired
    @Lazy
    private RuntimeService runtimeService;

    @Autowired
    @Lazy
    private HistoryService historyService;

    @Autowired
    @Lazy
    private RepositoryService repositoryService;

    @Autowired
    @Lazy
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;

    @Autowired
    @Lazy
    private NotificationDispatchHelper notificationDispatchHelper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() == FlowableEngineEventType.TASK_CREATED) {
            handleTaskCreated(event);
        }
    }

    private void handleTaskCreated(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEventImpl)) {
            return;
        }

        FlowableEntityEventImpl entityEvent = (FlowableEntityEventImpl) event;
        Object entity = entityEvent.getEntity();

        if (!(entity instanceof TaskEntity)) {
            return;
        }

        TaskEntity task = (TaskEntity) entity;
        String taskId = task.getId();
        String processInstanceId = task.getProcessInstanceId();
        String taskDefinitionKey = task.getTaskDefinitionKey();
        String processDefinitionId = task.getProcessDefinitionId();

        log.info("Task created: taskId={}, taskName={}, taskDefKey={}, processInstanceId={}",
                taskId, task.getName(), taskDefinitionKey, processInstanceId);

        if (task.getAssignee() != null && !task.getAssignee().isEmpty()) {
            log.info("Task {} already has assignee: {}", taskId, task.getAssignee());
            notifyNewTask(task.getAssignee(), taskId, task.getName(), processInstanceId);
            return;
        }

        try {
            String assigneeTypeRaw = null;
            String roleId = null;
            String businessUnitId = null;
            String assigneeValue = null;
            String assigneeAnchorExt = null;
            String manualAssignVariable = null;
            String manualAssignBuVariable = null;
            String manualAssignRoleVariable = null;
            String assigneeVariable = null;
            Map<String, Object> cachedVariables = null;

            UserTask userTask = null;
            if (processDefinitionId != null && taskDefinitionKey != null) {
                BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
                if (bpmnModel != null) {
                    FlowElement flowElement = bpmnModel.getFlowElement(taskDefinitionKey);
                    if (flowElement instanceof UserTask ut) {
                        userTask = ut;
                        assigneeTypeRaw = getExtensionProperty(ut, "assigneeType");
                        roleId = getExtensionProperty(ut, "roleId");
                        businessUnitId = getExtensionProperty(ut, "businessUnitId");
                        assigneeValue = getExtensionProperty(ut, "assigneeValue");
                        assigneeAnchorExt = getExtensionProperty(ut, "assigneeAnchor");
                        manualAssignVariable = getExtensionProperty(ut, "manualAssignVariable");
                        manualAssignBuVariable = getExtensionProperty(ut, "manualAssignBuVariable");
                        manualAssignRoleVariable = getExtensionProperty(ut, "manualAssignRoleVariable");
                        assigneeVariable = getExtensionProperty(ut, "assigneeVariable");

                        if ((assigneeTypeRaw == null || assigneeTypeRaw.isEmpty()) && ut.getAssignee() != null
                                && !ut.getAssignee().isBlank()) {
                            String ga = ut.getAssignee().trim();
                            if (isInitiatorExpression(ga)) {
                                assigneeTypeRaw = "INITIATOR";
                            }
                        }

                        log.info("Found BPMN extension: assigneeType={}, roleId={}, businessUnitId={}, assigneeAnchor={}",
                                assigneeTypeRaw, roleId, businessUnitId, assigneeAnchorExt);
                    }
                }
            }

            if (assigneeTypeRaw == null || assigneeTypeRaw.isEmpty()) {
                cachedVariables = runtimeService.getVariables(processInstanceId);
                assigneeTypeRaw = getStringVariable(cachedVariables, "assigneeType");
                roleId = firstNonBlank(roleId, getStringVariable(cachedVariables, "roleId"));
                businessUnitId = firstNonBlank(businessUnitId, getStringVariable(cachedVariables, "businessUnitId"));
                assigneeValue = firstNonBlank(assigneeValue, getStringVariable(cachedVariables, "assigneeValue"));
                assigneeAnchorExt = firstNonBlank(assigneeAnchorExt, getStringVariable(cachedVariables, "assigneeAnchor"));
            }

            assigneeTypeRaw = normalizeLegacyAssigneeType(assigneeTypeRaw, assigneeValue);
            if (assigneeTypeRaw != null && "INITIATOR".equalsIgnoreCase(assigneeTypeRaw.trim())) {
                assigneeValue = null;
            }

            if (assigneeTypeRaw == null || assigneeTypeRaw.isEmpty()) {
                log.debug("No assigneeType defined for task {}", taskId);
                return;
            }

            if ("ELEMENT_VARIABLE".equalsIgnoreCase(assigneeTypeRaw.trim())) {
                handleElementVariableAssignment(task, taskId, processInstanceId, processDefinitionId, taskDefinitionKey);
                return;
            }

            Map<String, Object> processVariables = cachedVariables != null
                    ? cachedVariables
                    : runtimeService.getVariables(processInstanceId);

            String initiatorId = getStringVariable(processVariables, "initiator");
            if (initiatorId == null || initiatorId.isEmpty()) {
                ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .singleResult();
                if (pi != null && pi.getStartUserId() != null && !pi.getStartUserId().isBlank()) {
                    initiatorId = pi.getStartUserId().trim();
                    log.info("Task {}: no initiator variable; using process startUserId as fallback: {}",
                            taskId, initiatorId);
                }
            }
            if (initiatorId == null || initiatorId.isEmpty()) {
                HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .singleResult();
                if (hpi != null && hpi.getStartUserId() != null && !hpi.getStartUserId().isBlank()) {
                    initiatorId = hpi.getStartUserId().trim();
                    log.info("Task {}: no initiator variable; using historic startUserId as fallback: {}",
                            taskId, initiatorId);
                }
            }
            if (initiatorId == null || initiatorId.isEmpty()) {
                log.warn("No initiator found for process instance {} (variable empty and no startUserId)",
                        processInstanceId);
                return;
            }

            AssigneeType resolvedType = AssigneeType.fromCode(assigneeTypeRaw.trim());
            if (resolvedType == null) {
                log.error("Unknown or deprecated assigneeType '{}' for task {}", assigneeTypeRaw, taskId);
                return;
            }

            if (resolvedType == AssigneeType.MANUAL_ASSIGN) {
                applyResolveResult(taskId, task, processInstanceId,
                        resolveManualAssign(taskDefinitionKey, manualAssignVariable, manualAssignBuVariable,
                                manualAssignRoleVariable, processVariables, initiatorId),
                        assigneeTypeRaw);
                return;
            }

            if (resolvedType == AssigneeType.ASSIGNEE_FROM_VARIABLE) {
                String varName = firstNonBlank(assigneeVariable, assigneeValue);
                TaskAssigneeResolver.ResolveResult vr = resolveAssigneeFromVariable(varName, processVariables);
                applyResolveResult(taskId, task, processInstanceId, vr, assigneeTypeRaw);
                return;
            }

            if (roleId == null || roleId.isEmpty()) {
                roleId = assigneeValue;
            }
            if ((businessUnitId == null || businessUnitId.isEmpty()) && resolvedType == AssigneeType.BU_ROLE) {
                businessUnitId = assigneeValue;
            }

            AssigneeAnchor anchor = computeAnchor(assigneeTypeRaw.trim(), resolvedType, assigneeAnchorExt);
            String anchorUserId = null;
            if (resolvedType.requiresAnchorUserId()) {
                anchorUserId = resolveAnchorUserId(anchor, initiatorId, processInstanceId);
            }

            String activeBusinessUnitId = getStringVariable(processVariables, "activeBusinessUnitId");

            log.info("Resolving assignee for task {}: rawType={}, resolvedType={}, anchor={}, anchorUser={}, roleId={}, buId={}, activeBu={}",
                    taskId, assigneeTypeRaw, resolvedType, anchor, anchorUserId, roleId, businessUnitId, activeBusinessUnitId);

            TaskAssigneeResolver.ResolveResult result = taskAssigneeResolver.resolve(
                    assigneeTypeRaw.trim(), roleId, businessUnitId, initiatorId, anchorUserId, activeBusinessUnitId);

            applyResolveResult(taskId, task, processInstanceId, result, assigneeTypeRaw);
        } catch (Exception e) {
            log.error("Error handling task assignment for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    private void applyResolveResult(String taskId, TaskEntity task, String processInstanceId,
                                    TaskAssigneeResolver.ResolveResult result, String assigneeTypeRaw) {
        if (result == null) {
            return;
        }
        if (result.getErrorMessage() != null) {
            log.error("Failed to resolve assignee for task {}: {}", taskId, result.getErrorMessage());
            return;
        }
        if (result.getAssignee() != null && !result.getAssignee().isBlank()) {
            taskService.setAssignee(taskId, result.getAssignee().trim());
            log.info("Task {} assigned to user: {}", taskId, result.getAssignee());
            notifyNewTask(result.getAssignee().trim(), taskId, task.getName(), processInstanceId);
            return;
        }
        List<String> cands = result.getCandidateUsers();
        if (cands != null && !cands.isEmpty()) {
            for (String candidateUser : cands) {
                if (candidateUser != null && !candidateUser.isBlank()) {
                    taskService.addCandidateUser(taskId, candidateUser.trim());
                }
            }
            log.info("Task {} set candidate users: {}", taskId, cands);
            for (String candidateUser : cands) {
                if (candidateUser != null && !candidateUser.isBlank()) {
                    notifyCandidateTask(candidateUser.trim(), taskId, task.getName(), processInstanceId);
                }
            }
        } else {
            log.error("Task {}: no assignee and no candidates (assigneeType={})", taskId, assigneeTypeRaw);
        }
    }

    private TaskAssigneeResolver.ResolveResult resolveManualAssign(String taskDefKey,
                                                                   String manualAssignVariable,
                                                                   String manualAssignBuVariable,
                                                                   String manualAssignRoleVariable,
                                                                   Map<String, Object> variables,
                                                                   String initiatorId) {
        String defKey = taskDefKey != null ? taskDefKey : "task";
        String userVar = manualAssignVariable != null && !manualAssignVariable.isBlank()
                ? manualAssignVariable.trim()
                : "manualAssignee_" + defKey;
        String uid = getStringVariable(variables, userVar);
        if (uid != null && !uid.isBlank()) {
            return TaskAssigneeResolver.ResolveResult.builder()
                    .assignee(uid.trim())
                    .assigneeType(AssigneeType.MANUAL_ASSIGN)
                    .requiresClaim(false)
                    .build();
        }
        String buVar = manualAssignBuVariable != null && !manualAssignBuVariable.isBlank()
                ? manualAssignBuVariable.trim()
                : "manualAssignBu_" + defKey;
        String roleVar = manualAssignRoleVariable != null && !manualAssignRoleVariable.isBlank()
                ? manualAssignRoleVariable.trim()
                : "manualAssignRole_" + defKey;
        String buId = getStringVariable(variables, buVar);
        String rid = getStringVariable(variables, roleVar);
        if (buId == null || buId.isBlank() || rid == null || rid.isBlank()) {
            return TaskAssigneeResolver.ResolveResult.builder()
                    .assigneeType(AssigneeType.MANUAL_ASSIGN)
                    .errorMessage("MANUAL_ASSIGN: set variable " + userVar + " or both " + buVar + " and " + roleVar)
                    .build();
        }
        return taskAssigneeResolver.resolve(AssigneeType.BU_ROLE, rid, buId, initiatorId, null);
    }

    private TaskAssigneeResolver.ResolveResult resolveAssigneeFromVariable(String varName,
                                                                            Map<String, Object> variables) {
        if (varName == null || varName.isBlank()) {
            return TaskAssigneeResolver.ResolveResult.builder()
                    .assigneeType(AssigneeType.ASSIGNEE_FROM_VARIABLE)
                    .errorMessage("ASSIGNEE_FROM_VARIABLE requires assigneeVariable or assigneeValue")
                    .build();
        }
        Object raw = variables != null ? variables.get(varName.trim()) : null;
        List<String> ids = new ArrayList<>();
        if (raw == null) {
            return taskAssigneeResolver.resolveFromUserIdList(AssigneeType.ASSIGNEE_FROM_VARIABLE, ids);
        }
        if (raw instanceof Collection<?> col) {
            for (Object o : col) {
                if (o != null) {
                    ids.addAll(splitUserList(o.toString()));
                }
            }
        } else {
            ids.addAll(splitUserList(raw.toString()));
        }
        return taskAssigneeResolver.resolveFromUserIdList(AssigneeType.ASSIGNEE_FROM_VARIABLE, ids);
    }

    private static List<String> splitUserList(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private AssigneeAnchor computeAnchor(String assigneeTypeRaw, AssigneeType type, String extAnchor) {
        if (extAnchor != null && !extAnchor.isBlank()) {
            return AssigneeAnchor.fromCode(extAnchor);
        }
        if (type == AssigneeType.HIERARCHY_ROLE) {
            return inferHierarchyAnchorFromRaw(assigneeTypeRaw);
        }
        return AssigneeAnchor.INITIATOR;
    }

    private static AssigneeAnchor inferHierarchyAnchorFromRaw(String raw) {
        if (raw == null) {
            return AssigneeAnchor.INITIATOR;
        }
        String u = raw.trim().toUpperCase();
        if ("CURRENT_BU_ROLE".equals(u) || "CURRENT_PARENT_BU_ROLE".equals(u)) {
            return AssigneeAnchor.LAST_TASK_ASSIGNEE;
        }
        if ("DEPT_OTHERS".equals(u) || "DEPTOTHERS".equals(u)
                || "PARENT_DEPT".equals(u) || "PARENTDEPT".equals(u)) {
            return AssigneeAnchor.LAST_TASK_ASSIGNEE;
        }
        return AssigneeAnchor.INITIATOR;
    }

    private String resolveAnchorUserId(AssigneeAnchor anchor, String initiatorId, String processInstanceId) {
        if (anchor == AssigneeAnchor.INITIATOR) {
            return initiatorId;
        }
        return lastUserTaskAssigneeQuery.findLastCompletedUserTaskAssignee(processInstanceId)
                .orElse(initiatorId);
    }

    private void handleElementVariableAssignment(TaskEntity task, String taskId,
                                                 String processInstanceId,
                                                 String processDefinitionId,
                                                 String taskDefinitionKey) {
        try {
            log.info("Handling ELEMENT_VARIABLE assignment for task {}", taskId);

            String executionId = task.getExecutionId();
            Object currentItemObj = runtimeService.getVariable(executionId, "currentItem");

            if (currentItemObj == null) {
                log.warn("currentItem variable is null for task {}, task will remain CREATED", taskId);
                return;
            }

            if (!(currentItemObj instanceof Map)) {
                log.warn("currentItem variable is not a Map for task {}, task will remain CREATED", taskId);
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> currentItem = (Map<String, Object>) currentItemObj;

            Object rowIdObj = currentItem.get("rowId");
            Object rowVersionObj = currentItem.get("rowVersion");

            Long subTableRowId = null;
            Long subTableRowVersion = null;

            if (rowIdObj != null) {
                if (rowIdObj instanceof Number) {
                    subTableRowId = ((Number) rowIdObj).longValue();
                } else {
                    try {
                        subTableRowId = Long.parseLong(String.valueOf(rowIdObj));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid rowId format in currentItem for task {}: {}", taskId, rowIdObj);
                    }
                }
            }

            if (rowVersionObj != null) {
                if (rowVersionObj instanceof Number) {
                    subTableRowVersion = ((Number) rowVersionObj).longValue();
                } else {
                    try {
                        subTableRowVersion = Long.parseLong(String.valueOf(rowVersionObj));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid rowVersion format in currentItem for task {}: {}", taskId, rowVersionObj);
                    }
                }
            }

            String subTableId = null;
            String subTableName = null;
            String assigneeFieldFromBpmn = null;

            if (processDefinitionId != null && taskDefinitionKey != null) {
                BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
                if (bpmnModel != null) {
                    FlowElement flowElement = bpmnModel.getFlowElement(taskDefinitionKey);
                    if (flowElement instanceof UserTask userTask) {
                        subTableId = getExtensionProperty(userTask, "subTableId");
                        subTableName = getExtensionProperty(userTask, "subTableName");
                        assigneeFieldFromBpmn = getExtensionProperty(userTask, "assigneeField");
                    }
                }
            }

            // 与门户 buildParticipantsCollection、子表列名对齐：优先 BPMN assigneeField，其次 assigneeId，再次 assignee_user_id
            Object assigneeIdObj = null;
            if (assigneeFieldFromBpmn != null && !assigneeFieldFromBpmn.isBlank()) {
                assigneeIdObj = currentItem.get(assigneeFieldFromBpmn.trim());
            }
            if (assigneeIdObj == null) {
                assigneeIdObj = currentItem.get("assigneeId");
            }
            if (assigneeIdObj == null) {
                assigneeIdObj = currentItem.get("assignee_user_id");
            }
            if (assigneeIdObj == null) {
                log.warn("No assignee in currentItem for task {} (tried assigneeField={}, assigneeId, assignee_user_id); task will remain CREATED",
                        taskId, assigneeFieldFromBpmn);
                return;
            }

            String assigneeId = String.valueOf(assigneeIdObj);

            try {
                taskService.setAssignee(taskId, assigneeId);
                log.info("Task {} assigned to user {} via ELEMENT_VARIABLE", taskId, assigneeId);
                notifyNewTask(assigneeId, taskId, task.getName(), processInstanceId);
            } catch (Exception e) {
                log.warn("Failed to set assignee {} for task {}: {}, task will remain CREATED",
                        assigneeId, taskId, e.getMessage());
                return;
            }

            Map<String, Object> extendedProps = new HashMap<>();
            extendedProps.put("multiInstance", true);
            if (subTableRowId != null) {
                extendedProps.put("subTableRowId", subTableRowId);
            }
            if (subTableRowVersion != null) {
                extendedProps.put("subTableRowVersion", subTableRowVersion);
            }
            if (subTableId != null) {
                extendedProps.put("subTableId", subTableId);
            }
            if (subTableName != null) {
                extendedProps.put("subTableName", subTableName);
            }

            String extendedPropertiesJson;
            try {
                extendedPropertiesJson = objectMapper.writeValueAsString(extendedProps);
            } catch (Exception e) {
                log.error("Failed to serialize extendedProperties for task {}: {}", taskId, e.getMessage());
                extendedPropertiesJson = "{}";
            }

            try {
                ExtendedTaskInfo extInfo = ExtendedTaskInfo.builder()
                        .taskId(taskId)
                        .processInstanceId(processInstanceId)
                        .processDefinitionId(processDefinitionId)
                        .taskDefinitionKey(taskDefinitionKey)
                        .taskName(task.getName())
                        .assignmentType(AssignmentType.USER)
                        .assignmentTarget(assigneeId)
                        .status("ASSIGNED")
                        .createdTime(LocalDateTime.now())
                        .extendedProperties(extendedPropertiesJson)
                        .build();

                extendedTaskInfoRepository.save(extInfo);
                log.info("Created ExtendedTaskInfo for multi-instance task {}: assignee={}, rowId={}",
                        taskId, assigneeId, subTableRowId);
            } catch (Exception e) {
                log.error("Failed to save ExtendedTaskInfo for task {}: {}", taskId, e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("Error handling ELEMENT_VARIABLE assignment for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    private static String normalizeLegacyAssigneeType(String assigneeType, String assigneeValue) {
        if (assigneeType == null) {
            return null;
        }
        String t = assigneeType.trim();
        if ("initiator".equalsIgnoreCase(t)) {
            return "INITIATOR";
        }
        if ("expression".equalsIgnoreCase(t) && assigneeValue != null) {
            if (isInitiatorExpression(assigneeValue.trim())) {
                return "INITIATOR";
            }
        }
        return assigneeType;
    }

    private static boolean isInitiatorExpression(String expr) {
        if (expr == null || expr.isEmpty()) {
            return false;
        }
        String e = expr.trim();
        if ("${initiator}".equals(e) || "${initiatorId}".equalsIgnoreCase(e)) {
            return true;
        }
        return e.matches("(?i)^\\$\\{\\s*initiator\\s*}$") || e.matches("(?i)^\\$\\{\\s*initiatorId\\s*}$");
    }

    private String getExtensionProperty(UserTask userTask, String propertyName) {
        if (userTask.getExtensionElements() == null || userTask.getExtensionElements().isEmpty()) {
            return null;
        }
        for (List<ExtensionElement> group : userTask.getExtensionElements().values()) {
            if (group == null) {
                continue;
            }
            for (ExtensionElement container : group) {
                if (container == null || container.getName() == null) {
                    continue;
                }
                if (!"properties".equalsIgnoreCase(container.getName())) {
                    continue;
                }
                String v = findPropertyInPropertiesContainer(container, propertyName);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    private String findPropertyInPropertiesContainer(ExtensionElement propertiesElement, String propertyName) {
        if (propertiesElement.getChildElements() == null) {
            return null;
        }
        for (List<ExtensionElement> propertyElements : propertiesElement.getChildElements().values()) {
            if (propertyElements == null) {
                continue;
            }
            for (ExtensionElement propertyElement : propertyElements) {
                if (propertyElement.getName() == null || !"property".equalsIgnoreCase(propertyElement.getName())) {
                    continue;
                }
                String name = propertyElement.getAttributeValue(null, "name");
                if (propertyName.equals(name)) {
                    return propertyElement.getAttributeValue(null, "value");
                }
            }
        }
        return null;
    }

    private String getStringVariable(Map<String, Object> variables, String key) {
        if (variables == null) {
            return null;
        }
        Object value = variables.get(key);
        return value != null ? value.toString() : null;
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }

    private void notifyNewTask(String userId, String taskId, String taskName, String processInstanceId) {
        if (userId == null || userId.isBlank() || notificationDispatchHelper == null) {
            return;
        }
        String label = taskName != null && !taskName.isBlank() ? taskName : taskId;
        notificationDispatchHelper.publishToUserAfterCommit(
                userId.trim(),
                "TASK",
                "新任务待办",
                String.format("您有新的待办任务「%s」。流程实例：%s", label, processInstanceId != null ? processInstanceId : "-"),
                "/tasks/" + taskId,
                "workflow-engine");
    }

    private void notifyCandidateTask(String userId, String taskId, String taskName, String processInstanceId) {
        if (userId == null || userId.isBlank() || notificationDispatchHelper == null) {
            return;
        }
        String label = taskName != null && !taskName.isBlank() ? taskName : taskId;
        notificationDispatchHelper.publishToUserAfterCommit(
                userId.trim(),
                "TASK",
                "新的候选任务",
                String.format("您被加入任务「%s」的候选人列表，可前往待办认领。流程实例：%s",
                        label, processInstanceId != null ? processInstanceId : "-"),
                "/tasks/" + taskId,
                "workflow-engine");
    }
}
