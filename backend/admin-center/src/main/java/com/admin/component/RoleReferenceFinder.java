package com.admin.component;

import com.admin.dto.response.ReferenceNotice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Where a role is still used (platform access model §4.3, §8.3): BU memberships and bindings, access configuration,
 * BI, the task assignments of published processes and the access rules of published views. Processes and views name
 * the role by code, or by id in older definitions.
 *
 * <ul>
 *   <li>{@link #find} — one line per kind, so that deleting a role in use is refused with the places to fix first.</li>
 *   <li>{@link #findForNotice} — the function units that use it, shown before a change or delete (notice only).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RoleReferenceFinder {

    private static final String FU_ACCESS = "sys_function_unit_access";
    private static final String FU_AUDIT_ACCESS = "sys_function_unit_audit_access";

    /** Tables that name a role in {@code target_id} (by id or code) when {@code target_type = 'ROLE'}. */
    private static final List<String[]> ROLE_TARGET_TABLES = List.of(
            new String[] {FU_ACCESS, "admin.role.ref_fu_access"},
            new String[] {FU_AUDIT_ACCESS, "admin.role.ref_fu_audit_access"},
            new String[] {"rt_table_access", "admin.role.ref_rt_access"},
            new String[] {"bi_dashboard_assignment", "admin.role.ref_bi_assignment"});

    /**
     * A task assignment names its roles in {@code roleIds} (all of them, comma separated) and the first one again in
     * {@code roleId}; the engine reads {@code roleIds}.
     */
    private static final Set<String> ROLE_PROPERTIES = Set.of("roleId", "roleIds");

    private final JdbcTemplate jdbcTemplate;
    private final I18nService i18nService;
    private final ObjectMapper objectMapper;

    /** One line per kind of reference, e.g. "BU memberships: 3"; empty when nothing uses the role. */
    public List<String> find(Role role) {
        List<String> references = new ArrayList<>();
        countNonFunctionUnitReferences(references, role, true);
        Usage usage = publishedUsage(role);
        if (!usage.processes().isEmpty()) {
            references.add(i18nService.getMessage("admin.role.ref_processes", String.join(", ", usage.processes())));
        }
        if (!usage.views().isEmpty()) {
            references.add(i18nService.getMessage("admin.role.ref_views", String.join(", ", usage.views())));
        }
        return references;
    }

    /**
     * The function units whose access, audit access, published processes or published views name the role, by code
     * and current name, plus one line per other kind of reference.
     */
    public ReferenceNotice findForNotice(Role role) {
        List<String> others = new ArrayList<>();
        countNonFunctionUnitReferences(others, role, false);
        Set<String> codes = new TreeSet<>(publishedUsage(role).codes());
        for (String table : List.of(FU_ACCESS, FU_AUDIT_ACCESS)) {
            codes.addAll(jdbcTemplate.queryForList("SELECT DISTINCT fu.code FROM " + table + " a "
                            + "JOIN sys_function_units fu ON fu.id = a.function_unit_id "
                            + "WHERE a.target_type = 'ROLE' AND a.target_id IN (?, ?)",
                    String.class, role.getId(), role.getCode()));
        }
        return new ReferenceNotice(PublishedContents.currentNames(jdbcTemplate, codes), others);
    }

    /** @param withFunctionUnitAccess also count FU access / audit access rows (the notice lists their units instead) */
    private void countNonFunctionUnitReferences(List<String> references, Role role, boolean withFunctionUnitAccess) {
        count(references, "admin.role.ref_ubr",
                "SELECT COUNT(*) FROM sys_user_business_unit_roles WHERE role_id = ?", role.getId());
        count(references, "admin.role.ref_bu_binding",
                "SELECT COUNT(*) FROM sys_business_unit_roles WHERE role_id = ?", role.getId());
        for (String[] table : ROLE_TARGET_TABLES) {
            if (!withFunctionUnitAccess && (FU_ACCESS.equals(table[0]) || FU_AUDIT_ACCESS.equals(table[0]))) {
                continue;
            }
            count(references, table[1], "SELECT COUNT(*) FROM " + table[0]
                    + " WHERE target_type = 'ROLE' AND target_id IN (?, ?)", role.getId(), role.getCode());
        }
        count(references, "admin.role.ref_bi_rbac",
                "SELECT COUNT(*) FROM bi_rbac_mapping WHERE sys_role_id = ?", role.getId());
    }

    /** Published processes and views that name the role: unit names for the refusal, codes for the notice. */
    private record Usage(Set<String> processes, Set<String> views, Set<String> codes) {
    }

    private Usage publishedUsage(Role role) {
        Set<String> names = Set.of(role.getId(), role.getCode());
        Usage usage = new Usage(new TreeSet<>(), new TreeSet<>(), new TreeSet<>());
        for (PublishedContents.Content content : PublishedContents.mayName(jdbcTemplate,
                List.of(role.getCode(), role.getId()))) {
            if (content.process() ? assignsRole(content.data(), names) : grantsViewToRole(content, names)) {
                (content.process() ? usage.processes() : usage.views()).add(content.functionUnitName());
                usage.codes().add(content.functionUnitCode());
            }
        }
        return usage;
    }

    private void count(List<String> references, String labelKey, String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        if (count != null && count > 0) {
            references.add(i18nService.getMessage(labelKey, count));
        }
    }

    /** Whether a task assignment of the process names the role, in {@code roleId} or anywhere in {@code roleIds}. */
    static boolean assignsRole(String bpmn, Set<String> roleNames) {
        for (Map.Entry<String, String> property : PublishedContents.properties(bpmn)) {
            if (!ROLE_PROPERTIES.contains(property.getKey())) {
                continue;
            }
            for (String value : property.getValue().split(",")) {
                if (roleNames.contains(value.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether an access rule of a published view targets the role. */
    private boolean grantsViewToRole(PublishedContents.Content views, Set<String> roleNames) {
        for (Map<?, ?> rule : PublishedContents.viewAccessRules(objectMapper, views)) {
            if ("ROLE".equals(rule.get("targetType")) && rule.get("targetId") instanceof String id
                    && roleNames.contains(id)) {
                return true;
            }
        }
        return false;
    }
}
