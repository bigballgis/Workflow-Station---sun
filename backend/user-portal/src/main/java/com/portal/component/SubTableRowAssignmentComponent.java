package com.portal.component;

import com.platform.common.jdbc.SqlIdentifiers;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskInfo;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sub-table row processor assignment (MI sub-process prerequisite): assigns a participant row to a user
 * via the workflow engine, resolving/creating participant and meeting rows by identity when needed.
 * Extracted from {@link TaskProcessComponent} (which keeps same-name public forwards).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubTableRowAssignmentComponent {

    private final WorkflowEngineClient workflowEngineClient;
    private final ProcessInstanceRepository processInstanceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TaskPermissionEvaluator taskPermissionEvaluator;

    /** Lazy: breaks cycle with {@link TaskProcessComponent} (auto-claim and task loading stay on the facade). */
    @Lazy
    @Autowired
    private TaskProcessComponent taskProcessComponent;

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

        TaskInfo task = taskProcessComponent.getTaskOrThrow(taskId);
        if (TaskPermissionEvaluator.isTaskAlreadyClosedInEngineView(task)) {
            throw new PortalException("409",
                    "This task is no longer active (it may already be completed). Please refresh your todo list.");
        }
        if (!taskPermissionEvaluator.canProcessTask(task, userId, portalUsername)) {
            throw new PortalException("403", "You do not have permission to process this task");
        }

        boolean poolStyleSt = "VIRTUAL_GROUP".equals(task.getAssignmentType()) || "CANDIDATE_USERS".equals(task.getAssignmentType())
                || "DEPT_ROLE".equals(task.getAssignmentType());
        boolean noAssigneeSt = task.getAssignee() == null || task.getAssignee().isEmpty();
        if (poolStyleSt && noAssigneeSt && !TaskPermissionEvaluator.isEmptyAssignmentPool(task)) {
            log.info("Auto-claiming pool task {} (type {}) for sub-table assign by user {}",
                    taskId, task.getAssignmentType(), userId);
            taskProcessComponent.claimTask(taskId, userId, portalUsername);
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
            TaskInfo task = taskProcessComponent.getTaskOrThrow(taskId);
            rowId = createParticipantRowIfMissing(participantTable, task, assigneeId, email, name, department, topic, location, organizerName);
            if (rowId == null) {
                throw new PortalException("400", "Assignment failed: participant row not found/created");
            }
        }
        updateAssigneeDisplayName(participantTable, rowId, assigneeId);
        return assignSubTableRow(taskId, rowId, null, assigneeId, userId, portalUsername);
    }

    private Long resolveParticipantRowIdByIdentity(String participantTable, String email, String name, String department) {
        participantTable = SqlIdentifiers.requireQualifiedName(participantTable);
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
        participantTable = SqlIdentifiers.requireQualifiedName(participantTable);
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
        meetingTable = SqlIdentifiers.requireQualifiedName(meetingTable);
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
            table = SqlIdentifiers.requireQualifiedName(table);
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
        participantTable = SqlIdentifiers.requireQualifiedName(participantTable);
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
        participantTable = SqlIdentifiers.requireQualifiedName(participantTable);
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
}
