package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns "which rows may this user see" into a SQL predicate, for views restricted to the users
 * involved in each process.
 *
 * <p>Two of {@link MainTableViewInvolvementChecker}'s three branches are already exact in SQL —
 * the initiator is a column and the historic assignee is a join. The third, MI participation,
 * lives inside a JSON document and cannot be decided precisely in SQL: matching the user id as a
 * substring of {@code __subTables__} would make a row visible because the id happens to occur in
 * someone else's longer id, in a free-text field, or in a file path. Access control does not
 * accept that.
 *
 * <p>So the substring match is used only to narrow the candidates, and every candidate is then
 * put through the pinned-design checker itself. A configured person identity must appear in the
 * sub-table JSON string values, which guarantees the coarse pass
 * can only return a superset (pinned by {@code MainTableViewInvolvementSupersetTest}). The result
 * is the same set of rows the per-instance checker would have accepted, obtained in one pass
 * instead of one query per row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MainTableViewInvolvementScope {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MainTableViewInvolvementChecker involvementChecker;

    /** A WHERE fragment and the values it binds, in order. */
    public record Predicate(String sql, List<Object> params) {
    }

    /**
     * @return predicate limiting rows to those {@code userId} is involved in, for the processes of
     *         one function unit
     */
    public Predicate predicateFor(String userId, String functionUnitCode) {
        List<String> miVisibleIds = miVisibleInstanceIds(userId, functionUnitCode);
        String sql = " AND (pi.start_user_id = ?"
                + " OR EXISTS (SELECT 1 FROM ACT_HI_TASKINST ht"
                + " WHERE ht.PROC_INST_ID_ = pi.id AND ht.ASSIGNEE_ = ?)"
                + " OR pi.id = ANY(?))";
        List<Object> params = new ArrayList<>(3);
        params.add(userId);
        params.add(userId);
        params.add(miVisibleIds.toArray(String[]::new));
        return new Predicate(sql, params);
    }

    /** Mutable person assignments are rechecked; only immutable release declarations are cached. */
    public List<String> miVisibleInstanceIds(String userId, String functionUnitCode) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("User is required");
        String literal = objectMapper.valueToTree(userId).toString();
        String needle = literal.substring(1,literal.length()-1);
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList("""
                SELECT pi.id, pi.function_unit_catalog_id, pi.variables::text AS variables
                FROM up_process_instance pi
                WHERE pi.function_unit_code = ?
                  AND pi.start_user_id IS DISTINCT FROM ?
                  AND NOT EXISTS (SELECT 1 FROM ACT_HI_TASKINST ht
                      WHERE ht.PROC_INST_ID_ = pi.id AND ht.ASSIGNEE_ = ?)
                  AND (pi.variables -> '__subTables__')::text ILIKE ?
                """, functionUnitCode, userId, userId, "%" + escapeLike(needle) + "%");

        List<String> visible = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            Map<String, Object> variables = readVariables(candidate.get("variables"));
            if (involvementChecker.isMiParticipant(userId,
                    (String) candidate.get("function_unit_catalog_id"), variables)) {
                visible.add(String.valueOf(candidate.get("id")));
            }
        }
        log.debug("MI involvement scope for {} on {}: {} candidates, {} visible",
                userId, functionUnitCode, candidates.size(), visible.size());
        return List.copyOf(visible);
    }

    private Map<String, Object> readVariables(Object rawJson) {
        if (rawJson == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(rawJson), new TypeReference<>() {});
        } catch (Exception e) {
            // Unreadable variables would silently hide rows from a user entitled to see them.
            throw new IllegalStateException("Process variables are not readable JSON", e);
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

}
