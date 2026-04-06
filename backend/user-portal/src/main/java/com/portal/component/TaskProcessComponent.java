package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import com.portal.entity.DelegationAudit;
import com.portal.entity.DelegationRule;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.DelegationAuditRepository;
import com.portal.repository.DelegationRuleRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 任务处理组件
 * 支持任务认领、完成、转办、委托等操作
 * 
 * 通过 WorkflowEngineClient 调用 Flowable 引擎
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProcessComponent {

    private final TaskQueryComponent taskQueryComponent;
    private final DelegationRuleRepository delegationRuleRepository;
    private final DelegationAuditRepository delegationAuditRepository;
    private final WorkflowEngineClient workflowEngineClient;
    private final ProcessInstanceRepository processInstanceRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 认领任务
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    @Transactional
    public TaskInfo claimTask(String taskId, String userId) {
        return claimTask(taskId, userId, null);
    }

    /**
     * 认领任务（支持 JWT userId 与 Flowable 侧 assignee/候选人使用 username 时不一致的场景）
     */
    @Transactional
    public TaskInfo claimTask(String taskId, String userId, String portalUsername) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        TaskInfo taskBefore = getTaskOrThrow(taskId);
        String enginePrincipal = resolveEnginePrincipalForWorkflow(taskBefore, userId, portalUsername);

        log.info("Using Flowable engine to claim task: {} by engine principal: {} (portal userId: {})", taskId, enginePrincipal, userId);
        Optional<Map<String, Object>> result = workflowEngineClient.claimTask(taskId, enginePrincipal);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to claim task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to claim task";
            throw new PortalException("500", message);
        }
        
        // 任务状态已在 Flowable 中更新，重新获取最新状态
        TaskInfo task = getTaskOrThrow(taskId);
        
        // 更新流程实例的当前处理人（门户侧统一记 JWT userId）
        updateProcessInstanceAssignee(task.getProcessInstanceId(), userId, task.getTaskName());

        log.info("Task {} claimed via Flowable by user {}", taskId, userId);
        return task;
    }

    /**
     * 取消认领任务
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    @Transactional
    public TaskInfo unclaimTask(String taskId, String userId, String originalAssignmentType, String originalAssignee) {
        return unclaimTask(taskId, userId, originalAssignmentType, originalAssignee, null);
    }

    @Transactional
    public TaskInfo unclaimTask(String taskId, String userId, String originalAssignmentType, String originalAssignee,
                                String portalUsername) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        TaskInfo taskBefore = getTaskOrThrow(taskId);
        String enginePrincipal = resolveEnginePrincipalForWorkflow(taskBefore, userId, portalUsername);

        log.info("Using Flowable engine to unclaim task: {} by engine principal: {} (portal userId: {})", taskId, enginePrincipal, userId);
        Optional<Map<String, Object>> result = workflowEngineClient.unclaimTask(taskId, enginePrincipal);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to unclaim task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to unclaim task";
            throw new PortalException("500", message);
        }
        
        // 任务状态已在 Flowable 中更新，重新获取最新状态
        TaskInfo task = getTaskOrThrow(taskId);
        
        // 取消认领后，清空流程实例的当前处理人
        updateProcessInstanceAssignee(task.getProcessInstanceId(), null, task.getTaskName());

        log.info("Task {} unclaimed via Flowable by user {}", taskId, userId);
        return task;
    }

    /**
     * 完成任务
     */
    @Transactional
    public void completeTask(TaskCompleteRequest request, String userId) {
        completeTask(request, userId, null);
    }

    @Transactional
    public void completeTask(TaskCompleteRequest request, String userId, String portalUsername) {
        String taskId = request.getTaskId();
        TaskInfo task = getTaskOrThrow(taskId);

        // 验证用户是否有权限处理任务
        if (!canProcessTask(task, userId, portalUsername)) {
            throw new PortalException("403", "You do not have permission to process this task");
        }

        // 自动认领：虚拟组或 Flowable 候选人池任务且尚未有 assignee（排除「空池」孤儿任务，无 identity link 时 claim 必失败）
        boolean poolStyle = "VIRTUAL_GROUP".equals(task.getAssignmentType()) || "CANDIDATE_USERS".equals(task.getAssignmentType())
                || "DEPT_ROLE".equals(task.getAssignmentType());
        boolean noAssignee = task.getAssignee() == null || task.getAssignee().isEmpty();
        if (poolStyle && noAssignee && !isEmptyAssignmentPool(task)) {
            log.info("Auto-claiming pool task {} (type {}) for user {}", taskId, task.getAssignmentType(), userId);
            claimTask(taskId, userId, portalUsername);
            task = getTaskOrThrow(taskId); // 认领后刷新任务状态
        } else if (poolStyle && noAssignee && isEmptyAssignmentPool(task)) {
            log.info("Skipping auto-claim for empty-pool task {} (no assignee/target/candidates); completing without claim", taskId);
        }

        String action = request.getAction();
        switch (action) {
            case "APPROVE", "REJECT" -> handleApproval(task, request, userId);
            case "TRANSFER" -> handleTransfer(task, request, userId);
            case "DELEGATE" -> handleDelegate(task, request, userId);
            case "RETURN" -> handleReturn(task, request, userId);
            default -> throw new PortalException("400", "Unsupported action type: " + action);
        }
    }

    /**
     * 委托任务
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    @Transactional
    public void delegateTask(String taskId, String delegatorId, String delegateId, String reason) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        log.info("Using Flowable engine to delegate task: {} from {} to {}", taskId, delegatorId, delegateId);
        Optional<Map<String, Object>> result = workflowEngineClient.delegateTask(taskId, delegatorId, delegateId, reason);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to delegate task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to delegate task";
            throw new PortalException("500", message);
        }
        
        // 更新流程实例的当前处理人
        TaskInfo task = getTaskOrThrow(taskId);
        updateProcessInstanceAssignee(task.getProcessInstanceId(), delegateId, task.getTaskName());
        
        // 记录审计日志
        DelegationAudit audit = DelegationAudit.builder()
                .delegatorId(delegatorId)
                .delegateId(delegateId)
                .taskId(taskId)
                .operationType("DELEGATE_TASK")
                .operationResult("SUCCESS")
                .operationDetail(reason)
                .build();
        delegationAuditRepository.save(audit);

        log.info("Task {} delegated via Flowable from {} to {}", taskId, delegatorId, delegateId);
    }

    /**
     * 转办任务
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    @Transactional
    public void transferTask(String taskId, String fromUserId, String toUserId, String reason) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        log.info("Using Flowable engine to transfer task: {} from {} to {}", taskId, fromUserId, toUserId);
        Optional<Map<String, Object>> result = workflowEngineClient.transferTask(taskId, fromUserId, toUserId, reason);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to transfer task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to transfer task";
            throw new PortalException("500", message);
        }
        
        // 更新流程实例的当前处理人
        TaskInfo task = getTaskOrThrow(taskId);
        updateProcessInstanceAssignee(task.getProcessInstanceId(), toUserId, task.getTaskName());

        // 记录审计日志
        DelegationAudit audit = DelegationAudit.builder()
                .delegatorId(fromUserId)
                .delegateId(toUserId)
                .taskId(taskId)
                .operationType("TRANSFER_TASK")
                .operationResult("SUCCESS")
                .operationDetail(reason)
                .build();
        delegationAuditRepository.save(audit);

        log.info("Task {} transferred via Flowable from {} to {}", taskId, fromUserId, toUserId);
    }

    /**
     * 为子表行分配处理人（多实例子流程前置任务），经 {@link WorkflowEngineClient} 调用引擎。
     */
    @Transactional
    public Map<String, Object> assignSubTableRow(String taskId, Long rowId, String assigneeId, String userId) {
        return assignSubTableRow(taskId, rowId, assigneeId, userId, null);
    }

    @Transactional
    public Map<String, Object> assignSubTableRow(String taskId, Long rowId, String assigneeId, String userId,
                                                 String portalUsername) {
        // #region agent log
        appendDebugLog("H5-component-entry", "TaskProcessComponent.assignSubTableRow", String.format(
                "\"taskIdLen\":%d,\"rowId\":%d,\"assigneeIdLen\":%d,\"userIdLen\":%d",
                taskId != null ? taskId.length() : 0,
                rowId != null ? rowId : -1L,
                assigneeId != null ? assigneeId.length() : 0,
                userId != null ? userId.length() : 0));
        // #endregion
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        TaskInfo task = getTaskOrThrow(taskId);
        if (!canProcessTask(task, userId, portalUsername)) {
            throw new PortalException("403", "You do not have permission to process this task");
        }

        boolean poolStyleSt = "VIRTUAL_GROUP".equals(task.getAssignmentType()) || "CANDIDATE_USERS".equals(task.getAssignmentType())
                || "DEPT_ROLE".equals(task.getAssignmentType());
        boolean noAssigneeSt = task.getAssignee() == null || task.getAssignee().isEmpty();
        if (poolStyleSt && noAssigneeSt && !isEmptyAssignmentPool(task)) {
            log.info("Auto-claiming pool task {} (type {}) for sub-table assign by user {}",
                    taskId, task.getAssignmentType(), userId);
            claimTask(taskId, userId, portalUsername);
        }

        Optional<Map<String, Object>> result = workflowEngineClient.assignSubTableRow(taskId, rowId, assigneeId);
        if (result.isEmpty()) {
            // #region agent log
            appendDebugLog("H6-client-empty", "TaskProcessComponent.assignSubTableRow",
                    String.format("\"taskIdLen\":%d,\"rowId\":%d", taskId != null ? taskId.length() : 0, rowId != null ? rowId : -1L));
            // #endregion
            throw new PortalException("500", "Failed to assign sub-table row: " + taskId);
        }

        Map<String, Object> data = result.get();
        // #region agent log
        appendDebugLog("H7-client-result", "TaskProcessComponent.assignSubTableRow", String.format(
                "\"success\":%s,\"hasMessage\":%s,\"hasErrorMessage\":%s",
                String.valueOf(data.get("success")),
                String.valueOf(data.get("message") != null && !String.valueOf(data.get("message")).isBlank()),
                String.valueOf(data.get("errorMessage") != null && !String.valueOf(data.get("errorMessage")).isBlank())));
        // #endregion
        if (!Boolean.TRUE.equals(data.get("success"))) {
            // 引擎 AssignSubTableRowResponse 失败说明在 errorMessage；ApiResponse 错误在 message / error.message
            Object msgObj = data.get("message");
            if (msgObj == null || String.valueOf(msgObj).isBlank()) {
                msgObj = data.get("errorMessage");
            }
            String message = msgObj != null && !String.valueOf(msgObj).isBlank()
                    ? String.valueOf(msgObj) : "Assignment failed";
            throw new PortalException("400", message);
        }
        return data;
    }

    @Transactional
    public Map<String, Object> assignSubTableRowByIdentity(String taskId,
                                                            String assigneeId,
                                                            String userId,
                                                            String portalUsername,
                                                            String email,
                                                            String name,
                                                            String department,
                                                            String topic,
                                                            String location,
                                                            String organizerName) {
        String participantTable = resolveParticipantTableName();
        Long rowId = resolveParticipantRowIdByIdentity(participantTable, email, name, department);
        if (rowId == null) {
            TaskInfo task = getTaskOrThrow(taskId);
            rowId = createParticipantRowIfMissing(participantTable, task, assigneeId, email, name, department, topic, location, organizerName);
            if (rowId == null) {
                throw new PortalException("400", "Assignment failed: participant row not found/created");
            }
        }
        return assignSubTableRow(taskId, rowId, assigneeId, userId, portalUsername);
    }

    private Long resolveParticipantRowIdByIdentity(String participantTable, String email, String name, String department) {
        String em = email != null ? email.trim() : "";
        String nm = name != null ? name.trim() : "";
        String dept = department != null ? department.trim() : "";
        ensureParticipantsIdentityColumns(participantTable);
        // #region agent log
        appendDebugLog("H19-identity-input", "TaskProcessComponent.resolveParticipantRowIdByIdentity", String.format(
                "\"hasEmail\":%s,\"hasName\":%s,\"hasDept\":%s",
                String.valueOf(!em.isBlank()), String.valueOf(!nm.isBlank()), String.valueOf(!dept.isBlank())));
        // #endregion

        try {
            if (!em.isBlank()) {
                List<Long> rows = jdbcTemplate.query(
                        "SELECT id FROM " + participantTable + " WHERE lower(trim(email)) = lower(trim(?)) ORDER BY id LIMIT 1",
                        (rs, i) -> rs.getLong("id"),
                        em);
                // #region agent log
                appendDebugLog("H20-identity-email", "TaskProcessComponent.resolveParticipantRowIdByIdentity",
                        String.format("\"rows\":%d", rows.size()));
                // #endregion
                if (!rows.isEmpty()) {
                    return rows.get(0);
                }
            }
            if (!nm.isBlank() && !dept.isBlank()) {
                List<Long> rows = jdbcTemplate.query(
                        "SELECT id FROM " + participantTable + " WHERE lower(trim(name)) = lower(trim(?)) AND lower(trim(department)) = lower(trim(?)) ORDER BY id LIMIT 1",
                        (rs, i) -> rs.getLong("id"),
                        nm, dept);
                // #region agent log
                appendDebugLog("H21-identity-name-dept", "TaskProcessComponent.resolveParticipantRowIdByIdentity",
                        String.format("\"rows\":%d", rows.size()));
                // #endregion
                if (!rows.isEmpty()) {
                    return rows.get(0);
                }
            }
            if (!nm.isBlank()) {
                List<Long> rows = jdbcTemplate.query(
                        "SELECT id FROM " + participantTable + " WHERE lower(trim(name)) = lower(trim(?)) ORDER BY id LIMIT 1",
                        (rs, i) -> rs.getLong("id"),
                        nm);
                // #region agent log
                appendDebugLog("H22-identity-name", "TaskProcessComponent.resolveParticipantRowIdByIdentity",
                        String.format("\"rows\":%d", rows.size()));
                // #endregion
                if (!rows.isEmpty()) {
                    return rows.get(0);
                }
            }
        } catch (Exception e) {
            log.debug("resolveParticipantRowIdByIdentity failed: {}", e.getMessage());
            // #region agent log
            appendDebugLog("H23-identity-error", "TaskProcessComponent.resolveParticipantRowIdByIdentity",
                    String.format("\"error\":\"%s\"", String.valueOf(e.getMessage()).replace("\"", "'")));
            // #endregion
            String detail = String.valueOf(e.getMessage());
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            detail = detail.replace("\n", " ").replace("\r", " ");
            throw new PortalException("400", "Assignment failed: participant identity lookup error: " + detail);
        }
        return null;
    }

    private Long createParticipantRowIfMissing(String participantTable,
                                               TaskInfo task,
                                               String assigneeId,
                                               String email,
                                               String name,
                                               String department,
                                               String topic,
                                               String location,
                                               String organizerName) {
        Long meetingId = resolveMeetingId(task, topic, location, organizerName);
        if (meetingId == null) {
            return createParticipantRowWithoutMeetingId(participantTable, name, email, department, assigneeId);
        }
        String nm = name != null ? name.trim() : "";
        String em = email != null ? email.trim() : "";
        String dept = department != null ? department.trim() : "";
        if (nm.isBlank()) {
            nm = "Participant";
        }
        try {
            boolean hasMeetingIdCol = columnExists(participantTable, "meeting_id");
            if (hasMeetingIdCol) {
                List<Long> exists = jdbcTemplate.query(
                        "SELECT id FROM " + participantTable + " WHERE meeting_id = ? AND lower(trim(name)) = lower(trim(?)) ORDER BY id LIMIT 1",
                        (rs, i) -> rs.getLong("id"),
                        meetingId, nm);
                if (!exists.isEmpty()) {
                    return exists.get(0);
                }
            }
            Long newId = jdbcTemplate.queryForObject(
                    "INSERT INTO " + participantTable + " (meeting_id, name, department, email, assignee_user_id, sort_order) VALUES (?, ?, ?, ?, ?, 0) RETURNING id",
                    Long.class,
                    meetingId, nm, dept.isBlank() ? null : dept, em.isBlank() ? null : em, assigneeId);
            // #region agent log
            appendDebugLog("H25-create-row-ok", "TaskProcessComponent.createParticipantRowIfMissing",
                    String.format("\"meetingId\":%d,\"newRowId\":%d", meetingId, newId != null ? newId : -1L));
            // #endregion
            return newId;
        } catch (Exception e) {
            // #region agent log
            appendDebugLog("H26-create-row-error", "TaskProcessComponent.createParticipantRowIfMissing",
                    String.format("\"error\":\"%s\"", String.valueOf(e.getMessage()).replace("\"", "'")));
            // #endregion
            return null;
        }
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long resolveMeetingId(TaskInfo task, String topic, String location, String organizerName) {
        Map<String, Object> vars = task != null ? task.getVariables() : null;
        Long meetingId = toLong(vars != null ? vars.get("meeting_id") : null);
        if (meetingId == null) {
            meetingId = toLong(vars != null ? vars.get("mainRecordId") : null);
        }
        if (meetingId != null) {
            return meetingId;
        }
        String processInstanceId = task != null ? task.getProcessInstanceId() : null;
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return null;
        }
        Optional<ProcessInstance> processInstanceOpt = processInstanceRepository.findById(processInstanceId);
        if (processInstanceOpt.isEmpty()) {
            return null;
        }
        Map<String, Object> persistedVars = processInstanceOpt.get().getVariables();
        Long fromPersisted = toLong(persistedVars != null ? persistedVars.get("meeting_id") : null);
        if (fromPersisted == null) {
            fromPersisted = toLong(persistedVars != null ? persistedVars.get("mainRecordId") : null);
        }
        if (fromPersisted == null) {
            fromPersisted = findMeetingIdFromMainTable(vars != null ? vars : persistedVars);
        }
        if (fromPersisted == null) {
            Map<String, Object> hints = new HashMap<>();
            hints.put("topic", topic);
            hints.put("location", location);
            hints.put("organizer_name", organizerName);
            fromPersisted = findMeetingIdFromMainTable(hints);
        }
        // #region agent log
        appendDebugLog("H46-meeting-id-fallback", "TaskProcessComponent.resolveMeetingId",
                String.format("\"processInstanceIdLen\":%d,\"hit\":%s",
                        processInstanceId.length(), String.valueOf(fromPersisted != null)));
        // #endregion
        return fromPersisted;
    }

    private Long findMeetingIdFromMainTable(Map<String, Object> vars) {
        if (vars == null || vars.isEmpty()) {
            return null;
        }
        String topic = toNonBlankString(vars.get("topic"));
        String meetingTime = toNonBlankString(vars.get("meeting_time"));
        String location = toNonBlankString(vars.get("location"));
        String organizer = toNonBlankString(vars.get("organizer_name"));
        if (topic == null && location == null && organizer == null) {
            return null;
        }
        Long found = null;
        String hitTable = null;
        List<String> meetingTables = List.of("meeting", "rt_meeting", "up_meeting");
        for (String table : meetingTables) {
            if (!tableExists(table)) {
                continue;
            }
            boolean hasTopicCol = columnExists(table, "topic");
            boolean hasMeetingTimeCol = columnExists(table, "meeting_time");
            boolean hasLocationCol = columnExists(table, "location");
            boolean hasOrganizerCol = columnExists(table, "organizer_name");

            if (topic != null && meetingTime != null && hasTopicCol && hasMeetingTimeCol) {
                List<Long> rows = jdbcTemplate.query(
                        "SELECT id FROM " + table + " WHERE lower(trim(topic)) = lower(trim(?)) " +
                                "AND CAST(meeting_time AS text) LIKE ? ORDER BY id DESC LIMIT 1",
                        (rs, i) -> rs.getLong("id"),
                        topic, meetingTime + "%");
                found = rows.isEmpty() ? null : rows.get(0);
            }
            if (found == null && topic != null && hasTopicCol) {
                List<Long> rows = jdbcTemplate.query(
                        "SELECT id FROM " + table + " WHERE lower(trim(topic)) = lower(trim(?)) ORDER BY id DESC LIMIT 1",
                        (rs, i) -> rs.getLong("id"),
                        topic);
                found = rows.isEmpty() ? null : rows.get(0);
            }
            if (found == null && location != null && organizer != null && hasLocationCol && hasOrganizerCol) {
                List<Long> rows = jdbcTemplate.query(
                        "SELECT id FROM " + table + " WHERE lower(trim(location)) = lower(trim(?)) " +
                                "AND lower(trim(organizer_name)) = lower(trim(?)) ORDER BY id DESC LIMIT 1",
                        (rs, i) -> rs.getLong("id"),
                        location, organizer);
                found = rows.isEmpty() ? null : rows.get(0);
            }
            if (found == null && location != null && hasLocationCol) {
                List<Long> rows = jdbcTemplate.query(
                        "SELECT id FROM " + table + " WHERE lower(trim(location)) = lower(trim(?)) ORDER BY id DESC LIMIT 1",
                        (rs, i) -> rs.getLong("id"),
                        location);
                found = rows.isEmpty() ? null : rows.get(0);
            }
            if (found == null && organizer != null && hasOrganizerCol) {
                List<Long> rows = jdbcTemplate.query(
                        "SELECT id FROM " + table + " WHERE lower(trim(organizer_name)) = lower(trim(?)) ORDER BY id DESC LIMIT 1",
                        (rs, i) -> rs.getLong("id"),
                        organizer);
                found = rows.isEmpty() ? null : rows.get(0);
            }
            if (found == null) {
                List<Long> rows = jdbcTemplate.query(
                        "SELECT id FROM " + table + " ORDER BY id DESC LIMIT 1",
                        (rs, i) -> rs.getLong("id"));
                found = rows.isEmpty() ? null : rows.get(0);
            }
            if (found != null) {
                hitTable = table;
                break;
            }
        }
        // #region agent log
        appendDebugLog("H48-meeting-id-by-main-table", "TaskProcessComponent.findMeetingIdFromMainTable",
                String.format("\"hasTopic\":%s,\"hasMeetingTime\":%s,\"hasLocation\":%s,\"hasOrganizer\":%s,\"hit\":%s,\"table\":\"%s\"",
                        String.valueOf(topic != null), String.valueOf(meetingTime != null),
                        String.valueOf(location != null), String.valueOf(organizer != null),
                        String.valueOf(found != null), hitTable != null ? hitTable : ""));
        // #endregion
        return found;
    }

    private boolean tableExists(String tableName) {
        try {
            String regClass = jdbcTemplate.queryForObject(
                    "SELECT to_regclass(?)",
                    String.class,
                    tableName);
            return regClass != null && !regClass.isBlank();
        } catch (Exception e) {
            String detail = String.valueOf(e.getMessage());
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            detail = detail.replace("\n", " ").replace("\r", " ");
            throw new PortalException("400", "Assignment failed: table metadata lookup error: " + detail);
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (" +
                            "SELECT 1 FROM pg_attribute " +
                            "WHERE attrelid = to_regclass(?) " +
                            "  AND attname = ? " +
                            "  AND NOT attisdropped" +
                            ")",
                    Boolean.class,
                    tableName, columnName);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            String detail = String.valueOf(e.getMessage());
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            detail = detail.replace("\n", " ").replace("\r", " ");
            throw new PortalException("400", "Assignment failed: column metadata lookup error: " + detail);
        }
    }

    private String resolveParticipantTableName() {
        List<String> candidates = List.of("participants", "rt_participants", "up_participants");
        for (String candidate : candidates) {
            String qualified = resolveTableAcrossSchemas(candidate);
            if (qualified != null) {
                return qualified;
            }
        }
        try {
            List<String> discovered = jdbcTemplate.query(
                    "SELECT quote_ident(t.table_schema) || '.' || quote_ident(t.table_name) AS qualified_name " +
                            "FROM information_schema.tables t " +
                            "WHERE t.table_schema NOT IN ('pg_catalog', 'information_schema') " +
                            "  AND t.table_type = 'BASE TABLE' " +
                            "  AND (lower(t.table_name) LIKE '%participant%' OR lower(t.table_name) LIKE '%attendee%') " +
                            "  AND EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name AND c.column_name = 'id') " +
                            "  AND EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name AND c.column_name = 'name') " +
                            "ORDER BY CASE " +
                            "         WHEN t.table_name = 'participants' THEN 0 " +
                            "         WHEN t.table_name LIKE '%participants%' THEN 1 " +
                            "         WHEN t.table_name LIKE '%participant%' THEN 2 " +
                            "         ELSE 3 " +
                            "       END, t.table_schema ASC, t.table_name ASC",
                    (rs, i) -> rs.getString("qualified_name"));
            if (!discovered.isEmpty()) {
                return discovered.get(0);
            }
        } catch (Exception e) {
            String detail = String.valueOf(e.getMessage());
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            detail = detail.replace("\n", " ").replace("\r", " ");
            throw new PortalException("400", "Assignment failed: participant table discovery error: " + detail);
        }
        throw new PortalException("400", "Assignment failed: participants table not found");
    }

    private String resolveTableAcrossSchemas(String tableName) {
        try {
            List<String> matches = jdbcTemplate.query(
                    "SELECT quote_ident(n.nspname) || '.' || quote_ident(c.relname) AS qualified_name " +
                            "FROM pg_class c " +
                            "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                            "WHERE c.relkind = 'r' " +
                            "  AND c.relname = ? " +
                            "  AND n.nspname NOT IN ('pg_catalog', 'information_schema') " +
                            "ORDER BY CASE WHEN n.nspname = 'public' THEN 0 ELSE 1 END, n.nspname ASC " +
                            "LIMIT 1",
                    (rs, i) -> rs.getString("qualified_name"),
                    tableName);
            return matches.isEmpty() ? null : matches.get(0);
        } catch (Exception e) {
            String detail = String.valueOf(e.getMessage());
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            detail = detail.replace("\n", " ").replace("\r", " ");
            throw new PortalException("400", "Assignment failed: participant table discovery error: " + detail);
        }
    }

    private void ensureParticipantsIdentityColumns(String participantTable) {
        if (!columnExists(participantTable, "id")) {
            throw new PortalException("400", "Assignment failed: participants.id column not found");
        }
        if (!columnExists(participantTable, "name")) {
            throw new PortalException("400", "Assignment failed: participants.name column not found");
        }
    }

    private static String toNonBlankString(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private Long createParticipantRowWithoutMeetingId(String participantTable, String name, String email, String department, String assigneeId) {
        ensureParticipantsIdentityColumns(participantTable);
        String nm = name != null ? name.trim() : "";
        String em = email != null ? email.trim() : "";
        String dept = department != null ? department.trim() : "";
        if (nm.isBlank()) {
            // #region agent log
            appendDebugLog("H24-create-row-skip", "TaskProcessComponent.createParticipantRowWithoutMeetingId",
                    "\"reason\":\"missing meeting_id and blank name\"");
            // #endregion
            throw new PortalException("400", "Assignment failed: participant name is empty");
        }
        try {
            if (!em.isBlank()) {
                List<Long> byEmail = jdbcTemplate.query(
                        "SELECT id FROM " + participantTable + " WHERE lower(trim(email)) = lower(trim(?)) ORDER BY id LIMIT 1",
                        (rs, i) -> rs.getLong("id"),
                        em);
                if (!byEmail.isEmpty()) {
                    return byEmail.get(0);
                }
            }
            List<Long> byName = jdbcTemplate.query(
                    "SELECT id FROM " + participantTable + " WHERE lower(trim(name)) = lower(trim(?)) ORDER BY id LIMIT 1",
                    (rs, i) -> rs.getLong("id"),
                    nm);
            if (!byName.isEmpty()) {
                return byName.get(0);
            }
            List<Long> fallbackMeeting = columnExists(participantTable, "meeting_id")
                    ? jdbcTemplate.query(
                    "SELECT meeting_id FROM " + participantTable + " WHERE meeting_id IS NOT NULL ORDER BY id DESC LIMIT 1",
                    (rs, i) -> rs.getLong("meeting_id"))
                    : List.of();
            if (!fallbackMeeting.isEmpty()) {
                Long fallbackMeetingId = fallbackMeeting.get(0);
                Long newIdWithMeeting = jdbcTemplate.queryForObject(
                        "INSERT INTO " + participantTable + " (meeting_id, name, department, email, assignee_user_id, sort_order) VALUES (?, ?, ?, ?, ?, 0) RETURNING id",
                        Long.class,
                        fallbackMeetingId, nm, dept.isBlank() ? null : dept, em.isBlank() ? null : em, assigneeId);
                // #region agent log
                appendDebugLog("H25-create-row-ok", "TaskProcessComponent.createParticipantRowWithoutMeetingId",
                        String.format("\"fallbackMeetingId\":%d,\"newRowId\":%d",
                                fallbackMeetingId, newIdWithMeeting != null ? newIdWithMeeting : -1L));
                // #endregion
                return newIdWithMeeting;
            }
            Long newId = jdbcTemplate.queryForObject(
                    "INSERT INTO " + participantTable + " (name, department, email, assignee_user_id, sort_order) VALUES (?, ?, ?, ?, 0) RETURNING id",
                    Long.class,
                    nm, dept.isBlank() ? null : dept, em.isBlank() ? null : em, assigneeId);
            // #region agent log
            appendDebugLog("H25-create-row-ok", "TaskProcessComponent.createParticipantRowWithoutMeetingId",
                    String.format("\"newRowId\":%d", newId != null ? newId : -1L));
            // #endregion
            return newId;
        } catch (Exception e) {
            // #region agent log
            appendDebugLog("H26-create-row-error", "TaskProcessComponent.createParticipantRowWithoutMeetingId",
                    String.format("\"error\":\"%s\"", String.valueOf(e.getMessage()).replace("\"", "'")));
            // #endregion
            String detail = String.valueOf(e.getMessage());
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            detail = detail.replace("\n", " ").replace("\r", " ");
            throw new PortalException("400", "Assignment failed: participant row create error: " + detail);
        }
    }

    private void appendDebugLog(String hypothesisId, String location, String dataJson) {
        // #region agent log
        try {
            String line = String.format(
                    "{\"sessionId\":\"97dc8c\",\"runId\":\"run1\",\"hypothesisId\":\"%s\",\"location\":\"%s\",\"message\":\"assign pipeline\",\"data\":{%s},\"timestamp\":%d}%n",
                    hypothesisId, location, dataJson, System.currentTimeMillis());
            Files.writeString(Path.of("d:\\Repos\\Workflow-Station---sun\\.cursor\\debug-97dc8c.log"), line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
        // #endregion
    }

    /**
     * 运行时无处理人、无候选人用户/组、无 assignmentTarget（与 Flowable identity link 一致的空池），不因 assignmentType 字符串再收紧。
     */
    private static boolean isEmptyAssignmentPool(TaskInfo task) {
        if (task == null) {
            return false;
        }
        boolean noAssignee = task.getAssignee() == null || task.getAssignee().isBlank();
        if (!noAssignee) {
            return false;
        }
        boolean noUsers = task.getCandidateUserIds() == null || task.getCandidateUserIds().isEmpty();
        boolean noGroups = task.getCandidateGroupIds() == null || task.getCandidateGroupIds().isEmpty();
        boolean noTarget = task.getAssignmentTarget() == null || task.getAssignmentTarget().isBlank();
        return noUsers && noGroups && noTarget;
    }

    private static boolean isInitiatorOfTask(TaskInfo task, String userId, String portalUsername) {
        if (task == null || userId == null) {
            return false;
        }
        if (samePortalUserId(userId, task.getInitiatorId())) {
            return true;
        }
        if (task.getVariables() != null) {
            Object iv = task.getVariables().get("initiator");
            if (iv != null) {
                return matchesPortalIdentity(iv.toString(), userId, portalUsername);
            }
        }
        return false;
    }

    /**
     * JWT 与引擎侧用户 ID 比较：trim，避免首尾空格导致误判。
     */
    private static boolean samePortalUserId(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equals(b.trim());
    }

    /**
     * 引擎返回的 assignee / 候选人可能是 username，JWT {@code userId} 为用户主键 UUID。
     */
    private static boolean matchesPortalIdentity(String engineSideRef, String portalUserId, String portalUsername) {
        if (engineSideRef == null || engineSideRef.isBlank() || portalUserId == null) {
            return false;
        }
        if (samePortalUserId(portalUserId, engineSideRef)) {
            return true;
        }
        if (portalUsername != null && !portalUsername.isBlank()
                && portalUsername.trim().equals(engineSideRef.trim())) {
            return true;
        }
        return false;
    }

    /**
     * 认领 / 取消认领时须传入与 Flowable IdentityLink 一致的字符串（候选人常为 username）。
     */
    private static String resolveEnginePrincipalForWorkflow(TaskInfo task, String portalUserId, String portalUsername) {
        if (portalUserId == null || portalUserId.isBlank()) {
            return portalUserId != null ? portalUserId.trim() : "";
        }
        String pu = portalUserId.trim();
        String puName = portalUsername != null ? portalUsername.trim() : "";

        String assignee = task.getAssignee();
        if (assignee != null && !assignee.isBlank() && matchesPortalIdentity(assignee, portalUserId, portalUsername)) {
            return assignee.trim();
        }
        List<String> candidates = task.getCandidateUserIds();
        if (candidates != null) {
            for (String c : candidates) {
                if (c == null || c.isBlank()) {
                    continue;
                }
                if (pu.equals(c.trim())) {
                    return c.trim();
                }
            }
            if (!puName.isEmpty()) {
                for (String c : candidates) {
                    if (c != null && puName.equals(c.trim())) {
                        return c.trim();
                    }
                }
            }
        }
        return pu;
    }

    private static boolean candidateUserIdsContain(List<String> candidateUserIds, String userId, String portalUsername) {
        if (candidateUserIds == null || userId == null) {
            return false;
        }
        for (String id : candidateUserIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (matchesPortalIdentity(id.trim(), userId, portalUsername)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证用户是否可以认领任务
     */
    public boolean canClaimTask(TaskInfo task, String userId) {
        return canClaimTask(task, userId, null);
    }

    public boolean canClaimTask(TaskInfo task, String userId, String portalUsername) {
        String assignmentType = task.getAssignmentType();
        String assignee = task.getAssignee();

        return switch (assignmentType != null ? assignmentType : "") {
            case "CANDIDATE_USERS" ->
                    candidateUserIdsContain(task.getCandidateUserIds(), userId, portalUsername);
            case "VIRTUAL_GROUP" -> {
                if (assignee != null && !assignee.isEmpty()) {
                    yield isUserInVirtualGroup(userId, assignee);
                }
                if (task.getCandidateGroupIds() != null) {
                    for (String g : task.getCandidateGroupIds()) {
                        if (isUserInVirtualGroup(userId, g)) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
            default -> false;
        };
    }

    /**
     * 验证用户是否可以处理任务
     */
    public boolean canProcessTask(TaskInfo task, String userId) {
        return canProcessTask(task, userId, null);
    }

    public boolean canProcessTask(TaskInfo task, String userId, String portalUsername) {
        String assignmentType = task.getAssignmentType();
        String assignee = task.getAssignee();

        // 如果任务已分配给当前用户（包括认领后的任务），允许处理
        if (assignee != null && matchesPortalIdentity(assignee, userId, portalUsername)) {
            return true;
        }

        // 直接分配给用户
        if ("USER".equals(assignmentType) && assignee != null && matchesPortalIdentity(assignee, userId, portalUsername)) {
            return true;
        }

        // 委托任务
        if ("DELEGATED".equals(assignmentType) && assignee != null && matchesPortalIdentity(assignee, userId, portalUsername)) {
            return true;
        }

        // Flowable 候选人池：必须在候选人列表中
        if ("CANDIDATE_USERS".equals(assignmentType)) {
            return candidateUserIdsContain(task.getCandidateUserIds(), userId, portalUsername);
        }

        // 实体管理者任务（ENTITY_MANAGER）
        if ("ENTITY_MANAGER".equals(assignmentType)) {
            log.info("Entity manager task {} for user {}, allowing process (permission verified by query)", task.getTaskId(), userId);
            return true;
        }

        // 虚拟组：必须能证明组成员身份（assignee 存组 ID，或引擎返回 candidateGroupIds）
        if ("VIRTUAL_GROUP".equals(assignmentType)) {
            if (assignee != null && !assignee.isEmpty() && isUserInVirtualGroup(userId, assignee)) {
                return true;
            }
            if (task.getCandidateGroupIds() != null) {
                for (String g : task.getCandidateGroupIds()) {
                    if (isUserInVirtualGroup(userId, g)) {
                        return true;
                    }
                }
            }
        }

        // 检查是否有委托权限
        if (assignee != null) {
            List<DelegationRule> delegations = delegationRuleRepository
                    .findActiveDelegationsForDelegate(userId, LocalDateTime.now());
            for (DelegationRule delegation : delegations) {
                if (samePortalUserId(assignee, delegation.getDelegatorId())) {
                    return true;
                }
            }
        }

        // 无 assignee、无候选人/组、无 assignmentTarget 的「空池」任务：仅当 BPMN 为发起人办理时放行发起人；
        // BU_ROLE / HIERARCHY 等节点若误表现为空池，不得出现在发起人待办。
        if (isEmptyAssignmentPool(task) && isInitiatorOfTask(task, userId, portalUsername)) {
            if (!allowsInitiatorEmptyPoolFallback(task.getBpmnAssigneeType())) {
                log.debug("canProcessTask: deny initiator empty-pool for BPMN assigneeType={} task={}",
                        task.getBpmnAssigneeType(), task.getTaskId());
                return false;
            }
            log.info("canProcessTask: allow process for initiator on empty-pool task {}", task.getTaskId());
            return true;
        }

        return false;
    }

    /**
     * 空池发起人兜底：仅当 BPMN 明确为发起人节点时允许。
     * 未配置 assigneeType 或后续节点（BU_ROLE 等）不得因「发起人是自己」出现在待办——发起人只应处理明确标成 INITIATOR 的空池任务。
     */
    private static boolean allowsInitiatorEmptyPoolFallback(String bpmnAssigneeType) {
        if (bpmnAssigneeType == null || bpmnAssigneeType.isBlank()) {
            return false;
        }
        String u = bpmnAssigneeType.trim().toUpperCase(Locale.ROOT);
        return "INITIATOR".equals(u) || "PROCESS_INITIATOR".equals(u);
    }

    /**
     * 待办列表专用过滤：仅隐藏「发起人对空池且 BPMN 非 INITIATOR/PROCESS_INITIATOR」的条目（如误展示的 BU_ROLE 节点）。
     * <p>不要用完整 {@link #canProcessTask} 过滤整表：引擎已按 assignee/候选人/候选组聚合，二次过滤易因候选人 ID（UUID vs username）
     * 与 JWT 字段不一致导致合法处理人（如 BU_ROLE 池成员）待办为空。</p>
     */
    public boolean shouldHideTaskInTodoList(TaskInfo task, String userId, String portalUsername) {
        if (!isEmptyAssignmentPool(task) || !isInitiatorOfTask(task, userId, portalUsername)) {
            return false;
        }
        return !allowsInitiatorEmptyPoolFallback(task.getBpmnAssigneeType());
    }

    /**
     * 是否可查看任务表单（待办/已办快照）：处理人规则 + 发起人 + 当前 assignee（含已办仍带回 assignee 的场景）。
     */
    public boolean canViewTaskForm(TaskInfo task, String userId) {
        return canViewTaskForm(task, userId, null);
    }

    public boolean canViewTaskForm(TaskInfo task, String userId, String portalUsername) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (canProcessTask(task, userId, portalUsername)) {
            return true;
        }
        if (samePortalUserId(userId, task.getInitiatorId())) {
            return true;
        }
        if (task.getAssignee() != null && matchesPortalIdentity(task.getAssignee(), userId, portalUsername)) {
            return true;
        }
        return false;
    }

    /**
     * 获取任务或抛出异常
     */
    private TaskInfo getTaskOrThrow(String taskId) {
        Optional<TaskInfo> first = taskQueryComponent.getTaskById(taskId);
        if (first.isPresent()) {
            return first.get();
        }
        // #region agent log
        appendDebugLog("H45-task-lookup-retry", "TaskProcessComponent.getTaskOrThrow",
                String.format("\"taskId\":\"%s\",\"hit\":false,\"phase\":\"first-miss\"",
                        taskId != null ? taskId.replace("\"", "'") : ""));
        // #endregion
        Optional<TaskInfo> second = taskQueryComponent.getTaskById(taskId);
        if (second.isPresent()) {
            // #region agent log
            appendDebugLog("H45-task-lookup-retry", "TaskProcessComponent.getTaskOrThrow",
                    String.format("\"taskId\":\"%s\",\"hit\":true,\"phase\":\"retry-hit\"",
                            taskId != null ? taskId.replace("\"", "'") : ""));
            // #endregion
            return second.get();
        }
        // #region agent log
        appendDebugLog("H45-task-lookup-retry", "TaskProcessComponent.getTaskOrThrow",
                String.format("\"taskId\":\"%s\",\"hit\":false,\"phase\":\"retry-miss\"",
                        taskId != null ? taskId.replace("\"", "'") : ""));
        // #endregion
        throw new PortalException("404", "Task not found: " + taskId);
    }

    /**
     * 处理审批操作
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    private void handleApproval(TaskInfo task, TaskCompleteRequest request, String userId) {
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
        
        // Add action
        variables.put("action", action);
        
        // Auto-set decision variable based on action
        if ("APPROVE".equals(action)) {
            variables.put("decision", "yes");
            variables.put("approvalStatus", "APPROVED");
            log.info("Set decision=yes for APPROVE action");
        } else if ("REJECT".equals(action)) {
            variables.put("decision", "no");
            variables.put("approvalStatus", "REJECTED");
            log.info("Set decision=no for REJECT action");
        }
        
        // Add approver comments
        if (request.getComment() != null && !request.getComment().isEmpty()) {
            variables.put("approverComments", request.getComment());
        }
        
        // Add any additional form data
        if (request.getFormData() != null) {
            variables.putAll(request.getFormData());
        }

        // 如果是"分配参与人"任务，从子表数据构建多实例集合变量
        if ("Task_AssignParticipants".equals(task.getTaskDefinitionKey())) {
            buildParticipantsCollection(variables);
        }
        
        log.info("Variables before calling workflowEngineClient: {}", variables);
        
        Optional<Map<String, Object>> result = workflowEngineClient.completeTask(taskId, userId, action, variables);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to complete task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to complete task";
            throw new PortalException("500", message);
        }
        
        log.info("Task {} completed via Flowable by user {} with action {} (approvalStatus: {})", 
                taskId, userId, action, variables.get("approvalStatus"));
        
        // 将审批变量同步回本地 ProcessInstance，确保 Completed Tasks / My Requests 能看到
        // 注意：必须创建新的 HashMap 而非原地修改旧 Map，否则 Hibernate 对 JSON 列的脏检测
        // 会因新旧引用相同而误判为"未变更"，导致 UPDATE 语句不被执行
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
                syncInstance.setVariables(mergedVars);
                processInstanceRepository.save(syncInstance);
                log.info("Synced {} approval variables back to local ProcessInstance {}", 
                        mergedVars.size(), syncProcessId);
            }
        } catch (Exception e) {
            log.warn("Failed to sync approval variables to local ProcessInstance: {}", e.getMessage());
        }
        
        // 任务完成后，检查流程是否还有活动任务，如果没有则流程可能已完成
        // 这是一个补偿机制，防止 ProcessCompletionListener 通知失败导致状态不同步
        try {
            String processInstanceId = task.getProcessInstanceId();
            
            // 通过 workflowEngineClient 检查流程状态
            Optional<Map<String, Object>> processStatus = workflowEngineClient.getProcessInstanceStatus(processInstanceId);
            if (processStatus.isPresent()) {
                Map<String, Object> status = processStatus.get();
                Boolean isCompleted = (Boolean) status.get("completed");
                
                if (Boolean.TRUE.equals(isCompleted)) {
                    log.info("Process {} is completed after task completion, updating current node", processInstanceId);
                    String lastActivityName = (String) status.get("lastActivityName");
                    
                    // 更新流程实例状态
                    Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processInstanceId);
                    if (optInstance.isPresent()) {
                        ProcessInstance instance = optInstance.get();
                        if ("RUNNING".equals(instance.getStatus())) {
                            instance.setStatus("COMPLETED");
                            LocalDateTime finishedAt = LocalDateTime.now();
                            instance.setEndTime(finishedAt);
                            instance.setCompletedAt(finishedAt);
                            instance.setCurrentNode(lastActivityName != null ? lastActivityName : "Completed");
                            instance.setCurrentAssignee(null);
                            processInstanceRepository.save(instance);
                            log.info("Process instance {} updated to COMPLETED with currentNode: {}", 
                                    processInstanceId, instance.getCurrentNode());
                        }
                    }
                } else {
                    // 流程未完成，可能有下一个任务，尝试获取下一个任务信息
                    String nextTaskName = (String) status.get("nextTaskName");
                    String nextAssignee = (String) status.get("nextAssignee");
                    if (nextTaskName != null) {
                        updateProcessInstanceAssignee(processInstanceId, nextAssignee, nextTaskName);
                        log.info("Process {} continues with next task: {}", processInstanceId, nextTaskName);
                    } else {
                        // 没有下一个用户任务，可能流程已经到达非用户任务节点（如结束事件）
                        // 尝试获取当前活动节点
                        log.info("No next user task found for process {}, checking for current activity", processInstanceId);
                        Optional<Map<String, Object>> currentActivity = getCurrentActivity(processInstanceId);
                        if (currentActivity.isPresent()) {
                            String currentActivityName = (String) currentActivity.get().get("activityName");
                            String currentActivityType = (String) currentActivity.get().get("activityType");
                            log.info("Current activity for process {}: {} (type: {})", 
                                    processInstanceId, currentActivityName, currentActivityType);
                            
                            // 跳过 SequenceFlow 类型，其 name 是条件标签（如 "Yes"/"No"），不应作为 currentNode
                            if ("SequenceFlow".equals(currentActivityType)) {
                                log.warn("Current activity is SequenceFlow (name: {}), skipping currentNode update for process {}", 
                                        currentActivityName, processInstanceId);
                            } else {
                                // 更新流程实例的当前节点
                                Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processInstanceId);
                                if (optInstance.isPresent()) {
                                    ProcessInstance instance = optInstance.get();
                                    instance.setCurrentNode(currentActivityName);
                                    instance.setCurrentAssignee(null);
                                    
                                    // 如果当前活动是结束事件，则流程已完成
                                    if ("endEvent".equals(currentActivityType) || "EndEvent".equals(currentActivityType)) {
                                        log.info("Current activity is end event, marking process {} as COMPLETED", processInstanceId);
                                        instance.setStatus("COMPLETED");
                                        LocalDateTime finishedAt = LocalDateTime.now();
                                        instance.setEndTime(finishedAt);
                                        instance.setCompletedAt(finishedAt);
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
        } catch (Exception e) {
            log.warn("Failed to check process status after task completion: {}", e.getMessage());
            // 不抛出异常，因为这只是一个补偿机制
        }
    }

    /**
     * 处理转办操作
     */
    private void handleTransfer(TaskInfo task, TaskCompleteRequest request, String userId) {
        String targetUserId = request.getTargetUserId();
        if (targetUserId == null || targetUserId.isEmpty()) {
            throw new PortalException("400", "Transfer target user cannot be empty");
        }
        transferTask(task.getTaskId(), userId, targetUserId, request.getComment());
    }

    /**
     * 处理委托操作
     */
    private void handleDelegate(TaskInfo task, TaskCompleteRequest request, String userId) {
        String targetUserId = request.getTargetUserId();
        if (targetUserId == null || targetUserId.isEmpty()) {
            throw new PortalException("400", "Delegate target user cannot be empty");
        }
        delegateTask(task.getTaskId(), userId, targetUserId, request.getComment());
    }

    /**
     * 处理回退操作
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    private void handleReturn(TaskInfo task, TaskCompleteRequest request, String userId) {
        String taskId = task.getTaskId();
        String targetActivityId = request.getReturnActivityId();
        
        if (targetActivityId == null || targetActivityId.isEmpty()) {
            throw new PortalException("400", "Return target activity cannot be empty");
        }
        
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }
        
        log.info("Using Flowable engine to return task: {} to activity: {}", taskId, targetActivityId);
        Optional<Map<String, Object>> result = workflowEngineClient.returnTask(
            taskId, targetActivityId, userId, request.getComment());
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to return task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to return task";
            throw new PortalException("500", message);
        }
        
        // 记录审计日志
        DelegationAudit audit = DelegationAudit.builder()
                .delegatorId(userId)
                .delegateId(targetActivityId)
                .taskId(taskId)
                .operationType("RETURN_TASK")
                .operationResult("SUCCESS")
                .operationDetail(request.getComment())
                .build();
        delegationAuditRepository.save(audit);
        
        log.info("Task {} returned via Flowable to activity {} by user {}", taskId, targetActivityId, userId);
    }

    /**
     * 检查用户是否在虚拟组中
     * 通过 WorkflowEngineClient 调用 workflow-engine-core 验证
     */
    private boolean isUserInVirtualGroup(String userId, String groupId) {
        if (!workflowEngineClient.isAvailable()) {
            log.warn("Workflow engine not available, cannot verify virtual group membership");
            return false;
        }
        try {
            // checkTaskPermission 的第一参数为 taskId，不可传入虚拟组 ID
            Optional<Map<String, Object>> permissions = workflowEngineClient.getUserTaskPermissions(userId);
            if (permissions.isPresent()) {
                @SuppressWarnings("unchecked")
                List<String> groupIds = (List<String>) permissions.get().get("virtualGroupIds");
                if (groupIds != null) {
                    return groupIds.contains(groupId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check virtual group membership: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 催办任务
     */
    @Transactional
    public void urgeTask(String taskId, String urgerId, String message) {
        TaskInfo task = getTaskOrThrow(taskId);

        // 验证催办人是否有权限（通常是流程发起人或管理员）
        if (!canUrgeTask(task, urgerId)) {
            throw new PortalException("403", "You do not have permission to urge this task");
        }

        // 获取任务处理人
        String assignee = task.getAssignee();
        String assigneeName = task.getAssigneeName();

        // 发送催办通知（实际应调用消息服务）
        String urgeMessage = message != null ? message : "Please process the task as soon as possible: " + task.getTaskName();
        sendUrgeNotification(taskId, assignee, urgerId, urgeMessage);

        // 记录催办日志
        DelegationAudit audit = DelegationAudit.builder()
                .delegatorId(urgerId)
                .delegateId(assignee)
                .taskId(taskId)
                .operationType("URGE_TASK")
                .operationResult("SUCCESS")
                .operationDetail(urgeMessage)
                .build();
        delegationAuditRepository.save(audit);

        log.info("User {} urged task {}, assignee: {}", urgerId, taskId, assignee);
    }

    /**
     * 批量催办任务
     */
    @Transactional
    public void batchUrgeTasks(List<String> taskIds, String urgerId, String message) {
        for (String taskId : taskIds) {
            try {
                urgeTask(taskId, urgerId, message);
            } catch (Exception e) {
                log.warn("Failed to urge task {}: {}", taskId, e.getMessage());
            }
        }
    }

    /**
     * 验证用户是否可以催办任务
     */
    private boolean canUrgeTask(TaskInfo task, String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (task.getInitiatorId() != null && userId.equals(task.getInitiatorId())) {
            return true;
        }
        return SecurityContextUtils.isSuperAdmin();
    }

    /**
     * 发送催办通知
     */
    private void sendUrgeNotification(String taskId, String assignee, String urgerId, String message) {
        // 实际应调用消息服务发送通知
        // 这里只记录日志
        log.info("Sending urge notification: task={}, assignee={}, urger={}, message={}", taskId, assignee, urgerId, message);
    }

    /**
     * 更新流程实例的当前处理人
     */
    private void updateProcessInstanceAssignee(String processInstanceId, String assignee, String currentNode) {
        if (processInstanceId == null) {
            return;
        }
        
        try {
            Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processInstanceId);
            if (optInstance.isPresent()) {
                ProcessInstance instance = optInstance.get();
                instance.setCurrentAssignee(assignee);
                if (currentNode != null) {
                    instance.setCurrentNode(currentNode);
                }
                processInstanceRepository.save(instance);
                log.info("Updated process instance {} with currentAssignee={}, currentNode={}", 
                        processInstanceId, assignee, currentNode);
            }
        } catch (Exception e) {
            log.warn("Failed to update process instance assignee: {}", e.getMessage());
        }
    }

    /**
     * 获取流程实例的当前活动节点
     */
    private Optional<Map<String, Object>> getCurrentActivity(String processInstanceId) {
        try {
            if (!workflowEngineClient.isAvailable()) {
                return Optional.empty();
            }
            
            // 调用 workflow-engine 获取当前活动节点
            return workflowEngineClient.getCurrentActivity(processInstanceId);
        } catch (Exception e) {
            log.warn("Failed to get current activity for process {}: {}", processInstanceId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 从 __subTables__ 中解析 participants 行列表：优先表名 {@code participants}，否则取第一个「像子表行」的 List（含 id/rowId/assignee 等）。
     */
    @SuppressWarnings("unchecked")
    private List<Object> resolveParticipantsRows(Map<String, Object> subTables) {
        Object named = subTables.get("participants");
        if (named instanceof List && !((List<?>) named).isEmpty()) {
            return (List<Object>) named;
        }
        for (Object v : subTables.values()) {
            if (!(v instanceof List<?> list) || list.isEmpty()) {
                continue;
            }
            Object first = list.get(0);
            if (first instanceof Map<?, ?> m) {
                if (m.containsKey("assignee_user_id") || m.containsKey("assigneeId")
                        || m.containsKey("id") || m.containsKey("rowId")) {
                    return (List<Object>) v;
                }
            }
        }
        return List.of();
    }

    /**
     * 从 __subTables__.participants 构建多实例集合变量
     * 每个元素包含 rowId 和 assignee_user_id，供多实例子流程使用
     */
    @SuppressWarnings("unchecked")
    private void buildParticipantsCollection(Map<String, Object> variables) {
        try {
            Object subTablesObj = variables.get("__subTables__");
            if (!(subTablesObj instanceof Map)) {
                log.warn("[MultiInstance] No __subTables__ found, setting empty participants collection");
                variables.put("multiInstance_participants_collection", List.of());
                return;
            }
            Map<String, Object> subTables = (Map<String, Object>) subTablesObj;
            // 设计器/脚本可能用表名 participants；门户前端常以 bindingId 为 key，需兼容两种结构
            List<Object> rows = resolveParticipantsRows(subTables);
            if (rows.isEmpty()) {
                log.warn("[MultiInstance] No participants sub-table rows found, setting empty collection");
                variables.put("multiInstance_participants_collection", List.of());
                return;
            }
            List<Map<String, Object>> collection = new java.util.ArrayList<>();
            for (Object rowObj : rows) {
                if (!(rowObj instanceof Map)) continue;
                Map<String, Object> row = (Map<String, Object>) rowObj;
                Map<String, Object> item = new HashMap<>();
                // rowId is used by the sub-process to identify which row to update
                Object rowId = row.get("rowId");
                if (rowId == null) rowId = row.get("id");
                item.put("rowId", rowId);
                item.put("assignee_user_id", row.get("assignee_user_id"));
                collection.add(item);
            }
            variables.put("multiInstance_participants_collection", collection);
            log.info("[MultiInstance] Built participants collection with {} items", collection.size());
        } catch (Exception e) {
            log.warn("[MultiInstance] Failed to build participants collection: {}", e.getMessage());
            variables.put("multiInstance_participants_collection", List.of());
        }
    }
}
