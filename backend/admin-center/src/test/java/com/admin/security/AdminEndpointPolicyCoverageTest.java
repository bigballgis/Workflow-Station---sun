package com.admin.security;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.admin.security.AdminEndpointScan.endpoints;
import static com.admin.security.AdminEndpointScan.policy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard over every Admin Center endpoint's declared access (platform access model WP2, WP9):
 * reads are declared, every known caller is still admitted, no write is opened by accident, and every team-scoped
 * endpoint either names the team resource in its path or is a listed list filter / request-body check.
 * The backend caller lists come from the 2026-10-30 inventory of Portal / engine / DW backends and DW pages; the
 * Technical Lead page calls from the 2026-11-02 inventory of the Admin frontend's {@code src/api} call sites.
 */
class AdminEndpointPolicyCoverageTest {

    /**
     * Writes a Technical Lead may make (TEAM_WRITE: limited to the teams they lead; LEAD_WRITE: any team, §8.2, §8.3).
     * Adding one means deciding that Technical Leads may make it.
     */
    private static final Set<String> TECH_LEAD_WRITES = Set.of(
            // Custom roles, BUs and their bindable roles, BI Audience: every Technical Lead (D-12, D-15, D-50)
            "POST /roles", "PUT /roles/{}", "DELETE /roles/{}",
            "POST /business-units", "PUT /business-units/{}", "PUT /business-units/{}/move", "DELETE /business-units/{}",
            "POST /business-units/{}/roles", "DELETE /business-units/{}/roles/{}",
            "POST /bi/assignments", "PUT /bi/assignments/{}", "DELETE /bi/assignments/{}",
            "PUT /bi/assignments/{}/enabled",
            "POST /function-units/import", "POST /function-units/import/preview",
            // Function units of the teams they lead (D-10, D-49)
            "DELETE /function-units/{}", "POST /function-units/{}/restore", "POST /function-units/{}/deploy",
            "PUT /function-units/{}/enabled", "PUT /function-units/batch/enabled", "DELETE /function-units/batch",
            "POST /function-units/deployments/{}/rollback", "POST /function-units/{}/access",
            "DELETE /function-units/{}/access/{}", "PUT /function-units/{}/access",
            "POST /function-units/{}/audit-access", "DELETE /function-units/{}/audit-access/{}",
            "PUT /sla-policies/{}", "POST /sla-policies/{}/recalculate",
            // Relation tables of the teams they lead (D-47)
            "POST /relation-tables/structures", "PUT /relation-tables/structures/{}",
            "DELETE /relation-tables/structures/{}", "PUT /relation-tables/structures/{}/enabled",
            "PUT /relation-tables/structures/{}/portal-visibility", "POST /relation-tables/structures/{}/deploy",
            "POST /relation-tables/structures/{}/rollback", "POST /relation-tables/structures/{}/access",
            "PUT /relation-tables/structures/{}/access", "PUT /relation-tables/structures/{}/access/{}",
            "DELETE /relation-tables/structures/{}/access/{}",
            "POST /relation-tables/data/{}/import", "POST /relation-tables/data/{}/primary-keys/allocate",
            "POST /relation-tables/data/{}", "PUT /relation-tables/data/{}/{}", "DELETE /relation-tables/data/{}/{}",
            "PUT /relation-tables/data/{}/{}/status", "PUT /relation-tables/data/{}/status/batch",
            // BI Data View assignments of their teams' units (D-15, AC-32)
            "PUT /bi/data-view-assignments/{}/enabled", "POST /bi/data-view-assignments",
            "POST /bi/data-view-assignments/batch", "PUT /bi/data-view-assignments/{}",
            "DELETE /bi/data-view-assignments/{}");

    /**
     * Team-scoped endpoints that do not name a team resource in their path: each filters its list or checks its request
     * body through {@link AdminTeamScope} (unit tests named after each). Anything else team-scoped declares its owner.
     */
    private static final Map<String, String> SCOPED_WITHOUT_OWNER = Map.ofEntries(
            Map.entry("POST /function-units/query", "FunctionUnitListQueryComponent filters by code"),
            Map.entry("POST /function-units/archived/query", "FunctionUnitListQueryComponent filters by code"),
            Map.entry("GET /function-units/deployed/latest", "controller filters by code, rows say canManage"),
            Map.entry("POST /function-units/deployments/query", "FunctionUnitDeploymentListQueryComponent filters"),
            Map.entry("PUT /function-units/batch/enabled", "every id checked before anything changes"),
            Map.entry("DELETE /function-units/batch", "every id checked before anything changes"),
            Map.entry("POST /sla-policies/query", "SlaPolicyComponent filters by code (D-46)"),
            Map.entry("POST /relation-tables/structures", "requireLinkableFunctionUnits on the body (D-47)"),
            Map.entry("GET /relation-tables/structures", "controller keeps the visible tables (ER diagram)"),
            Map.entry("POST /relation-tables/structures/query", "list component appends the visibility filter"),
            Map.entry("GET /relation-tables/data/tables", "data service keeps the visible tables"),
            Map.entry("GET /relation-tables/data/function-units", "data service keeps the visible units"),
            Map.entry("GET /relation-tables/data/{}/search", "lookup search: like the Portal (D-41), not per table (D-52)"),
            Map.entry("GET /relation-tables/data/{}/view-fields", "lookup columns: like the Portal (D-41, D-52)"),
            Map.entry("POST /automation/flows/query", "AutomationFlowListQueryComponent filters workspaces (D-48)"),
            Map.entry("POST /automation/flow-runs/query", "AutomationFlowRunListQueryComponent filters (D-48)"),
            Map.entry("POST /security/user-portal-audit-logs/list-query", "Admin sends the codes to the Portal"),
            Map.entry("GET /security/user-portal-audit-logs/function-units", "controller filters by code"),
            Map.entry("POST /bi/data-view-assignments/query", "list component filters by code (D-46)"),
            Map.entry("GET /bi/data-view-assignments/function-units", "controller filters by code (D-46)"),
            Map.entry("POST /bi/data-view-assignments", "body functionUnitId checked (AC-32)"),
            Map.entry("POST /bi/data-view-assignments/batch", "body functionUnitId checked (AC-32)"));

    /** Paths of team resources (§8.2): a Technical Lead may reach them only team-scoped, or as listed below. */
    private static final List<String> TEAM_RESOURCE_PATHS = List.of(
            "/function-units", "/function-units-import", "/sla-policies", "/relation-tables", "/automation/flows",
            "/automation/flow-runs", "/bi/data-view-assignments", "/security/user-portal-audit-logs");

    /** Team-resource endpoints a Technical Lead reaches without the team check, and why that is right. */
    private static final Map<String, String> TEAM_RESOURCE_UNSCOPED = Map.ofEntries(
            Map.entry("GET /relation-tables/structures/name-available", "names are unique platform-wide (SHARED_READ)"),
            Map.entry("POST /function-units/import", "importer checks the chosen team (D-10, D-61)"),
            Map.entry("POST /function-units/import/preview", "preview reports the team check (D-61)"),
            Map.entry("POST /function-units-import/import", "DW deployment: importer judges the deploying user"),
            Map.entry("POST /function-units/{}/validate", "DW deployment: importer judges the deploying user"),
            Map.entry("POST /function-units-import/{}/deploy", "DW deployment: importer judges the deploying user"));

    /** Where each owner's key sits: the variable right after the collection it names (WP9 review). */
    private static final Map<AdminOwner, List<String>> OWNER_PATHS = Map.of(
            AdminOwner.FUNCTION_UNIT_ID, List.of("/function-units/{id}"),
            AdminOwner.FUNCTION_UNIT_CODE, List.of("/function-units/code/{code}", "/sla-policies/{functionUnitCode}",
                    "/bi/data-view-assignments/function-units/{functionUnitCode}"),
            AdminOwner.FUNCTION_UNIT_DEPLOYMENT, List.of("/function-units/deployments/{deploymentId}"),
            AdminOwner.RELATION_TABLE_ID, List.of("/relation-tables/structures/{id}", "/relation-tables/data/{tableId}"),
            AdminOwner.AUTOMATION_FLOW, List.of("/automation/flows/{flowId}"),
            AdminOwner.AUTOMATION_RUN, List.of("/automation/flow-runs/{runId}"),
            AdminOwner.DATA_VIEW_ASSIGNMENT, List.of("/bi/data-view-assignments/{id}"));

    /**
     * Team-scoped endpoints with a second path variable: the interceptor checks only the owner, so the handler must
     * tie the second key to it (a grant or row of another table must not be reachable through an own table's id).
     */
    private static final Map<String, String> SECOND_VARIABLE_TIED_TO_OWNER = Map.ofEntries(
            Map.entry("DELETE /function-units/{}/access/{}", "FunctionUnitAccessService: grant of another unit refused"),
            Map.entry("DELETE /function-units/{}/audit-access/{}",
                    "FunctionUnitAuditAccessService: grant of another unit refused"),
            Map.entry("PUT /relation-tables/structures/{}/access/{}",
                    "RelationTableAccessServiceImpl.requireOfTable (RelationTableTeamScopeControllerTest)"),
            Map.entry("DELETE /relation-tables/structures/{}/access/{}",
                    "RelationTableAccessServiceImpl.requireOfTable (RelationTableTeamScopeControllerTest)"),
            Map.entry("PUT /relation-tables/data/{}/{}", "rows read and written WHERE table_id = ? AND row_id = ?"),
            Map.entry("DELETE /relation-tables/data/{}/{}", "rows read and deleted WHERE table_id = ? AND row_id = ?"),
            Map.entry("PUT /relation-tables/data/{}/{}/status", "rows read and written WHERE table_id = ? AND row_id = ?"),
            Map.entry("GET /sla-policies/{}/jobs/{}/items", "SlaPolicyComponent joins WHERE j.function_unit_code = ?"),
            Map.entry("GET /bi/data-view-assignments/function-units/{}/tables/{}/views",
                    "BiDataViewMetadataRepository: WHERE v.function_unit_id = ? AND v.main_table_id = ?"));

    @Test
    void scanFoundTheControllers() {
        // About 370 since WP10 removed the unused role-member, permission check / delegation / conflict endpoints.
        assertThat(endpoints()).hasSizeGreaterThan(350);
    }

    @Test
    void everyReadDeclaresItsAudience() {
        List<String> undeclared = endpoints().entrySet().stream()
                .filter(e -> e.getKey().startsWith("GET ") && !e.getValue().policy().declared())
                .map(Map.Entry::getKey)
                .toList();
        assertThat(undeclared).isEmpty();
    }

    @Test
    void backendCallersStillPassWithTheServiceToken() {
        List<String> backendCalls = List.of(
                // user-portal
                "GET /permission-requests", "GET /approvers/check", "GET /virtual-groups/{}/members",
                "GET /business-units/{}/members", "GET /users/{}/memberships", "GET /roles/business",
                "GET /users/{}/roles", "GET /roles/{}", "GET /users/{}", "GET /users", "GET /virtual-groups",
                "GET /virtual-groups/{}", "GET /virtual-groups/{}/role", "GET /users/{}/virtual-groups",
                "GET /business-units/tree", "GET /business-units/{}", "GET /business-units/{}/roles",
                "GET /users/{}/business-units", "GET /approvers/user/{}/virtual-groups",
                "GET /approvers/user/{}/business-units", "GET /approvers/user/{}/is-any",
                "GET /users/{}/business-unit-roles", "GET /users/{}/business-unit-roles/by-business-unit/{}",
                "GET /function-units/{}", "GET /function-units/by-process-key/{}", "GET /function-units/code/{}/latest",
                "GET /function-units", "GET /function-units/deployed/latest", "GET /function-units/{}/access",
                "GET /function-units/{}/audit-access", "GET /function-units/{}/content",
                "GET /function-units/code/{}/active-for-start", "GET /function-units/{}/contents",
                "DELETE /virtual-groups/{}/members/{}", "DELETE /users/{}/business-unit-roles/{}/{}",
                "POST /force-unclaim/evaluate",
                "POST /virtual-groups/{}/members", "POST /business-units/{}/members",
                "POST /users/{}/business-unit-roles", "POST /exit/business-units/{}/users/{}",
                "GET /internal/bi/dashboards", "POST /internal/bi/guest-token", "GET /internal/bi/data-views/dashboards",
                // workflow-engine
                "GET /task-assignment/users/{}/business-unit", "GET /task-assignment/business-units/by-id/{}/code",
                "GET /task-assignment/business-units/{}/parent", "GET /task-assignment/business-units/{}/roles/{}/users",
                "GET /task-assignment/business-units/{}/roles/{}/eligible",
                "GET /task-assignment/business-units/{}/eligible-roles", "GET /task-assignment/roles/bu-bounded",
                "GET /internal/function-units/by-code/{}/id",
                "GET /internal/environment-variables/text-value",
                "GET /internal/function-units/{}/connections/{}/credentials", "GET /internal/function-units/{}/code",
                "GET /internal/system-imap", "GET /automation/flows/resolve",
                // developer-workstation
                "GET /developer-permissions/user/{}", "GET /internal/environment-variables",
                "GET /internal/environment-variables/vault-password", "GET /internal/system-smtp",
                "GET /automation/flows/internal/export", "POST /automation/flows/internal/restore");
        List<String> refused = backendCalls.stream()
                .filter(call -> !admitsService(policy(call)))
                .toList();
        assertThat(refused).isEmpty();
    }

    @Test
    void developerWorkstationPagesStillReachTheirLookups() {
        List<String> pageCalls = List.of(
                "GET /virtual-groups", "GET /business-units/tree", "GET /business-units", "GET /business-units/{}/roles",
                "GET /task-assignment/roles/bu-bounded",
                "GET /users/{}/virtual-groups", "GET /users/{}/roles", "GET /users", "GET /users/{}",
                "GET /internal/ap/token", "GET /internal/ap/flow-key-available", "GET /internal/ap/launch");
        Set<AdminAudience> developerReachable = Set.of(
                AdminAudience.PUBLIC, AdminAudience.SIGNED_IN, AdminAudience.WORKSTATION);
        List<String> refused = pageCalls.stream()
                .filter(call -> !developerReachable.contains(policy(call).audience()))
                .toList();
        assertThat(refused).isEmpty();
    }

    /** What the pages a Technical Lead sees read while loading and browsing (§8.2); none may answer 403. */
    @Test
    void techLeadPagesStillReachTheirReads() {
        List<String> pageCalls = List.of(
                // Dashboard, profile menu
                "GET /dashboard/stats", "GET /dashboard/activities", "GET /dashboard/user-trends",
                "GET /users/{}/virtual-groups", "GET /users/{}/roles",
                // Users, organization, virtual groups, roles
                "POST /users/query", "GET /users", "GET /users/{}", "GET /users/{}/business-units",
                "GET /users/{}/business-unit-roles", "GET /business-units/tree", "GET /business-units/{}",
                "GET /business-units/{}/members", "GET /business-units/{}/roles", "GET /business-units/{}/role-leaders",
                "GET /business-units/{}/references", "GET /approvers/business-units/{}", "GET /roles",
                "GET /roles/business", "POST /virtual-groups/query", "GET /virtual-groups/{}",
                "GET /virtual-groups/{}/members", "GET /approvers/virtual-groups/{}", "POST /roles/query",
                "GET /roles/{}/holders", "GET /roles/{}/references",
                // Function units, SLA
                "POST /function-units/query", "POST /function-units/archived/query",
                "POST /function-units/deployments/query", "GET /function-units/{}/access",
                "GET /function-units/{}/audit-access", "GET /function-units/code/{}/versions",
                "GET /function-units/{}/delete-preview", "GET /function-units/{}/deployments",
                "POST /sla-policies/query", "GET /sla-policies/{}/history", "GET /sla-policies/{}/jobs",
                "GET /sla-policies/{}/jobs/{}/items",
                // Relation tables
                "POST /relation-tables/structures/query", "GET /relation-tables/structures",
                "GET /relation-tables/structures/{}", "GET /relation-tables/structures/{}/versions",
                "GET /relation-tables/structures/{}/access", "GET /relation-tables/structures/name-available",
                "GET /function-units/deployed/latest", "GET /relation-tables/data/tables",
                "GET /relation-tables/data/{}", "POST /relation-tables/data/{}/query",
                "GET /relation-tables/data/{}/export", "GET /relation-tables/data/{}/template",
                "GET /relation-tables/data/{}/search", "GET /relation-tables/data/{}/view-fields",
                "GET /relation-tables/data/{}/audit-logs",
                // Automation, audit, LDAP
                "POST /automation/flows/query", "GET /automation/flows/{}/export", "POST /automation/flow-runs/query",
                "GET /automation/flow-runs/{}", "POST /automation/pieces/query", "GET /automation/pieces/export",
                "POST /security/audit-logs/list-query", "GET /security/audit-logs/{}",
                "GET /security/audit-logs/resource-types", "POST /security/audit-logs/export",
                "POST /security/user-portal-audit-logs/list-query", "GET /security/user-portal-audit-logs/function-units",
                "POST /ldap-sync/audit-logs/query",
                // BI
                "POST /bi/dashboards/query", "GET /bi/dashboards", "POST /bi/assignments/query",
                "POST /bi/data-view-assignments/query", "GET /bi/data-view-assignments/function-units",
                "GET /bi/data-view-assignments/function-units/{}/tables",
                "GET /bi/data-view-assignments/function-units/{}/tables/{}/views", "POST /bi/rbac/mappings/query",
                // Environment variables, settings
                "GET /environment-variables", "GET /configs");
        List<String> refused = pageCalls.stream()
                .filter(call -> !admits(policy(call).audience(), "TECH_LEAD"))
                .toList();
        assertThat(refused).isEmpty();
    }

    @Test
    void sensitiveEndpointsHaveTheirExactPolicy() {
        assertThat(policy("GET /internal/environment-variables/vault-password").audience())
                .isEqualTo(AdminAudience.SERVICE);
        for (String deploy : List.of("POST /function-units-import/import", "POST /function-units/{}/validate",
                "POST /function-units-import/{}/deploy")) {
            AdminEndpointPolicy policy = policy(deploy);
            assertThat(policy.audience()).as(deploy).isEqualTo(AdminAudience.DEPLOYER);
            assertThat(policy.services()).as(deploy + " must judge the deploying user").isFalse();
        }
        // BI writes: Audience by any Technical Lead (D-15), Data View within their teams (AC-32); never AUDITOR (§9).
        for (String audienceWrite : List.of("POST /bi/assignments", "PUT /bi/assignments/{}",
                "DELETE /bi/assignments/{}", "PUT /bi/assignments/{}/enabled")) {
            assertThat(policy(audienceWrite).audience()).as(audienceWrite).isEqualTo(AdminAudience.LEAD_WRITE);
        }
        for (String dataViewWrite : List.of("POST /bi/data-view-assignments", "POST /bi/data-view-assignments/batch",
                "PUT /bi/data-view-assignments/{}", "DELETE /bi/data-view-assignments/{}",
                "PUT /bi/data-view-assignments/{}/enabled")) {
            assertThat(policy(dataViewWrite).audience()).as(dataViewWrite).isEqualTo(AdminAudience.TEAM_WRITE);
            assertThat(policy(dataViewWrite).services()).as(dataViewWrite).isFalse();
        }
        // Members, UBR, approvers and exits stay SYS_ADMIN's (D-13, D-19, AC-29, AC-35); so do group and user writes.
        for (String adminOnly : List.of("POST /business-units/{}/members", "DELETE /business-units/{}/members/{}",
                "POST /users/{}/business-unit-roles", "DELETE /users/{}/business-unit-roles/{}/{}",
                "POST /approvers", "DELETE /approvers/{}", "POST /exit/business-units/{}/users/{}",
                "POST /virtual-groups", "PUT /virtual-groups/{}", "POST /virtual-groups/{}/members",
                "POST /users", "PUT /users/{}", "POST /ldap-sync/full",
                "POST /environment-variables", "PUT /configs/{}", "POST /configs/{}/assess-impact")) {
            assertThat(policy(adminOnly).audience()).as(adminOnly).isEqualTo(AdminAudience.SYS_ADMIN);
        }
        for (String publicCall : List.of("POST /auth/login", "POST /auth/refresh", "POST /auth/logout",
                "POST /sso/login", "POST /internal/sso/redeem", "GET /health/live", "GET /.well-known/health")) {
            assertThat(policy(publicCall).audience()).as(publicCall).isEqualTo(AdminAudience.PUBLIC);
        }
    }

    /**
     * A write opened to more than SYS_ADMIN is a read-shaped POST of a reading audience, a listed service-called write,
     * or a listed Technical Lead write.
     */
    @Test
    void noWriteIsOpenedByAccident() {
        Pattern readShapedPost = Pattern.compile(
                ".*/(query|list-query|export|assess-impact|validate-password|guest-token)$");
        Set<String> serviceCalledWrites = Set.of(
                "DELETE /virtual-groups/{}/members/{}", "DELETE /users/{}/business-unit-roles/{}/{}",
                "POST /force-unclaim/evaluate",
                "POST /virtual-groups/{}/members", "POST /business-units/{}/members",
                "POST /users/{}/business-unit-roles", "POST /exit/business-units/{}/users/{}",
                "POST /internal/bi/guest-token", "POST /automation/flows/internal/restore",
                "POST /function-units-import/import", "POST /function-units/{}/validate",
                "POST /function-units-import/{}/deploy");
        Set<AdminAudience> readingAudiences = Set.of(AdminAudience.SIGNED_IN, AdminAudience.WORKSTATION,
                AdminAudience.ADMIN_READ, AdminAudience.SHARED_READ, AdminAudience.TEAM_READ);
        Set<AdminAudience> techLeadWriteAudiences = Set.of(AdminAudience.TEAM_WRITE, AdminAudience.LEAD_WRITE);
        List<String> unexpected = new ArrayList<>();
        endpoints().forEach((call, endpoint) -> {
            AdminEndpointPolicy policy = endpoint.policy();
            if (isRead(call) || policy.audience() == AdminAudience.PUBLIC) {
                return;
            }
            String path = call.substring(call.indexOf(' ') + 1);
            boolean readShaped = readingAudiences.contains(policy.audience());
            boolean techLeadWrite = techLeadWriteAudiences.contains(policy.audience());
            boolean openToServices = admitsService(policy) || policy.audience() == AdminAudience.DEPLOYER;
            if ((readShaped && !readShapedPost.matcher(path).matches())
                    || (techLeadWrite != TECH_LEAD_WRITES.contains(call))
                    || (openToServices && !serviceCalledWrites.contains(call))) {
                unexpected.add(call + " -> " + policy);
            }
        });
        assertThat(unexpected).isEmpty();
    }

    /** WP6 lesson: a team-scoped endpoint checks the resource its path names, or is a listed filter / body check. */
    @Test
    void teamScopedEndpointsNameTheirOwnerOrAreListed() {
        List<String> problems = new ArrayList<>();
        endpoints().forEach((call, endpoint) -> {
            AdminEndpointPolicy policy = endpoint.policy();
            boolean scoped = policy.audience().teamScoped();
            boolean hasOwner = policy.owner() != AdminOwner.NONE;
            if (!scoped && hasOwner) {
                problems.add(call + ": an owner on a non-team audience is never checked");
            } else if (scoped && hasOwner
                    && !endpoint.rawPath().contains("{" + policy.ownerVariable() + "}")) {
                problems.add(call + ": owner variable " + policy.ownerVariable() + " is not in " + endpoint.rawPath());
            } else if (scoped && !hasOwner && !SCOPED_WITHOUT_OWNER.containsKey(call)) {
                problems.add(call + ": team-scoped without an owner and not listed");
            } else if (hasOwner && SCOPED_WITHOUT_OWNER.containsKey(call)) {
                problems.add(call + ": listed as owner-less but declares an owner");
            }
        });
        SCOPED_WITHOUT_OWNER.keySet().forEach(call -> {
            AdminEndpointPolicy policy = policy(call);
            if (!policy.audience().teamScoped()) {
                problems.add(call + ": listed but not team-scoped");
            }
        });
        assertThat(problems).isEmpty();
    }

    /** A team resource opened to Technical Leads as SHARED_READ / WORKSTATION / SIGNED_IN would skip the team check. */
    @Test
    void teamResourcesReachTechLeadsOnlyTeamScoped() {
        List<String> problems = new ArrayList<>();
        endpoints().forEach((call, endpoint) -> {
            AdminAudience audience = endpoint.policy().audience();
            if (isTeamResource(endpoint.rawPath()) && admits(audience, "TECH_LEAD") && !audience.teamScoped()
                    && !TEAM_RESOURCE_UNSCOPED.containsKey(call)) {
                problems.add(call + " -> " + audience);
            }
        });
        TEAM_RESOURCE_UNSCOPED.keySet().forEach(call -> {
            AdminAudience audience = policy(call).audience();
            if (audience.teamScoped() || !admits(audience, "TECH_LEAD")) {
                problems.add(call + ": listed but " + audience);
            }
        });
        assertThat(problems).isEmpty();
    }

    /** The owner variable is the key of the collection the owner names, not some other id that happens to be there. */
    @Test
    void ownerVariablesSitWhereTheirOwnerIsNamed() {
        List<String> problems = new ArrayList<>();
        endpoints().forEach((call, endpoint) -> {
            AdminEndpointPolicy policy = endpoint.policy();
            if (policy.owner() == AdminOwner.NONE) {
                return;
            }
            String raw = endpoint.rawPath();
            String variable = "{" + policy.ownerVariable() + "}";
            boolean named = OWNER_PATHS.get(policy.owner()).stream()
                    .anyMatch(prefix -> prefix.endsWith(variable)
                            && (raw.equals(prefix) || raw.startsWith(prefix + "/")));
            if (!named) {
                problems.add(call + ": " + policy.owner() + " via {" + policy.ownerVariable() + "} in " + raw);
            }
        });
        assertThat(problems).isEmpty();
    }

    @Test
    void secondPathVariablesOfTeamEndpointsAreReviewed() {
        List<String> found = endpoints().entrySet().stream()
                .filter(e -> e.getValue().policy().audience().teamScoped())
                .filter(e -> e.getKey().indexOf("{}") != e.getKey().lastIndexOf("{}"))
                .map(Map.Entry::getKey)
                .toList();
        assertThat(found).containsExactlyInAnyOrderElementsOf(SECOND_VARIABLE_TIED_TO_OWNER.keySet());
    }

    private static boolean isTeamResource(String rawPath) {
        return TEAM_RESOURCE_PATHS.stream().anyMatch(p -> rawPath.equals(p) || rawPath.startsWith(p + "/"));
    }

    private static boolean isRead(String call) {
        return call.startsWith("GET ") || call.startsWith("HEAD ") || call.startsWith("OPTIONS ")
                || call.startsWith("TRACE ");
    }

    private static boolean admitsService(AdminEndpointPolicy policy) {
        return policy.services() || policy.audience() == AdminAudience.SERVICE;
    }

    static boolean admits(AdminAudience audience, String role) {
        return audience == AdminAudience.PUBLIC || audience == AdminAudience.SIGNED_IN
                || audience.admitsAnyOf(List.of(role));
    }
}
