package com.admin.security;

import com.admin.bi.repository.BiDataViewMetadataRepository;
import com.admin.exception.AdminForbiddenException;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.servicetask.ApWorkspaceSql;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.VirtualGroup;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Which team resources the current Admin caller may read and change (platform access model §8.2, WP9). The single
 * place that turns "the teams this Technical Lead leads" into list filters, row flags and refusals; nothing else
 * judges team scope.
 *
 * <ul>
 *   <li>Service calls and System Administrators read and change everything; Auditors read everything (D-43).</li>
 *   <li>A Technical Lead reads and changes what the teams they lead own, looked up once per request (§7.1: leaving the
 *       list takes effect on the next request). An Auditor who also leads a team changes only that team's (§4.4).</li>
 *   <li>A function unit belongs to the team of its code's latest imported row; one without a team is only a System
 *       Administrator's (C-5, D-45). A relation table belongs to the teams of the units it is linked to; a Common table
 *       (no link) and the Public automation workspace are readable by every Technical Lead and changed by a System
 *       Administrator only (D-47, D-48).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AdminTeamScope {

    static final String RELATION_TABLE_EXISTS_SQL = "SELECT COUNT(*) FROM rt_table_definitions WHERE id = ?";
    /** A link to a catalog row that is gone yields a null code: an owner without a team, never "Common". */
    static final String RELATION_TABLE_OWNER_CODES_SQL = "SELECT f.code FROM rt_table_function_units l"
            + " LEFT JOIN sys_function_units f ON f.id = l.function_unit_id WHERE l.relation_table_id = ?";
    static final String DEPLOYMENT_CODE_SQL = "SELECT fu.code FROM sys_function_unit_deployments d"
            + " JOIN sys_function_units fu ON fu.id = d.function_unit_id WHERE d.id = ?";
    private static final String FLOW_WORKSPACE_FROM = " FROM flow f JOIN project p ON p.id = f.\"projectId\"";
    private static final String RUN_WORKSPACE_FROM = " FROM flow_run r JOIN project p ON p.id = r.\"projectId\"";

    private static final String TEAM_TYPE = "DEVELOPER";
    private static final String LED_TEAMS = AdminTeamScope.class.getName() + ".ledTeams";
    private static final String VISIBLE_CODES = AdminTeamScope.class.getName() + ".visibleCodes";
    private static final String MANAGEABLE_CODES = AdminTeamScope.class.getName() + ".manageableCodes";

    private final VirtualGroupMemberRepository memberRepository;
    private final VirtualGroupRepository virtualGroupRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ApWorkspaceSql workspaceSql;
    private final ServiceTaskProperties serviceTaskProperties;
    private final BiDataViewMetadataRepository biMetadata;
    private final I18nService i18nService;

    /**
     * Who owns a team resource.
     *
     * @param exists    false when the resource is not there: the handler answers 404 itself
     * @param everyTeam a Common relation table or the Public workspace
     * @param teamCodes owning teams; a null entry is an owner without a team
     */
    record Owners(boolean exists, boolean everyTeam, List<String> teamCodes) {
        static final Owners MISSING = new Owners(false, false, List.of());
        static final Owners EVERY_TEAM = new Owners(true, true, List.of());

        static Owners team(String teamCode) {
            return new Owners(true, false, Collections.singletonList(teamCode));
        }
    }

    public AdminCaller caller() {
        return AdminCaller.of(currentRequest());
    }

    /** Ids of the active teams the caller leads; empty for a service call. */
    public Set<String> ledTeamIds() {
        return ledTeams(currentRequest()).stream().map(VirtualGroupMemberRepository.LedTeam::getId)
                .collect(Collectors.toSet());
    }

    /** Function unit codes the caller may see; null = every code. */
    public Set<String> visibleFunctionUnitCodes() {
        HttpServletRequest request = currentRequest();
        return AdminCaller.of(request).readsAllTeams() ? null
                : memo(request, VISIBLE_CODES, () -> codesOfLedTeams(request));
    }

    /** Function unit codes the caller may change; null = every code. */
    public Set<String> manageableFunctionUnitCodes() {
        HttpServletRequest request = currentRequest();
        return AdminCaller.of(request).writesAllTeams() ? null
                : memo(request, MANAGEABLE_CODES, () -> codesOfLedTeams(request));
    }

    /** Row flag: may the caller change the unit of this code. */
    public boolean mayManageFunctionUnit(String functionUnitCode) {
        Set<String> manageable = manageableFunctionUnitCodes();
        return manageable == null || manageable.contains(functionUnitCode);
    }

    /** Request-body check of a team resource the path does not name; 403 naming the team when refused. */
    public void require(AdminOwner owner, String key, boolean write) {
        refusal(currentRequest(), owner, key, write).ifPresent(message -> {
            throw new AdminForbiddenException(message);
        });
    }

    /** Whether the caller may change this resource; the same decision the interceptor makes for a path. */
    public boolean mayWrite(AdminOwner owner, String key) {
        return refusal(currentRequest(), owner, key, true).isEmpty();
    }

    /**
     * Relation tables a team-scoped reader may see (D-47): Common tables, and tables linked to a unit of a team they
     * lead. Appends nothing for a caller who reads every team.
     */
    public void appendVisibleRelationTables(StringBuilder where, List<Object> params, String tableIdColumn) {
        Set<String> visible = visibleFunctionUnitCodes();
        if (visible == null) {
            return;
        }
        where.append(" AND (NOT EXISTS (SELECT 1 FROM rt_table_function_units l WHERE l.relation_table_id = ")
                .append(tableIdColumn).append(")");
        if (!visible.isEmpty()) {
            where.append(" OR EXISTS (SELECT 1 FROM rt_table_function_units l")
                    .append(" JOIN sys_function_units fu ON fu.id = l.function_unit_id")
                    .append(" WHERE l.relation_table_id = ").append(tableIdColumn).append(" AND fu.code IN (")
                    .append(String.join(",", Collections.nCopies(visible.size(), "?"))).append("))");
            params.addAll(visible);
        }
        where.append(")");
    }

    /**
     * Row flags of a relation table loaded with the codes of the units it is linked to (a null code is a link to a
     * removed catalog row): visible when Common or one unit is the caller's; writable when every unit is (D-47).
     */
    public boolean mayReadRelationTableLinkedTo(Collection<String> linkedCodes) {
        Set<String> visible = visibleFunctionUnitCodes();
        return visible == null || linkedCodes.isEmpty()
                || linkedCodes.stream().anyMatch(code -> code != null && visible.contains(code));
    }

    public boolean mayWriteRelationTableLinkedTo(Collection<String> linkedCodes) {
        Set<String> manageable = manageableFunctionUnitCodes();
        return manageable == null || (!linkedCodes.isEmpty()
                && linkedCodes.stream().allMatch(code -> code != null && manageable.contains(code)));
    }

    /**
     * The units a relation table is linked to after a create or update by this caller (D-47): a caller limited to
     * teams links at least one unit, and only units of teams they lead; sharing with another team is SYS_ADMIN's.
     */
    public void requireLinkableFunctionUnits(Collection<String> functionUnitIds) {
        if (AdminCaller.of(currentRequest()).writesAllTeams()) {
            return;
        }
        if (functionUnitIds == null || functionUnitIds.isEmpty()) {
            throw new AdminForbiddenException(i18nService.getMessage("admin.team.rt_link_required"));
        }
        for (String id : functionUnitIds) {
            require(AdminOwner.FUNCTION_UNIT_ID, id, true);
        }
    }

    /**
     * Automation workspaces a team-scoped reader may see: the teams they lead and Public (D-48). Appends
     * {@code AND column IN (...)} on the workspace group id, nothing for a caller who reads every team.
     */
    public void appendVisibleWorkspaces(StringBuilder where, List<Object> params, String groupIdColumn) {
        if (caller().readsAllTeams()) {
            return;
        }
        Set<String> groups = new java.util.LinkedHashSet<>(ledTeamIds());
        groups.add(serviceTaskProperties.getManaged().getPublicGroupId());
        where.append(" AND ").append(groupIdColumn).append(" IN (")
                .append(String.join(",", Collections.nCopies(groups.size(), "?"))).append(")");
        params.addAll(groups);
    }

    /** Every function unit id must be the caller's to change (batch writes refuse the whole batch). */
    public void requireManageFunctionUnits(Collection<String> functionUnitIds) {
        for (String id : functionUnitIds) {
            require(AdminOwner.FUNCTION_UNIT_ID, id, true);
        }
    }

    /**
     * Appends {@code AND column IN (...)} for a code filter from this class; nothing when every code is allowed, a
     * false condition when none is.
     */
    public static void appendCodeFilter(StringBuilder where, List<Object> params, String column, Set<String> codes) {
        if (codes == null) {
            return;
        }
        if (codes.isEmpty()) {
            where.append(" AND 1 = 0");
            return;
        }
        where.append(" AND ").append(column).append(" IN (")
                .append(String.join(",", Collections.nCopies(codes.size(), "?"))).append(")");
        params.addAll(codes);
    }

    /** Why the caller may not read / change this resource; empty when allowed or when the resource is not there. */
    Optional<String> refusal(HttpServletRequest request, AdminOwner owner, String key, boolean write) {
        AdminCaller caller = AdminCaller.of(request);
        if (write ? caller.writesAllTeams() : caller.readsAllTeams()) {
            return Optional.empty();
        }
        Owners owners = ownersOf(owner, key);
        if (!owners.exists()) {
            return Optional.empty();
        }
        Set<String> led = ledTeams(request).stream().map(VirtualGroupMemberRepository.LedTeam::getCode)
                .collect(Collectors.toSet());
        if (!write) {
            if (owners.everyTeam() || owners.teamCodes().stream().anyMatch(led::contains)) {
                return Optional.empty();
            }
            return Optional.of(refusalFor(owners.teamCodes().isEmpty() ? null : owners.teamCodes().get(0)));
        }
        if (owners.everyTeam()) {
            return Optional.of(i18nService.getMessage("admin.team.shared_admin_only"));
        }
        for (String code : owners.teamCodes()) {
            if (code == null || !led.contains(code)) {
                return Optional.of(refusalFor(code));
            }
        }
        return Optional.empty();
    }

    Owners ownersOf(AdminOwner owner, String key) {
        if (key == null || key.isBlank()) {
            return Owners.MISSING;
        }
        return switch (owner) {
            case NONE -> throw new IllegalArgumentException("No owner to resolve");
            case FUNCTION_UNIT_ID -> functionUnitRepository.findById(key)
                    .map(unit -> Owners.team(teamOf(unit.getCode())))
                    .orElse(Owners.MISSING);
            // A code the catalog does not have is a unit without a team, never "not there": SLA and BI hold codes
            // of units Admin has not imported.
            case FUNCTION_UNIT_CODE -> Owners.team(teamOf(key));
            case FUNCTION_UNIT_DEPLOYMENT -> jdbcTemplate.queryForList(DEPLOYMENT_CODE_SQL, String.class, key).stream()
                    .findFirst()
                    .map(code -> Owners.team(teamOf(code)))
                    .orElse(Owners.MISSING);
            case RELATION_TABLE_ID -> relationTableOwners(key);
            case AUTOMATION_FLOW -> workspaceOwners(FLOW_WORKSPACE_FROM + workspaceSql.joinClause() + " WHERE f.id = ?",
                    key);
            case AUTOMATION_RUN -> workspaceOwners(RUN_WORKSPACE_FROM + workspaceSql.joinClause() + " WHERE r.id = ?",
                    key);
            case DATA_VIEW_ASSIGNMENT -> biMetadata.findFunctionUnitCodeOfAssignment(key)
                    .map(code -> Owners.team(teamOf(code)))
                    .orElse(Owners.MISSING);
        };
    }

    private Owners relationTableOwners(String key) {
        Optional<Long> id = parseLong(key);
        if (id.isEmpty()) {
            return Owners.MISSING;
        }
        Integer count = jdbcTemplate.queryForObject(RELATION_TABLE_EXISTS_SQL, Integer.class, id.get());
        if (count == null || count == 0) {
            return Owners.MISSING;
        }
        List<String> codes = jdbcTemplate.queryForList(RELATION_TABLE_OWNER_CODES_SQL, String.class, id.get());
        if (codes.isEmpty()) {
            return Owners.EVERY_TEAM;
        }
        List<String> teams = new ArrayList<>();
        for (String code : new HashSet<>(codes)) {
            teams.add(code == null ? null : teamOf(code));
        }
        return new Owners(true, false, teams);
    }

    private Owners workspaceOwners(String fromWhere, String id) {
        List<Object> params = new ArrayList<>(workspaceSql.joinParams());
        params.add(id);
        List<String[]> rows = jdbcTemplate.query("SELECT vg.id AS team_id, vg.code AS team_code" + fromWhere,
                (rs, n) -> new String[]{rs.getString("team_id"), rs.getString("team_code")}, params.toArray());
        if (rows.isEmpty()) {
            return Owners.MISSING;
        }
        String[] team = rows.get(0);
        if (serviceTaskProperties.getManaged().getPublicGroupId().equals(team[0])) {
            return Owners.EVERY_TEAM;
        }
        // A project mapped to no group (an AP sandbox) belongs to no team.
        return Owners.team(team[0] == null ? null : team[1]);
    }

    private String refusalFor(String teamCode) {
        if (teamCode == null) {
            return i18nService.getMessage("admin.team.no_team_admin_only");
        }
        Optional<VirtualGroup> team = virtualGroupRepository.findByCode(teamCode)
                .filter(group -> TEAM_TYPE.equals(group.getType()));
        if (team.isEmpty()) {
            return i18nService.getMessage("admin.team.deleted_admin_only", teamCode);
        }
        if (!team.get().isActive()) {
            return i18nService.getMessage("admin.team.inactive_admin_only", team.get().getName(), teamCode);
        }
        return i18nService.getMessage("admin.team.not_led", team.get().getName(), teamCode);
    }

    private String teamOf(String functionUnitCode) {
        return functionUnitRepository.findTeamCodeOfLatestImport(functionUnitCode).orElse(null);
    }

    private Set<String> codesOfLedTeams(HttpServletRequest request) {
        List<String> teamCodes = ledTeams(request).stream().map(VirtualGroupMemberRepository.LedTeam::getCode)
                .toList();
        return teamCodes.isEmpty() ? Set.of() : Set.copyOf(functionUnitRepository.findCodesOfTeams(teamCodes));
    }

    private List<VirtualGroupMemberRepository.LedTeam> ledTeams(HttpServletRequest request) {
        AdminCaller caller = AdminCaller.of(request);
        if (caller.service() || caller.userId() == null) {
            return List.of();
        }
        return memo(request, LED_TEAMS, () -> memberRepository.findTeamsLedBy(caller.userId()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T memo(HttpServletRequest request, String attribute, Supplier<T> load) {
        Object cached = request.getAttribute(attribute);
        if (cached != null) {
            return (T) cached;
        }
        T loaded = load.get();
        request.setAttribute(attribute, loaded);
        return loaded;
    }

    private static Optional<Long> parseLong(String key) {
        try {
            return Optional.of(Long.parseLong(key.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new IllegalStateException("Team scope is only defined inside an Admin request");
    }
}
