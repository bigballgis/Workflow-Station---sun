package com.admin.component;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Current administrator grants for protected operations; legacy profile queries stay unchanged. */
@Component
public class AssignmentManagementAuthority {
    private final JdbcTemplate jdbc;

    public AssignmentManagementAuthority(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean systemAdministrator(String userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                WITH observed AS MATERIALIZED (SELECT clock_timestamp() AS at),
                active_groups AS (
                    SELECT g.id FROM sys_virtual_groups g JOIN sys_virtual_group_members m ON m.group_id=g.id
                    WHERE m.user_id=? AND g.status='ACTIVE'
                )
                SELECT EXISTS (
                    SELECT 1 FROM sys_roles r CROSS JOIN observed t
                    WHERE r.code='SYS_ADMIN' AND r.type='ADMIN' AND r.status='ACTIVE' AND (
                        EXISTS (SELECT 1 FROM sys_user_roles ur WHERE ur.role_id=r.id AND ur.user_id=?
                            AND (ur.valid_from IS NULL OR ur.valid_from<=t.at)
                            AND (ur.valid_to IS NULL OR ur.valid_to>t.at))
                        OR EXISTS (SELECT 1 FROM sys_role_assignments ra WHERE ra.role_id=r.id
                            AND ((ra.target_type='USER' AND ra.target_id=?)
                                OR (ra.target_type='VIRTUAL_GROUP' AND ra.target_id IN (SELECT id FROM active_groups)))
                            AND (ra.valid_from IS NULL OR ra.valid_from<=t.at)
                            AND (ra.valid_to IS NULL OR ra.valid_to>t.at))
                        OR EXISTS (SELECT 1 FROM sys_virtual_group_roles vr WHERE vr.role_id=r.id
                            AND vr.virtual_group_id IN (SELECT id FROM active_groups))
                        OR EXISTS (SELECT 1 FROM sys_user_business_unit_roles ubr WHERE ubr.role_id=r.id AND ubr.user_id=?)
                    )
                )
                """, Boolean.class, userId, userId, userId, userId));
    }
}
