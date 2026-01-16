package com.platform.security.resolver;

import com.platform.security.dto.ResolvedUser;
import com.platform.security.enums.AssignmentTargetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 虚拟组目标解析器
 * 解析 VIRTUAL_GROUP 类型的分配目标
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualGroupTargetResolver implements TargetResolver {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public AssignmentTargetType getTargetType() {
        return AssignmentTargetType.VIRTUAL_GROUP;
    }
    
    @Override
    public boolean targetExists(String targetId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_virtual_groups WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                targetId
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Failed to check virtual group existence: {}", targetId, e);
            return false;
        }
    }
    
    @Override
    public List<ResolvedUser> resolveUsers(String targetId) {
        try {
            return jdbcTemplate.query(
                "SELECT u.id, u.username, u.display_name, u.full_name, u.employee_id, u.email " +
                "FROM sys_virtual_group_members vgm " +
                "JOIN sys_users u ON vgm.user_id = u.id " +
                "WHERE vgm.group_id = ? " +
                "AND u.deleted = false AND u.status = 'ACTIVE'",
                (rs, rowNum) -> ResolvedUser.builder()
                    .userId(rs.getString("id"))
                    .username(rs.getString("username"))
                    .displayName(rs.getString("display_name") != null ? 
                        rs.getString("display_name") : rs.getString("full_name"))
                    .employeeId(rs.getString("employee_id"))
                    .email(rs.getString("email"))
                    .build(),
                targetId
            );
        } catch (Exception e) {
            log.error("Failed to resolve virtual group users: {}", targetId, e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public String getTargetDisplayName(String targetId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT name FROM sys_virtual_groups WHERE id = ?",
                String.class,
                targetId
            );
        } catch (Exception e) {
            log.warn("Failed to get virtual group display name: {}", targetId, e);
            return targetId;
        }
    }
    
    @Override
    public long getUserCount(String targetId) {
        try {
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_virtual_group_members vgm " +
                "JOIN sys_users u ON vgm.user_id = u.id " +
                "WHERE vgm.group_id = ? " +
                "AND u.deleted = false AND u.status = 'ACTIVE'",
                Long.class,
                targetId
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to count virtual group users: {}", targetId, e);
            return 0;
        }
    }
}
