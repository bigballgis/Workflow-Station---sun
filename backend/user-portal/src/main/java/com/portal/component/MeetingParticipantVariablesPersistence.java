package com.portal.component;

import com.portal.debug.AgentDebugLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会议参与人演示流程：发起时子表行仅有表单字段，无 DB 主键。
 * 在自动完成「创建会议」任务前，将 meeting / participants 写入物理表并回填 {@code __subTables__} 行 {@code id}，
 * 供后续「分配参与人」任务调用引擎 assign 接口（需要 rowId）。
 * <p>
 * JDBC 写入使用独立事务（REQUIRES_NEW），避免表缺失或 SQL 错误中止门户 {@code startProcess} 外层事务。
 */
@Slf4j
@Component
public class MeetingParticipantVariablesPersistence {

    /** 与 deploy/init-scripts/16-meeting-participant-collection 中功能单元 code 一致 */
    private static final String MEETING_PARTICIPANT_FU_CODE = "fu-20260403-a1b2c5";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate requiresNewTx;

    public MeetingParticipantVariablesPersistence(JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * @param processInstanceId Flowable / 门户流程实例 ID
     * @param variables         即将随 completeTask 提交的变量（可被原地修改）
     * @param functionUnitCode  当前发起的功能单元 code
     */
    public void persistIfApplicable(String processInstanceId, Map<String, Object> variables, String functionUnitCode) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        if (functionUnitCode == null || !MEETING_PARTICIPANT_FU_CODE.equals(functionUnitCode.trim())) {
            return;
        }
        if (Boolean.TRUE.equals(variables.get("_meeting_participants_persisted"))) {
            return;
        }
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map<?, ?>)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> subTables = (Map<String, Object>) subTablesObj;

        try {
            requiresNewTx.executeWithoutResult(status ->
                    persistMeetingAndParticipants(processInstanceId, variables, subTables));
        } catch (Exception e) {
            log.warn("[MeetingParticipant] persist skipped (tables missing or SQL error): {}", e.getMessage());
        }
    }

    private void persistMeetingAndParticipants(String processInstanceId, Map<String, Object> variables,
            Map<String, Object> subTables) {
        Long meetingId = insertMeetingRow(variables);
        if (meetingId == null) {
            log.warn("[MeetingParticipant] skip persist: missing meeting fields in variables");
            return;
        }
        variables.put("meeting_id", meetingId);

        int enriched = 0;
        for (Object listObj : subTables.values()) {
            if (!(listObj instanceof List<?> list)) {
                continue;
            }
            for (Object rowObj : list) {
                if (!(rowObj instanceof Map<?, ?>)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) rowObj;
                if (row.get("id") != null || row.get("rowId") != null) {
                    continue;
                }
                if (row.get("name") == null && row.get("email") == null) {
                    continue;
                }
                Long pid = insertParticipantRow(meetingId, row);
                if (pid != null) {
                    row.put("id", pid);
                    enriched++;
                }
            }
        }
        if (enriched > 0) {
            variables.put("_meeting_participants_persisted", true);
            log.info("[MeetingParticipant] persisted meeting_id={} and {} participant row(s) for process {}",
                    meetingId, enriched, processInstanceId);
            // #region agent log
            Map<String, Object> dbg = new LinkedHashMap<>();
            dbg.put("meetingId", meetingId);
            dbg.put("enriched", enriched);
            dbg.put("processInstanceId", processInstanceId != null ? processInstanceId : "");
            AgentDebugLog.ff0c74("MeetingParticipantVariablesPersistence.persistIfApplicable", "H-persist",
                    "participant_rows_backfilled", dbg);
            // #endregion
        }
    }

    private Long insertMeetingRow(Map<String, Object> variables) {
        Object topic = variables.get("topic");
        Object meetingTime = variables.get("meeting_time");
        Object location = variables.get("location");
        Object organizer = variables.get("organizer_name");
        if (topic == null || String.valueOf(topic).isBlank()) {
            return null;
        }
        String desc = variables.get("description") != null ? String.valueOf(variables.get("description")) : null;
        String createdBy = variables.get("initiator") != null ? String.valueOf(variables.get("initiator")) : null;
        Timestamp ts = parseMeetingTime(meetingTime);

        String sql = "INSERT INTO meeting (topic, meeting_time, location, organizer_name, description, status, "
                + "created_by, created_at, updated_at) VALUES (?,?,?,?,?,?,?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "RETURNING id";
        List<Long> ids = jdbcTemplate.query(sql,
                (rs, i) -> rs.getLong("id"),
                String.valueOf(topic).trim(),
                ts,
                location != null ? String.valueOf(location) : "",
                organizer != null ? String.valueOf(organizer) : "",
                desc,
                "DRAFT",
                createdBy != null ? createdBy : "");
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Timestamp parseMeetingTime(Object meetingTime) {
        if (meetingTime == null) {
            return new Timestamp(System.currentTimeMillis());
        }
        if (meetingTime instanceof Timestamp t) {
            return t;
        }
        String s = String.valueOf(meetingTime).trim();
        try {
            return Timestamp.valueOf(s.replace('T', ' '));
        } catch (Exception e) {
            return new Timestamp(System.currentTimeMillis());
        }
    }

    private Long insertParticipantRow(Long meetingId, Map<String, Object> row) {
        String name = row.get("name") != null ? String.valueOf(row.get("name")).trim() : "";
        String dept = row.get("department") != null ? String.valueOf(row.get("department")) : null;
        String email = row.get("email") != null ? String.valueOf(row.get("email")).trim() : "";
        String sql = "INSERT INTO participants (meeting_id, name, department, email, sort_order) "
                + "VALUES (?,?,?,?,?) RETURNING id";
        List<Long> ids = jdbcTemplate.query(sql,
                (rs, i) -> rs.getLong("id"),
                meetingId,
                name,
                dept,
                email,
                0);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
