package com.developer.security;

import com.developer.dto.DevGroupOptionDTO;
import com.developer.entity.FunctionUnitDevGroupAssignment;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FunctionUnitDevGroupAssignmentRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.VirtualGroupMembershipDao;
import com.developer.repository.VirtualGroupMembershipDao.TeamMembership;
import com.developer.repository.VirtualGroupMembershipDao.WorkspaceGroup;
import com.platform.security.service.UserRoleService;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 功能单元工作区隔离（平台访问模型 §6）：每个功能单元属于且只属于一个 Workspace（Public 或一个团队），能做什么按用户在
 * <b>这个</b>团队所在的名单判断，不按全局角色（§4.4）。
 * <ul>
 *   <li>SYS_ADMIN：所有 Workspace（含 Public、已停用与已删除团队）的全部操作，不需要是成员。</li>
 *   <li>AUDITOR：所有 Workspace 只读。</li>
 *   <li>有效团队：Technical Lead 全部操作；Developer 查看、编辑设计、发布、部署、回滚（{@link WorkspaceAccessAction}）。</li>
 *   <li>Public：其他能进 DW 的人只读。</li>
 *   <li>已停用、已删除团队的功能单元只有 SYS_ADMIN、AUDITOR 看得到，SYS_ADMIN 可以迁走（D-09、D-33）。</li>
 * </ul>
 * 新建、导入、克隆、移入的目标必须正好一个：有效团队（调用者是它的 Technical Lead）或 Public（只有 SYS_ADMIN）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionUnitWorkspaceAccessService {
    private static final String ROLE_SYS_ADMIN = "SYS_ADMIN";
    private static final String ROLE_AUDITOR = "AUDITOR";
    private static final String TEAM_TYPE = "DEVELOPER";
    private final UserRoleService userRoleService;
    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitDevGroupAssignmentRepository devGroupAssignmentRepository;
    private final VirtualGroupMembershipDao virtualGroupMembershipDao;

    /** PUBLIC; ACTIVE team; INACTIVE (disabled) team; MISSING: the group was deleted or is not a team. */
    public enum WorkspaceStatus { PUBLIC, ACTIVE, INACTIVE, MISSING }

    /** The workspace a function unit sits in; {@code groupId} is null when the unit has no assignment at all. */
    public record Workspace(String groupId, String name, WorkspaceStatus status) {
    }

    /** What the current user may do with one function unit, and where it sits. */
    public record FunctionUnitAccess(Workspace workspace, boolean canModify, boolean canManage) {
    }

    /** The current user's platform roles and the teams they are on. */
    private record Caller(boolean sysAdmin, boolean auditor, Map<String, TeamMembership> activeTeams) {
        boolean entitled() {
            return sysAdmin || auditor || !activeTeams.isEmpty();
        }

        boolean allows(Workspace workspace, WorkspaceAccessAction action) {
            if (sysAdmin) {
                return true;
            }
            if (action == WorkspaceAccessAction.VIEW && auditor) {
                return true;
            }
            if (workspace.status() == WorkspaceStatus.PUBLIC) {
                return action == WorkspaceAccessAction.VIEW && entitled();
            }
            TeamMembership team = workspace.status() == WorkspaceStatus.ACTIVE
                    ? activeTeams.get(workspace.groupId()) : null;
            if (team == null) {
                return false;
            }
            return switch (action) {
                case VIEW -> true;
                case MODIFY -> team.techLead() || team.developer();
                case DELETE, RESTORE, ASSIGN_DEV_GROUPS, IMPORT_VERSION -> team.techLead();
            };
        }

        boolean canCreateIn(Workspace workspace) {
            if (workspace.status() == WorkspaceStatus.PUBLIC) {
                return sysAdmin;
            }
            if (workspace.status() != WorkspaceStatus.ACTIVE) {
                return false;
            }
            TeamMembership team = activeTeams.get(workspace.groupId());
            return sysAdmin || (team != null && team.techLead());
        }
    }

    /**
     * 缺失的 FU 抛 404（{@link ResourceNotFoundException}）而非 403：DW 是需登录的开发工作区，
     * FU id 的存在性不作保密（403 会把「已删除/不存在」伪装成权限问题，误导排障）。
     */
    public void assertCanAccess(Long functionUnitId, WorkspaceAccessAction action) {
        if (!functionUnitRepository.existsById(functionUnitId)) {
            throw new ResourceNotFoundException("FunctionUnit", functionUnitId);
        }
        if (!canAccess(functionUnitId, action)) {
            log.warn("Workspace denied: functionUnitId={}, action={}, userId={}",
                    functionUnitId, action, SecurityContextUtils.getCurrentUserId().orElse("?"));
            throw new FunctionUnitWorkspaceAccessDeniedException(
                    "Not authorized to perform this operation on this function unit");
        }
    }

    public boolean canAccess(Long functionUnitId, WorkspaceAccessAction action) {
        Optional<Caller> caller = currentCaller();
        if (caller.isEmpty() || !functionUnitRepository.existsById(functionUnitId)) {
            return false;
        }
        return caller.get().allows(workspacesOf(List.of(functionUnitId)).get(functionUnitId), action);
    }

    /** Workspace and capabilities of each function unit, for list and detail responses. */
    public Map<Long, FunctionUnitAccess> describe(Collection<Long> functionUnitIds) {
        Map<Long, FunctionUnitAccess> result = new HashMap<>();
        if (functionUnitIds == null || functionUnitIds.isEmpty()) {
            return result;
        }
        Optional<Caller> caller = currentCaller();
        Map<Long, Workspace> workspaces = workspacesOf(functionUnitIds);
        for (Long id : functionUnitIds) {
            Workspace workspace = workspaces.get(id);
            result.put(id, new FunctionUnitAccess(workspace,
                    caller.map(c -> c.allows(workspace, WorkspaceAccessAction.MODIFY)).orElse(false),
                    caller.map(c -> c.allows(workspace, WorkspaceAccessAction.DELETE)).orElse(false)));
        }
        return result;
    }

    /**
     * Teams for the switcher: every team (disabled ones marked) for SYS_ADMIN and AUDITOR, the user's active teams for
     * everyone else. Each carries the user's lists and whether they may create there.
     */
    public List<DevGroupOptionDTO> getSelectableTeams(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        Caller caller = callerFor(userId);
        if (caller.sysAdmin() || caller.auditor()) {
            return virtualGroupMembershipDao.findAllTeams(DevGroupConstants.PUBLIC_GROUP_ID).stream()
                    .map(team -> option(team.getId(), team.getName(), team.getStatus(), caller))
                    .toList();
        }
        return caller.activeTeams().values().stream()
                .map(team -> option(team.teamId(), team.name(), team.status(), caller))
                .toList();
    }

    private static DevGroupOptionDTO option(String id, String name, String status, Caller caller) {
        TeamMembership team = caller.activeTeams().get(id);
        Workspace workspace = new Workspace(id, name,
                "ACTIVE".equals(status) ? WorkspaceStatus.ACTIVE : WorkspaceStatus.INACTIVE);
        return new DevGroupOptionDTO(id, name, status, team != null && team.techLead(),
                team != null && team.developer(), caller.canCreateIn(workspace));
    }

    /** 当前用户是否可查看全部功能单元、选择「全部团队」（SYS_ADMIN 或 AUDITOR）。 */
    public boolean canSeeAllGroups(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        Caller caller = callerFor(userId);
        return caller.sysAdmin() || caller.auditor();
    }

    /** Whether the user may create in, or move into, Public (§6.2: System Administrators only). */
    public boolean canWritePublic(String userId) {
        return userId != null && !userId.isBlank() && hasRole(userId, ROLE_SYS_ADMIN);
    }

    /**
     * The one workspace a new function unit lands in (create, clone target, import of an old package without a team):
     * exactly one id, which the current user may create in. No fallback to the selected team: a missing or ambiguous
     * target is an error (§6.3).
     */
    public String resolveTargetWorkspace(List<String> requested) {
        List<String> ids = requested == null ? List.of() : requested.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (ids.size() != 1) {
            throw new DeveloperBusinessException("WORKSPACE_EXACTLY_ONE",
                    "A function unit belongs to exactly one workspace; choose one (got " + ids.size() + ")");
        }
        assertCanCreateIn(ids.get(0));
        return ids.get(0);
    }

    /**
     * The target must be Public (System Administrators only) or an active team the current user leads (any team for a
     * System Administrator). A team that does not exist, is not a team or is disabled is refused with its reason.
     */
    public void assertCanCreateIn(String groupId) {
        Caller caller = currentCaller().orElseThrow(() -> new FunctionUnitWorkspaceAccessDeniedException(
                "Not authorized to add a function unit to this workspace"));
        Workspace workspace = workspaceOfGroup(groupId);
        if (workspace.status() == WorkspaceStatus.MISSING) {
            throw new DeveloperBusinessException("WORKSPACE_NOT_FOUND",
                    "Workspace " + groupId + " does not exist or is not a team");
        }
        if (workspace.status() == WorkspaceStatus.INACTIVE) {
            throw new DeveloperBusinessException("WORKSPACE_INACTIVE",
                    "Team " + workspace.name() + " is disabled; enable it first");
        }
        if (!caller.canCreateIn(workspace)) {
            throw new FunctionUnitWorkspaceAccessDeniedException(workspace.status() == WorkspaceStatus.PUBLIC
                    ? "Only a System Administrator can add function units to Public"
                    : "Only a Technical Lead of team " + workspace.name() + " can add function units to it");
        }
    }

    /**
     * Where a new function unit from an export package lands (§6.3): the team of this environment with the package's
     * team code, matched by code across environments (§5.4). A team that does not exist here or is disabled is refused
     * with its name and code (D-23); the caller must be allowed to create there.
     */
    public String resolvePackageWorkspace(String teamCode, String teamName) {
        String label = teamName == null || teamName.isBlank() ? teamCode : teamName + " (" + teamCode + ")";
        WorkspaceGroup group = virtualGroupMembershipDao.findGroupByCode(teamCode)
                .filter(found -> TEAM_TYPE.equals(found.type()))
                .orElseThrow(() -> new DeveloperBusinessException("IMPORT_TEAM_NOT_FOUND",
                        "Team " + label + " does not exist in this environment; create it first"));
        if (!"ACTIVE".equals(group.status())) {
            throw new DeveloperBusinessException("IMPORT_TEAM_INACTIVE",
                    "Team " + label + " is disabled; enable it first");
        }
        assertCanCreateIn(group.id());
        return group.id();
    }

    /** A team as an export package names it: by code, which matches the same team in another environment (§5.4). */
    public record PackageTeam(String code, String name) {
    }

    /** The team an export package of this unit carries (§8.1); empty when its group no longer exists. */
    public Optional<PackageTeam> packageTeamOf(Long functionUnitId) {
        List<String> groupIds = devGroupAssignmentRepository.findByFunctionUnitId(functionUnitId).stream()
                .map(FunctionUnitDevGroupAssignment::getVirtualGroupId)
                .toList();
        return virtualGroupMembershipDao.findGroups(groupIds).values().stream()
                .findFirst()
                .map(group -> new PackageTeam(group.code(), group.name()));
    }

    /** The workspace a group id stands for, read from Admin's virtual groups. */
    public Workspace workspaceOfGroup(String groupId) {
        if (DevGroupConstants.PUBLIC_GROUP_ID.equals(groupId)) {
            return new Workspace(groupId, "Public", WorkspaceStatus.PUBLIC);
        }
        return toWorkspace(groupId, virtualGroupMembershipDao.findGroups(List.of(groupId)).get(groupId));
    }

    private Map<Long, Workspace> workspacesOf(Collection<Long> functionUnitIds) {
        Map<Long, String> groupByUnit = new HashMap<>();
        for (FunctionUnitDevGroupAssignment assignment : devGroupAssignmentRepository.findByFunctionUnitIdIn(functionUnitIds)) {
            groupByUnit.put(assignment.getFunctionUnitId(), assignment.getVirtualGroupId());
        }
        Map<String, WorkspaceGroup> groups = virtualGroupMembershipDao.findGroups(new HashSet<>(groupByUnit.values()));
        Map<Long, Workspace> workspaces = new HashMap<>();
        for (Long id : functionUnitIds) {
            String groupId = groupByUnit.get(id);
            workspaces.put(id, groupId == null
                    ? new Workspace(null, null, WorkspaceStatus.MISSING)
                    : DevGroupConstants.PUBLIC_GROUP_ID.equals(groupId)
                            ? new Workspace(groupId, "Public", WorkspaceStatus.PUBLIC)
                            : toWorkspace(groupId, groups.get(groupId)));
        }
        return workspaces;
    }

    private static Workspace toWorkspace(String groupId, WorkspaceGroup group) {
        if (group == null || !TEAM_TYPE.equals(group.type())) {
            return new Workspace(groupId, group == null ? null : group.name(), WorkspaceStatus.MISSING);
        }
        return new Workspace(groupId, group.name(),
                "ACTIVE".equals(group.status()) ? WorkspaceStatus.ACTIVE : WorkspaceStatus.INACTIVE);
    }

    /**
     * 列表/可见范围：
     * <ul>
     * <li><b>SYS_ADMIN / AUDITOR</b>：未选择团队（或 __ALL__）→ 全部（返回 {@code null}，含已停用、已删除团队的功能单元）；
     * 选择团队或 Public → 仅显示所选组。</li>
     * <li><b>其余用户</b>：选择 Public → 仅 Public；选择自己名单所在的有效团队 → 仅该团队；
     * 未选择或选了别的 → 回退到其全部有效团队。</li>
     * </ul>
     * 返回 {@code null} 表示「全部可见」；返回空集表示「无可见项」。
     */
    public Set<Long> visibleFunctionUnitIds() {
        Optional<Caller> callerOpt = currentCaller();
        if (callerOpt.isEmpty()) {
            return Collections.emptySet();
        }
        Caller caller = callerOpt.get();
        Optional<String> selected = DevGroupContextHolder.getSelectedGroupId();
        if (caller.sysAdmin() || caller.auditor()) {
            return selected.<Set<Long>>map(groupId -> new HashSet<>(devGroupAssignmentRepository
                    .findDistinctFunctionUnitIdsByVirtualGroupIdIn(List.of(groupId)))).orElse(null);
        }
        if (!caller.entitled()) {
            return Collections.emptySet();
        }
        Collection<String> scope;
        if (selected.filter(DevGroupConstants.PUBLIC_GROUP_ID::equals).isPresent()) {
            scope = List.of(DevGroupConstants.PUBLIC_GROUP_ID);
        } else if (selected.isPresent() && caller.activeTeams().containsKey(selected.get())) {
            scope = List.of(selected.get());
        } else {
            scope = caller.activeTeams().keySet();
        }
        return scope.isEmpty() ? new HashSet<>()
                : new HashSet<>(devGroupAssignmentRepository.findDistinctFunctionUnitIdsByVirtualGroupIdIn(scope));
    }

    private Optional<Caller> currentCaller() {
        return SecurityContextUtils.getCurrentUserId().map(this::callerFor);
    }

    private Caller callerFor(String userId) {
        Map<String, TeamMembership> activeTeams = virtualGroupMembershipDao
                .findTeamMemberships(userId, DevGroupConstants.PUBLIC_GROUP_ID).stream()
                .filter(TeamMembership::active)
                .collect(Collectors.toMap(TeamMembership::teamId, team -> team, (a, b) -> a, LinkedHashMap::new));
        return new Caller(hasRole(userId, ROLE_SYS_ADMIN), hasRole(userId, ROLE_AUDITOR), activeTeams);
    }

    /**
     * Current authenticated user prefers JWT roles; otherwise the platform role resolution decides.
     */
    boolean hasRole(String userId, String roleCode) {
        if (userId == null || roleCode == null) {
            return false;
        }
        Optional<String> current = SecurityContextUtils.getCurrentUserId();
        if (current.isPresent() && current.get().equals(userId) && SecurityContextUtils.hasRole(roleCode)) {
            return true;
        }
        return userRoleService.hasRole(userId, roleCode);
    }
}
