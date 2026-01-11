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
 * 部门层级目标解析器
 * 解析 DEPARTMENT_HIERARCHY 类型的分配目标（该部门及所有下级部门的用户）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentHierarchyTargetResolver implements TargetResolver {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public AssignmentTargetType getTargetType() {
        return AssignmentTargetType.DEPARTMENT_HIERARCHY;
    }
    
    @Override
    public boolean targetExists(String targetId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_departments WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                targetId
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Failed to check department existence: {}", targetId, e);
            return false;
        }
    }
    
    @Override
    public List<ResolvedUser> resolveUsers(String targetId) {
        try {
            // 首先获取目标部门的path
            String departmentPath = jdbcTemplate.queryForObject(
                "SELECT path FROM sys_departments WHERE id = ?",
                String.class,
                targetId
            );
            
            if (departmentPath == null) {
                return Collections.emptyList();
            }
            
            // 查询该部门及所有子部门的用户
            // 使用 path LIKE 'targetPath%' 来匹配所有子部门
            return jdbcTemplate.query(
                "SELECT u.id, u.username, u.display_name, u.full_name, u.department_id, u.email, " +
                "d.name as department_name " +
                "FROM sys_users u " +
                "JOIN sys_departments d ON u.department_id = d.id " +
                "WHERE (d.id = ? OR d.path LIKE ?) " +
                "AND u.deleted = false AND u.status = 'ACTIVE' AND d.status = 'ACTIVE'",
                (rs, rowNum) -> ResolvedUser.builder()
                    .userId(rs.getString("id"))
                    .username(rs.getString("username"))
                    .displayName(rs.getString("display_name") != null ? 
                        rs.getString("display_name") : rs.getString("full_name"))
                    .departmentId(rs.getString("department_id"))
                    .departmentName(rs.getString("department_name"))
                    .email(rs.getString("email"))
                    .build(),
                targetId,
                departmentPath + "/%"
            );
        } catch (Exception e) {
            log.error("Failed to resolve department hierarchy users: {}", targetId, e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public String getTargetDisplayName(String targetId) {
        try {
            String name = jdbcTemplate.queryForObject(
                "SELECT name FROM sys_departments WHERE id = ?",
                String.class,
                targetId
            );
            return name + " (及下级)";
        } catch (Exception e) {
            log.warn("Failed to get department display name: {}", targetId, e);
            return targetId;
        }
    }
    
    @Override
    public long getUserCount(String targetId) {
        try {
            // 首先获取目标部门的path
            String departmentPath = jdbcTemplate.queryForObject(
                "SELECT path FROM sys_departments WHERE id = ?",
                String.class,
                targetId
            );
            
            if (departmentPath == null) {
                return 0;
            }
            
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_users u " +
                "JOIN sys_departments d ON u.department_id = d.id " +
                "WHERE (d.id = ? OR d.path LIKE ?) " +
                "AND u.deleted = false AND u.status = 'ACTIVE' AND d.status = 'ACTIVE'",
                Long.class,
                targetId,
                departmentPath + "/%"
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to count department hierarchy users: {}", targetId, e);
            return 0;
        }
    }
}
