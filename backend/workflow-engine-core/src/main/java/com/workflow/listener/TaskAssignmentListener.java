package com.workflow.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.component.BpmnActionParser;
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
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private BpmnActionParser bpmnActionParser;

    @Autowired
    @Lazy
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;

    @Autowired
    @Lazy
    private NotificationDispatchHelper notificationDispatchHelper;

    @Autowired
    @Lazy
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final ObjectMapper USER_REF_OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() == FlowableEngineEventType.TASK_CREATED) {
            handleTaskCreated(event);
        }
    }

    private void handleTaskCreated(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEvent)) {
            return;
        }

        FlowableEntityEvent entityEvent = (FlowableEntityEvent) event;
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

            if (processDefinitionId != null && taskDefinitionKey != null) {
                BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
                if (bpmnModel != null) {
                    FlowElement flowElement = bpmnModel.getFlowElement(taskDefinitionKey);
                    if (flowElement instanceof UserTask ut) {
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

            // Flowable BpmnModel 常未载入 designer custom 命名空间扩展，与 actionIds 一致从已部署 XML 补读
            if (processDefinitionId != null && taskDefinitionKey != null) {
                assigneeTypeRaw = firstNonBlank(assigneeTypeRaw,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "assigneeType"));
                roleId = firstNonBlank(roleId,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "roleId"));
                businessUnitId = firstNonBlank(businessUnitId,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "businessUnitId"));
                assigneeValue = firstNonBlank(assigneeValue,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "assigneeValue"));
                assigneeAnchorExt = firstNonBlank(assigneeAnchorExt,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "assigneeAnchor"));
                manualAssignVariable = firstNonBlank(manualAssignVariable,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "manualAssignVariable"));
                manualAssignBuVariable = firstNonBlank(manualAssignBuVariable,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "manualAssignBuVariable"));
                manualAssignRoleVariable = firstNonBlank(manualAssignRoleVariable,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "manualAssignRoleVariable"));
                assigneeVariable = firstNonBlank(assigneeVariable,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "assigneeVariable"));
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
                log.warn("TaskAssignmentListener: task {} has no assigneeType in BPMN extensions, process variables, or delegateAssigneeVariable — task will remain unassigned (no assignee, no candidates). processInstanceId={}, taskDefKey={}, processDefId={}",
                        taskId, processInstanceId, taskDefinitionKey, processDefinitionId);
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
                updateCurrentItemProgress(processVariables, processDefinitionId, taskDefinitionKey, task.getName());
                applyResolveResult(taskId, task, processInstanceId,
                        resolveManualAssign(taskDefinitionKey, manualAssignVariable, manualAssignBuVariable,
                                manualAssignRoleVariable, processVariables, initiatorId),
                        assigneeTypeRaw);
                return;
            }

            if (resolvedType == AssigneeType.ASSIGNEE_FROM_VARIABLE) {
                String varName = firstNonBlank(assigneeVariable, assigneeValue);
                TaskAssigneeResolver.ResolveResult vr = resolveAssigneeFromVariable(varName, processVariables);
                updateCurrentItemProgress(processVariables, processDefinitionId, taskDefinitionKey, task.getName());
                applyResolveResult(taskId, task, processInstanceId, vr, assigneeTypeRaw);
                return;
            }

            if (roleId == null || roleId.isEmpty()) {
                roleId = assigneeValue;
            }
            if ((businessUnitId == null || businessUnitId.isEmpty()) && resolvedType == AssigneeType.BU_ROLE) {
                businessUnitId = assigneeValue;
            }

            if (resolvedType == AssigneeType.BU_ROLE && (businessUnitId == null || businessUnitId.isEmpty())) {
                log.warn("TaskAssignmentListener: BU_ROLE task {} has no businessUnitId — task will be created without assignee/candidates. roleId={}, activeBusinessUnitId from process vars={}",
                        taskId, roleId, getStringVariable(
                                cachedVariables != null ? cachedVariables
                                        : runtimeService.getVariables(processInstanceId), "activeBusinessUnitId"));
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

            log.info("TaskAssignmentListener: resolve result for task {}: assignee={}, candidateUsers={}, error={}",
                    taskId, result != null ? result.getAssignee() : "null",
                    result != null ? result.getCandidateUsers() : "null",
                    result != null ? result.getErrorMessage() : "null");

            updateCurrentItemProgress(processVariables, processDefinitionId, taskDefinitionKey, task.getName());
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
                if (o == null) {
                    continue;
                }
                if (o instanceof Map<?, ?> m) {
                    String uid = extractUserIdFromRefMap(m);
                    if (uid != null) {
                        ids.addAll(splitUserList(uid));
                    } else {
                        log.warn("ASSIGNEE_FROM_VARIABLE: collection element map has no id/userId/user_id/value; skip (avoid Map#toString)");
                    }
                } else {
                    ids.addAll(splitUserList(o.toString()));
                }
            }
        } else if (raw instanceof Map<?, ?> map) {
            String uid = extractUserIdFromRefMap(map);
            if (uid != null) {
                ids.addAll(splitUserList(uid));
            } else {
                log.warn("ASSIGNEE_FROM_VARIABLE: map has no id/userId/user_id/value; skip (avoid Map#toString length overflow in Flowable identity link)");
            }
        } else {
            ids.addAll(splitUserList(raw.toString()));
        }
        ids = sanitizeFlowableUserIds(ids);
        return taskAssigneeResolver.resolveFromUserIdList(AssigneeType.ASSIGNEE_FROM_VARIABLE, ids);
    }

    /** Flowable ACT_RU/HI identity link USER_ID_/GROUP_ID_ columns are varchar(255). */
    private static final int FLOWABLE_IDENTITY_USER_ID_MAX = 255;

    private static String extractUserIdFromRefMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        for (String k : new String[]{"id", "userId", "user_id", "value"}) {
            Object v = map.get(k);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    private static String normalizeFlowableUserIdValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String id;
        if (raw instanceof Map<?, ?> m) {
            id = extractUserIdFromRefMap(m);
        } else if (raw instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isFinite(d) && Math.floor(d) == d) {
                id = String.valueOf(n.longValue());
            } else {
                id = n.toString();
            }
        } else {
            id = extractUserIdFromString(String.valueOf(raw));
        }
        if (id == null) {
            return null;
        }
        String t = id.trim();
        if (t.isEmpty() || "null".equalsIgnoreCase(t)) {
            return null;
        }
        if (t.length() > FLOWABLE_IDENTITY_USER_ID_MAX) {
            log.warn("ELEMENT_VARIABLE: skip assignee id longer than {} chars (Flowable identity link limit)",
                    FLOWABLE_IDENTITY_USER_ID_MAX);
            return null;
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    private static String extractUserIdFromString(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return value;
        }
        String mapLikeId = extractUserIdFromMapLikeString(value);
        if (mapLikeId != null) {
            return mapLikeId;
        }
        if (value.startsWith("\"")) {
            try {
                Object parsed = USER_REF_OBJECT_MAPPER.readValue(value, Object.class);
                if (parsed instanceof String parsedString) {
                    return extractUserIdFromString(parsedString);
                }
            } catch (Exception ignored) {
                // Not a JSON string literal; try the generic UUID fallback below.
            }
        }
        if (value.startsWith("{") || value.startsWith("[")) {
            try {
                Object parsed = USER_REF_OBJECT_MAPPER.readValue(value, Object.class);
                if (parsed instanceof Map<?, ?> map) {
                    String id = extractUserIdFromRefMap(map);
                    return id != null ? id : value;
                }
                if (parsed instanceof List<?> list && !list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof Map<?, ?> map) {
                        String id = extractUserIdFromRefMap(map);
                        return id != null ? id : value;
                    }
                    return first != null ? String.valueOf(first).trim() : value;
                }
            } catch (Exception ignored) {
                // Not JSON, keep the original string.
            }
        }
        Matcher matcher = UUID_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group();
        }
        return value;
    }

    private static String extractUserIdFromMapLikeString(String value) {
        if (value == null || !value.startsWith("{") || !value.endsWith("}") || !value.contains("=")) {
            return null;
        }
        for (String key : new String[]{"id", "userId", "user_id", "value"}) {
            Matcher matcher = Pattern.compile("(?i)(^|[,\\{]\\s*)" + Pattern.quote(key) + "\\s*=\\s*([^,}]+)")
                    .matcher(value);
            if (matcher.find()) {
                String id = matcher.group(2).trim();
                return id.isEmpty() || "null".equalsIgnoreCase(id) ? null : id;
            }
        }
        return null;
    }

    private static List<String> sanitizeFlowableUserIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return ids;
        }
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String t = id.trim();
            if (t.length() > FLOWABLE_IDENTITY_USER_ID_MAX) {
                log.warn("ASSIGNEE_FROM_VARIABLE: skip user id longer than {} chars (Flowable identity link limit)",
                        FLOWABLE_IDENTITY_USER_ID_MAX);
                continue;
            }
            out.add(t);
        }
        return out;
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
                // Flowable's in-memory BpmnModel can miss designer custom properties. Keep this
                // aligned with TaskManagerComponent orphan repair, which reads the deployed XML.
                subTableId = firstNonBlank(subTableId,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "subTableId"));
                subTableName = firstNonBlank(subTableName,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "subTableName"));
                assigneeFieldFromBpmn = firstNonBlank(assigneeFieldFromBpmn,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "assigneeField"));
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

            String assigneeId = normalizeFlowableUserIdValue(assigneeIdObj);
            if (assigneeId == null || assigneeId.isBlank()) {
                log.warn("ELEMENT_VARIABLE: cannot normalize assignee id from currentItem for task {} (assigneeField={}, rawType={})",
                        taskId, assigneeFieldFromBpmn,
                        assigneeIdObj != null ? assigneeIdObj.getClass().getSimpleName() : "null");
                return;
            }

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

            String[] progressCols = resolveMiProgressColumnNames(processDefinitionId, taskDefinitionKey);
            extendedProps.put("miTaskStatusField", progressCols[0]);
            extendedProps.put("miTaskCurrentNodeField", progressCols[1]);

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
                updateSubTableTaskProgress(subTableName, subTableRowId, task.getName(), progressCols[0], progressCols[1]);
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
                // Flowable 解析 designer 导出的 custom:properties 时，getName() 可能是 "properties"
                // 或带前缀如 "custom:properties"，仅 equals "properties" 会漏读 assigneeType
                if (!isExtensionPropertiesContainer(container.getName())) {
                    continue;
                }
                String v = findPropertyInPropertiesContainer(container, propertyName);
                if (v != null) {
                    return v;
                }
            }
        }
        // 兜底：任意层级下的 property（兼容非标准嵌套）
        for (List<ExtensionElement> group : userTask.getExtensionElements().values()) {
            if (group == null) {
                continue;
            }
            for (ExtensionElement root : group) {
                String v = findExtensionPropertyRecursive(root, propertyName);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    private static boolean isExtensionPropertiesContainer(String elementName) {
        if (elementName == null || elementName.isBlank()) {
            return false;
        }
        String n = elementName.trim();
        if ("properties".equalsIgnoreCase(n)) {
            return true;
        }
        int colon = n.lastIndexOf(':');
        if (colon >= 0 && colon < n.length() - 1) {
            return "properties".equalsIgnoreCase(n.substring(colon + 1));
        }
        return false;
    }

    private static boolean isExtensionPropertyElementName(String elementName) {
        if (elementName == null || elementName.isBlank()) {
            return false;
        }
        String n = elementName.trim();
        if ("property".equalsIgnoreCase(n)) {
            return true;
        }
        int colon = n.lastIndexOf(':');
        if (colon >= 0 && colon < n.length() - 1) {
            return "property".equalsIgnoreCase(n.substring(colon + 1));
        }
        return false;
    }

    private static String findExtensionPropertyRecursive(ExtensionElement el, String propertyName) {
        if (el == null) {
            return null;
        }
        if (el.getName() != null && isExtensionPropertyElementName(el.getName())) {
            String name = el.getAttributeValue(null, "name");
            if (propertyName.equals(name)) {
                return el.getAttributeValue(null, "value");
            }
        }
        if (el.getChildElements() == null) {
            return null;
        }
        for (List<ExtensionElement> children : el.getChildElements().values()) {
            if (children == null) {
                continue;
            }
            for (ExtensionElement child : children) {
                String v = findExtensionPropertyRecursive(child, propertyName);
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
                if (propertyElement.getName() == null
                        || !isExtensionPropertyElementName(propertyElement.getName())) {
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

    /**
     * Column names come from SubProcess BPMN extensions {@code miTaskStatusField} / {@code miTaskCurrentNodeField}
     * (designer) with defaults {@code task_status} / {@code task_current_node}.
     */
    private void updateSubTableTaskProgress(String subTableName, Long subTableRowId, String taskName,
                                            String statusColumn, String currentNodeColumn) {
        if (subTableName == null || subTableRowId == null) {
            return;
        }
        try {
            String tableName = requireSafeIdentifier(subTableName);
            String statusCol = requireSafeIdentifier(statusColumn);
            String nodeCol = requireSafeIdentifier(currentNodeColumn);
            boolean hasTaskStatus = columnExists(tableName, statusCol);
            boolean hasTaskCurrentNode = columnExists(tableName, nodeCol);
            if (!hasTaskStatus && !hasTaskCurrentNode) {
                return;
            }

            StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
            List<Object> params = new ArrayList<>();
            if (hasTaskStatus) {
                sql.append(statusCol).append(" = ?, ");
                params.add("IN_PROGRESS");
            }
            if (hasTaskCurrentNode) {
                sql.append(nodeCol).append(" = ?, ");
                params.add(taskName);
            }
            sql.setLength(sql.length() - 2);
            sql.append(" WHERE id = ?");
            params.add(subTableRowId);
            jdbcTemplate.update(sql.toString(), params.toArray());
        } catch (Exception e) {
            log.debug("Skipped updating sub-table task progress for {}#{}: {}",
                    subTableName, subTableRowId, e.getMessage());
        }
    }

    private static final Pattern SAFE_SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private String[] resolveMiProgressColumnNames(String processDefinitionId, String taskDefinitionKey) {
        String statusDefault = "task_status";
        String nodeDefault = "task_current_node";
        if (processDefinitionId == null || processDefinitionId.isBlank()
                || taskDefinitionKey == null || taskDefinitionKey.isBlank()) {
            return new String[] { statusDefault, nodeDefault };
        }
        String st = bpmnActionParser.getMultiInstanceSubProcessExtensionPropertyValue(
                processDefinitionId, taskDefinitionKey, "miTaskStatusField");
        String nd = bpmnActionParser.getMultiInstanceSubProcessExtensionPropertyValue(
                processDefinitionId, taskDefinitionKey, "miTaskCurrentNodeField");
        return new String[] { safeSqlColumnName(st, statusDefault), safeSqlColumnName(nd, nodeDefault) };
    }

    private static String safeSqlColumnName(String candidate, String defaultName) {
        if (candidate == null || candidate.isBlank()) {
            return defaultName;
        }
        String t = candidate.trim();
        return SAFE_SQL_IDENTIFIER.matcher(t).matches() ? t : defaultName;
    }

    private void updateCurrentItemProgress(Map<String, Object> processVariables, String processDefinitionId,
                                           String taskDefinitionKey, String taskName) {
        if (processVariables == null || processDefinitionId == null || taskDefinitionKey == null) {
            return;
        }
        Object currentItemObj = processVariables.get("currentItem");
        if (!(currentItemObj instanceof Map<?, ?> currentItem)) {
            return;
        }
        Long rowId = extractLong(currentItem.get("rowId"));
        if (rowId == null) {
            rowId = extractLong(currentItem.get("id"));
        }
        if (rowId == null) {
            return;
        }

        String subTableName = firstNonBlank(
                bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey, "subTableName"),
                bpmnActionParser.getMultiInstanceSubProcessSubTableName(processDefinitionId, taskDefinitionKey)
        );
        String[] cols = resolveMiProgressColumnNames(processDefinitionId, taskDefinitionKey);
        updateSubTableTaskProgress(subTableName, rowId, taskName, cols[0], cols[1]);
    }

    private Long extractLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String requireSafeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid sub-table name");
        }
        return identifier;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName);
        return count != null && count > 0;
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
                "New Task",
                String.format("You have a new task \"%s\". Process: %s", label, processInstanceId != null ? processInstanceId : "-"),
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
                "New Candidate Task",
                String.format("You have been added as a candidate for task \"%s\". You can claim it from your pending tasks. Process: %s",
                        label, processInstanceId != null ? processInstanceId : "-"),
                "/tasks/" + taskId,
                "workflow-engine");
    }
}
