package com.workflow.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.client.AdminCenterClient;
import com.workflow.dto.response.TaskListResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assembles TaskListResult.TaskInfo objects from Flowable tasks, historic tasks,
 * and ExtendedTaskInfo entities. Also handles user display-name resolution.
 * Extracted from TaskQueryService.
 */
@Slf4j
@Component
public class TaskInfoAssembler {

    private static final ObjectMapper USER_REF_OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Autowired
    private TaskService taskService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private AdminCenterClient adminCenterClient;

    @Autowired
    private BpmnActionParser bpmnActionParser;

    @Autowired
    private com.workflow.repository.ExtendedTaskInfoRepository extendedTaskInfoRepository;

    // ==================== Current Step (MI-aware) Resolution ====================

    /**
     * 「当前步骤」名：普通节点直接用任务名；若该任务处于**多实例子流程**内部，返回外层
     * 多实例 subProcess 的 name（如 BPMN 里 &lt;subProcess name="multi"&gt;），使列表/详情能展示
     * 「进到多实例这一大步」而非具体某个内层子任务名。
     *
     * @param processDefinitionId 流程定义 id（取 BpmnModel 用）
     * @param taskDefinitionKey   任务 element id（BPMN 里定位起点）
     * @param taskName            任务自身名（回退值）
     * @return MI 内 → 外层 MI subProcess name（回退到 taskName 当边界无 name）；否则 → taskName
     */
    public String resolveCurrentStepName(String processDefinitionId, String taskDefinitionKey, String taskName) {
        String miName = resolveMiSubProcessName(processDefinitionId, taskDefinitionKey);
        return StringUtils.hasText(miName) ? miName : taskName;
    }

    // 同一流程定义 + element 的 MI 归属是静态的，缓存避免列表里每行重复解析 BpmnModel。
    // value 用 Optional 语义：present=MI 名（可能空串表示 MI 但边界无 name），absent 用哨兵 "" 表示「非 MI」。
    private final Map<String, String> miSubProcessNameCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String NOT_IN_MI = "__NOT_IN_MI__";

    /**
     * 沿 BPMN 静态父链，从 {@code taskDefinitionKey} 对应的 element 上溯，找到最近的一层带
     * {@code multiInstanceLoopCharacteristics} 的 subProcess，返回其 name。非 MI 内部时返回 null。
     */
    private String resolveMiSubProcessName(String processDefinitionId, String taskDefinitionKey) {
        if (!StringUtils.hasText(processDefinitionId) || !StringUtils.hasText(taskDefinitionKey)) {
            return null;
        }
        String cacheKey = processDefinitionId + "" + taskDefinitionKey;
        String cached = miSubProcessNameCache.get(cacheKey);
        if (cached != null) {
            return NOT_IN_MI.equals(cached) ? null : cached;
        }
        String resolved = computeMiSubProcessName(processDefinitionId, taskDefinitionKey);
        miSubProcessNameCache.put(cacheKey, resolved == null ? NOT_IN_MI : resolved);
        return resolved;
    }

    private String computeMiSubProcessName(String processDefinitionId, String taskDefinitionKey) {
        try {
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
            if (bpmnModel == null) {
                return null;
            }
            FlowElement element = bpmnModel.getFlowElement(taskDefinitionKey);
            if (element == null) {
                return null;
            }
            // 沿父 subProcess 链上溯，找最近一层多实例 subProcess。
            SubProcess parent = element.getSubProcess();
            while (parent != null) {
                if (parent.getLoopCharacteristics() != null) {
                    // 边界无 name 时回退用 subProcess id，避免展示空白。
                    return StringUtils.hasText(parent.getName()) ? parent.getName() : parent.getId();
                }
                parent = parent.getSubProcess();
            }
        } catch (Exception e) {
            log.debug("resolve MI subProcess name failed for pd={} key={}: {}",
                    processDefinitionId, taskDefinitionKey, e.getMessage());
        }
        return null;
    }

    // ==================== User Display Name Resolution ====================

    public Map<String, String> resolveUserDisplayNames(Collection<String> userIds) {
        Map<String, String> out = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        for (String userId : userIds) {
            if (userId == null || userId.isBlank()) {
                continue;
            }
            String key = userId.trim();
            out.computeIfAbsent(key, this::resolveUserDisplayName);
        }
        return out;
    }

    public String resolveUserDisplayName(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> userInfo = adminCenterClient.getUserInfo(userId);
            String resolved = pickDisplayNameFromUserInfo(userInfo, userId);
            return resolved != null ? resolved : userId;
        } catch (Exception e) {
            // FALLBACK(external): 展示列降级为显示原始 userId（含 AdminCenterUnavailableException）。
            log.warn("Failed to resolve user display name for {}: {}", userId, e.getMessage());
        }
        return userId;
    }

    static String pickDisplayNameFromUserInfo(Map<String, Object> userInfo, String fallback) {
        if (userInfo == null) {
            return fallback;
        }
        String fullName = (String) userInfo.get("fullName");
        if (fullName != null && !fullName.isEmpty()) {
            return fullName;
        }
        String displayName = (String) userInfo.get("displayName");
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        String username = (String) userInfo.get("username");
        if (username != null && !username.isEmpty()) {
            return username;
        }
        return fallback;
    }

    // ==================== Task Info Builders ====================

    /**
     * 在逐行组装之前，把整页会用到的用户信息一次性灌进 {@link AdminCenterClient} 的缓存。
     *
     * <p>不这么做的话，每行任务都会为 initiator + assignee 各打一次 HTTP，一页 20 行就是
     * 几十次串行远程调用——这是 To Do 接口压测崩掉的主因。这里只做"预热"而不改变
     * {@link #buildTaskInfoFromFlowableTask} 的语义：后者照常调 resolveUserDisplayName，
     * 只是全部命中缓存。
     *
     * @return 本批 {@code processInstanceId → startUserId}（仅收录查到且发起人非空的）。把它回传给
     *         {@link #convertFlowableTaskToTaskInfo(Task, Map)}，那一行就不必再自己查一次流程实例——
     *         批量查询这里已经做了，此前结果只用于预热用户名缓存就丢掉了。查不到的键不收录，
     *         调用方自然回落到原有逐行查询，故批量失败时行为与改动前完全一致。
     */
    public Map<String, String> prewarmUserDisplayNames(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Map.of();
        }
        Set<String> userIds = new LinkedHashSet<>();
        Set<String> processInstanceIds = new LinkedHashSet<>();
        for (Task t : tasks) {
            if (StringUtils.hasText(t.getAssignee())) {
                userIds.add(t.getAssignee().trim());
            }
            if (StringUtils.hasText(t.getProcessInstanceId())) {
                processInstanceIds.add(t.getProcessInstanceId());
            }
        }

        Map<String, String> startUserByProcessInstanceId = new HashMap<>();
        if (!processInstanceIds.isEmpty()) {
            try {
                runtimeService.createProcessInstanceQuery()
                    .processInstanceIds(processInstanceIds)
                    .list()
                    .forEach(pi -> {
                        if (StringUtils.hasText(pi.getStartUserId())) {
                            startUserByProcessInstanceId.put(pi.getId(), pi.getStartUserId());
                            userIds.add(pi.getStartUserId().trim());
                        }
                    });
            } catch (Exception e) {
                log.debug("Prewarm: batch process-instance query failed, falling back to per-row: {}",
                        e.getMessage());
            }
        }

        if (!userIds.isEmpty()) {
            try {
                adminCenterClient.getUserInfoBatch(userIds);
            } catch (Exception e) {
                log.debug("Prewarm: batch user lookup failed, falling back to per-row: {}", e.getMessage());
            }
        }
        return startUserByProcessInstanceId;
    }

    /** Lightweight converter for To Do list — skips full variable bag. */
    public TaskListResult.TaskInfo convertFlowableTaskToTaskInfo(Task task) {
        return buildTaskInfoFromFlowableTask(task, false, Map.of());
    }

    /**
     * List converter reusing the {@code processInstanceId → startUserId} map from
     * {@link #prewarmUserDisplayNames(List)} so each row skips its own process-instance query.
     */
    public TaskListResult.TaskInfo convertFlowableTaskToTaskInfo(
            Task task, Map<String, String> startUserByProcessInstanceId) {
        return buildTaskInfoFromFlowableTask(task, false, startUserByProcessInstanceId);
    }

    /** Detail path — includes full process-variable bag. */
    public TaskListResult.TaskInfo buildTaskInfoFromFlowableTask(Task task) {
        return buildTaskInfoFromFlowableTask(task, true, Map.of());
    }

    public TaskListResult.TaskInfo buildTaskInfoFromFlowableTask(Task task, boolean includeVariables) {
        return buildTaskInfoFromFlowableTask(task, includeVariables, Map.of());
    }

    public TaskListResult.TaskInfo buildTaskInfoFromFlowableTask(
            Task task, boolean includeVariables, Map<String, String> startUserByProcessInstanceId) {
        String processDefinitionId = task.getProcessDefinitionId();
        String processDefinitionKey = extractProcessDefinitionKey(processDefinitionId);
        String processDefinitionName = getProcessDefinitionName(processDefinitionId);

        List<String> candidateUserIds = new ArrayList<>();
        List<String> candidateGroupIds = new ArrayList<>();
        for (IdentityLink link : taskService.getIdentityLinksForTask(task.getId())) {
            if (!"candidate".equals(link.getType())) {
                continue;
            }
            if (link.getUserId() != null && !link.getUserId().isBlank()) {
                candidateUserIds.add(link.getUserId());
            }
            if (link.getGroupId() != null && !link.getGroupId().isBlank()) {
                candidateGroupIds.add(link.getGroupId());
            }
        }

        AssignmentType assignmentType;
        String assignmentTarget;
        if (task.getAssignee() != null && !task.getAssignee().isEmpty()) {
            assignmentType = AssignmentType.USER;
            assignmentTarget = task.getAssignee();
        } else if (!candidateUserIds.isEmpty()) {
            assignmentType = AssignmentType.CANDIDATE_USERS;
            assignmentTarget = String.join(",", candidateUserIds);
        } else if (!candidateGroupIds.isEmpty()) {
            assignmentType = AssignmentType.VIRTUAL_GROUP;
            assignmentTarget = String.join(",", candidateGroupIds);
        } else {
            assignmentType = AssignmentType.VIRTUAL_GROUP;
            assignmentTarget = null;
        }

        Map<String, Object> variables = null;
        if (includeVariables && task.getProcessInstanceId() != null) {
            try {
                variables = runtimeService.getVariables(task.getProcessInstanceId());
                log.debug("Retrieved {} variables for task {}",
                    variables != null ? variables.size() : 0, task.getId());
            } catch (Exception e) {
                log.warn("Failed to get variables for process instance {}: {}",
                    task.getProcessInstanceId(), e.getMessage());
                variables = new HashMap<>();
            }

            if (task.getExecutionId() != null && variables != null) {
                try {
                    Object currentItemObj = runtimeService.getVariable(task.getExecutionId(), "currentItem");
                    if (currentItemObj instanceof Map) {
                        variables.put("_currentItem", currentItemObj);
                        log.debug("Injected _currentItem for MI sub-task {}: {}", task.getId(), currentItemObj);
                    }
                } catch (Exception e) {
                    log.debug("No currentItem for task {}: {}", task.getId(), e.getMessage());
                }
            }
        }

        String initiatorId = null;
        String initiatorName = null;
        if (task.getProcessInstanceId() != null) {
            initiatorId = startUserByProcessInstanceId.get(task.getProcessInstanceId());
            if (initiatorId == null) {
                ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult();
                if (processInstance != null) {
                    initiatorId = processInstance.getStartUserId();
                }
            }
            if (initiatorId != null) {
                initiatorName = resolveUserDisplayName(initiatorId);
            }
        }
        if (!StringUtils.hasText(initiatorId)) {
            Object initiatorVar = null;
            if (variables != null) {
                initiatorVar = variables.get("initiator");
            } else if (task.getProcessInstanceId() != null) {
                try {
                    initiatorVar = runtimeService.getVariable(task.getProcessInstanceId(), "initiator");
                } catch (Exception e) {
                    log.debug("Failed to read initiator variable for {}: {}",
                            task.getProcessInstanceId(), e.getMessage());
                }
            }
            if (initiatorVar != null) {
                String iv = initiatorVar.toString().trim();
                if (StringUtils.hasText(iv)) {
                    initiatorId = iv;
                    initiatorName = resolveUserDisplayName(initiatorId);
                }
            }
        }

        String bpmnAssigneeType = null;
        String bpmnBusinessUnitId = null;
        java.util.List<String> bpmnRoleIds = java.util.Collections.emptyList();
        boolean allowBatch = false;
        if (processDefinitionId != null && task.getTaskDefinitionKey() != null) {
            try {
                bpmnAssigneeType = bpmnActionParser.getUserTaskExtensionPropertyValue(
                        processDefinitionId, task.getTaskDefinitionKey(), "assigneeType");
                String rawBu = bpmnActionParser.getUserTaskExtensionPropertyValue(
                        processDefinitionId, task.getTaskDefinitionKey(), "businessUnitId");
                if (StringUtils.hasText(rawBu)) {
                    bpmnBusinessUnitId = rawBu.trim();
                }
                String rawRoleIds = bpmnActionParser.getUserTaskExtensionPropertyValue(
                        processDefinitionId, task.getTaskDefinitionKey(), "roleIds");
                String rawRoleId = bpmnActionParser.getUserTaskExtensionPropertyValue(
                        processDefinitionId, task.getTaskDefinitionKey(), "roleId");
                bpmnRoleIds = com.workflow.util.AssigneeRoleIdsSupport.parseRoleIds(rawRoleIds, rawRoleId);
                // DW 节点开关「允许批量处理」(custom:property allowBatch)
                allowBatch = "true".equalsIgnoreCase(bpmnActionParser.getUserTaskExtensionPropertyValue(
                        processDefinitionId, task.getTaskDefinitionKey(), "allowBatch"));
            } catch (Exception e) {
                log.debug("Read bpmn assigneeType for task {}: {}", task.getId(), e.getMessage());
            }
        }
        if (bpmnAssigneeType != null) {
            bpmnAssigneeType = bpmnAssigneeType.trim();
        }

        String currentAssignee = task.getAssignee();
        String currentAssigneeName = null;
        if (currentAssignee != null && !currentAssignee.isEmpty()) {
            currentAssigneeName = resolveUserDisplayName(currentAssignee);
        }

        if (TaskQueryService.isBpmnProcessInitiatorType(bpmnAssigneeType)
                && StringUtils.hasText(initiatorId)
                && !StringUtils.hasText(currentAssignee)
                && initiatorId != null) {
            assignmentType = AssignmentType.USER;
            assignmentTarget = initiatorId.trim();
            currentAssignee = initiatorId.trim();
            currentAssigneeName = initiatorName != null ? initiatorName : resolveUserDisplayName(initiatorId);
            candidateUserIds.clear();
            candidateGroupIds.clear();
            log.info("Normalized task {} JSON to USER/initiator from BPMN assigneeType={} (runtime had no assignee)",
                    task.getId(), bpmnAssigneeType);
        }

        List<String> extractedActionIds = bpmnActionParser.extractActionIds(task);

        // MI 按角色分派信号（用于 portal workspace 可见性：role 分派只在切到该 role 的 workspace 可见）。
        // 从 ExtendedTaskInfo.extendedProperties 读 assigneeMode / roleCodes / businessUnitCode。
        String miAssigneeMode = null;
        String miRoleCode = null;
        String miBusinessUnitCode = null;
        Boolean delegatedFlag = null;
        String delegatedTo = null;
        String delegatedBy = null;
        String delegatedTargetType = null;
        String delegatedBuCode = null;
        String delegatedRoleCode = null;
        try {
            ExtendedTaskInfo eti = extendedTaskInfoRepository
                    .findByTaskIdAndIsDeletedFalse(task.getId()).orElse(null);
            if (eti != null) {
                delegatedFlag = eti.isDelegated();
                delegatedTo = eti.getDelegatedTo();
                delegatedBy = eti.getDelegatedBy();
                if (eti.getDelegatedTargetType() != null) {
                    delegatedTargetType = eti.getDelegatedTargetType().name();
                } else if (eti.isDelegated()) {
                    delegatedTargetType = "USER";
                }
                delegatedBuCode = eti.getDelegatedBuCode();
                delegatedRoleCode = eti.getDelegatedRoleCode();
                if (StringUtils.hasText(eti.getExtendedProperties())) {
                    Map<String, Object> ext = USER_REF_OBJECT_MAPPER.readValue(
                            eti.getExtendedProperties(), new TypeReference<Map<String, Object>>() {});
                    Object mode = ext.get("assigneeMode");
                    if (mode != null) {
                        miAssigneeMode = String.valueOf(mode).trim();
                    }
                    Object rc = ext.get("roleCodes");
                    if (rc instanceof List<?> rcList && !rcList.isEmpty()) {
                        miRoleCode = String.valueOf(rcList.get(0)).trim();
                    } else if (rc != null && !String.valueOf(rc).isBlank()) {
                        miRoleCode = String.valueOf(rc).trim();
                    }
                    Object bu = ext.get("businessUnitCode");
                    if (bu != null && !String.valueOf(bu).isBlank()) {
                        miBusinessUnitCode = String.valueOf(bu).trim();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("read ExtendedTaskInfo role signal failed for task {}: {}", task.getId(), e.getMessage());
        }

        return TaskListResult.TaskInfo.builder()
            .taskId(task.getId())
            .taskName(task.getName())
            .currentStepName(resolveCurrentStepName(processDefinitionId, task.getTaskDefinitionKey(), task.getName()))
            .taskDescription(task.getDescription())
            .processInstanceId(task.getProcessInstanceId())
            .processDefinitionId(processDefinitionId)
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionName(processDefinitionName)
            .taskDefinitionKey(task.getTaskDefinitionKey())
            .currentAssignee(currentAssignee)
            .currentAssigneeName(currentAssigneeName)
            .assignmentType(assignmentType)
            .bpmnAssigneeType(StringUtils.hasText(bpmnAssigneeType) ? bpmnAssigneeType : null)
            .bpmnBusinessUnitId(bpmnBusinessUnitId)
            .bpmnRoleIds(bpmnRoleIds)
            .miAssigneeMode(miAssigneeMode)
            .miRoleCode(miRoleCode)
            .miBusinessUnitCode(miBusinessUnitCode)
            .isDelegated(delegatedFlag)
            .delegatedTo(delegatedTo)
            .delegatedBy(delegatedBy)
            .delegatedTargetType(delegatedTargetType)
            .delegatedBuCode(delegatedBuCode)
            .delegatedRoleCode(delegatedRoleCode)
            .assignmentTarget(assignmentTarget)
            .priority(task.getPriority())
            .createdTime(task.getCreateTime() != null ?
                LocalDateTime.ofInstant(task.getCreateTime().toInstant(), java.time.ZoneId.systemDefault()) : null)
            .dueDate(task.getDueDate() != null ?
                LocalDateTime.ofInstant(task.getDueDate().toInstant(), java.time.ZoneId.systemDefault()) : null)
            .formKey(task.getFormKey())
            .status("PENDING")
            .initiatorId(initiatorId)
            .initiatorName(initiatorName)
            .variables(variables)
            .candidateUserIds(candidateUserIds.isEmpty() ? null : candidateUserIds)
            .candidateGroupIds(candidateGroupIds.isEmpty() ? null : candidateGroupIds)
            .actionIds(extractedActionIds)
            .allowBatch(allowBatch)
            .build();
    }

    public TaskListResult.TaskInfo buildTaskInfoFromHistoricTask(HistoricTaskInstance task) {
        String processDefinitionId = task.getProcessDefinitionId();
        String processDefinitionKey = extractProcessDefinitionKey(processDefinitionId);
        String processDefinitionName = getProcessDefinitionName(processDefinitionId);
        String assignee = task.getAssignee();

        return TaskListResult.TaskInfo.builder()
            .taskId(task.getId())
            .taskName(task.getName())
            .currentStepName(resolveCurrentStepName(processDefinitionId, task.getTaskDefinitionKey(), task.getName()))
            .taskDescription(task.getDescription())
            .processInstanceId(task.getProcessInstanceId())
            .processDefinitionId(processDefinitionId)
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionName(processDefinitionName)
            .taskDefinitionKey(task.getTaskDefinitionKey())
            .currentAssignee(assignee)
            .currentAssigneeName(StringUtils.hasText(assignee) ? resolveUserDisplayName(assignee) : null)
            .assignmentType(StringUtils.hasText(assignee) ? AssignmentType.USER : AssignmentType.VIRTUAL_GROUP)
            .assignmentTarget(assignee)
            .priority(task.getPriority())
            .createdTime(task.getCreateTime() != null
                ? LocalDateTime.ofInstant(task.getCreateTime().toInstant(), java.time.ZoneId.systemDefault()) : null)
            .dueDate(task.getDueDate() != null
                ? LocalDateTime.ofInstant(task.getDueDate().toInstant(), java.time.ZoneId.systemDefault()) : null)
            .formKey(task.getFormKey())
            .status("COMPLETED")
            .build();
    }

    public TaskListResult.TaskInfo convertToTaskInfo(ExtendedTaskInfo extendedTaskInfo) {
        String processDefinitionName = getProcessDefinitionName(extendedTaskInfo.getProcessDefinitionId());
        String processDefinitionKey = extractProcessDefinitionKey(extendedTaskInfo.getProcessDefinitionId());
        String assignee = extendedTaskInfo.getCurrentAssignee();

        return TaskListResult.TaskInfo.builder()
            .taskId(extendedTaskInfo.getTaskId())
            .taskName(extendedTaskInfo.getTaskName())
            .currentStepName(resolveCurrentStepName(
                    extendedTaskInfo.getProcessDefinitionId(),
                    extendedTaskInfo.getTaskDefinitionKey(),
                    extendedTaskInfo.getTaskName()))
            .taskDescription(extendedTaskInfo.getTaskDescription())
            .processInstanceId(extendedTaskInfo.getProcessInstanceId())
            .processDefinitionId(extendedTaskInfo.getProcessDefinitionId())
            .processDefinitionKey(processDefinitionKey)
            .processDefinitionName(processDefinitionName)
            .taskDefinitionKey(extendedTaskInfo.getTaskDefinitionKey())
            .assignmentType(extendedTaskInfo.getAssignmentType())
            .assignmentTarget(extendedTaskInfo.getAssignmentTarget())
            .currentAssignee(assignee)
            .currentAssigneeName(StringUtils.hasText(assignee) ? resolveUserDisplayName(assignee) : null)
            .priority(extendedTaskInfo.getPriority())
            .dueDate(extendedTaskInfo.getDueDate())
            .status(extendedTaskInfo.getStatus())
            .createdTime(extendedTaskInfo.getCreatedTime())
            .isDelegated(extendedTaskInfo.isDelegated())
            .delegatedTo(extendedTaskInfo.getDelegatedTo())
            .delegatedBy(extendedTaskInfo.getDelegatedBy())
            .delegatedTargetType(extendedTaskInfo.getDelegatedTargetType() != null
                    ? extendedTaskInfo.getDelegatedTargetType().name() : null)
            .delegatedBuCode(extendedTaskInfo.getDelegatedBuCode())
            .delegatedRoleCode(extendedTaskInfo.getDelegatedRoleCode())
            .isClaimed(extendedTaskInfo.isClaimed())
            .isOverdue(extendedTaskInfo.isOverdue())
            .formKey(extendedTaskInfo.getFormKey())
            .businessKey(extendedTaskInfo.getBusinessKey())
            .build();
    }

    // ==================== Process Definition Helpers ====================

    String getProcessDefinitionName(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        try {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
            if (processDefinition != null) {
                return processDefinition.getName();
            }
        } catch (Exception e) {
            log.warn("Failed to get process definition name for id: {}", processDefinitionId, e);
        }
        return extractProcessDefinitionKey(processDefinitionId);
    }

    String extractProcessDefinitionKey(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        int colonIndex = processDefinitionId.indexOf(':');
        if (colonIndex > 0) {
            return processDefinitionId.substring(0, colonIndex);
        }
        try {
            ProcessDefinition pd = repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
            if (pd != null) {
                log.debug("Resolved process definition key via repositoryService: {} -> {}", processDefinitionId, pd.getKey());
                return pd.getKey();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve process definition key for ID {}: {}", processDefinitionId, e.getMessage());
        }
        return processDefinitionId;
    }

    // ==================== User ID Normalization (static helpers) ====================

    static String normalizeFlowableUserIdValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String id;
        if (raw instanceof Map<?, ?> m) {
            id = null;
            for (String k : new String[]{"id", "userId", "user_id", "value"}) {
                Object v = m.get(k);
                if (v != null) {
                    String s = String.valueOf(v).trim();
                    if (!s.isEmpty()) {
                        id = s;
                        break;
                    }
                }
            }
            if (id == null) {
                id = String.valueOf(raw);
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
        if (t.length() > 255) {
            log.warn("Repair orphan MI task: skip assignee id longer than 255 chars");
            return null;
        }
        return t;
    }

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

    private static String extractUserIdFromRefMap(Map<?, ?> map) {
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
}
