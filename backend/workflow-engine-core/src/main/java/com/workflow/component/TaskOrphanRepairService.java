package com.workflow.component;

import com.workflow.client.AdminCenterClient;
import com.workflow.util.AssigneeRoleIdsSupport;
import com.workflow.util.InitiatorOrphanRepairEligibility;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orphan-task repair (BU_ROLE pool tasks, multi-instance initiator tasks)
 * and BU workspace visibility filtering.
 * Extracted from TaskQueryService.
 */
@Slf4j
@Component
public class TaskOrphanRepairService {

    private static final long ORPHAN_REPAIR_MIN_INTERVAL_MS = 30_000L;
    private volatile long lastOrphanRepairAtMs = 0L;

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

    // ==================== Rate-limited Orphan Repair ====================

    void maybeRepairOrphanTasks(int fetchLimit) {
        long now = System.currentTimeMillis();
        if (now - lastOrphanRepairAtMs < ORPHAN_REPAIR_MIN_INTERVAL_MS) {
            return;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - lastOrphanRepairAtMs < ORPHAN_REPAIR_MIN_INTERVAL_MS) {
                return;
            }
            lastOrphanRepairAtMs = System.currentTimeMillis();
        }
        repairOrphanBuRolePoolTasks(fetchLimit);
        repairOrphanMultiInstanceTasks(fetchLimit);
    }

    // ==================== BU_ROLE Orphan Repair ====================

    private void repairOrphanBuRolePoolTasks(int fetchLimit) {
        List<Task> unassigned = taskService.createTaskQuery()
                .taskUnassigned()
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);
        for (Task t : unassigned) {
            try {
                List<IdentityLink> links = taskService.getIdentityLinksForTask(t.getId());
                boolean hasCandidate = false;
                for (IdentityLink l : links) {
                    if ("candidate".equals(l.getType())
                            && ((l.getUserId() != null && !l.getUserId().isBlank())
                            || (l.getGroupId() != null && !l.getGroupId().isBlank()))) {
                        hasCandidate = true;
                        break;
                    }
                }
                if (hasCandidate) {
                    continue;
                }
                String pdId = t.getProcessDefinitionId();
                String defKey = t.getTaskDefinitionKey();
                String at = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "assigneeType");
                if (at == null || at.isBlank()) {
                    continue;
                }
                String u = at.trim().toUpperCase(Locale.ROOT);
                if (!"BU_ROLE".equals(u) && !"FIXED_BU_ROLE".equals(u)) {
                    continue;
                }
                String roleId = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "roleId");
                String roleIdsRaw = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "roleIds");
                String buId = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "businessUnitId");
                List<String> roleIds = AssigneeRoleIdsSupport.parseRoleIds(roleIdsRaw, roleId);
                if (roleIds.isEmpty() || buId == null || buId.isBlank()) {
                    log.warn("Orphan BU_ROLE task {} has missing roleIds/businessUnitId; skip repair", t.getId());
                    continue;
                }
                LinkedHashSet<String> users = new LinkedHashSet<>();
                for (String rid : roleIds) {
                    if (!adminCenterClient.isEligibleRole(buId.trim(), rid.trim())) {
                        log.warn("Orphan BU_ROLE task {} role {} not eligible for bu {}; skip repair",
                                t.getId(), rid, buId);
                        users.clear();
                        break;
                    }
                    List<String> chunk = adminCenterClient.getUsersByBusinessUnitAndRole(buId.trim(), rid.trim());
                    if (chunk != null) {
                        for (String uid : chunk) {
                            if (uid != null && !uid.isBlank()) {
                                users.add(uid.trim());
                            }
                        }
                    }
                }
                if (users.isEmpty()) {
                    log.warn("Orphan BU_ROLE task {} resolved no users for bu={} roleIds={}", t.getId(), buId, roleIds);
                    continue;
                }
                List<String> userList = new ArrayList<>(users);
                if (userList.size() == 1) {
                    taskService.setAssignee(t.getId(), userList.get(0));
                    log.info("Repaired orphan BU_ROLE task {} with direct assignee {}", t.getId(), userList.get(0));
                } else {
                    for (String uid : userList) {
                        taskService.addCandidateUser(t.getId(), uid);
                    }
                    log.info("Repaired orphan BU_ROLE task {} with {} candidate users", t.getId(), userList.size());
                }
            } catch (Exception ex) {
                log.warn("Repair orphan BU_ROLE task {} failed: {}", t.getId(), ex.getMessage());
            }
        }
    }

    // ==================== Multi-Instance Orphan Repair ====================

    @SuppressWarnings("unchecked")
    void repairOrphanMultiInstanceTasks(int fetchLimit) {
        List<Task> unassigned = taskService.createTaskQuery()
                .taskUnassigned()
                .orderByTaskCreateTime().desc()
                .listPage(0, fetchLimit);
        for (Task t : unassigned) {
            try {
                String pdId = t.getProcessDefinitionId();
                String defKey = t.getTaskDefinitionKey();
                String at = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "assigneeType");
                if (!"ELEMENT_VARIABLE".equalsIgnoreCase(at != null ? at.trim() : "")) {
                    continue;
                }
                Object currentItemObj = runtimeService.getVariable(t.getExecutionId(), "currentItem");
                if (!(currentItemObj instanceof Map)) {
                    continue;
                }
                Map<String, Object> currentItem = (Map<String, Object>) currentItemObj;
                String assigneeField = bpmnActionParser.getUserTaskExtensionPropertyValue(pdId, defKey, "assigneeField");
                Object assigneeObj = null;
                if (assigneeField != null && !assigneeField.isBlank()) {
                    assigneeObj = currentItem.get(assigneeField.trim());
                }
                if (assigneeObj == null) {
                    assigneeObj = currentItem.get("assigneeId");
                }
                if (assigneeObj == null) {
                    assigneeObj = currentItem.get("assignee_user_id");
                }
                if (assigneeObj == null) {
                    continue;
                }
                String assigneeId = TaskInfoAssembler.normalizeFlowableUserIdValue(assigneeObj);
                if (assigneeId == null || assigneeId.isBlank()) {
                    continue;
                }
                taskService.setAssignee(t.getId(), assigneeId);
                log.info("Repaired orphan MI task {} assigned to {} (defKey={})",
                        t.getId(), assigneeId, defKey);
            } catch (Exception ex) {
                log.warn("Repair orphan MI task {} failed: {}", t.getId(), ex.getMessage());
            }
        }
    }

    // ==================== Initiator Orphan Merge ====================

    void mergeOrphanInitiatorTasksRepair(String userId, int fetchLimit,
            LinkedHashMap<String, Task> taskMap) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        String uid = userId.trim();
        try {
            appendUnassignedInitiatorTasks(uid, fetchLimit, taskMap, false);
            if (uid.matches("^-?\\d+$")) {
                appendUnassignedInitiatorTasks(uid, fetchLimit, taskMap, true);
            }
        } catch (Exception e) {
            log.warn("mergeOrphanInitiatorTasksRepair for user {}: {}", uid, e.getMessage());
        }
    }

    private void appendUnassignedInitiatorTasks(String userId, int fetchLimit,
            LinkedHashMap<String, Task> taskMap, boolean initiatorVarAsLong) {
        var query = taskService.createTaskQuery()
                .taskUnassigned()
                .orderByTaskCreateTime().desc();
        if (initiatorVarAsLong) {
            query.processVariableValueEquals("initiator", Long.parseLong(userId));
        } else {
            query.processVariableValueEquals("initiator", userId);
        }
        List<Task> orphans = query.listPage(0, fetchLimit);
        for (Task t : orphans) {
            try {
                String assigneeType = bpmnActionParser.getUserTaskExtensionPropertyValue(
                        t.getProcessDefinitionId(), t.getTaskDefinitionKey(), "assigneeType");
                String flowableAssignee = null;
                if (!StringUtils.hasText(assigneeType)) {
                    flowableAssignee = readUserTaskAssigneeExpression(
                            t.getProcessDefinitionId(), t.getTaskDefinitionKey());
                }
                if (!InitiatorOrphanRepairEligibility.shouldRepair(assigneeType, flowableAssignee)) {
                    log.debug(
                            "Skip initiator orphan repair for task {} (not initiator user task per BPMN; assigneeType={}, flowableAssignee={})",
                            t.getId(), assigneeType, flowableAssignee);
                    continue;
                }
                taskService.setAssignee(t.getId(), userId);
                Task refreshed = taskService.createTaskQuery().taskId(t.getId()).singleResult();
                if (refreshed != null) {
                    taskMap.putIfAbsent(refreshed.getId(), refreshed);
                }
            } catch (Exception ex) {
                log.warn("Could not repair assignee for orphan task {}: {}", t.getId(), ex.getMessage());
                taskMap.putIfAbsent(t.getId(), t);
            }
        }
    }

    private String readUserTaskAssigneeExpression(String processDefinitionId, String taskDefinitionKey) {
        if (!StringUtils.hasText(processDefinitionId) || !StringUtils.hasText(taskDefinitionKey)) {
            return null;
        }
        try {
            BpmnModel model = repositoryService.getBpmnModel(processDefinitionId);
            if (model == null) {
                return null;
            }
            FlowElement el = model.getFlowElement(taskDefinitionKey);
            if (!(el instanceof UserTask ut)) {
                return null;
            }
            String a = ut.getAssignee();
            return StringUtils.hasText(a) ? a.trim() : null;
        } catch (Exception e) {
            log.debug("readUserTaskAssigneeExpression: {}", e.getMessage());
            return null;
        }
    }

    // ==================== BU Workspace Filter ====================

    List<Task> applyActiveWorkspaceBuTaskFilter(List<Task> tasks, String activeBusinessUnitId, String queryUserId) {
        if (!StringUtils.hasText(activeBusinessUnitId) || tasks == null || tasks.isEmpty()) {
            return tasks;
        }
        String activeBu = activeBusinessUnitId.trim();
        List<Task> out = new ArrayList<>();
        for (Task t : tasks) {
            if (fixedBuRoleVisibleForActiveWorkspace(t, activeBu, queryUserId)) {
                out.add(t);
            }
        }
        return out;
    }

    private boolean fixedBuRoleVisibleForActiveWorkspace(Task t, String activeBu, String queryUserId) {
        if (t != null && StringUtils.hasText(queryUserId)) {
            String assignee = t.getAssignee();
            if (StringUtils.hasText(assignee) && queryUserId.trim().equals(assignee.trim())) {
                return true;
            }
        }
        if (t == null || !StringUtils.hasText(activeBu)) {
            return true;
        }
        String bpmnAssigneeType = bpmnActionParser.getUserTaskExtensionPropertyValue(
                t.getProcessDefinitionId(), t.getTaskDefinitionKey(), "assigneeType");
        String bpmnBusinessUnitId = bpmnActionParser.getUserTaskExtensionPropertyValue(
                t.getProcessDefinitionId(), t.getTaskDefinitionKey(), "businessUnitId");
        bpmnBusinessUnitId = StringUtils.hasText(bpmnBusinessUnitId) ? bpmnBusinessUnitId.trim() : null;
        if (!isWorkspaceScopedBuPoolSemantics(bpmnAssigneeType, bpmnBusinessUnitId)) {
            return true;
        }
        if (!StringUtils.hasText(bpmnBusinessUnitId)) {
            return true;
        }
        return equalsNormalizedBuId(activeBu, bpmnBusinessUnitId);
    }

    private static boolean isWorkspaceScopedBuPoolSemantics(String bpmnAssigneeType, String bpmnBusinessUnitId) {
        if (bpmnAssigneeType != null) {
            String u = bpmnAssigneeType.trim().toUpperCase(Locale.ROOT);
            if ("FIXED_BU_ROLE".equals(u)) {
                return true;
            }
            if ("BU_ROLE".equals(u) && StringUtils.hasText(bpmnBusinessUnitId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equalsNormalizedBuId(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String x = a.trim();
        String y = b.trim();
        if (x.equals(y)) {
            return true;
        }
        try {
            if (x.matches("^-?\\d+$") && y.matches("^-?\\d+$")) {
                return Long.parseLong(x) == Long.parseLong(y);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return false;
    }
}
