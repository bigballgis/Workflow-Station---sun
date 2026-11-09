package com.platform.security.service.impl;

import com.platform.security.dto.RoleSource;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.enums.AssignmentTargetType;
import com.platform.security.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户角色服务实现：三端（Admin、Portal、DW）共用的平台角色解析。
 *
 * <p>Platform access model §4.1: each of the four platform roles has exactly one source, and only an
 * active, not deleted user in an ACTIVE group gets it.
 * <ul>
 *   <li>SYS_ADMIN, AUDITOR: the fixed binding ({@code sys_role_assignments}) of the SYSTEM group the
 *       user is a member of.</li>
 *   <li>TECH_LEAD, DEVELOPER: being on the Technical Lead / Developer list of a team (DEVELOPER group).
 *       The role says only that the user is on such a list somewhere; which team it applies to is
 *       decided where the team matters.</li>
 * </ul>
 * Direct user assignments, {@code sys_user_roles} and the bindings of any other group grant nothing.
 * Business roles come from the user's BU + Role (UBR), not from here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private static final String ACTIVE_ROLE =
            "(r.status = 'ACTIVE' OR r.status IS NULL OR TRIM(COALESCE(r.status, '')) = '') ";
    private static final String ACTIVE_USER =
            "u.id = ? AND u.status = 'ACTIVE' AND COALESCE(u.deleted, false) = false ";

    private static final String EFFECTIVE_ROLES_SQL =
            "SELECT r.id AS role_id, r.code, r.name, r.type, vg.id AS group_id, vg.name AS group_name, "
            + "ra.id AS assignment_id "
            + "FROM sys_users u "
            + "JOIN sys_virtual_group_members m ON m.user_id = u.id AND m.list_type = 'MEMBER' "
            + "JOIN sys_virtual_groups vg ON vg.id = m.group_id AND vg.type = 'SYSTEM' AND vg.status = 'ACTIVE' "
            + "JOIN sys_role_assignments ra ON ra.target_type = 'VIRTUAL_GROUP' AND ra.target_id = vg.id "
            + "AND (ra.valid_from IS NULL OR ra.valid_from <= CURRENT_TIMESTAMP) "
            + "AND (ra.valid_to IS NULL OR ra.valid_to >= CURRENT_TIMESTAMP) "
            + "JOIN sys_roles r ON r.id = ra.role_id AND r.code IN ('SYS_ADMIN', 'AUDITOR') AND " + ACTIVE_ROLE
            + "WHERE " + ACTIVE_USER
            + "UNION "
            + "SELECT r.id, r.code, r.name, r.type, vg.id, vg.name, NULL "
            + "FROM sys_users u "
            + "JOIN sys_virtual_group_members m ON m.user_id = u.id AND m.list_type IN ('TECH_LEAD', 'DEVELOPER') "
            + "JOIN sys_virtual_groups vg ON vg.id = m.group_id AND vg.type = 'DEVELOPER' AND vg.status = 'ACTIVE' "
            + "JOIN sys_roles r ON r.code = m.list_type AND " + ACTIVE_ROLE
            + "WHERE " + ACTIVE_USER
            + "ORDER BY 2, 6";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<UserEffectiveRole> getEffectiveRolesForUser(String userId) {
        Map<String, UserEffectiveRole> roleMap = new LinkedHashMap<>();
        jdbcTemplate.query(EFFECTIVE_ROLES_SQL, rs -> {
            RoleSource source = RoleSource.builder()
                    .sourceType(AssignmentTargetType.VIRTUAL_GROUP)
                    .sourceId(rs.getString("group_id"))
                    .sourceName(rs.getString("group_name"))
                    .assignmentId(rs.getString("assignment_id"))
                    .build();
            String roleId = rs.getString("role_id");
            UserEffectiveRole role = roleMap.get(roleId);
            if (role == null) {
                role = UserEffectiveRole.builder()
                        .roleId(roleId)
                        .roleCode(rs.getString("code"))
                        .roleName(rs.getString("name"))
                        .roleType(rs.getString("type"))
                        .build();
                roleMap.put(roleId, role);
            }
            role.addSource(source);
        }, userId, userId);
        log.debug("User {} has {} effective roles", userId, roleMap.size());
        return new ArrayList<>(roleMap.values());
    }

    @Override
    public List<String> getEffectiveRoleCodesForUser(String userId) {
        return getEffectiveRolesForUser(userId).stream()
                .map(UserEffectiveRole::getRoleCode)
                .distinct()
                .collect(Collectors.toList());
    }
    
    @Override
    public List<String> getPermissionsForUser(String userId) {
        List<String> roleCodes = getEffectiveRoleCodesForUser(userId);
        if (roleCodes.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 根据角色代码获取权限
        Set<String> permissions = new HashSet<>();
        for (String roleCode : roleCodes) {
            permissions.addAll(getPermissionsForRole(roleCode));
        }
        
        return new ArrayList<>(permissions);
    }
    
    @Override
    public boolean hasRole(String userId, String roleCode) {
        return getEffectiveRoleCodesForUser(userId).contains(roleCode);
    }
    
    @Override
    public boolean hasPermission(String userId, String permission) {
        return getPermissionsForUser(userId).contains(permission);
    }
    
    @Override
    public List<String> getPermissionsForRoleCodes(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of("basic:access");
        }
        
        Set<String> permissions = new HashSet<>();
        for (String roleCode : roleCodes) {
            permissions.addAll(getPermissionsForRole(roleCode));
        }
        
        if (permissions.isEmpty()) {
            permissions.add("basic:access");
        }
        
        return new ArrayList<>(permissions);
    }
    
    /**
     * 获取角色的权限列表（从数据库查询）
     */
    private List<String> getPermissionsForRole(String roleCode) {
        try {
            // 从数据库查询角色对应的权限
            // 通过 sys_roles -> sys_role_permissions -> sys_permissions 关联查询
            List<String> permissions = jdbcTemplate.queryForList(
                "SELECT p.code FROM sys_permissions p " +
                "JOIN sys_role_permissions rp ON p.id = rp.permission_id " +
                "JOIN sys_roles r ON rp.role_id = r.id " +
                "WHERE r.code = ? AND r.status = 'ACTIVE'",
                String.class,
                roleCode
            );
            
            if (!permissions.isEmpty()) {
                log.debug("Found {} permissions for role {} from database", permissions.size(), roleCode);
                return permissions;
            }
            
            // 如果数据库中没有配置权限，返回基本权限
            log.debug("No permissions found in database for role {}, returning basic access", roleCode);
            return List.of("basic:access");
            
        } catch (Exception e) {
            log.warn("Failed to get permissions for role {}: {}", roleCode, e.getMessage());
            return List.of("basic:access");
        }
    }
}
