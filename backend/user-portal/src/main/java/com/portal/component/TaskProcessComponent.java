package com.portal.component;

import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import com.portal.dto.ChangeHistoryContext;
import com.portal.dto.SubTableChange;
import com.portal.entity.DelegationAudit;
import com.portal.entity.DelegationRule;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.DelegationAuditRepository;
import com.portal.repository.DelegationRuleRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.ProcessAssigneeSnapshot;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessException;

import jakarta.persistence.OptimisticLockException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private final ChangeHistoryComponent changeHistoryComponent;
    private final TaskFormComponent taskFormComponent;
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
        updateProcessInstanceAssignee(task.getProcessInstanceId(), userId, null, task.getTaskName());

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
        ProcessAssigneeSnapshot snapshot = ProcessAssigneeSnapshot.fromTaskInfo(task);
        updateProcessInstanceAssignee(
                task.getProcessInstanceId(),
                snapshot.getAssigneeUserId(),
                snapshot.getCandidateUserIds(),
                task.getTaskName());

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
        if (isTaskAlreadyClosedInEngineView(task)) {
            throw new PortalException("409",
                    "This task is no longer active (it may already be completed). Please refresh your todo list.");
        }

        // 验证用户是否有权限处理任务
        if (!canProcessTask(task, userId, portalUsername)) {
            throw new PortalException("403", "You do not have permission to process this task");
        }

        // 自动认领：虚拟组或 Flowable 候选人池任务且尚未有 assignee（排除「空池」孤儿任务，无 identity link 时 claim 必失败）
        boolean poolStyle = "VIRTUAL_GROUP".equals(task.getAssignmentType()) || "CANDIDATE_USERS".equals(task.getAssignmentType())
                || "DEPT_ROLE".equals(task.getAssignmentType());
        boolean noAssignee = task.getAssignee() == null || task.getAssignee().isEmpty();
        boolean poolAutoClaimed = false;
        if (poolStyle && noAssignee && !isEmptyAssignmentPool(task)) {
            log.info("Auto-claiming pool task {} (type {}) for user {}", taskId, task.getAssignmentType(), userId);
            poolAutoClaimed = true;
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
        updateProcessInstanceAssignee(task.getProcessInstanceId(), delegateId, null, task.getTaskName());
        
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
        updateProcessInstanceAssignee(task.getProcessInstanceId(), toUserId, null, task.getTaskName());

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
        return assignSubTableRow(taskId, rowId, null, assigneeId, userId, null);
    }

    @Transactional
    public Map<String, Object> assignSubTableRow(String taskId, Long rowId, String assigneeId, String userId,
                                                 String portalUsername) {
        return assignSubTableRow(taskId, rowId, null, assigneeId, userId, portalUsername);
    }

    @Transactional
    public Map<String, Object> assignSubTableRow(String taskId, Long rowId, Map<String, Object> rowKey, String assigneeId,
                                                 String userId, String portalUsername) {
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }

        TaskInfo task = getTaskOrThrow(taskId);
        if (isTaskAlreadyClosedInEngineView(task)) {
            throw new PortalException("409",
                    "This task is no longer active (it may already be completed). Please refresh your todo list.");
        }
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

        long pathRowId = rowId != null ? rowId : 0L;
        Optional<Map<String, Object>> result = workflowEngineClient.assignSubTableRow(taskId, pathRowId, assigneeId, rowKey);
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to assign sub-table row: " + taskId);
        }

        Map<String, Object> data = result.get();
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
        updateAssigneeDisplayName(participantTable, rowId, assigneeId);
        return assignSubTableRow(taskId, rowId, null, assigneeId, userId, portalUsername);
    }

    private Long resolveParticipantRowIdByIdentity(String participantTable, String email, String name, String department) {
        String em = email != null ? email.trim() : "";
        String nm = name != null ? name.trim() : "";
        String dept = department != null ? department.trim() : "";
        ensureParticipantsIdentityColumns(participantTable);

        try {
            if (!em.isBlank()) {
                List<Long> rows = jdbcTemplate.query(
                        "SELECT id FROM " + participantTable + " WHERE lower(trim(email)) = lower(trim(?)) ORDER BY id LIMIT 1",
                        (rs, i) -> rs.getLong("id"),
                        em);
                if (!rows.isEmpty()) {
                    return rows.get(0);
                }
            }
            if (!nm.isBlank() && !dept.isBlank()) {
                List<Long> rows = jdbcTemplate.query(
                        "SELECT id FROM " + participantTable + " WHERE lower(trim(name)) = lower(trim(?)) AND lower(trim(department)) = lower(trim(?)) ORDER BY id LIMIT 1",
                        (rs, i) -> rs.getLong("id"),
                        nm, dept);
                if (!rows.isEmpty()) {
                    return rows.get(0);
                }
            }
            if (!nm.isBlank()) {
                List<Long> rows = jdbcTemplate.query(
                        "SELECT id FROM " + participantTable + " WHERE lower(trim(name)) = lower(trim(?)) ORDER BY id LIMIT 1",
                        (rs, i) -> rs.getLong("id"),
                        nm);
                if (!rows.isEmpty()) {
                    return rows.get(0);
                }
            }
        } catch (Exception e) {
            log.debug("resolveParticipantRowIdByIdentity failed: {}", e.getMessage());
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
        if (meetingId == null && columnExists(participantTable, "meeting_id")) {
            meetingId = createMeetingRecordFromVariables(task, topic, location, organizerName);
        }
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
                    meetingId, nm, dept.isBlank() ? null : dept, em, assigneeId);
            return newId;
        } catch (Exception e) {
            log.warn("createParticipantRowIfMissing failed: meetingId={}, name={}, error={}", meetingId, nm, e.getMessage());
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
        return fromPersisted;
    }

    private Long createMeetingRecordFromVariables(TaskInfo task, String topic, String location, String organizerName) {
        Map<String, Object> vars = task != null ? task.getVariables() : null;
        String tp = topic != null ? topic.trim() : (vars != null ? toNonBlankString(vars.get("topic")) : null);
        String loc = location != null ? location.trim() : (vars != null ? toNonBlankString(vars.get("location")) : null);
        String org = organizerName != null ? organizerName.trim() : (vars != null ? toNonBlankString(vars.get("organizer_name")) : null);
        String mt = vars != null ? toNonBlankString(vars.get("meeting_time")) : null;
        String desc = vars != null ? toNonBlankString(vars.get("description")) : null;
        String initiator = vars != null ? toNonBlankString(vars.get("initiator")) : null;
        if (tp == null || tp.isBlank()) {
            return null;
        }
        String meetingTable = resolveTableAcrossSchemas("meeting");
        if (meetingTable == null) {
            return null;
        }
        try {
            java.sql.Timestamp meetingTimestamp = null;
            if (mt != null) {
                try {
                    meetingTimestamp = java.sql.Timestamp.valueOf(
                            java.time.LocalDateTime.parse(mt, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                } catch (Exception e1) {
                    try {
                        meetingTimestamp = java.sql.Timestamp.valueOf(mt);
                    } catch (Exception ignored) {
                        meetingTimestamp = java.sql.Timestamp.valueOf(java.time.LocalDateTime.now());
                    }
                }
            } else {
                meetingTimestamp = java.sql.Timestamp.valueOf(java.time.LocalDateTime.now());
            }
            Long newId = jdbcTemplate.queryForObject(
                    "INSERT INTO " + meetingTable +
                    " (topic, meeting_time, location, organizer_name, description, status, created_by, created_at, updated_at)" +
                    " VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id",
                    Long.class,
                    tp,
                    meetingTimestamp,
                    loc != null ? loc : "",
                    org != null ? org : "",
                    desc,
                    initiator);
            return newId;
        } catch (Exception e) {
            log.warn("createMeetingRecordFromVariables failed: {}", e.getMessage());
            return null;
        }
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

    private void updateAssigneeDisplayName(String participantTable, Long rowId, String assigneeId) {
        if (rowId == null || assigneeId == null || assigneeId.isBlank()) {
            return;
        }
        if (!columnExists(participantTable, "assignee_display_name")) {
            return;
        }
        try {
            List<String> names = jdbcTemplate.query(
                    "SELECT COALESCE(display_name, username) FROM sys_users WHERE id = ? LIMIT 1",
                    (rs, i) -> rs.getString(1),
                    assigneeId);
            String displayName = names.isEmpty() ? assigneeId : names.get(0);
            jdbcTemplate.update(
                    "UPDATE " + participantTable + " SET assignee_display_name = ? WHERE id = ?",
                    displayName, rowId);
        } catch (Exception e) {
            log.debug("updateAssigneeDisplayName failed: {}", e.getMessage());
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
                        fallbackMeetingId, nm, dept.isBlank() ? null : dept, em, assigneeId);
                return newIdWithMeeting;
            }
            Long newId = jdbcTemplate.queryForObject(
                    "INSERT INTO " + participantTable + " (name, department, email, assignee_user_id, sort_order) VALUES (?, ?, ?, ?, 0) RETURNING id",
                    Long.class,
                    nm, dept.isBlank() ? null : dept, em, assigneeId);
            return newId;
        } catch (Exception e) {
            String detail = String.valueOf(e.getMessage());
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            detail = detail.replace("\n", " ").replace("\r", " ");
            throw new PortalException("400", "Assignment failed: participant row create error: " + detail);
        }
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
     * {@link TaskQueryComponent#getTaskById} resolves through workflow-engine {@code getTaskInfo}, which may return a
     * <strong>historic</strong> row when no runtime execution exists ({@code status=COMPLETED}). Those rows must not
     * be completed again — Flowable runtime complete would yield "Task not found".
     */
    private static boolean isTaskAlreadyClosedInEngineView(TaskInfo task) {
        if (task == null) {
            return false;
        }
        if (task.getCompletedTime() != null) {
            return true;
        }
        String s = task.getStatus();
        if (s == null || s.isBlank()) {
            return false;
        }
        String u = s.trim().toUpperCase(Locale.ROOT);
        return "COMPLETED".equals(u) || "CANCELLED".equals(u) || "TERMINATED".equals(u);
    }

    private static boolean isEngineTaskInactiveMessage(String engineMessage) {
        if (engineMessage == null) {
            return false;
        }
        String m = engineMessage.trim();
        if (m.isEmpty()) {
            return false;
        }
        String low = m.toLowerCase(Locale.ROOT);
        return low.contains("task not found") || low.contains("task already completed");
    }

    /**
     * 获取任务或抛出异常。
     */
    private TaskInfo getTaskOrThrow(String taskId) {
        Optional<TaskInfo> first = taskQueryComponent.getTaskById(taskId);
        if (first.isPresent()) {
            return first.get();
        }
        Optional<TaskInfo> second = taskQueryComponent.getTaskById(taskId);
        if (second.isPresent()) {
            return second.get();
        }
        throw new PortalException("404", "Task not found: " + taskId);
    }

    /**
     * Generic catch blocks must not swallow exceptions that already poisoned the Spring transaction;
     * committing afterward surfaces as UnexpectedRollbackException.
     */
    private static boolean isTransactionRollbackOnly() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionAspectSupport.currentTransactionStatus().isRollbackOnly();
    }

    private void rethrowIfRollbackOnlyAfterCatch(Exception e, String taskId) {
        if (!isTransactionRollbackOnly()) {
            return;
        }
        if (e instanceof RuntimeException re) {
            throw re;
        }
        throw new PortalException("500",
                "Portal data could not be persisted (context: " + taskId + "); please retry or refresh.", e);
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

        // 完成审批时前端常只提交增量字段；__subTables__ 往往在 TaskInfo（本地 ProcessInstance 合并）里才有。
        // 不在此处合并则 injectMiCollectionFromBpmn 读不到子表行，多实例集合为空 → 子任务数为 0。
        mergeSubTablesFromTaskInfoForMi(task, variables);
        Object subTablesAfterMerge = variables.get("__subTables__");
        if (!(subTablesAfterMerge instanceof Map<?, ?> subMap) || subMap.isEmpty()) {
            log.warn("[MI] After TaskInfo merge, variables have no __subTables__ (taskId={}, processInstanceId={}). "
                    + "Multi-instance injection will not be able to build row collection.",
                    task.getTaskId(), task.getProcessInstanceId());
        }

        // 检测当前任务是否是多实例子流程的前置任务；若是，从 BPMN 读取 collection 变量名和 assignee 字段并构建集合变量
        injectMiCollectionFromBpmn(task.getProcessDefinitionKey(), task.getTaskDefinitionKey(), task.getProcessInstanceId(), variables);

        log.info("Variables before calling workflowEngineClient: {}", variables);
        
        Optional<Map<String, Object>> result = workflowEngineClient.completeTask(taskId, userId, action, variables);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to complete task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to complete task";
            if (isEngineTaskInactiveMessage(message)) {
                throw new PortalException("409",
                        "The task is no longer active in the workflow engine (completed, cancelled, or superseded). "
                                + "Please refresh your todo list.");
            }
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
                taskFormComponent.mergeCompletedTaskSnapshotIntoVariables(
                        taskId, userId, task.getTaskDefinitionKey(), mergedVars);
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
            rethrowIfRollbackOnlyAfterCatch(e, taskId);
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
            rethrowIfRollbackOnlyAfterCatch(e, taskId);
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
                    log.info("Process {} is completed after task completion, syncing portal process instance", processInstanceId);

                    // 更新流程实例状态
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
                    // 流程未完成，可能有下一个任务，尝试获取下一个任务信息
                    String nextTaskName = (String) status.get("nextTaskName");
                    String nextAssignee = (String) status.get("nextAssignee");
                    String nextCandidateUsers = (String) status.get("nextCandidateUsers");
                    if (nextTaskName != null) {
                        updateProcessInstanceAssignee(
                                processInstanceId, nextAssignee, nextCandidateUsers, nextTaskName);
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
            rethrowIfRollbackOnlyAfterCatch(e, taskId);
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
    private void updateProcessInstanceAssignee(String processInstanceId, String assigneeUserId,
                                               String candidateUserIds, String currentNode) {
        if (processInstanceId == null) {
            return;
        }

        try {
            Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processInstanceId);
            if (optInstance.isPresent()) {
                ProcessInstance instance = optInstance.get();
                instance.setCurrentAssignee(assigneeUserId);
                instance.setCandidateUsers(candidateUserIds);
                if (currentNode != null) {
                    instance.setCurrentNode(currentNode);
                }
                processInstanceRepository.save(instance);
                log.info("Updated process instance {} with currentAssignee={}, candidateUsers={}, currentNode={}",
                        processInstanceId, assigneeUserId, candidateUserIds, currentNode);
            }
        } catch (PortalException e) {
            throw e;
        } catch (DataAccessException e) {
            log.warn("Failed to update process instance assignee (data access) for {}: {}",
                    processInstanceId, e.getMessage());
            throw e;
        } catch (OptimisticLockException e) {
            log.warn("Failed to update process instance assignee (optimistic lock) for {}: {}",
                    processInstanceId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("Failed to update process instance assignee for {}: {}", processInstanceId, e.getMessage());
            rethrowIfRollbackOnlyAfterCatch(e,
                    "UA-RBONLY updateProcessInstanceAssignee " + processInstanceId);
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
     * 审批完成请求体往往不含完整 {@code __subTables__}；待办详情 {@link TaskInfo#getVariables()} 已与本地 ProcessInstance 合并，
     * 补齐后再构建多实例集合，否则会生成 0 个子任务。
     */
    @SuppressWarnings("unchecked")
    private void mergeSubTablesFromTaskInfoForMi(TaskInfo task, Map<String, Object> variables) {
        if (task == null || variables == null) {
            return;
        }
        Map<String, Object> taskVars = task.getVariables();
        if (taskVars == null || taskVars.isEmpty()) {
            return;
        }
        Object fromTask = taskVars.get("__subTables__");
        if (!(fromTask instanceof Map<?, ?> taskSubMap) || taskSubMap.isEmpty()) {
            return;
        }
        Object cur = variables.get("__subTables__");
        if (!(cur instanceof Map<?, ?>) || ((Map<?, ?>) cur).isEmpty()) {
            variables.put("__subTables__", new LinkedHashMap<>((Map<String, Object>) fromTask));
            log.info("[MI] Hydrated __subTables__ from TaskInfo for task {} (processInstanceId={})",
                    task.getTaskId(), task.getProcessInstanceId());
            return;
        }
        Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) cur);
        for (Map.Entry<?, ?> e : taskSubMap.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String k = String.valueOf(e.getKey());
            if (!merged.containsKey(k)) {
                merged.put(k, e.getValue());
            }
        }
        variables.put("__subTables__", merged);
    }

    /**
     * 设计器里 assignee 列字段名可能是 {@code assignee}，落库/变量行里常为 {@code assignee_user_id} 等；BPMN 仍用配置的 assigneeField。
     */
    private static final List<String> MI_ASSIGNEE_ALTERNATE_KEYS = List.of(
            "assignee_user_id", "assigneeUserId", "assignee_id", "assigneeId", "assignee", "user_id", "userId");

    private Object resolveMiAssigneeRaw(Map<String, Object> row, String configuredAssigneeField) {
        if (row == null || configuredAssigneeField == null || configuredAssigneeField.isBlank()) {
            return null;
        }
        Object direct = SubTableRowKeySupport.getRowValueIgnoreCase(row, configuredAssigneeField);
        if (direct != null && !String.valueOf(direct).trim().isEmpty()) {
            return direct;
        }
        String trimmed = configuredAssigneeField.trim();
        for (String alt : MI_ASSIGNEE_ALTERNATE_KEYS) {
            if (alt.equalsIgnoreCase(trimmed)) {
                continue;
            }
            Object v = SubTableRowKeySupport.getRowValueIgnoreCase(row, alt);
            if (v != null && !String.valueOf(v).trim().isEmpty()) {
                return v;
            }
        }
        return null;
    }

    /**
     * 检测当前完成的任务是否是多实例子流程的前置任务；若是，从 BPMN 动态读取 collection 变量名和 assignee 字段，
     * 从 __subTables__ 构建集合变量并注入到 variables 中。
     * <p>
     * 替代原有的硬编码 {@code Task_AssignParticipants} 判断，自动适配所有 BPMN 多实例配置。
     */
    @SuppressWarnings("unchecked")
    private void injectMiCollectionFromBpmn(String processDefinitionKey, String taskDefinitionKey,
                                            String processInstanceId, Map<String, Object> variables) {
        try {
            if (processDefinitionKey == null || processDefinitionKey.isBlank()
                    || taskDefinitionKey == null || taskDefinitionKey.isBlank()) {
                log.warn("[MI] Missing processDefinitionKey or taskDefinitionKey, skip collection injection (procDef={}, taskDef={})",
                        processDefinitionKey, taskDefinitionKey);
                return;
            }
            log.info("[MI] injectMiCollectionFromBpmn begin processDefinitionKey={} taskDefinitionKey={} processInstanceId={}",
                    processDefinitionKey, taskDefinitionKey, processInstanceId);
            Optional<String> bpmnOpt = workflowEngineClient.getBpmnXml(processDefinitionKey);
            if (bpmnOpt.isEmpty()) {
                log.warn("[MI] Could not fetch BPMN XML for processDefinitionKey={}", processDefinitionKey);
                return;
            }
            Document document = parseBpmnSecurely(bpmnOpt.get());

            // 1. 找到当前任务节点（兼容 bpmn:userTask / userTask）
            Element taskElement = findElementByLocalNameAndId(document, "userTask", taskDefinitionKey);
            if (taskElement == null) {
                log.warn("[MI] UserTask id={} not found in BPMN (check taskDefinitionKey vs XML). Skip MI injection.",
                        taskDefinitionKey);
                return;
            }

            // 2. 出线：许多导出的 BPMN 只有 sequenceFlow@sourceRef，没有 userTask 下 <outgoing> 子元素
            List<String> outgoingFlowIds = getDirectChildTextValues(taskElement, "outgoing");
            if (outgoingFlowIds.isEmpty()) {
                outgoingFlowIds = listSequenceFlowIdsWithSourceRef(document, taskDefinitionKey);
                if (!outgoingFlowIds.isEmpty()) {
                    log.info("[MI] Task {} has no <outgoing> children; using {} sequenceFlow(s) via sourceRef",
                            taskDefinitionKey, outgoingFlowIds.size());
                }
            }
            if (outgoingFlowIds.isEmpty()) {
                log.warn("[MI] No outgoing from userTask {} (no child <outgoing> and no sequenceFlow with matching sourceRef). Skip MI injection.",
                        taskDefinitionKey);
                return;
            }

            Deque<String> frontier = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            for (String flowId : outgoingFlowIds) {
                enqueueSequenceFlowTargets(document, flowId, frontier);
            }

            while (!frontier.isEmpty()) {
                String nodeId = frontier.poll();
                if (nodeId == null || nodeId.isBlank()) {
                    continue;
                }
                if (!visited.add(nodeId)) {
                    continue;
                }

                Element subProcess = findElementByLocalNameAndId(document, "subProcess", nodeId);
                if (subProcess != null) {
                    Element loopCharacteristics = findMultiInstanceLoopInSubProcess(subProcess);
                    if (loopCharacteristics != null) {
                        String collectionVariableName = extractFlowableCollection(loopCharacteristics);
                        if (collectionVariableName == null || collectionVariableName.isBlank()) {
                            log.warn("[MI] SubProcess {} has no flowable:collection configuration", nodeId);
                            continue;
                        }
                        String assigneeField = extractAssigneeFieldFromSubProcess(subProcess);
                        if (assigneeField == null || assigneeField.isBlank()) {
                            log.warn("[MI] No assigneeField found in subProcess {} inner UserTask", nodeId);
                            continue;
                        }
                        String bpmnSubTableName = findFirstPropertyValue(subProcess, "subTableName");
                        buildMiCollectionVariable(variables, collectionVariableName, assigneeField, bpmnSubTableName);
                        return;
                    }
                    List<String> spOut = getDirectChildTextValues(subProcess, "outgoing");
                    for (String outFlow : spOut) {
                        enqueueSequenceFlowTargets(document, outFlow, frontier);
                    }
                    if (spOut.isEmpty()) {
                        for (String sfId : listSequenceFlowIdsWithSourceRef(document, nodeId)) {
                            enqueueSequenceFlowTargets(document, sfId, frontier);
                        }
                    }
                    continue;
                }

                Element flowNode = findElementByBpmnId(document, nodeId);
                if (flowNode != null) {
                    List<String> outs = getDirectChildTextValues(flowNode, "outgoing");
                    if (outs.isEmpty()) {
                        outs = listSequenceFlowIdsWithSourceRef(document, nodeId);
                    }
                    for (String outFlow : outs) {
                        enqueueSequenceFlowTargets(document, outFlow, frontier);
                    }
                }
            }

            log.warn("[MI] No reachable multi-instance subProcess found from task {} (BFS exhausted). Skip MI injection.",
                    taskDefinitionKey);
        } catch (Exception e) {
            log.warn("[MI] injectMiCollectionFromBpmn failed for processDefinitionKey={}, taskDefinitionKey={}: {}",
                    processDefinitionKey, taskDefinitionKey, e.getMessage());
        }
    }

    /**
     * 从 __subTables__ 构建多实例集合变量。
     * collectionVariableName 常为 {@code multiInstance_{subTableName}_collection}；主键优先从 PG / 设计器元数据解析。
     * 纯 JSON 子表（无物理表）时用 {@code dw_table_definitions} 模糊匹配表名；仍失败时对 {@code __subTables__} 推断单列 {@code id}。
     * <p>
     * __subTables__ 中常有多个 binding 列表；若简单扁平合并，则凡是能凑齐目标表主键列且带有 assignee 的行都会被当成多实例元素
     * （例如多个子表都有列 {@code id}），会在完成前置任务后创建远多于预期的子任务。此处对每个 map 值的列表单独打分，
     * 只采用与目标表主键 + assignee 最匹配的来源列表（并列则合并并去重）。
     */
    @SuppressWarnings("unchecked")
    private void buildMiCollectionVariable(Map<String, Object> variables, String collectionVariableName,
                                          String assigneeField, String bpmnSubTableName) {
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map)) {
            log.warn("[MI] No __subTables__ found, setting empty collection for {}", collectionVariableName);
            variables.put(collectionVariableName, List.of());
            return;
        }
        Map<String, Object> subTables = (Map<String, Object>) subTablesObj;

        String tokenFromCollectionVar = parseSubTableNameFromMiCollectionVariable(collectionVariableName);
        MiSubTablePkResult pkResult = resolveMiSubTablePk(tokenFromCollectionVar);
        if (pkResult == null && bpmnSubTableName != null && !bpmnSubTableName.isBlank()) {
            String trimmed = bpmnSubTableName.trim();
            if (tokenFromCollectionVar == null || !trimmed.equalsIgnoreCase(tokenFromCollectionVar)) {
                pkResult = resolveMiSubTablePk(trimmed);
            }
        }
        if ((pkResult == null || pkResult.pkCols() == null || pkResult.pkCols().isEmpty())
                && tokenFromCollectionVar == null
                && collectionVariableName != null
                && collectionVariableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            pkResult = resolveMiSubTablePk(collectionVariableName.trim());
        }
        if ((pkResult == null || pkResult.pkCols() == null || pkResult.pkCols().isEmpty())) {
            pkResult = inferMiPkFromJsonSubTables(subTables, assigneeField, collectionVariableName);
        }

        if (pkResult == null || pkResult.pkCols() == null || pkResult.pkCols().isEmpty()) {
            log.warn(
                    "[MI] Cannot resolve primary key for multi-instance collection '{}' (parsed token='{}', bpmnSubTableName='{}'). "
                            + "No PG table match, designer metadata match, or inferable JSON id column. Setting empty collection.",
                    collectionVariableName,
                    tokenFromCollectionVar,
                    bpmnSubTableName);
            variables.put(collectionVariableName, List.of());
            return;
        }
        List<String> pkCols = pkResult.pkCols();

        List<Map<String, Object>> allRows = selectRowsForMiCollection(subTables, pkCols, assigneeField);
        if (allRows.isEmpty()) {
            log.warn(
                    "[MI] No eligible sub-table rows for '{}' (resolvedTable={}, pk={}, assigneeField='{}'); setting empty collection",
                    collectionVariableName,
                    pkResult.resolvedTable(),
                    pkCols,
                    assigneeField);
            variables.put(collectionVariableName, List.of());
            return;
        }

        List<Map<String, Object>> collection = new ArrayList<>();
        List<Integer> emptyAssigneeRows = new ArrayList<>();
        Set<String> seenRowKeys = new LinkedHashSet<>();
        int skippedUnmappedPk = 0;
        for (int i = 0; i < allRows.size(); i++) {
            Map<String, Object> row = allRows.get(i);
            Map<String, Object> rowKey = SubTableRowKeySupport.rowKeyFromVariableRow(row, pkCols);
            if (rowKey == null) {
                skippedUnmappedPk++;
                log.warn(
                        "[MI] Row {} omitted from '{}': sub-table row does not contain values for primary key columns {} (available keys: {})",
                        i + 1,
                        collectionVariableName,
                        pkCols,
                        row.keySet());
                continue;
            }
            Object rowId = null;
            if (pkCols.size() == 1) {
                rowId = rowKey.get(pkCols.get(0));
            }
            Object assigneeValue = resolveMiAssigneeRaw(row, assigneeField);
            String assigneeText = assigneeValue != null ? String.valueOf(assigneeValue).trim() : "";
            if (assigneeText.isEmpty()) {
                emptyAssigneeRows.add(i + 1);
                continue;
            }
            String dedupKey = SubTableRowKeySupport.canonicalRowKeyString(pkCols, rowKey);
            if (!seenRowKeys.add(dedupKey)) {
                log.debug("[MI] Duplicate row identity skipped for collection {}: {}", collectionVariableName, dedupKey);
                continue;
            }

            Map<String, Object> item = new HashMap<>();
            item.put("rowKey", new LinkedHashMap<>(rowKey));
            if (rowId instanceof Number) {
                item.put("rowId", ((Number) rowId).longValue());
            } else if (rowId != null && pkCols.size() == 1) {
                item.put("rowId", rowId);
            }
            item.put(assigneeField, assigneeText);

            collection.add(item);
        }

        if (skippedUnmappedPk > 0) {
            log.warn("[MI] {} row(s) omitted from '{}' because PK values were missing in form data; collection size={}",
                    skippedUnmappedPk, collectionVariableName, collection.size());
        }

        if (!emptyAssigneeRows.isEmpty()) {
            String rowNumbers = String.join(", ",
                    emptyAssigneeRows.stream().map(String::valueOf).toArray(String[]::new));
            log.warn("[MI] Rows {} have empty assigneeField '{}' for collection {}", rowNumbers, assigneeField, collectionVariableName);
        }

        variables.put(collectionVariableName, collection);
        log.info("[MI] Built collection '{}' with {} items, assigneeField='{}', collectionVarMiddleToken='{}', resolvedTable='{}'",
                collectionVariableName, collection.size(), assigneeField, tokenFromCollectionVar, pkResult.resolvedTable());
    }

    /**
     * Result of resolving MI row identity: Postgres table id, designer table_name, or JSON-inferred sentinel.
     */
    private record MiSubTablePkResult(String resolvedTable, List<String> pkCols) {
    }

    /**
     * 将 BPMN 集合变量里的逻辑段（如 {@code participants}）解析为主键列名。
     * 顺序：物理表精确/模糊 →{@code dw_table_definitions} 模糊（纯 JSON 子表常见于仅有设计器元数据而无 PG 表）。
     */
    private MiSubTablePkResult resolveMiSubTablePk(String middleSegment) {
        if (middleSegment == null || middleSegment.isBlank()) {
            return null;
        }
        String token = middleSegment.trim();
        if (!token.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return null;
        }

        try {
            List<String> pk = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, token);
            return new MiSubTablePkResult(token, pk);
        } catch (Exception ignored) {
        }

        try {
            List<String> names = jdbcTemplate.queryForList(
                    """
                            SELECT table_name FROM information_schema.tables
                            WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'
                              AND lower(table_name) = lower(?)
                            LIMIT 1
                            """,
                    String.class,
                    token);
            if (!names.isEmpty()) {
                String physical = names.get(0);
                List<String> pk = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, physical);
                if (!physical.equals(token)) {
                    log.info("[MI] Resolved MI sub-table token '{}' to physical table '{}' (case variant)", token, physical);
                }
                return new MiSubTablePkResult(physical, pk);
            }
        } catch (Exception e) {
            log.debug("[MI] Case-insensitive exact match failed for token={}: {}", token, e.getMessage());
        }

        if (token.length() < 4) {
            log.debug("[MI] Skip fuzzy table search for very short token '{}'", token);
            return null;
        }

        try {
            List<String> names = jdbcTemplate.queryForList(
                    """
                            SELECT table_name FROM information_schema.tables
                            WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'
                              AND (
                                lower(table_name) LIKE '%' || lower(?)
                                OR lower(table_name) LIKE lower(?) || '%'
                              )
                            ORDER BY
                              CASE WHEN lower(table_name) = lower(?) THEN 0 ELSE 1 END,
                              length(table_name) ASC
                            LIMIT 24
                            """,
                    String.class,
                    token,
                    token,
                    token);
            for (String physical : names) {
                try {
                    List<String> pk = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, physical);
                    log.info("[MI] Resolved MI sub-table token '{}' to physical table '{}' (fuzzy schema match)", token, physical);
                    return new MiSubTablePkResult(physical, pk);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.debug("[MI] Fuzzy table search failed for token={}: {}", token, e.getMessage());
        }

        if (token.length() >= 4) {
            try {
                List<String> designerNames = jdbcTemplate.query(
                        """
                                SELECT td.table_name
                                FROM dw_table_definitions td
                                WHERE lower(td.table_name) LIKE '%' || lower(?) || '%'
                                   OR lower(?) LIKE '%' || lower(td.table_name) || '%'
                                ORDER BY
                                  CASE WHEN lower(td.table_name) = lower(?) THEN 0 ELSE 1 END,
                                  length(td.table_name) ASC,
                                  td.id DESC
                                LIMIT 24
                                """,
                        (rs, i) -> rs.getString(1),
                        token,
                        token,
                        token);
                Set<String> tried = new HashSet<>();
                for (String designerTable : designerNames) {
                    if (designerTable == null || !tried.add(designerTable.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    try {
                        List<String> pk = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, designerTable);
                        log.info("[MI] Resolved MI token '{}' to designer table '{}' (dw_table_definitions / no physical table required)",
                                token, designerTable);
                        return new MiSubTablePkResult(designerTable, pk);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception e) {
                log.debug("[MI] Designer metadata fuzzy match failed for token={}: {}", token, e.getMessage());
            }
        }

        return null;
    }

    /**
     * 当物理表与设计器表名均无法匹配时：若 {@code __subTables__} 行带非空 {@code id} 与 assignee，则按单列 id 作为行主键（JSON 存储子表）。
     */
    private MiSubTablePkResult inferMiPkFromJsonSubTables(Map<String, Object> subTables, String assigneeField,
                                                          String collectionVariableName) {
        if (subTables == null || subTables.isEmpty() || assigneeField == null || assigneeField.isBlank()) {
            return null;
        }
        List<String> idPk = List.of("id");
        List<Map<String, Object>> rows = selectRowsForMiCollection(subTables, idPk, assigneeField);
        if (rows.isEmpty()) {
            log.debug("[MI] JSON id inference found no eligible rows for collection '{}'", collectionVariableName);
            return null;
        }
        log.info("[MI] Inferred PK [id] for '{}': {} eligible JSON sub-table row(s)", collectionVariableName, rows.size());
        return new MiSubTablePkResult("__json_id__", idPk);
    }

    /**
     * 从 __subTables__ 的多个列表中选出最可能属于当前多实例物理表的数据源，避免跨子表扁平化导致实例数爆炸。
     */
    private List<Map<String, Object>> selectRowsForMiCollection(Map<String, Object> subTables,
                                                                List<String> pkCols,
                                                                String assigneeField) {
        if (subTables == null || subTables.isEmpty() || pkCols == null || pkCols.isEmpty()) {
            return List.of();
        }
        int bestScore = -1;
        int bestTotalSize = Integer.MAX_VALUE;
        List<List<Map<String, Object>>> bestLists = new ArrayList<>();
        for (Object v : subTables.values()) {
            if (!(v instanceof List<?> rawList)) {
                continue;
            }
            List<Map<String, Object>> typed = new ArrayList<>();
            for (Object rowObj : rawList) {
                if (rowObj instanceof Map<?, ?> m) {
                    typed.add((Map<String, Object>) m);
                }
            }
            int score = scoreRowsEligibleForMi(typed, pkCols, assigneeField);
            if (score <= 0) {
                continue;
            }
            int totalSize = typed.size();
            if (score > bestScore) {
                bestScore = score;
                bestTotalSize = totalSize;
                bestLists.clear();
                bestLists.add(typed);
            } else if (score == bestScore) {
                if (totalSize < bestTotalSize) {
                    bestTotalSize = totalSize;
                    bestLists.clear();
                    bestLists.add(typed);
                } else if (totalSize == bestTotalSize) {
                    bestLists.add(typed);
                }
            }
        }
        if (bestScore <= 0) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> merged = new ArrayList<>();
        for (List<Map<String, Object>> lst : bestLists) {
            for (Map<String, Object> row : lst) {
                Map<String, Object> rowKey = SubTableRowKeySupport.rowKeyFromVariableRow(row, pkCols);
                if (rowKey == null) {
                    continue;
                }
                Object assigneeValue = resolveMiAssigneeRaw(row, assigneeField);
                if (assigneeValue == null || String.valueOf(assigneeValue).trim().isEmpty()) {
                    continue;
                }
                String dedup = SubTableRowKeySupport.canonicalRowKeyString(pkCols, rowKey);
                if (dedup.isEmpty() || !seen.add(dedup)) {
                    continue;
                }
                merged.add(row);
            }
        }
        return merged;
    }

    private int scoreRowsEligibleForMi(List<Map<String, Object>> rows, List<String> pkCols, String assigneeField) {
        int n = 0;
        for (Map<String, Object> row : rows) {
            if (SubTableRowKeySupport.rowKeyFromVariableRow(row, pkCols) == null) {
                continue;
            }
            Object assigneeValue = resolveMiAssigneeRaw(row, assigneeField);
            if (assigneeValue == null || String.valueOf(assigneeValue).trim().isEmpty()) {
                continue;
            }
            n++;
        }
        return n;
    }

    /**
     * {@code multiInstance_{subTableName}_collection} → physical sub-table name.
     */
    private static String parseSubTableNameFromMiCollectionVariable(String collectionVariableName) {
        if (collectionVariableName == null
                || !collectionVariableName.startsWith("multiInstance_")
                || !collectionVariableName.endsWith("_collection")) {
            return null;
        }
        return collectionVariableName.substring(
                "multiInstance_".length(),
                collectionVariableName.length() - "_collection".length());
    }

    private void enqueueSequenceFlowTargets(Document document, String flowId, Deque<String> frontier) {
        if (document == null || flowId == null || flowId.isBlank()) {
            return;
        }
        Element sequenceFlow = findElementByLocalNameAndId(document, "sequenceFlow", flowId);
        if (sequenceFlow == null) {
            sequenceFlow = findElementByBpmnId(document, flowId);
        }
        if (sequenceFlow == null) {
            log.debug("[MI] sequenceFlow id={} not found", flowId);
            return;
        }
        String targetRef = sequenceFlow.getAttribute("targetRef");
        if (targetRef != null && !targetRef.isBlank()) {
            frontier.add(targetRef);
        }
    }

    private static Element findElementByBpmnId(Document document, String id) {
        if (document == null || id == null || id.isBlank()) {
            return null;
        }
        NodeList nodes = document.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element e && id.equals(e.getAttribute("id"))) {
                return e;
            }
        }
        return null;
    }

    // ==================== BPMN XML 解析辅助方法 ====================

    private Document parseBpmnSecurely(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private Element findElementByLocalNameAndId(Document document, String localName, String id) {
        if (document == null || localName == null || id == null) {
            return null;
        }
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && id.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    private Element firstDirectChild(Element parent, String localName) {
        if (parent == null || localName == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    private List<String> getDirectChildTextValues(Element parent, String localName) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        if (parent == null || localName == null) {
            return values;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                String text = element.getTextContent();
                if (text != null && !text.isBlank()) {
                    values.add(text.trim());
                }
            }
        }
        return values;
    }

    /**
     * BPMN 中出线常用 sequenceFlow 的 sourceRef 指向活动 id，而无须 userTask 下显式 &lt;outgoing&gt;。
     */
    private List<String> listSequenceFlowIdsWithSourceRef(Document document, String sourceActivityId) {
        List<String> ids = new ArrayList<>();
        if (document == null || sourceActivityId == null || sourceActivityId.isBlank()) {
            return ids;
        }
        NodeList flows = document.getElementsByTagNameNS("*", "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Node n = flows.item(i);
            if (n instanceof Element e && sourceActivityId.equals(e.getAttribute("sourceRef"))) {
                String fid = e.getAttribute("id");
                if (fid != null && !fid.isBlank()) {
                    ids.add(fid);
                }
            }
        }
        return ids;
    }

    /** multiInstanceLoopCharacteristics 在部分导出中不是 subProcess 的第一个直接子节点。 */
    private Element findMultiInstanceLoopInSubProcess(Element subProcess) {
        if (subProcess == null) {
            return null;
        }
        Element direct = firstDirectChild(subProcess, "multiInstanceLoopCharacteristics");
        if (direct != null) {
            return direct;
        }
        NodeList list = subProcess.getElementsByTagNameNS("*", "multiInstanceLoopCharacteristics");
        if (list.getLength() > 0 && list.item(0) instanceof Element e) {
            return e;
        }
        return null;
    }

    private String findFirstPropertyValue(Element root, String propertyName) {
        if (root == null || propertyName == null) {
            return null;
        }
        NodeList nodes = root.getElementsByTagNameNS("*", "property");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element
                    && propertyName.equals(element.getAttribute("name"))) {
                String value = element.getAttribute("value");
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private int findElementStart(String xml, String elementName, String attrName, String attrValue) {
        String openTag = "<" + elementName + " ";
        int pos = 0;
        while (true) {
            int idx = xml.indexOf(openTag, pos);
            if (idx < 0) return -1;
            int tagEnd = xml.indexOf('>', idx);
            if (tagEnd < 0) return -1;
            String tag = xml.substring(idx, tagEnd + 1);
            if (extractAttributeFromTag(tag, attrName, attrValue) != null) {
                return idx;
            }
            pos = idx + 1;
        }
    }

    private int findElementEnd(String xml, int start) {
        int depth = 1;
        int pos = start + 1;
        while (depth > 0 && pos < xml.length()) {
            int nextOpen = xml.indexOf('<', pos);
            int nextClose = xml.indexOf("</", pos);
            if (nextClose >= 0 && (nextOpen < 0 || nextClose <= nextOpen)) {
                if (nextClose == start) break;
                depth--;
                pos = nextClose + 2;
            } else if (nextOpen >= 0) {
                String nsTag = xml.substring(nextOpen, Math.min(nextOpen + elementName(xml, nextOpen).length() + 2, xml.length()));
                depth++;
                pos = nextOpen + 1;
            } else {
                break;
            }
        }
        int closeTag = xml.indexOf('>', pos);
        return closeTag > 0 ? closeTag + 1 : pos;
    }

    private String elementName(String xml, int openTagPos) {
        int end = openTagPos + 1;
        while (end < xml.length() && !Character.isWhitespace(xml.charAt(end)) && xml.charAt(end) != '>') {
            end++;
        }
        return xml.substring(openTagPos + 1, end);
    }

    private String extractAttributeFromTag(String tag, String attrName, String expectedValue) {
        int pos = 0;
        while (pos < tag.length()) {
            while (pos < tag.length() && Character.isWhitespace(tag.charAt(pos))) pos++;
            if (pos >= tag.length()) break;
            int eq = tag.indexOf('=', pos);
            if (eq < 0) break;
            String name = tag.substring(pos, eq).trim();
            pos = eq + 1;
            while (pos < tag.length() && Character.isWhitespace(tag.charAt(pos))) pos++;
            if (pos >= tag.length()) break;
            char quote = tag.charAt(pos);
            if (quote == '"' || quote == '\'') {
                pos++;
                int end = tag.indexOf(quote, pos);
                if (end < 0) break;
                String value = tag.substring(pos, end);
                pos = end + 1;
                if (name.equals(attrName) && value.equals(expectedValue)) {
                    return value;
                }
            } else {
                break;
            }
        }
        return null;
    }

    private String extractAttribute(String element, String childElement, String attrName) {
        int childStart = element.indexOf("<" + childElement + " ");
        if (childStart < 0) return null;
        int tagEnd = element.indexOf('>', childStart);
        if (tagEnd < 0) return null;
        String tag = element.substring(childStart, tagEnd);
        return extractAttributeFromTag(tag, attrName, null);
    }

    private String extractAttributeMultiline(String element, String childElement, String attrName) {
        int childStart = element.indexOf("<" + childElement);
        if (childStart < 0) return null;
        int closeTag = element.indexOf("</" + childElement + ">", childStart);
        if (closeTag < 0) return null;
        String inner = element.substring(childStart, closeTag);
        return extractAttributeFromTag(inner, attrName, null);
    }

    private String extractFlowableCollection(Element loopCharacteristics) {
        if (loopCharacteristics == null) {
            return null;
        }

        String collection = loopCharacteristics.getAttributeNS("http://flowable.org/bpmn", "collection");
        if (collection == null || collection.isBlank()) {
            collection = loopCharacteristics.getAttribute("flowable:collection");
        }
        if (collection == null || collection.isBlank()) {
            collection = loopCharacteristics.getAttribute("collection");
        }
        if (collection != null && !collection.isBlank()) {
            return collection.trim();
        }

        NodeList children = loopCharacteristics.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && "collection".equals(element.getLocalName())) {
                String text = element.getTextContent();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private String extractAssigneeFieldFromSubProcess(Element subProcessElement) {
        return findFirstPropertyValue(subProcessElement, "assigneeField");
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

        // Build row lookup maps by row id
        Map<Object, Map<String, Object>> oldRowMap = new HashMap<>();
        for (Map<String, Object> row : oldRows) {
            Object rowId = row.get("id");
            if (rowId != null) {
                oldRowMap.put(rowId, row);
            }
        }
        Map<Object, Map<String, Object>> newRowMap = new HashMap<>();
        for (Map<String, Object> row : newRows) {
            Object rowId = row.get("id");
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
