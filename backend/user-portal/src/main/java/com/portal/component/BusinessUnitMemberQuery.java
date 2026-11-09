package com.portal.component;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The members of a business unit with the roles they hold there, for the Member Management page where a BU approver
 * removes a member's BU role (D-25). A member is someone in the BU's membership list or holding one of its roles, not
 * deleted; one query for the whole list. Admin Center's {@code GET /business-units/{id}/members} names the users only
 * (and one page of them), so the page had no role to show or remove.
 */
@Component
@RequiredArgsConstructor
public class BusinessUnitMemberQuery {

    private static final String MEMBERS_SQL = """
            SELECT u.id AS user_id, u.username, u.full_name, u.display_name, m.joined_at,
                   r.id AS role_id, r.code AS role_code, r.name AS role_name
            FROM (SELECT x.user_id, MIN(x.created_at) AS joined_at
                  FROM (SELECT user_id, created_at FROM sys_user_business_units WHERE business_unit_id = ?
                        UNION ALL
                        SELECT user_id, created_at FROM sys_user_business_unit_roles WHERE business_unit_id = ?) x
                  GROUP BY x.user_id) m
            JOIN sys_users u ON u.id = m.user_id AND COALESCE(u.deleted, false) = false
            LEFT JOIN sys_user_business_unit_roles ubr ON ubr.user_id = m.user_id AND ubr.business_unit_id = ?
            LEFT JOIN sys_roles r ON r.id = ubr.role_id
            ORDER BY u.username, r.name, r.id
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * One entry per member: {@code userId}, {@code username}, {@code fullName} (full name, else display name; null
     * when the user has neither), {@code joinedAt} and {@code roles} ({@code id}, {@code code}, {@code name}; empty
     * when the member holds no role in the unit).
     */
    public List<Map<String, Object>> members(String businessUnitId) {
        Map<String, Map<String, Object>> byUser = new LinkedHashMap<>();
        jdbcTemplate.query(MEMBERS_SQL, (RowCallbackHandler) rs -> {
            String userId = rs.getString("user_id");
            Map<String, Object> member = byUser.get(userId);
            if (member == null) {
                member = newMember(userId, rs);
                byUser.put(userId, member);
            }
            if (rs.getString("role_id") != null) {
                Map<String, Object> role = new LinkedHashMap<>();
                role.put("id", rs.getString("role_id"));
                role.put("code", rs.getString("role_code"));
                role.put("name", rs.getString("role_name"));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> roles = (List<Map<String, Object>>) member.get("roles");
                roles.add(role);
            }
        }, businessUnitId, businessUnitId, businessUnitId);
        return new ArrayList<>(byUser.values());
    }

    private static Map<String, Object> newMember(String userId, ResultSet rs) throws SQLException {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("userId", userId);
        member.put("username", rs.getString("username"));
        String fullName = textOrNull(rs.getString("full_name"));
        member.put("fullName", fullName != null ? fullName : textOrNull(rs.getString("display_name")));
        Timestamp joined = rs.getTimestamp("joined_at");
        member.put("joinedAt", joined != null ? joined.toLocalDateTime() : null);
        member.put("roles", new ArrayList<Map<String, Object>>());
        return member;
    }

    private static String textOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
