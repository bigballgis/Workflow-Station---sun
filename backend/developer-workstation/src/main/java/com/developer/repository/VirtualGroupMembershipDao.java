package com.developer.repository;

import com.developer.dto.DevGroupOptionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Teams (DEVELOPER virtual groups) and the Technical Lead / Developer lists a user is on
 * (sys_virtual_group_members, shared with admin-center). Platform access model §5.4, §6.1.
 */
@Repository
@RequiredArgsConstructor
public class VirtualGroupMembershipDao {
    private final JdbcTemplate jdbcTemplate;

    /** A team the user is on, with the lists that put them there; a user on both lists is a Technical Lead (§4.4). */
    public record TeamMembership(String teamId, String name, String status, boolean techLead, boolean developer) {
        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    /** A virtual group a function unit can sit in, as stored. */
    public record WorkspaceGroup(String id, String code, String name, String type, String status) {
    }

    /**
     * Teams the user is on, any team status, excluding Public (which has no lists). Like the platform role resolver,
     * a list counts only for an active, not deleted account and while its role (TECH_LEAD / DEVELOPER) is active.
     */
    public List<TeamMembership> findTeamMemberships(String userId, String publicGroupId) {
        return jdbcTemplate.query(
                "SELECT g.id, g.name, g.status, "
                        + "MAX(CASE WHEN m.list_type = 'TECH_LEAD' THEN 1 ELSE 0 END) AS tech_lead, "
                        + "MAX(CASE WHEN m.list_type = 'DEVELOPER' THEN 1 ELSE 0 END) AS developer "
                        + "FROM sys_virtual_group_members m "
                        + "JOIN sys_users u ON u.id = m.user_id AND u.status = 'ACTIVE' "
                        + "AND COALESCE(u.deleted, false) = false "
                        + "JOIN sys_roles r ON r.code = m.list_type AND (r.status = 'ACTIVE' OR r.status IS NULL "
                        + "OR TRIM(COALESCE(r.status, '')) = '') "
                        + "JOIN sys_virtual_groups g ON g.id = m.group_id "
                        + "WHERE m.user_id = ? AND g.type = 'DEVELOPER' AND g.id <> ? "
                        + "AND m.list_type IN ('TECH_LEAD', 'DEVELOPER') "
                        + "GROUP BY g.id, g.name, g.status "
                        + "ORDER BY g.name",
                (rs, rowNum) -> new TeamMembership(rs.getString("id"), rs.getString("name"), rs.getString("status"),
                        rs.getInt("tech_lead") == 1, rs.getInt("developer") == 1),
                userId,
                publicGroupId);
    }

    /** All teams (ACTIVE and INACTIVE), excluding Public, for users who see every workspace. */
    public List<DevGroupOptionDTO> findAllTeams(String publicGroupId) {
        return jdbcTemplate.query(
                "SELECT g.id, g.name, g.status FROM sys_virtual_groups g "
                        + "WHERE g.status IN ('ACTIVE', 'INACTIVE') "
                        + "AND g.type = 'DEVELOPER' AND g.id <> ? "
                        + "ORDER BY g.name",
                (rs, rowNum) -> new DevGroupOptionDTO(
                        rs.getString("id"), rs.getString("name"), rs.getString("status")),
                publicGroupId);
    }

    /** The stored groups among {@code groupIds}; a group that no longer exists is absent from the map. */
    public Map<String, WorkspaceGroup> findGroups(Collection<String> groupIds) {
        Map<String, WorkspaceGroup> groups = new HashMap<>();
        if (groupIds == null || groupIds.isEmpty()) {
            return groups;
        }
        String placeholders = String.join(",", groupIds.stream().map(id -> "?").toList());
        jdbcTemplate.query(
                "SELECT id, code, name, type, status FROM sys_virtual_groups WHERE id IN (" + placeholders + ")",
                rs -> {
                    WorkspaceGroup group = new WorkspaceGroup(rs.getString("id"), rs.getString("code"),
                            rs.getString("name"), rs.getString("type"), rs.getString("status"));
                    groups.put(group.id(), group);
                },
                groupIds.toArray());
        return groups;
    }

    /** The virtual group with this code, if any (team codes are global and match across environments, §5.4). */
    public Optional<WorkspaceGroup> findGroupByCode(String code) {
        return jdbcTemplate.query(
                "SELECT id, code, name, type, status FROM sys_virtual_groups WHERE code = ?",
                (rs, rowNum) -> new WorkspaceGroup(rs.getString("id"), rs.getString("code"),
                        rs.getString("name"), rs.getString("type"), rs.getString("status")),
                code).stream().findFirst();
    }
}
