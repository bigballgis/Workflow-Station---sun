package com.portal.component;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.portal.exception.PortalException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Current BU/Role admission and non-empty active membership, without changing a role pool into a person. */
final class SubTableBatchRoleEligibility {
    private SubTableBatchRoleEligibility() { }
    record Target(String businessUnitCode, String roleCode) { }
    static void requireEligible(Collection<Target> targets, Map<String, Object> baseline, JdbcTemplate jdbc) {
        if (targets.isEmpty()) return;
        String contextCode = targets.stream().anyMatch(target -> target.businessUnitCode() == null)
                ? workspaceCode(baseline, jdbc) : null;
        Set<Target> requested = new LinkedHashSet<>();
        for (Target target : targets) {
            String bu = target.businessUnitCode() == null ? contextCode : target.businessUnitCode();
            if (!valid(bu) || !valid(target.roleCode())) throw invalid();
            requested.add(new Target(bu, target.roleCode()));
        }
        Set<Target> eligible = new LinkedHashSet<>(new NamedParameterJdbcTemplate(jdbc).query("""
                SELECT DISTINCT b.code AS bu_code, r.code AS role_code
                FROM sys_business_units b
                JOIN sys_business_unit_roles br ON br.business_unit_id = b.id
                JOIN sys_roles r ON r.id = br.role_id
                WHERE b.code IN (:bus) AND r.code IN (:roles)
                  AND b.status = 'ACTIVE' AND r.status = 'ACTIVE' AND r.type = 'BU_BOUNDED'
                  AND EXISTS (SELECT 1 FROM sys_user_business_unit_roles ur
                    JOIN sys_users u ON u.id = ur.user_id
                    WHERE ur.business_unit_id = b.id AND ur.role_id = r.id
                      AND u.status = 'ACTIVE' AND u.deleted = false)
                """, Map.of("bus", requested.stream().map(Target::businessUnitCode).toList(),
                "roles", requested.stream().map(Target::roleCode).toList()),
                (row, index) -> new Target(row.getString("bu_code"), row.getString("role_code"))));
        if (!eligible.containsAll(requested)) throw invalid();
    }
    private static String workspaceCode(Map<String, Object> baseline, JdbcTemplate jdbc) {
        Object id = baseline.get("activeBusinessUnitId");
        if (!(id instanceof String text) || !valid(text)) throw invalid();
        var codes = jdbc.queryForList("SELECT code FROM sys_business_units WHERE id = ? AND status = 'ACTIVE'", String.class, text);
        if (codes.size() != 1 || !valid(codes.getFirst())) throw invalid();
        return codes.getFirst();
    }
    private static boolean valid(String value) { return value != null && !value.isBlank() && value.equals(value.trim()); }
    private static PortalException invalid() { return new PortalException("403", "Initial assignment role target is not currently eligible"); }
}
