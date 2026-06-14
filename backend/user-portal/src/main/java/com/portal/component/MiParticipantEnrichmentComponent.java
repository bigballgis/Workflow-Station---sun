package com.portal.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backfills participant sub-table rows in process variables ({@code __subTables__}) from the
 * physical {@code participants} relation table: row primary keys for Assign, and
 * assignee/attend-status display data for completed views.
 * Extracted from {@link TaskQueryComponent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiParticipantEnrichmentComponent {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Rows in process variable {@code __subTables__} may only contain form fields (no {@code id});
     * portal Assign needs rowId. When the relation table has been persisted, backfill the primary key
     * from the {@code participants} table by email (with name disambiguation when needed).
     */
    @SuppressWarnings("unchecked")
    public void enrichMissingParticipantRowIdsInSubTables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> subTables = (Map<String, Object>) subTablesObj;
        List<Map<String, Object>> pending = new ArrayList<>();
        for (Object v : subTables.values()) {
            if (!(v instanceof List<?> list)) {
                continue;
            }
            for (Object rowObj : list) {
                if (!(rowObj instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> row = (Map<String, Object>) rowObj;
                if (row.get("id") != null || row.get("rowId") != null) {
                    continue;
                }
                Object email = row.get("email");
                Object name = row.get("name");
                if ((email == null || String.valueOf(email).isBlank())
                        && (name == null || String.valueOf(name).isBlank())) {
                    continue;
                }
                pending.add(row);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        String table = "participants";
        if (!table.matches("[a-zA-Z0-9_]+")) {
            return;
        }
        try {
            Long meetingId = null;
            Object midObj = variables.get("meeting_id");
            if (midObj == null) {
                midObj = variables.get("mainRecordId");
            }
            if (midObj instanceof Number n) {
                meetingId = n.longValue();
            } else if (midObj != null) {
                try {
                    meetingId = Long.parseLong(String.valueOf(midObj).trim());
                } catch (Exception ignored) {
                    meetingId = null;
                }
            }
            int enriched = 0;
            for (Map<String, Object> row : pending) {
                String em = row.get("email") == null ? "" : String.valueOf(row.get("email")).trim();
                String nm = row.get("name") == null ? "" : String.valueOf(row.get("name")).trim();
                String dept = row.get("department") == null ? "" : String.valueOf(row.get("department")).trim();

                Long id = null;
                if (!em.isBlank()) {
                    if (meetingId != null) {
                        List<Long> ids = jdbcTemplate.query(
                                "SELECT id FROM " + table + " WHERE meeting_id = ? AND lower(trim(email)) = lower(trim(?)) ORDER BY id LIMIT 1",
                                (rs, i) -> rs.getLong("id"),
                                meetingId, em);
                        if (!ids.isEmpty()) id = ids.get(0);
                    } else {
                        List<Long> ids = jdbcTemplate.query(
                                "SELECT id FROM " + table + " WHERE lower(trim(email)) = lower(trim(?)) ORDER BY id LIMIT 1",
                                (rs, i) -> rs.getLong("id"),
                                em);
                        if (!ids.isEmpty()) id = ids.get(0);
                    }
                }

                if (id == null && !nm.isBlank() && !dept.isBlank()) {
                    if (meetingId != null) {
                        List<Long> ids = jdbcTemplate.query(
                                "SELECT id FROM " + table + " WHERE meeting_id = ? AND lower(trim(name)) = lower(trim(?)) AND lower(trim(department)) = lower(trim(?)) ORDER BY id LIMIT 1",
                                (rs, i) -> rs.getLong("id"),
                                meetingId, nm, dept);
                        if (!ids.isEmpty()) id = ids.get(0);
                    } else {
                        List<Long> ids = jdbcTemplate.query(
                                "SELECT id FROM " + table + " WHERE lower(trim(name)) = lower(trim(?)) AND lower(trim(department)) = lower(trim(?)) ORDER BY id LIMIT 1",
                                (rs, i) -> rs.getLong("id"),
                                nm, dept);
                        if (!ids.isEmpty()) id = ids.get(0);
                    }
                }

                if (id == null && !nm.isBlank()) {
                    if (meetingId != null) {
                        List<Long> ids = jdbcTemplate.query(
                                "SELECT id FROM " + table + " WHERE meeting_id = ? AND lower(trim(name)) = lower(trim(?)) ORDER BY id LIMIT 1",
                                (rs, i) -> rs.getLong("id"),
                                meetingId, nm);
                        if (!ids.isEmpty()) id = ids.get(0);
                    } else {
                        List<Long> ids = jdbcTemplate.query(
                                "SELECT id FROM " + table + " WHERE lower(trim(name)) = lower(trim(?)) ORDER BY id LIMIT 1",
                                (rs, i) -> rs.getLong("id"),
                                nm);
                        if (!ids.isEmpty()) id = ids.get(0);
                    }
                }

                if (id != null) {
                    row.put("id", id);
                    enriched++;
                }
            }
            if (enriched > 0) {
                log.debug("Enriched {} sub-table rows with DB id from {}", enriched, table);
            } else {
                log.debug("No participant row id enriched from {}", table);
            }
        } catch (Exception e) {
            log.debug("enrichMissingParticipantRowIdsInSubTables skipped: {}", e.getMessage());
        }
    }

    /**
     * Backfill assignee_display_name and attend_status from the participants physical table
     * into __subTables__ rows, so the completed tasks view can display assignment results.
     */
    @SuppressWarnings("unchecked")
    public void enrichParticipantAssignmentData(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> subTables = (Map<String, Object>) subTablesObj;
        List<Map<String, Object>> rowsWithId = new ArrayList<>();
        for (Object v : subTables.values()) {
            if (!(v instanceof List<?> list)) {
                continue;
            }
            for (Object rowObj : list) {
                if (!(rowObj instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> row = (Map<String, Object>) rowObj;
                Object rowId = row.get("id");
                if (rowId == null) {
                    rowId = row.get("rowId");
                }
                if (rowId != null) {
                    rowsWithId.add(row);
                }
            }
        }
        if (rowsWithId.isEmpty()) {
            return;
        }
        try {
            for (Map<String, Object> row : rowsWithId) {
                Object rowId = row.get("id");
                if (rowId == null) {
                    rowId = row.get("rowId");
                }
                Long id;
                if (rowId instanceof Number n) {
                    id = n.longValue();
                } else {
                    try {
                        id = Long.parseLong(String.valueOf(rowId).trim());
                    } catch (Exception ignored) {
                        continue;
                    }
                }
                List<Map<String, Object>> dbRows = jdbcTemplate.query(
                        "SELECT assignee_user_id, assignee_display_name, attend_status FROM participants WHERE id = ?",
                        (rs, i) -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("assignee_user_id", rs.getString("assignee_user_id"));
                            m.put("assignee_display_name", rs.getString("assignee_display_name"));
                            m.put("attend_status", rs.getString("attend_status"));
                            return m;
                        },
                        id);
                if (!dbRows.isEmpty()) {
                    Map<String, Object> dbRow = dbRows.get(0);
                    String displayName = (String) dbRow.get("assignee_display_name");
                    String assigneeUserId = (String) dbRow.get("assignee_user_id");
                    if (displayName == null && assigneeUserId != null && !assigneeUserId.isBlank()) {
                        displayName = resolveUsernameByUserId(assigneeUserId);
                    }
                    if (displayName != null) {
                        row.put("assignee_display_name", displayName);
                    }
                    if (dbRow.get("attend_status") != null) {
                        row.put("attend_status", dbRow.get("attend_status"));
                    }
                    if (assigneeUserId != null && row.get("assignee_user_id") == null) {
                        row.put("assignee_user_id", assigneeUserId);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("enrichParticipantAssignmentData skipped: {}", e.getMessage());
        }
    }

    private String resolveUsernameByUserId(String userId) {
        try {
            List<String> names = jdbcTemplate.query(
                    "SELECT COALESCE(username, display_name) FROM sys_users WHERE id = ? LIMIT 1",
                    (rs, i) -> rs.getString(1), userId);
            return names.isEmpty() ? userId : names.get(0);
        } catch (Exception e) {
            return userId;
        }
    }
}
