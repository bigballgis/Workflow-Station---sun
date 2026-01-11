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
 * 用户目标解析器
 * 解析 USER 类型的分配目标
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserTargetResolver implements TargetResolver {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public AssignmentTargetType getTargetType() {
        return AssignmentTargetType.USER;
    }
    
    @Override
    public boolean targetExists(String targetId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_users WHERE id = ? AND deleted = false",
                Integer.class,
                targetId
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Failed to check user existence: {}", targetId, e);
            return false;
        }
    }
    
    @Override
    public List<ResolvedUser> resolveUsers(String targetId) {
        try {
            return jdbcTemplate.query(
                "SELECT u.id, u.username, u.display_name, u.full_name, u.employee_id, u.department_id, u.email, " +
                "d.name as department_name " +
                "FROM sys_users u " +
                "LEFT JOIN sys_departments d ON u.department_id = d.id " +
                "WHERE u.id = ? AND u.deleted = false AND u.status = 'ACTIVE'",
                (rs, rowNum) -> ResolvedUser.builder()
                    .userId(rs.getString("id"))
                    .username(rs.getString("username"))
                    .displayName(rs.getString("display_name") != null ? 
                        rs.getString("display_name") : rs.getString("full_name"))
                    .employeeId(rs.getString("employee_id"))
                    .departmentId(rs.getString("department_id"))
                    .departmentName(rs.getString("department_name"))
                    .email(rs.getString("email"))
                    .build(),
                targetId
            );
        } catch (Exception e) {
            log.error("Failed to resolve user: {}", targetId, e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public String getTargetDisplayName(String targetId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COALESCE(display_name, full_name, username) FROM sys_users WHERE id = ?",
                String.class,
                targetId
            );
        } catch (Exception e) {
            log.warn("Failed to get user display name: {}", targetId, e);
            return targetId;
        }
    }
}
