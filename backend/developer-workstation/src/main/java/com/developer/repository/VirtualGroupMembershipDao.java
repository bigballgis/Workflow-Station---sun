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
     * 用户可选择的「团队」：其所属的 ACTIVE、CUSTOM 类型虚拟组，排除内置 Public 组。
     * 用于进入工作区的团队选择弹窗与顶部切换器。
     */
    public List<DevGroupOptionDTO> findSelectableTeamsByUserId(String userId, String publicGroupId) {
        return jdbcTemplate.query(
                "SELECT DISTINCT g.id, g.name FROM sys_virtual_group_members m "
                        + "JOIN sys_virtual_groups g ON g.id = m.group_id "
                        + "WHERE m.user_id = ? AND g.status = 'ACTIVE' AND g.type = 'CUSTOM' AND g.id <> ? "
                        + "ORDER BY g.name",
                (rs, rowNum) -> new DevGroupOptionDTO(rs.getString("id"), rs.getString("name")),
                userId,
                publicGroupId);
    }

    /**
     * All active CUSTOM teams, excluding the built-in Public group, for ADMIN
     * switching.
     */
    public List<DevGroupOptionDTO> findAllSelectableTeams(String publicGroupId) {
        return jdbcTemplate.query(
                "SELECT g.id, g.name FROM sys_virtual_groups g "
                        + "WHERE g.status = 'ACTIVE' AND g.type = 'CUSTOM' AND g.id <> ? "
                        + "ORDER BY g.name",
                (rs, rowNum) -> new DevGroupOptionDTO(rs.getString("id"), rs.getString("name")),
                publicGroupId);
    }
}