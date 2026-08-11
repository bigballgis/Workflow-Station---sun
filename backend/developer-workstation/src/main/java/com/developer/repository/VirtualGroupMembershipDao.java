package com.developer.repository;

import com.developer.dto.DevGroupOptionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 查询用户所属虚拟组（sys_virtual_group_members，与 admin 共用库）
 */
@Repository
@RequiredArgsConstructor
public class VirtualGroupMembershipDao {
    private final JdbcTemplate jdbcTemplate;

    public List<String> findVirtualGroupIdsByUserId(String userId) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT group_id FROM sys_virtual_group_members WHERE user_id = ?",
                String.class,
                userId);
    }

    /**
     * 用户所属的「团队」列表：CUSTOM/DEVELOPER，排除 Public。
     * 含 ACTIVE 与 INACTIVE（INACTIVE 供 UI 展示为不可选，不从列表删除）。
     */
    public List<DevGroupOptionDTO> findSelectableTeamsByUserId(String userId, String publicGroupId) {
        return jdbcTemplate.query(
                "SELECT DISTINCT g.id, g.name, g.status FROM sys_virtual_group_members m "
                        + "JOIN sys_virtual_groups g ON g.id = m.group_id "
                        + "WHERE m.user_id = ? AND g.status IN ('ACTIVE', 'INACTIVE') "
                        + "AND g.type IN ('CUSTOM', 'DEVELOPER') AND g.id <> ? "
                        + "ORDER BY g.name",
                (rs, rowNum) -> new DevGroupOptionDTO(
                        rs.getString("id"), rs.getString("name"), rs.getString("status")),
                userId,
                publicGroupId);
    }

    /**
     * All CUSTOM/DEVELOPER teams (ACTIVE + INACTIVE), excluding Public, for ADMIN switching.
     */
    public List<DevGroupOptionDTO> findAllSelectableTeams(String publicGroupId) {
        return jdbcTemplate.query(
                "SELECT g.id, g.name, g.status FROM sys_virtual_groups g "
                        + "WHERE g.status IN ('ACTIVE', 'INACTIVE') "
                        + "AND g.type IN ('CUSTOM', 'DEVELOPER') AND g.id <> ? "
                        + "ORDER BY g.name",
                (rs, rowNum) -> new DevGroupOptionDTO(
                        rs.getString("id"), rs.getString("name"), rs.getString("status")),
                publicGroupId);
    }
}
