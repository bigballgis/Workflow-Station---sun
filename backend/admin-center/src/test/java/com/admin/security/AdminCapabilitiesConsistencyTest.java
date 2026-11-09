package com.admin.security;

import com.admin.security.AdminCapabilities.Feature;
import com.admin.security.AdminCapabilities.Level;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.admin.security.AdminEndpointScan.policy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The capability table the frontend shows pages and buttons by must match what the endpoints admit (WP9 decision 6):
 * a page is visible exactly when its reads admit the role, writable exactly when its writes do, and TEAM exactly when
 * those writes are limited to the caller's teams.
 */
class AdminCapabilitiesConsistencyTest {

    /** Representative reads and writes of each page. */
    private record Page(List<String> reads, List<String> writes) {
    }

    private static final Map<Feature, Page> PAGES = new EnumMap<>(Map.ofEntries(
            Map.entry(Feature.DASHBOARD, new Page(List.of("GET /dashboard/stats", "GET /dashboard/activities"), List.of())),
            Map.entry(Feature.USERS, new Page(List.of("POST /users/query"), List.of("POST /users", "PUT /users/{}"))),
            Map.entry(Feature.ORGANIZATION, new Page(List.of("GET /business-units/tree"),
                    List.of("POST /business-units", "PUT /business-units/{}", "POST /business-units/{}/roles"))),
            Map.entry(Feature.ORGANIZATION_MEMBERS, new Page(List.of("GET /business-units/{}/members",
                    "GET /approvers/business-units/{}"), List.of("POST /business-units/{}/members", "POST /approvers",
                    "POST /users/{}/business-unit-roles"))),
            Map.entry(Feature.VIRTUAL_GROUPS, new Page(List.of("POST /virtual-groups/query"),
                    List.of("POST /virtual-groups", "POST /virtual-groups/{}/members"))),
            Map.entry(Feature.ROLES, new Page(List.of("POST /roles/query", "GET /roles/{}/holders"),
                    List.of("POST /roles", "PUT /roles/{}", "DELETE /roles/{}"))),
            Map.entry(Feature.FUNCTION_UNITS, new Page(List.of("POST /function-units/query"),
                    List.of("POST /function-units/{}/deploy", "PUT /function-units/{}/enabled", "DELETE /function-units/{}"))),
            Map.entry(Feature.SLA_POLICIES, new Page(List.of("POST /sla-policies/query"),
                    List.of("PUT /sla-policies/{}"))),
            Map.entry(Feature.RELATION_TABLES, new Page(List.of("POST /relation-tables/structures/query",
                    "GET /relation-tables/data/tables"), List.of("PUT /relation-tables/structures/{}",
                    "POST /relation-tables/data/{}"))),
            Map.entry(Feature.AUTOMATION_FLOWS, new Page(List.of("POST /automation/flows/query"),
                    List.of("POST /automation/flows/import"))),
            Map.entry(Feature.AUTOMATION_RUNS, new Page(List.of("POST /automation/flow-runs/query"), List.of())),
            Map.entry(Feature.AUTOMATION_PIECES, new Page(List.of("POST /automation/pieces/query"),
                    List.of("POST /automation/pieces/import"))),
            Map.entry(Feature.AUDIT_ADMIN_CENTER, new Page(List.of("POST /security/audit-logs/list-query"), List.of())),
            Map.entry(Feature.AUDIT_USER_PORTAL, new Page(List.of("POST /security/user-portal-audit-logs/list-query"),
                    List.of())),
            Map.entry(Feature.BI_DASHBOARD_REGISTRY, new Page(List.of("POST /bi/dashboards/query"),
                    List.of("POST /bi/dashboards/sync"))),
            Map.entry(Feature.BI_AUDIENCE_ASSIGNMENT, new Page(List.of("POST /bi/assignments/query"),
                    List.of("POST /bi/assignments"))),
            Map.entry(Feature.BI_DATA_VIEW_ASSIGNMENT, new Page(List.of("POST /bi/data-view-assignments/query"),
                    List.of("POST /bi/data-view-assignments"))),
            Map.entry(Feature.BI_RBAC_MAPPING, new Page(List.of("POST /bi/rbac/mappings/query"),
                    List.of("POST /bi/rbac/mappings"))),
            Map.entry(Feature.ENVIRONMENT_VARIABLES, new Page(List.of("GET /environment-variables"),
                    List.of("POST /environment-variables"))),
            Map.entry(Feature.SYSTEM_CONFIG, new Page(List.of("GET /configs"), List.of("PUT /configs/{}"))),
            Map.entry(Feature.LDAP_SYNC, new Page(List.of("POST /ldap-sync/audit-logs/query"),
                    List.of("POST /ldap-sync/full")))));

    @ParameterizedTest
    @ValueSource(strings = {"SYS_ADMIN", "AUDITOR", "TECH_LEAD"})
    void everyPagesLevelMatchesItsEndpoints(String role) {
        assertThat(PAGES.keySet()).containsExactlyInAnyOrder(Feature.values());
        List<String> mismatches = new ArrayList<>();
        PAGES.forEach((feature, page) -> {
            Level level = AdminCapabilities.levelOf(feature, List.of(role));
            boolean reads = page.reads().stream().allMatch(call -> admits(call, role));
            boolean writes = !page.writes().isEmpty() && page.writes().stream().allMatch(call -> admits(call, role));
            boolean teamWrites = writes && !"SYS_ADMIN".equals(role)
                    && page.writes().stream().allMatch(call -> policy(call).audience().teamScoped());
            // A System Administrator is WRITE everywhere, read-only pages included (nothing there to write).
            boolean sysAdminOfReadOnlyPage = "SYS_ADMIN".equals(role) && page.writes().isEmpty();
            Level expected = !reads ? Level.NONE
                    : sysAdminOfReadOnlyPage ? Level.WRITE
                    : !writes ? Level.READ : teamWrites ? Level.TEAM : Level.WRITE;
            if (level != expected) {
                mismatches.add(feature + ": table says " + level + ", endpoints give " + expected);
            }
        });
        assertThat(mismatches).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEVELOPER", "MANAGER"})
    void rolesThatCannotSignInGetNothing(String role) {
        assertThat(AdminCapabilities.forRoles(List.of(role))).allSatisfy((key, level) -> assertThat(level)
                .as(key).isEqualTo(Level.NONE));
    }

    @org.junit.jupiter.api.Test
    void identitiesAddUpToTheHighestLevel() {
        Map<String, Level> auditorLead = AdminCapabilities.forRoles(List.of("AUDITOR", "TECH_LEAD"));
        assertThat(auditorLead).containsEntry("functionUnits", Level.TEAM)
                .containsEntry("users", Level.READ)
                .containsEntry("organization", Level.WRITE);
        assertThat(AdminCapabilities.forRoles(List.of("AUDITOR", "SYS_ADMIN")))
                .allSatisfy((key, level) -> assertThat(level).as(key).isEqualTo(Level.WRITE));
    }

    private static boolean admits(String call, String role) {
        return AdminEndpointPolicyCoverageTest.admits(policy(call).audience(), role);
    }
}
