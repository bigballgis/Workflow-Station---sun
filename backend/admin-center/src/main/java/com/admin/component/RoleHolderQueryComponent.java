package com.admin.component;

import com.admin.dto.response.RoleHolder;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.RoleNotFoundException;
import com.admin.repository.RoleRepository;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Who holds a role, read from where the platform role resolution and the BU + Role model take it (§4.1, §5.3), so the
 * Role page no longer depends on a virtual group bound to the role:
 * <ul>
 *   <li>SYS_ADMIN / AUDITOR — members of the active SYSTEM groups the role is assigned to;</li>
 *   <li>TECH_LEAD / DEVELOPER — the role's list of every active team;</li>
 *   <li>a business role — its business unit memberships (UBR).</li>
 * </ul>
 * Counted like {@code UserRoleServiceImpl} resolves roles: only active, undeleted users, assignments within their
 * validity, and nobody for an inactive role.
 */
@Component
@RequiredArgsConstructor
public class RoleHolderQueryComponent {

    private static final String USER_COLUMNS = "SELECT DISTINCT u.id, u.username, u.full_name, u.employee_id,"
            + " u.email, u.status, ";
    private static final String ACTIVE_USER = " JOIN sys_users u ON u.id = %s AND u.status = 'ACTIVE'"
            + " AND COALESCE(u.deleted, false) = false";

    static final String SYSTEM_GROUP_SQL = USER_COLUMNS + "'GROUP' AS scope_type, vg.name AS scope_name"
            + " FROM sys_role_assignments ra"
            + " JOIN sys_virtual_groups vg ON vg.id = ra.target_id AND vg.type = 'SYSTEM' AND vg.status = 'ACTIVE'"
            + " JOIN sys_virtual_group_members m ON m.group_id = vg.id AND m.list_type = 'MEMBER'"
            + ACTIVE_USER.formatted("m.user_id")
            + " WHERE ra.target_type = 'VIRTUAL_GROUP' AND ra.role_id = ?"
            + " AND (ra.valid_from IS NULL OR ra.valid_from <= CURRENT_TIMESTAMP)"
            + " AND (ra.valid_to IS NULL OR ra.valid_to >= CURRENT_TIMESTAMP)"
            + " ORDER BY scope_name, u.username";
    static final String TEAM_LIST_SQL = USER_COLUMNS + "'TEAM' AS scope_type, vg.name AS scope_name"
            + " FROM sys_virtual_group_members m"
            + " JOIN sys_virtual_groups vg ON vg.id = m.group_id AND vg.type = 'DEVELOPER' AND vg.status = 'ACTIVE'"
            + ACTIVE_USER.formatted("m.user_id")
            + " WHERE m.list_type = ?"
            + " ORDER BY scope_name, u.username";
    static final String BUSINESS_UNIT_SQL = USER_COLUMNS + "'BUSINESS_UNIT' AS scope_type, bu.name AS scope_name"
            + " FROM sys_user_business_unit_roles ubr"
            + " JOIN sys_business_units bu ON bu.id = ubr.business_unit_id"
            + ACTIVE_USER.formatted("ubr.user_id")
            + " WHERE ubr.role_id = ?"
            + " ORDER BY scope_name, u.username";

    private static final RowMapper<RoleHolder> ROW = (rs, n) -> new RoleHolder(rs.getString("id"),
            rs.getString("username"), rs.getString("full_name"), rs.getString("employee_id"), rs.getString("email"),
            rs.getString("status"), rs.getString("scope_type"), rs.getString("scope_name"));

    private final RoleRepository roleRepository;
    private final JdbcTemplate jdbcTemplate;
    private final I18nService i18nService;

    public List<RoleHolder> holders(String roleId) {
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RoleNotFoundException(roleId));
        String type = role.getType() == null ? "" : role.getType();
        List<RoleHolder> holders = switch (type) {
            case "ADMIN", "AUDITOR" -> jdbcTemplate.query(SYSTEM_GROUP_SQL, ROW, role.getId());
            case "DEVELOPER" -> jdbcTemplate.query(TEAM_LIST_SQL, ROW, role.getCode());
            // A business role is held per business unit.
            case "BU_BOUNDED" -> jdbcTemplate.query(BUSINESS_UNIT_SQL, ROW, role.getId());
            default -> throw new AdminBusinessException("ROLE_TYPE_UNKNOWN",
                    i18nService.getMessage("admin.role.type_unknown", role.getCode(), role.getType()));
        };
        return isActive(role) ? holders : List.of();
    }

    /** Blank status counts as active, as in {@code UserRoleServiceImpl} and the Portal workspace. */
    private static boolean isActive(Role role) {
        String status = role.getStatus();
        return status == null || status.isBlank() || "ACTIVE".equalsIgnoreCase(status.trim());
    }
}
