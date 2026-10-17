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
    record Pool(Target resolved, Set<String> members) { }
    private static final String ROLE_RELATION = """
            FROM sys_business_units b
            JOIN sys_business_unit_roles br ON br.business_unit_id = b.id
            JOIN sys_roles r ON r.id = br.role_id
            """;
    private static final String ROLE_FILTER = """
            WHERE (b.code, r.code) IN (:targets)
              AND b.status = 'ACTIVE' AND r.status = 'ACTIVE' AND r.type = 'BU_BOUNDED'
            """;
    static void requireEligible(Collection<Target> targets, Map<String, Object> baseline, JdbcTemplate jdbc) {
        if (targets.isEmpty()) return;
        Set<Target> requested = new LinkedHashSet<>(resolveTargets(targets, baseline, jdbc).values());
        Set<Target> eligible = new LinkedHashSet<>(new NamedParameterJdbcTemplate(jdbc).query(
                "SELECT DISTINCT b.code AS bu_code, r.code AS role_code " + ROLE_RELATION + ROLE_FILTER + """
                  AND EXISTS (SELECT 1 FROM sys_user_business_unit_roles ur
                    JOIN sys_users u ON u.id = ur.user_id
                    WHERE ur.business_unit_id = b.id AND ur.role_id = r.id
                      AND u.status = 'ACTIVE' AND u.deleted = false)
                """, parameters(requested),
                (row, index) -> new Target(row.getString("bu_code"), row.getString("role_code"))));
        if (!eligible.containsAll(requested)) throw invalid();
    }
    static Map<Target, Pool> resolvePools(Collection<Target> targets, Map<String, Object> baseline, JdbcTemplate jdbc) {
        if (targets.isEmpty()) return Map.of();
        var resolved = resolveTargets(targets, baseline, jdbc);
        var rows = new NamedParameterJdbcTemplate(jdbc).query(
                "SELECT DISTINCT b.code AS bu_code, r.code AS role_code, u.id AS user_id " + ROLE_RELATION + """
                JOIN sys_user_business_unit_roles ur ON ur.business_unit_id = b.id AND ur.role_id = r.id
                JOIN sys_users u ON u.id = ur.user_id
                """ + ROLE_FILTER + " AND u.status = 'ACTIVE' AND u.deleted = false", parameters(resolved.values()),
                (row, index) -> Map.entry(new Target(row.getString("bu_code"), row.getString("role_code")), row.getString("user_id")));
        Map<Target, Set<String>> members = new java.util.LinkedHashMap<>();
        for (var row : rows) {
            if (!valid(row.getValue())) throw invalid();
            members.computeIfAbsent(row.getKey(), key -> new LinkedHashSet<>()).add(row.getValue());
        }
        Map<Target, Pool> result = new java.util.LinkedHashMap<>();
        for (var entry : resolved.entrySet()) {
            Set<String> people = members.get(entry.getValue());
            if (people == null || people.isEmpty()) throw invalid();
            result.put(entry.getKey(), new Pool(entry.getValue(), Set.copyOf(people)));
        }
        return Map.copyOf(result);
    }
    private static Map<Target, Target> resolveTargets(Collection<Target> targets, Map<String, Object> baseline, JdbcTemplate jdbc) {
        String contextCode = targets.stream().anyMatch(target -> target.businessUnitCode() == null) ? workspaceCode(baseline, jdbc) : null;
        Map<Target, Target> requested = new java.util.LinkedHashMap<>();
        for (Target target : targets) {
            String bu = target.businessUnitCode() == null ? contextCode : target.businessUnitCode();
            if (!valid(bu) || !valid(target.roleCode())) throw invalid();
            requested.put(target, new Target(bu, target.roleCode()));
        }
        return requested;
    }
    private static Map<String, Object> parameters(Collection<Target> targets) {
        return Map.of("targets", targets.stream().distinct()
                .map(target -> new Object[] { target.businessUnitCode(), target.roleCode() }).toList());
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
