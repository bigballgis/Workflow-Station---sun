package com.portal.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 门户工作台上下文：基于 {@code sys_user_business_unit_roles} 的查询与权限（按 roleId）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalWorkspaceAuthService {

    private final JdbcTemplate jdbcTemplate;

    @Data
    @Builder
    public static class WorkspaceContextRow {
        private String businessUnitId;
        private String roleId;
        private String businessUnitName;
        private String roleCode;
        private String roleName;
    }

    public List<WorkspaceContextRow> listWorkspaceContexts(String userId) {
        String sql = """
                SELECT ubr.business_unit_id, ubr.role_id,
                       bu.name AS bu_name, r.code AS role_code, r.name AS role_name
                FROM sys_user_business_unit_roles ubr
                LEFT JOIN sys_business_units bu ON bu.id = ubr.business_unit_id
                LEFT JOIN sys_roles r ON r.id = ubr.role_id
                WHERE ubr.user_id = ?
                  AND (r.id IS NULL OR r.status IS NULL OR TRIM(COALESCE(r.status, '')) = ''
                       OR UPPER(TRIM(r.status)) = 'ACTIVE')
                ORDER BY bu.name NULLS LAST, r.name NULLS LAST
                """;
        return jdbcTemplate.query(sql, (rs, i) -> WorkspaceContextRow.builder()
                .businessUnitId(rs.getString("business_unit_id"))
                .roleId(rs.getString("role_id"))
                .businessUnitName(rs.getString("bu_name"))
                .roleCode(rs.getString("role_code"))
                .roleName(rs.getString("role_name"))
                .build(), userId);
    }

    public boolean hasContext(String userId, String businessUnitId, String roleId) {
        if (businessUnitId == null || businessUnitId.isBlank() || roleId == null || roleId.isBlank()) {
            return false;
        }
        Integer n = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM sys_user_business_unit_roles ubr
                        LEFT JOIN sys_roles r ON r.id = ubr.role_id
                        WHERE ubr.user_id = ? AND ubr.business_unit_id = ? AND ubr.role_id = ?
                          AND (r.id IS NULL OR r.status IS NULL OR TRIM(COALESCE(r.status, '')) = ''
                               OR UPPER(TRIM(r.status)) = 'ACTIVE')
                        """,
                Integer.class,
                userId, businessUnitId, roleId);
        return n != null && n > 0;
    }

    /**
     * 与 {@link com.platform.security.service.impl.UserRoleServiceImpl#getPermissionsForRole} 一致链路（按 role 主键）。
     */
    public List<String> permissionsForRoleId(String roleId) {
        try {
            List<String> fromDb = jdbcTemplate.queryForList(
                    "SELECT p.code FROM sys_permissions p "
                            + "JOIN sys_role_permissions rp ON p.id = rp.permission_id "
                            + "JOIN sys_roles r ON rp.role_id = r.id "
                            + "WHERE r.id = ? AND r.status = 'ACTIVE'",
                    String.class,
                    roleId);
            if (!fromDb.isEmpty()) {
                return fromDb;
            }
        } catch (Exception e) {
            log.warn("permissionsForRoleId failed for {}: {}", roleId, e.getMessage());
        }
        return List.of("basic:access");
    }
}
