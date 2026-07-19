package com.workflow.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.workflow.component.BpmnActionParser;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.service.TaskAssigneeResolver;
import com.workflow.util.AssigneeRoleIdsSupport;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.workflow.listener.AssigneeUserIdNormalizer.normalizeFlowableUserIdValue;
import static com.workflow.listener.UserTaskExtensionPropertyReader.getExtensionProperty;

/**
 * Writes multi-instance (MI) sub-task metadata: {@code wf_extended_task_info} rows and sub-table task-progress
 * columns, plus the ELEMENT_VARIABLE assignment path. Extracted verbatim from {@link TaskAssignmentListener}.
 * <p>
 * All Flowable services and repositories are read from the owning {@link TaskAssignmentListener} so that
 * unit tests injecting mocks into the listener's fields (via reflection, no Spring context) continue to work
 * unchanged. This collaborator therefore holds no injected state of its own.
 */
@Slf4j
@Component
class MultiInstanceTaskWriter {

    private static final Pattern SAFE_SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * Flowable often creates MI inner userTasks with assignee already set (BPMN expression).
     * The early return in {@code TaskAssignmentListener.handleTaskCreated} previously skipped ExtendedTaskInfo
     * creation, so multi-instance status / initiator sub-table progress only saw completed predecessor tasks.
     */
    void ensureMultiInstanceExtendedTaskForPreassignedTask(TaskAssignmentListener owner, TaskEntity task,
            String taskId, String processInstanceId, String processDefinitionId, String taskDefinitionKey,
            String assigneeOverride) {
        RuntimeService runtimeService = owner.runtimeService();
        RepositoryService repositoryService = owner.repositoryService();
        BpmnActionParser bpmnActionParser = owner.bpmnActionParser();
        JdbcTemplate jdbcTemplate = owner.jdbcTemplate();
        ExtendedTaskInfoRepository extendedTaskInfoRepository = owner.extendedTaskInfoRepository();
        ObjectMapper objectMapper = owner.objectMapper();

        if (taskDefinitionKey == null || processDefinitionId == null) {
            return;
        }
        String miScopeTable = bpmnActionParser.getMultiInstanceSubProcessSubTableName(
                processDefinitionId, taskDefinitionKey);
        if (miScopeTable == null || miScopeTable.isBlank()) {
            return;
        }
        String executionId = task.getExecutionId();
        if (executionId == null) {
            return;
        }
        Object currentItemObj = runtimeService.getVariable(executionId, "currentItem");
        if (currentItemObj == null) {
            currentItemObj = runtimeService.getVariable(executionId, "_currentItem");
        }
        if (!(currentItemObj instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> currentItem = (Map<String, Object>) currentItemObj;

        String subTableId = null;
        String subTableName = null;
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        if (bpmnModel != null) {
            FlowElement flowElement = bpmnModel.getFlowElement(taskDefinitionKey);
            if (flowElement instanceof UserTask userTask) {
                subTableId = getExtensionProperty(userTask, "subTableId");
                subTableName = getExtensionProperty(userTask, "subTableName");
            }
        }
        subTableId = firstNonBlank(subTableId,
                bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey, "subTableId"));
        subTableName = firstNonBlank(subTableName,
                bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                        "subTableName"));
        subTableName = firstNonBlank(subTableName,
                bpmnActionParser.getMultiInstanceSubProcessSubTableName(processDefinitionId, taskDefinitionKey));
        if (subTableName == null || subTableName.isBlank()) {
            subTableName = miScopeTable;
        }

        List<String> pkColsPre;
        Map<String, Object> rowKeyPre;
        try {
            pkColsPre = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate,
                    requireSafeIdentifier(subTableName));
            rowKeyPre = SubTableRowKeySupport.rowKeyFromCurrentItem(currentItem, pkColsPre);
        } catch (Exception e) {
            log.debug("ensureMI preassigned: skip task {}, could not resolve row key: {}", taskId, e.getMessage());
            return;
        }
        if (rowKeyPre == null) {
            return;
        }
        final List<String> pkCols = pkColsPre;
        final Map<String, Object> rowKey = rowKeyPre;
        Long subTableRowVersion = extractLong(currentItem.get("rowVersion"));

        String assigneeId = assigneeOverride != null && !assigneeOverride.isBlank()
                ? normalizeFlowableUserIdValue(assigneeOverride.trim())
                : normalizeFlowableUserIdValue(task.getAssignee());
        if (assigneeId == null || assigneeId.isBlank()) {
            return;
        }

        String[] progressCols = resolveMiProgressColumnNames(owner, processDefinitionId, taskDefinitionKey);
        Map<String, Object> extendedProps = new HashMap<>();
        extendedProps.put("multiInstance", true);
        extendedProps.put("subTableRowKey", rowKey);
        if (pkCols.size() == 1 && rowKey.get(pkCols.get(0)) instanceof Number) {
            extendedProps.put("subTableRowId", ((Number) rowKey.get(pkCols.get(0))).longValue());
        }
        if (subTableRowVersion != null) {
            extendedProps.put("subTableRowVersion", subTableRowVersion);
        }
        if (subTableId != null) {
            extendedProps.put("subTableId", subTableId);
        }
        extendedProps.put("subTableName", subTableName);
        extendedProps.put("miTaskStatusField", progressCols[0]);
        extendedProps.put("miTaskCurrentNodeField", progressCols[1]);

        Optional<ExtendedTaskInfo> existingOpt = extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(taskId);
        Map<String, Object> merged = new HashMap<>();
        if (existingOpt.isPresent() && existingOpt.get().getExtendedProperties() != null
                && !existingOpt.get().getExtendedProperties().isBlank()) {
            try {
                Map<String, Object> cur = objectMapper.readValue(
                        existingOpt.get().getExtendedProperties(),
                        new TypeReference<Map<String, Object>>() {});
                if (cur != null) {
                    merged.putAll(cur);
                }
            } catch (Exception e) {
                log.debug("ensureMI preassigned: reset extended JSON for task {}: {}", taskId, e.getMessage());
            }
        }
        merged.putAll(extendedProps);

        String extendedPropertiesJson;
        try {
            extendedPropertiesJson = objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            log.error("Failed to serialize extendedProperties for preassigned MI task {}: {}", taskId, e.getMessage());
            return;
        }

        if (existingOpt.isPresent()) {
            ExtendedTaskInfo ext = existingOpt.get();
            ext.setExtendedProperties(extendedPropertiesJson);
            ext.setTaskName(task.getName());
            ext.setTaskDefinitionKey(taskDefinitionKey);
            ext.setProcessDefinitionId(processDefinitionId);
            if (!"COMPLETED".equalsIgnoreCase(ext.getStatus()) && !"CANCELLED".equalsIgnoreCase(ext.getStatus())) {
                ext.setStatus("ASSIGNED");
            }
            ext.setAssignmentTarget(assigneeId);
            extendedTaskInfoRepository.save(ext);
        } else {
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
        }
        updateSubTableTaskProgress(owner, subTableName, rowKey, task.getName(), progressCols[0], progressCols[1]);
        log.info("Ensured ExtendedTaskInfo for preassigned MI task {}: rowKey={}, subTable={}",
                taskId, rowKey, subTableName);
    }

    void handleElementVariableAssignment(TaskAssignmentListener owner, TaskEntity task, String taskId,
            String processInstanceId, String processDefinitionId, String taskDefinitionKey) {
        RuntimeService runtimeService = owner.runtimeService();
        RepositoryService repositoryService = owner.repositoryService();
        BpmnActionParser bpmnActionParser = owner.bpmnActionParser();
        JdbcTemplate jdbcTemplate = owner.jdbcTemplate();
        ExtendedTaskInfoRepository extendedTaskInfoRepository = owner.extendedTaskInfoRepository();
        TaskService taskService = owner.taskService();
        ObjectMapper objectMapper = owner.objectMapper();

        try {
            log.info("Handling ELEMENT_VARIABLE assignment for task {}", taskId);

            String executionId = task.getExecutionId();
            Object currentItemObj = runtimeService.getVariable(executionId, "currentItem");
            if (currentItemObj == null) {
                currentItemObj = runtimeService.getVariable(executionId, "_currentItem");
            }

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

            Object rowVersionObj = currentItem.get("rowVersion");
            Long subTableRowVersion = null;
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
            String assigneeModeFromBpmn = null;
            String roleFieldFromBpmn = null;
            String buFieldFromBpmn = null;

            if (processDefinitionId != null && taskDefinitionKey != null) {
                BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
                if (bpmnModel != null) {
                    FlowElement flowElement = bpmnModel.getFlowElement(taskDefinitionKey);
                    if (flowElement instanceof UserTask userTask) {
                        subTableId = getExtensionProperty(userTask, "subTableId");
                        subTableName = getExtensionProperty(userTask, "subTableName");
                        assigneeFieldFromBpmn = getExtensionProperty(userTask, "assigneeField");
                        assigneeModeFromBpmn = getExtensionProperty(userTask, "assigneeMode");
                        roleFieldFromBpmn = getExtensionProperty(userTask, "roleField");
                        buFieldFromBpmn = getExtensionProperty(userTask, "buField");
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
                // Later userTasks in the same MI subprocess often omit subTableName — inherit from sibling nodes.
                subTableName = firstNonBlank(subTableName,
                        bpmnActionParser.getMultiInstanceSubProcessSubTableName(processDefinitionId, taskDefinitionKey));
                assigneeFieldFromBpmn = firstNonBlank(assigneeFieldFromBpmn,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "assigneeField"));
                assigneeModeFromBpmn = firstNonBlank(assigneeModeFromBpmn,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "assigneeMode"));
                roleFieldFromBpmn = firstNonBlank(roleFieldFromBpmn,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "roleField"));
                buFieldFromBpmn = firstNonBlank(buFieldFromBpmn,
                        bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey,
                                "buField"));
            }

            // 分派方式由「行内实际填了什么」逐行决定（支持场景 C：同一 MI 节点两种方式混用，逐行二选一）。
            // assigneeMode 仅作节点级「允许了哪些方式」的参考（user|role|both），不再用于硬分流。
            boolean roleAllowed = assigneeModeFromBpmn == null
                    || "role".equalsIgnoreCase(assigneeModeFromBpmn.trim())
                    || "both".equalsIgnoreCase(assigneeModeFromBpmn.trim());

            if (subTableName == null || subTableName.isBlank()) {
                log.warn("subTableName missing for MI ELEMENT_VARIABLE task {}, task will remain CREATED", taskId);
                return;
            }
            List<String> pkColsEv;
            Map<String, Object> rowKeyEv;
            try {
                pkColsEv = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate,
                        requireSafeIdentifier(subTableName));
                rowKeyEv = SubTableRowKeySupport.rowKeyFromCurrentItem(currentItem, pkColsEv);
            } catch (Exception e) {
                log.warn("Could not resolve row key for task {}: {}", taskId, e.getMessage());
                return;
            }
            if (rowKeyEv == null) {
                log.warn("currentItem missing rowKey / PK values for task {}, task will remain CREATED", taskId);
                return;
            }
            final List<String> pkCols = pkColsEv;
            final Map<String, Object> rowKey = rowKeyEv;

            // 逐行判定：该行填了 role code（且节点允许 role）→ 走 BU_ROLE 池分派（共享认领），
            // 否则落到下方 user 分支（读 assignee）。场景 C 下同一节点两种行混用，互斥由前端表单保证。
            boolean rowHasRole = roleAllowed
                    && roleFieldFromBpmn != null && !roleFieldFromBpmn.isBlank()
                    && !resolveRoleCodesFromItem(currentItem, roleFieldFromBpmn.trim()).isEmpty();
            if (rowHasRole) {
                handleRoleModeAssignment(owner, task, taskId, processInstanceId, processDefinitionId,
                        taskDefinitionKey, currentItem, subTableId, subTableName, rowKey, pkCols,
                        subTableRowVersion, roleFieldFromBpmn, buFieldFromBpmn);
                return;
            }

            // Align with portal buildParticipantsCollection and sub-table column names:
            // prefer BPMN assigneeField, then assigneeId, then assignee_user_id
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
                owner.notifyNewTask(assigneeId, taskId, task.getName(), processInstanceId);
            } catch (Exception e) {
                log.warn("Failed to set assignee {} for task {}: {}, task will remain CREATED",
                        assigneeId, taskId, e.getMessage());
                return;
            }

            Map<String, Object> extendedProps = new HashMap<>();
            extendedProps.put("multiInstance", true);
            extendedProps.put("subTableRowKey", rowKey);
            if (pkCols.size() == 1 && rowKey.get(pkCols.get(0)) instanceof Number) {
                extendedProps.put("subTableRowId", ((Number) rowKey.get(pkCols.get(0))).longValue());
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

            String[] progressCols = resolveMiProgressColumnNames(owner, processDefinitionId, taskDefinitionKey);
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
                updateSubTableTaskProgress(owner, subTableName, rowKey, task.getName(), progressCols[0], progressCols[1]);
                log.info("Created ExtendedTaskInfo for multi-instance task {}: assignee={}, rowKey={}",
                        taskId, assigneeId, rowKey);
            } catch (Exception e) {
                log.error("Failed to save ExtendedTaskInfo for task {}: {}", taskId, e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("Error handling ELEMENT_VARIABLE assignment for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    /**
     * role 模式：从 {@code currentItem} 读逐行 role code（{@code roleField}）与可选 BU code（{@code buField}），
     * 走 {@link TaskAssigneeResolver} 的 BU_ROLE 解析（共享认领池）。行为与 {@code TaskAssignmentListener} 的
     * BU_ROLE 路径一致：解析出 1 人 → {@code setAssignee}；多人 → {@code addCandidateUser}（谁先认领谁负责）；
     * 空/错 → 任务保持 CREATED，由 {@code TaskOrphanRepairService} 兜底修复。
     */
    private void handleRoleModeAssignment(TaskAssignmentListener owner, TaskEntity task, String taskId,
            String processInstanceId, String processDefinitionId, String taskDefinitionKey,
            Map<String, Object> currentItem, String subTableId, String subTableName,
            Map<String, Object> rowKey, List<String> pkCols, Long subTableRowVersion,
            String roleField, String buField) {
        TaskService taskService = owner.taskService();
        RuntimeService runtimeService = owner.runtimeService();
        ExtendedTaskInfoRepository extendedTaskInfoRepository = owner.extendedTaskInfoRepository();
        ObjectMapper objectMapper = owner.objectMapper();
        TaskAssigneeResolver resolver = owner.taskAssigneeResolver();

        if (roleField == null || roleField.isBlank()) {
            log.warn("[MI][role] roleField missing for task {}, task will remain CREATED", taskId);
            return;
        }
        List<String> roleCodes = resolveRoleCodesFromItem(currentItem, roleField.trim());
        if (roleCodes.isEmpty()) {
            log.warn("[MI][role] no role code in currentItem[{}] for task {}, task will remain CREATED",
                    roleField, taskId);
            return;
        }

        // BU code：优先行内 buField（已是 code），否则回退进程 active BU（id→code）。
        String buCode = null;
        if (buField != null && !buField.isBlank()) {
            Object buObj = currentItem.get(buField.trim());
            if (buObj != null && !String.valueOf(buObj).trim().isEmpty()) {
                buCode = String.valueOf(buObj).trim();
            }
        }
        if (buCode == null) {
            try {
                Object activeBuId = runtimeService.getVariable(task.getExecutionId(), "activeBusinessUnitId");
                buCode = owner.mapActiveBusinessUnitIdToCode(
                        activeBuId != null ? String.valueOf(activeBuId).trim() : null);
            } catch (Exception e) {
                log.debug("[MI][role] could not read activeBusinessUnitId for task {}: {}", taskId, e.getMessage());
            }
        }
        if (buCode == null || buCode.isBlank()) {
            log.warn("[MI][role] no BU code (row buField={} empty and no active BU) for task {}, task will remain CREATED",
                    buField, taskId);
            return;
        }

        String initiatorId = resolveInitiatorId(owner, processInstanceId);

        TaskAssigneeResolver.ResolveResult result;
        try {
            // BU_ROLE 解析（非 ELEMENT_VARIABLE，后者是 listenerOnly 会被拒）；anchor 用 initiator。
            result = resolver.resolveWithRoleIds("BU_ROLE", roleCodes, buCode, initiatorId, initiatorId, buCode);
        } catch (Exception e) {
            log.warn("[MI][role] resolve failed for task {} (roles={}, bu={}): {}; task will remain CREATED",
                    taskId, roleCodes, buCode, e.getMessage());
            return;
        }
        if (result == null || result.getErrorMessage() != null) {
            log.warn("[MI][role] resolve empty/error for task {} (roles={}, bu={}): {}; task will remain CREATED",
                    taskId, roleCodes, buCode, result != null ? result.getErrorMessage() : "null result");
            return;
        }

        List<String> assignedTargets = new ArrayList<>();
        AssignmentType assignmentType;
        try {
            if (result.getAssignee() != null && !result.getAssignee().isBlank()) {
                String uid = result.getAssignee().trim();
                taskService.setAssignee(taskId, uid);
                owner.notifyNewTask(uid, taskId, task.getName(), processInstanceId);
                assignedTargets.add(uid);
                assignmentType = AssignmentType.USER;
                log.info("[MI][role] task {} assigned to sole role holder {} (roles={}, bu={})",
                        taskId, uid, roleCodes, buCode);
            } else if (result.getCandidateUsers() != null && !result.getCandidateUsers().isEmpty()) {
                for (String cand : result.getCandidateUsers()) {
                    if (cand != null && !cand.isBlank()) {
                        taskService.addCandidateUser(taskId, cand.trim());
                        owner.notifyCandidateTask(cand.trim(), taskId, task.getName(), processInstanceId);
                        assignedTargets.add(cand.trim());
                    }
                }
                assignmentType = AssignmentType.CANDIDATE_USERS;
                log.info("[MI][role] task {} candidate pool set: {} (roles={}, bu={})",
                        taskId, assignedTargets, roleCodes, buCode);
            } else {
                log.warn("[MI][role] resolve returned no assignee and no candidates for task {} (roles={}, bu={}); task will remain CREATED",
                        taskId, roleCodes, buCode);
                return;
            }
        } catch (Exception e) {
            log.warn("[MI][role] failed to apply assignment for task {}: {}; task will remain CREATED",
                    taskId, e.getMessage());
            return;
        }

        // ExtendedTaskInfo：标 BU_ROLE，target 存解析出的用户（逗号），并把 role/BU code 存进 extendedProperties。
        Map<String, Object> extendedProps = new HashMap<>();
        extendedProps.put("multiInstance", true);
        extendedProps.put("subTableRowKey", rowKey);
        if (pkCols.size() == 1 && rowKey.get(pkCols.get(0)) instanceof Number) {
            extendedProps.put("subTableRowId", ((Number) rowKey.get(pkCols.get(0))).longValue());
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
        extendedProps.put("assigneeMode", "role");
        extendedProps.put("roleCodes", roleCodes);
        extendedProps.put("businessUnitCode", buCode);

        String[] progressCols = resolveMiProgressColumnNames(owner, processDefinitionId, taskDefinitionKey);
        extendedProps.put("miTaskStatusField", progressCols[0]);
        extendedProps.put("miTaskCurrentNodeField", progressCols[1]);

        String extendedPropertiesJson;
        try {
            extendedPropertiesJson = objectMapper.writeValueAsString(extendedProps);
        } catch (Exception e) {
            log.error("[MI][role] failed to serialize extendedProperties for task {}: {}", taskId, e.getMessage());
            extendedPropertiesJson = "{}";
        }

        try {
            ExtendedTaskInfo extInfo = ExtendedTaskInfo.builder()
                    .taskId(taskId)
                    .processInstanceId(processInstanceId)
                    .processDefinitionId(processDefinitionId)
                    .taskDefinitionKey(taskDefinitionKey)
                    .taskName(task.getName())
                    .assignmentType(assignmentType)
                    .assignmentTarget(String.join(",", assignedTargets))
                    .status("ASSIGNED")
                    .createdTime(LocalDateTime.now())
                    .extendedProperties(extendedPropertiesJson)
                    .build();
            extendedTaskInfoRepository.save(extInfo);
            updateSubTableTaskProgress(owner, subTableName, rowKey, task.getName(), progressCols[0], progressCols[1]);
            log.info("[MI][role] created ExtendedTaskInfo for task {}: targets={}, rowKey={}",
                    taskId, assignedTargets, rowKey);
        } catch (Exception e) {
            log.error("[MI][role] failed to save ExtendedTaskInfo for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 把 {@code currentItem[roleField]} 规范成 role code 列表：支持 {@code List}、JSON 数组文本、逗号串、单值。
     */
    private static List<String> resolveRoleCodesFromItem(Map<String, Object> currentItem, String roleField) {
        Object raw = currentItem.get(roleField);
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null && !String.valueOf(o).trim().isEmpty()) {
                    out.add(String.valueOf(o).trim());
                }
            }
            return out;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        // AssigneeRoleIdsSupport.parseRoleIds 按逗号切分并去重去空；JSON 数组文本先剥括号引号。
        String normalized = text.replaceAll("^[\\[\\]\"']+|[\\[\\]\"']+$", "").replace("\"", "").replace("'", "");
        return AssigneeRoleIdsSupport.parseRoleIds(normalized, null);
    }

    /**
     * MI role 分支的 initiator：进程变量 {@code initiator} 优先，回退到运行/历史流程实例的 startUserId。
     */
    private static String resolveInitiatorId(TaskAssignmentListener owner, String processInstanceId) {
        RuntimeService runtimeService = owner.runtimeService();
        try {
            Object v = runtimeService.getVariable(processInstanceId, "initiator");
            if (v != null && !String.valueOf(v).trim().isEmpty()) {
                return String.valueOf(v).trim();
            }
        } catch (Exception ignored) {
        }
        try {
            org.flowable.engine.runtime.ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (pi != null && pi.getStartUserId() != null && !pi.getStartUserId().isBlank()) {
                return pi.getStartUserId().trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Column names come from SubProcess BPMN extensions {@code miTaskStatusField} / {@code miTaskCurrentNodeField}
     * (designer) with defaults {@code task_status} / {@code task_current_node}.
     */
    void updateSubTableTaskProgress(TaskAssignmentListener owner, String subTableName, Map<String, Object> rowKey,
            String taskName, String statusColumn, String currentNodeColumn) {
        JdbcTemplate jdbcTemplate = owner.jdbcTemplate();
        if (subTableName == null || rowKey == null || rowKey.isEmpty()) {
            return;
        }
        try {
            String tableName = requireSafeIdentifier(subTableName);
            List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, tableName);
            String pkWhere = SubTableRowKeySupport.buildPkWhereClause(pkCols);
            Object[] pkArgs = SubTableRowKeySupport.orderedPkParams(pkCols, rowKey);
            String statusCol = requireSafeIdentifier(statusColumn);
            String nodeCol = requireSafeIdentifier(currentNodeColumn);
            boolean hasTaskStatus = columnExists(jdbcTemplate, tableName, statusCol);
            boolean hasTaskCurrentNode = columnExists(jdbcTemplate, tableName, nodeCol);
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
            sql.append(" WHERE ").append(pkWhere);
            params.addAll(Arrays.asList(pkArgs));
            jdbcTemplate.update(sql.toString(), params.toArray());
        } catch (Exception e) {
            log.debug("Skipped updating sub-table task progress for {} / rowKey={}: {}",
                    subTableName, rowKey, e.getMessage());
        }
    }

    String[] resolveMiProgressColumnNames(TaskAssignmentListener owner, String processDefinitionId,
            String taskDefinitionKey) {
        BpmnActionParser bpmnActionParser = owner.bpmnActionParser();
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

    void updateCurrentItemProgress(TaskAssignmentListener owner, Map<String, Object> processVariables,
            String processDefinitionId, String taskDefinitionKey, String taskName) {
        BpmnActionParser bpmnActionParser = owner.bpmnActionParser();
        JdbcTemplate jdbcTemplate = owner.jdbcTemplate();
        if (processVariables == null || processDefinitionId == null || taskDefinitionKey == null) {
            return;
        }
        Object currentItemObj = processVariables.get("currentItem");
        if (currentItemObj == null) {
            currentItemObj = processVariables.get("_currentItem");
        }
        if (!(currentItemObj instanceof Map<?, ?> currentItemRaw)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> currentItem = (Map<String, Object>) currentItemRaw;

        String subTableName = firstNonBlank(
                bpmnActionParser.getUserTaskExtensionPropertyValue(processDefinitionId, taskDefinitionKey, "subTableName"),
                bpmnActionParser.getMultiInstanceSubProcessSubTableName(processDefinitionId, taskDefinitionKey)
        );
        if (subTableName == null || subTableName.isBlank()) {
            return;
        }
        Map<String, Object> rowKey;
        try {
            List<String> pkCols = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate,
                    requireSafeIdentifier(subTableName));
            rowKey = SubTableRowKeySupport.rowKeyFromCurrentItem(currentItem, pkCols);
        } catch (Exception e) {
            return;
        }
        if (rowKey == null) {
            return;
        }

        String[] cols = resolveMiProgressColumnNames(owner, processDefinitionId, taskDefinitionKey);
        updateSubTableTaskProgress(owner, subTableName, rowKey, taskName, cols[0], cols[1]);
    }

    private static String safeSqlColumnName(String candidate, String defaultName) {
        if (candidate == null || candidate.isBlank()) {
            return defaultName;
        }
        String t = candidate.trim();
        return SAFE_SQL_IDENTIFIER.matcher(t).matches() ? t : defaultName;
    }

    private static Long extractLong(Object value) {
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

    private static String requireSafeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid sub-table name");
        }
        return identifier;
    }

    private static boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName);
        return count != null && count > 0;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
