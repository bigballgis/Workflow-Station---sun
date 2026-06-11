package com.portal.component;

import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.portal.client.WorkflowEngineClient;
import com.portal.util.SubTableNestingSanitizer;
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
import java.util.Comparator;
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
 * Task processing component
 * Supports claim, complete, transfer, delegate, and related operations
 * 
 * Uses WorkflowEngineClient to call Flowable engine
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
     * Claims task
     * Via WorkflowEngineClient calling Flowable engine
     */
    @Transactional
    public TaskInfo claimTask(String taskId, String userId) {
        return claimTask(taskId, userId, null);
    }

    /**
     * Claims task when JWT userId differs from Flowable assignee/candidate username.
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
        
        // Task updated in Flowable; reload latest state
        TaskInfo task = getTaskOrThrow(taskId);
        
        // Update process instance current assignee (portal stores JWT userId)
        updateProcessInstanceAssignee(task.getProcessInstanceId(), userId, null, task.getTaskName());

        log.info("Task {} claimed via Flowable by user {}", taskId, userId);
        return task;
    }

    /**
     * Unclaims task
     * Via WorkflowEngineClient calling Flowable engine
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
        
        // Task updated in Flowable; reload latest state
        TaskInfo task = getTaskOrThrow(taskId);
        
        // After unclaim, restore process instance assignee from task snapshot
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
     * Completes task
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

        // Verify user may process task
        if (!canProcessTask(task, userId, portalUsername)) {
            throw new PortalException("403", "You do not have permission to process this task");
        }

        // Auto-claim: virtual group or candidate pool without assignee (skip empty pool; claim fails without identity links)
        boolean poolStyle = "VIRTUAL_GROUP".equals(task.getAssignmentType()) || "CANDIDATE_USERS".equals(task.getAssignmentType())
                || "DEPT_ROLE".equals(task.getAssignmentType());
        boolean noAssignee = task.getAssignee() == null || task.getAssignee().isEmpty();
        boolean poolAutoClaimed = false;
        if (poolStyle && noAssignee && !isEmptyAssignmentPool(task)) {
            log.info("Auto-claiming pool task {} (type {}) for user {}", taskId, task.getAssignmentType(), userId);
            poolAutoClaimed = true;
            claimTask(taskId, userId, portalUsername);
            task = getTaskOrThrow(taskId); // Refresh task after claim
        } else if (poolStyle && noAssignee && isEmptyAssignmentPool(task)) {
            log.info("Skipping auto-claim for empty-pool task {} (no assignee/target/candidates); completing without claim", taskId);
        }

        String action = request.getAction();
        switch (action) {
            case "APPROVE", "REJECT" -> handleApproval(task, request, userId);
            case "TRANSFER" -> handleTransfer(task, request, userId);
            case "DELEGATE" -> handleDelegate(task, request, userId);
            case "RETURN" -> handleReturn(task, request, userId, "RETURN");
            case "DRAFT" -> handleReturn(task, request, userId, "DRAFT");
            default -> throw new PortalException("400", "Unsupported action type: " + action);
        }
    }

    /**
     * Delegates task
     * Via WorkflowEngineClient calling Flowable engine
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
        
        // Update process instance current assignee
        TaskInfo task = getTaskOrThrow(taskId);
        updateProcessInstanceAssignee(task.getProcessInstanceId(), delegateId, null, task.getTaskName());
        
        // Record audit log
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
     * Transfers task
     * Via WorkflowEngineClient calling Flowable engine
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
        
        // Update process instance current assignee
        TaskInfo task = getTaskOrThrow(taskId);
        updateProcessInstanceAssignee(task.getProcessInstanceId(), toUserId, null, task.getTaskName());

        // Record audit log
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
     * Assigns sub-table row processor (MI sub-process prerequisite) via {@link WorkflowEngineClient}.
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
            // Engine AssignSubTableRowResponse failure in errorMessage; ApiResponse errors in message / error.message
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
     * Runtime empty pool: no assignee, candidates, groups, or assignmentTarget (matches Flowable identity links); do not tighten by assignmentType.
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
     * Compare JWT and engine user IDs with trim to avoid whitespace false negatives.
     */
    private static boolean samePortalUserId(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equals(b.trim());
    }

    /**
     * Engine assignee/candidates may be username; JWT {@code userId} is primary-key UUID.
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
     * Claim/unclaim must pass the same string as Flowable IdentityLink (candidates often username).
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
     * Whether the user may claim the task
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
     * Whether the user may process the task
     */
    public boolean canProcessTask(TaskInfo task, String userId) {
        return canProcessTask(task, userId, null);
    }

    public boolean canProcessTask(TaskInfo task, String userId, String portalUsername) {
        String assignmentType = task.getAssignmentType();
        String assignee = task.getAssignee();

        // Allow when assignee matches current user (including after claim)
        if (assignee != null && matchesPortalIdentity(assignee, userId, portalUsername)) {
            return true;
        }

        // Direct user assignment
        if ("USER".equals(assignmentType) && assignee != null && matchesPortalIdentity(assignee, userId, portalUsername)) {
            return true;
        }

        // Delegated task
        if ("DELEGATED".equals(assignmentType) && assignee != null && matchesPortalIdentity(assignee, userId, portalUsername)) {
            return true;
        }

        // Flowable candidate pool: user must be in candidate list
        if ("CANDIDATE_USERS".equals(assignmentType)) {
            return candidateUserIdsContain(task.getCandidateUserIds(), userId, portalUsername);
        }

        // Entity manager task (ENTITY_MANAGER)
        if ("ENTITY_MANAGER".equals(assignmentType)) {
            log.info("Entity manager task {} for user {}, allowing process (permission verified by query)", task.getTaskId(), userId);
            return true;
        }

        // Virtual group: prove membership (assignee is group ID or engine candidateGroupIds)
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

        // Check delegation rules
        if (assignee != null) {
            List<DelegationRule> delegations = delegationRuleRepository
                    .findActiveDelegationsForDelegate(userId, LocalDateTime.now());
            for (DelegationRule delegation : delegations) {
                if (samePortalUserId(assignee, delegation.getDelegatorId())) {
                    return true;
                }
            }
        }

        // Empty pool (no assignee/candidates/groups/target): allow initiator only when BPMN is initiator task;
        // BU_ROLE / HIERARCHY nodes that look empty must not appear on initiator todo.
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
     * Empty-pool initiator fallback: only when BPMN explicitly marks initiator.
     * Without assigneeType or on later nodes (BU_ROLE), initiator must not see empty-pool tasks unless BPMN marks INITIATOR.
     */
    private static boolean allowsInitiatorEmptyPoolFallback(String bpmnAssigneeType) {
        if (bpmnAssigneeType == null || bpmnAssigneeType.isBlank()) {
            return false;
        }
        String u = bpmnAssigneeType.trim().toUpperCase(Locale.ROOT);
        return "INITIATOR".equals(u) || "PROCESS_INITIATOR".equals(u);
    }

    /**
     * Todo-list filter: hide initiator on empty pool when BPMN is not INITIATOR/PROCESS_INITIATOR (e.g. mis-shown BU_ROLE).
     * <p>Do not filter the whole list with {@link #canProcessTask}: engine already aggregates assignee/candidates/groups; re-filter risks candidate ID (UUID vs username)
     * JWT mismatch can hide valid processors (e.g. BU_ROLE pool members) from the todo list.</p>
     */
    public boolean shouldHideTaskInTodoList(TaskInfo task, String userId, String portalUsername) {
        if (!isEmptyAssignmentPool(task) || !isInitiatorOfTask(task, userId, portalUsername)) {
            return false;
        }
        return !allowsInitiatorEmptyPoolFallback(task.getBpmnAssigneeType());
    }

    /**
     * Whether task form is viewable (todo/done snapshot): processor rules + initiator + current assignee (including done tasks).
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
     * Loads task or throws.
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
     * Handles approval completion
     * Via WorkflowEngineClient calling Flowable engine
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

        // Approval submit is often incremental; __subTables__ may only exist on TaskInfo (merged ProcessInstance).
        // Without merge here, injectMiCollectionFromBpmn sees no sub-table rows → empty MI collection → zero child tasks.
        mergeSubTablesFromTaskInfoForMi(task, variables);
        Object subTablesAfterMerge = variables.get("__subTables__");
        if (!(subTablesAfterMerge instanceof Map<?, ?> subMap) || subMap.isEmpty()) {
            log.warn("[MI] After TaskInfo merge, variables have no __subTables__ (taskId={}, processInstanceId={}). "
                    + "Multi-instance injection will not be able to build row collection.",
                    task.getTaskId(), task.getProcessInstanceId());
        }

        // If MI sub-process prerequisite, read collection variable and assignee field from BPMN and build collection
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
                        updateProcessInstanceAssignee(
                                processInstanceId, nextAssignee, nextCandidateUsers, nextTaskName);
                        log.info("Process {} continues with next task: {}", processInstanceId, nextTaskName);
                    } else {
                        // No next user task; may be at non-user task (e.g. end event)
                        // Try to load current activity
                        log.info("No next user task found for process {}, checking for current activity", processInstanceId);
                        Optional<Map<String, Object>> currentActivity = getCurrentActivity(processInstanceId);
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
            rethrowIfRollbackOnlyAfterCatch(e, taskId);
        }
    }

    /**
     * Handles transfer action
     */
    private void handleTransfer(TaskInfo task, TaskCompleteRequest request, String userId) {
        String targetUserId = request.getTargetUserId();
        if (targetUserId == null || targetUserId.isEmpty()) {
            throw new PortalException("400", "Transfer target user cannot be empty");
        }
        transferTask(task.getTaskId(), userId, targetUserId, request.getComment());
    }

    /**
     * Handles delegate action
     */
    private void handleDelegate(TaskInfo task, TaskCompleteRequest request, String userId) {
        String targetUserId = request.getTargetUserId();
        if (targetUserId == null || targetUserId.isEmpty()) {
            throw new PortalException("400", "Delegate target user cannot be empty");
        }
        delegateTask(task.getTaskId(), userId, targetUserId, request.getComment());
    }

    /**
     * Handles return (rollback) action
     * Via WorkflowEngineClient calling Flowable engine
     */
    private void handleReturn(TaskInfo task, TaskCompleteRequest request, String userId, String returnKind) {
        String taskId = task.getTaskId();
        String targetActivityId = request.getReturnActivityId();
        
        if (targetActivityId == null || targetActivityId.isEmpty()) {
            throw new PortalException("400", "Return target activity cannot be empty");
        }
        
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable engine unavailable, please check if workflow-engine-core service is running");
        }
        
        log.info("Using Flowable engine to return task: {} to activity: {} (kind={})", taskId, targetActivityId, returnKind);
        Optional<Map<String, Object>> result = workflowEngineClient.returnTask(
            taskId, targetActivityId, userId, request.getComment(), returnKind);
        
        if (result.isEmpty()) {
            throw new PortalException("500", "Failed to return task: " + taskId);
        }
        
        Map<String, Object> data = result.get();
        if (!Boolean.TRUE.equals(data.get("success"))) {
            String message = data.get("message") != null ? (String) data.get("message") : "Failed to return task";
            throw new PortalException("500", message);
        }
        
        // Record audit log
        String auditOp = "DRAFT".equals(returnKind) ? "DRAFT_TASK" : "RETURN_TASK";
        DelegationAudit audit = DelegationAudit.builder()
                .delegatorId(userId)
                .delegateId(targetActivityId)
                .taskId(taskId)
                .operationType(auditOp)
                .operationResult("SUCCESS")
                .operationDetail(request.getComment())
                .build();
        delegationAuditRepository.save(audit);
        
        log.info("Task {} returned via Flowable to activity {} (kind={}) by user {}", taskId, targetActivityId, returnKind, userId);
    }

    /**
     * Whether the user belongs to the virtual group
     * Verified via WorkflowEngineClient against workflow-engine-core
     */
    private boolean isUserInVirtualGroup(String userId, String groupId) {
        if (!workflowEngineClient.isAvailable()) {
            log.warn("Workflow engine not available, cannot verify virtual group membership");
            return false;
        }
        try {
            // checkTaskPermission first argument is taskId, not virtual group ID
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
     * Urges a task
     */
    @Transactional
    public void urgeTask(String taskId, String urgerId, String message) {
        TaskInfo task = getTaskOrThrow(taskId);

        // Verify urger permission (usually initiator or admin)
        if (!canUrgeTask(task, urgerId)) {
            throw new PortalException("403", "You do not have permission to urge this task");
        }

        // Resolve task assignee
        String assignee = task.getAssignee();
        String assigneeName = task.getAssigneeName();

        // Send urge notification (should call messaging service)
        String urgeMessage = message != null ? message : "Please process the task as soon as possible: " + task.getTaskName();
        sendUrgeNotification(taskId, assignee, urgerId, urgeMessage);

        // Record urge audit log
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
     * Batch urge tasks
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
     * Whether the user may urge the task
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
     * Sends urge notification
     */
    private void sendUrgeNotification(String taskId, String assignee, String urgerId, String message) {
        // Should invoke messaging service in production
        // Log only for now
        log.info("Sending urge notification: task={}, assignee={}, urger={}, message={}", taskId, assignee, urgerId, message);
    }

    /**
     * Updates the process instance current assignee
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
     * Returns the current activity node for a process instance
     */
    private Optional<Map<String, Object>> getCurrentActivity(String processInstanceId) {
        try {
            if (!workflowEngineClient.isAvailable()) {
                return Optional.empty();
            }
            
            // Call workflow-engine for current activity
            return workflowEngineClient.getCurrentActivity(processInstanceId);
        } catch (Exception e) {
            log.warn("Failed to get current activity for process {}: {}", processInstanceId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Approval completion body often lacks full {@code __subTables__}; todo detail {@link TaskInfo#getVariables()} is merged with local ProcessInstance,
     * Merge before building MI collection or zero child tasks are created.
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
            Map<String, Object> hydrated = new LinkedHashMap<>((Map<String, Object>) fromTask);
            variables.put("__subTables__", canonicalizeSubTablesAliasKeys(hydrated));
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
        variables.put("__subTables__", canonicalizeSubTablesAliasKeys(merged));
    }

    /**
     * Stops alias-slice key monotonic growth (bindingId / tableName / normalizedName) for JSONB {@code __subTables__}.
     * <p>
     * When numeric keys exist at a given {@code __subTables__} level, keep only those numeric keys and recursively
     * canonicalize nested {@code __subTables__} stored under each row.
     */
    private static Map<String, Object> canonicalizeSubTablesAliasKeys(Map<String, Object> subTables) {
        if (subTables == null || subTables.isEmpty()) {
            return subTables;
        }
        boolean hasNumeric = false;
        for (String k : subTables.keySet()) {
            if (isDigitsKey(k)) {
                hasNumeric = true;
                break;
            }
        }
        Map<String, Object> out = hasNumeric ? new LinkedHashMap<>() : subTables;
        for (Map.Entry<String, Object> e : subTables.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (hasNumeric && !isDigitsKey(e.getKey())) {
                continue;
            }
            Object v = e.getValue();
            canonicalizeNestedSubTablesInValue(v);
            if (hasNumeric) {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    private static void canonicalizeNestedSubTablesInValue(Object value) {
        if (!(value instanceof List<?> rows)) {
            return;
        }
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Map<?, ?> rowMap)) {
                continue;
            }
            Object nestedObj = rowMap.get("__subTables__");
            if (!(nestedObj instanceof Map<?, ?> nestedMap) || nestedMap.isEmpty()) {
                continue;
            }
            // Rebuild with String keys to avoid ClassCastException during canonicalization.
            Map<String, Object> nestedStringKeyMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> ne : nestedMap.entrySet()) {
                if (ne.getKey() == null || ne.getValue() == null) {
                    continue;
                }
                nestedStringKeyMap.put(String.valueOf(ne.getKey()), ne.getValue());
            }
            Map<String, Object> nestedCanonical = canonicalizeSubTablesAliasKeys(nestedStringKeyMap);
            @SuppressWarnings("unchecked")
            Map<String, Object> rowMapString = (Map<String, Object>) rowMap;
            rowMapString.put("__subTables__", nestedCanonical);
        }
    }

    private static boolean isDigitsKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            if (!Character.isDigit(key.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Designer assignee column may be {@code assignee}; stored rows often use {@code assignee_user_id}; BPMN still uses configured assigneeField.
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
     * When the completed task is a prerequisite for a multi-instance sub-process, reads collection variable name and assignee field from BPMN,
     * Builds collection variable from __subTables__ and injects into variables.
     * <p>
     * Replaces hard-coded {@code Task_AssignParticipants} checks; adapts to any BPMN multi-instance configuration.
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

            // 1. Locate current task node (bpmn:userTask / userTask)
            Element taskElement = findElementByLocalNameAndId(document, "userTask", taskDefinitionKey);
            if (taskElement == null) {
                log.warn("[MI] UserTask id={} not found in BPMN (check taskDefinitionKey vs XML). Skip MI injection.",
                        taskDefinitionKey);
                return;
            }

            // 2. Outgoing: many exported BPMN files only have sequenceFlow@sourceRef, no <outgoing> under userTask
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
     * Builds multi-instance collection variable from __subTables__.
     * collectionVariableName is often {@code multiInstance_{subTableName}_collection}; PK resolved from PG / designer metadata first.
     * For JSON-only sub-tables (no physical table), fuzzy-match table name via {@code dw_table_definitions}; else infer single {@code id} from {@code __subTables__}.
     * <p>
     * __subTables__ often has multiple binding lists; naive flattening treats any row with target PK columns and assignee as an MI element
     * (e.g. multiple sub-tables with column {@code id}) creates far more child tasks than expected after the prerequisite task. Each map value list is scored separately,
     * Uses only the source list that best matches target PK + assignee (merge and dedupe on ties).
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
            String assigneeText = normalizeMiAssigneeText(assigneeValue);
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
     * Resolves the logical segment in a BPMN collection variable (e.g. {@code participants}) to primary-key column names.
     * Order: physical table exact/fuzzy → {@code dw_table_definitions} fuzzy (JSON-only sub-tables often have designer metadata only).
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
     * When physical/designer table names do not match: if {@code __subTables__} rows have non-empty {@code id} and assignee, use single-column id as row key (JSON sub-table).
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
     * Picks the sub-table list most likely for the current MI physical table, avoiding cross-table flattening that explodes instance count.
     * When multiple {@code __subTables__} slices tie (same table, e.g. binding 64 vs 66), merge rows by PK and let later numeric binding keys win field conflicts (Edit on canvas binding must not lose to stale sibling slice).
     */
    private List<Map<String, Object>> selectRowsForMiCollection(Map<String, Object> subTables,
                                                                List<String> pkCols,
                                                                String assigneeField) {
        if (subTables == null || subTables.isEmpty() || pkCols == null || pkCols.isEmpty()) {
            return List.of();
        }
        record ScoredSlice(String sliceKey, List<Map<String, Object>> rows, int score) {
        }
        List<ScoredSlice> scored = new ArrayList<>();
        for (Map.Entry<String, Object> entry : subTables.entrySet()) {
            if (!(entry.getValue() instanceof List<?> rawList)) {
                continue;
            }
            List<Map<String, Object>> typed = new ArrayList<>();
            for (Object rowObj : rawList) {
                if (rowObj instanceof Map<?, ?> m) {
                    typed.add((Map<String, Object>) m);
                }
            }
            int score = scoreRowsEligibleForMi(typed, pkCols, assigneeField);
            if (score > 0) {
                scored.add(new ScoredSlice(entry.getKey(), typed, score));
            }
        }
        if (scored.isEmpty()) {
            return List.of();
        }
        int bestScore = scored.stream().mapToInt(ScoredSlice::score).max().orElse(-1);
        if (bestScore <= 0) {
            return List.of();
        }
        List<ScoredSlice> best = scored.stream().filter(s -> s.score == bestScore).toList();
        int bestTotalSize = best.stream().mapToInt(s -> s.rows.size()).min().orElse(Integer.MAX_VALUE);
        best = best.stream().filter(s -> s.rows.size() == bestTotalSize).toList();

        List<ScoredSlice> mergeOrder = best.stream()
                .sorted(Comparator.comparingInt(s -> parseNumericSubTableSliceKey(s.sliceKey())))
                .toList();

        Map<String, Map<String, Object>> mergedByPk = new LinkedHashMap<>();
        for (ScoredSlice slice : mergeOrder) {
            for (Map<String, Object> row : slice.rows) {
                Map<String, Object> rowKey = SubTableRowKeySupport.rowKeyFromVariableRow(row, pkCols);
                if (rowKey == null) {
                    continue;
                }
                Object assigneeValue = resolveMiAssigneeRaw(row, assigneeField);
                if (assigneeValue == null || normalizeMiAssigneeText(assigneeValue).isEmpty()) {
                    continue;
                }
                String dedup = SubTableRowKeySupport.canonicalRowKeyString(pkCols, rowKey);
                if (dedup.isEmpty()) {
                    continue;
                }
                mergedByPk.merge(dedup, new LinkedHashMap<>(row), TaskProcessComponent::mergeMiCollectionRowPreferIncoming);
            }
        }
        return new ArrayList<>(mergedByPk.values());
    }

    /** Numeric binding ids sort ascending so higher ids (runtime canvas binding) overwrite stale sibling slices on merge. */
    static int parseNumericSubTableSliceKey(String sliceKey) {
        if (sliceKey != null && sliceKey.matches("\\d+")) {
            try {
                return Integer.parseInt(sliceKey);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return Integer.MAX_VALUE;
    }

    static Map<String, Object> mergeMiCollectionRowPreferIncoming(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> out = new LinkedHashMap<>(existing);
        for (Map.Entry<String, Object> e : incoming.entrySet()) {
            if (e.getValue() != null) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /** Extract Flowable user id from assignee cell (plain id or user snapshot map). */
    static String normalizeMiAssigneeText(Object assigneeValue) {
        if (assigneeValue == null) {
            return "";
        }
        if (assigneeValue instanceof Map<?, ?> map) {
            for (String key : new String[]{"id", "userId", "user_id", "value"}) {
                Object v = map.get(key);
                if (v != null && !String.valueOf(v).trim().isEmpty()) {
                    return String.valueOf(v).trim();
                }
            }
            return "";
        }
        return String.valueOf(assigneeValue).trim();
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

    // ==================== BPMN XML parsing helpers ====================

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
     * Outgoing flows often use sequenceFlow sourceRef to the activity id without an explicit &lt;outgoing&gt; under userTask.
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

    /** multiInstanceLoopCharacteristics is not always the first direct child of subProcess in some exports. */
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
