package com.developer.security;

import com.developer.dto.DevGroupOptionDTO;
import com.developer.repository.FunctionUnitDevGroupAssignmentRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.RoleRepository;
import com.developer.repository.VirtualGroupMembershipDao;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 功能单元工作区隔离 —— 二维模型（团队 scope × 能力角色）。
 *
 * <p>
 * 维度：
 * <ul>
 * <li><b>Scope（团队）</b>：FU 通过 {@code dw_function_unit_dev_groups} 分配给虚拟组；
 * 用户属于该虚拟组（{@code sys_virtual_group_members}）即获得该 FU 的可见性。</li>
 * <li><b>Capability（能力）</b>：{@code TEAM_LEAD} / {@code DEVELOPER} 决定能否编辑，
 * 但仅在团队 scope 内生效（编辑 ⊆ 可见）。</li>
 * </ul>
 *
 * <p>
 * 可见范围（scope）：
 * <ul>
 * <li><b>仅 {@code ADMIN} 型角色（如 {@code SYS_ADMIN}）</b>为平台级超级视角，默认全局可见；
 * 可通过 {@code X-Dev-Group-Id} 收窄到某一团队或 Public。</li>
 * <li>其余用户（含 {@code TECH_LEAD}）只能看到「当前所选团队」（或未选择时其全部团队）
 * 或主动选择的 Public 组。</li>
 * </ul>
 * Public 组（{@link DevGroupConstants#PUBLIC_GROUP_ID}）的功能单元对所有能进入工作区者可见，
 * 但列表中须通过顶部切换器主动选择 Public 后单独显示。
 * </p>
 *
 * <p>
 * 能力（capability）在可见 scope 内生效：
 * {@code TECH_LEAD}/{@code TEAM_LEAD}/{@code DEVELOPER}
 * 可编辑；{@code TECH_LEAD}/{@code TEAM_LEAD}
 * 可删除/维护团队分配；仅团队成员身份（无能力角色）为<b>只读</b>。Public 组功能单元
 * 仅 {@code ADMIN} 可改（承载历史/共享，团队成员只读）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionUnitWorkspaceAccessService {
    private static final String ROLE_TECH_LEAD = "TECH_LEAD";
    private static final String ROLE_TEAM_LEAD = "TEAM_LEAD";
    private static final String ROLE_DEVELOPER = "DEVELOPER";
    private final RoleRepository roleRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitDevGroupAssignmentRepository devGroupAssignmentRepository;
    private final VirtualGroupMembershipDao virtualGroupMembershipDao;

    public void assertCanAccess(Long functionUnitId, WorkspaceAccessAction action) {
        if (!canAccess(functionUnitId, action)) {
            log.warn("Workspace denied: functionUnitId={}, action={}, userId={}",
                    functionUnitId, action, SecurityContextUtils.getCurrentUserId().orElse("?"));
            throw new FunctionUnitWorkspaceAccessDeniedException(
                    "Not authorized to perform this operation on this function unit");
        }
    }

    public boolean canAccess(Long functionUnitId, WorkspaceAccessAction action) {
        Optional<String> userIdOpt = SecurityContextUtils.getCurrentUserId();
        if (userIdOpt.isEmpty()) {
            return false;
        }
        String userId = userIdOpt.get();
        if (!functionUnitRepository.existsById(functionUnitId)) {
            return false;
        }
        // 仅 ADMIN 型角色全局豁免团队隔离（TECH_LEAD 已收窄为团队 scope）
        if (roleRepository.userHasActiveAdminTypeRole(userId)) {
            return true;
        }
        boolean inMemberScope = isAssignedViaVirtualGroup(userId, functionUnitId);
        boolean inPublic = publicFunctionUnitIds().contains(functionUnitId);
        // 既不在自己团队、也不属于 Public —— 不可做任何操作
        if (!inMemberScope && !inPublic) {
            return false;
        }
        boolean techLead = roleRepository.hasRoleByUserId(userId, ROLE_TECH_LEAD);
        boolean teamLead = roleRepository.hasRoleByUserId(userId, ROLE_TEAM_LEAD);
        boolean developer = roleRepository.hasRoleByUserId(userId, ROLE_DEVELOPER);
        return switch (action) {
            // 团队成员与 Public 组功能单元：任何可进入工作区者均可查看
            case VIEW -> true;
            // 编辑/设计/发布/部署/回滚：需能力角色，且必须在自己团队 scope 内（Public 仅 ADMIN 可改）
            case MODIFY -> inMemberScope && (techLead || teamLead || developer);
            // 删除、维护团队分配：团队内的 Tech Lead / Team Lead（Public 仅 ADMIN）
            case DELETE, ASSIGN_DEV_GROUPS -> inMemberScope && (techLead || teamLead);
        };
    }

    /**
     * 是否为「拥有功能单元的团队」成员：用户所属任一虚拟组被分配了 ≥1 个功能单元
     * （{@code dw_function_unit_dev_groups}）。保留供历史调用；DW 只读基线判断改用
     * {@link #isMemberOfAnyDevTeam(String)}。
     */
    public boolean isMemberOfFunctionUnitOwningTeam(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        List<String> groups = virtualGroupMembershipDao.findVirtualGroupIdsByUserId(userId);
        if (groups.isEmpty()) {
            return false;
        }
        return !devGroupAssignmentRepository.findDistinctFunctionUnitIdsByVirtualGroupIdIn(groups).isEmpty();
    }

    /**
     * 是否为任一「团队」（CUSTOM 或 DEVELOPER 虚拟组，排除 Public）的成员。
     *
     * <p>
     * 作为 DW 只读基线：团队成员即可进入工作区，查看本团队（可能暂无 FU）与 Public 的功能单元。
     * 迁移后历史 FU 归入 Public、团队初始可能无 FU，故不再要求「团队已拥有 FU」。
     * </p>
     */
    public boolean isMemberOfAnyDevTeam(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return virtualGroupMembershipDao
                .findSelectableTeamsByUserId(userId, DevGroupConstants.PUBLIC_GROUP_ID)
                .stream()
                .anyMatch(DevGroupOptionDTO::isSelectable);
    }

    /**
     * 能否进入 DW 功能单元工作区（能力门禁）：
     * <ul>
     * <li>{@code ADMIN} 型 / {@code TECH_LEAD} / {@code TEAM_LEAD} /
     * {@code DEVELOPER} 能力角色；或</li>
     * <li>任一团队（CUSTOM 或 DEVELOPER 虚拟组）成员（团队成员身份 → 只读基线）。</li>
     * </ul>
     * 进入后可见/可改的具体 FU 仍受团队 scope 约束。
     */
    public boolean canEnterWorkspace(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (roleRepository.userHasActiveAdminTypeRole(userId)
                || roleRepository.hasRoleByUserId(userId, ROLE_TECH_LEAD)
                || roleRepository.hasRoleByUserId(userId, ROLE_TEAM_LEAD)
                || roleRepository.hasRoleByUserId(userId, ROLE_DEVELOPER)) {
            return true;
        }
        return isMemberOfAnyDevTeam(userId);
    }

    /** 当前用户可选择的团队列表；ADMIN 可选择所有活跃团队。 */
    public List<DevGroupOptionDTO> getSelectableTeams(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        if (canSeeAllGroups(userId)) {
            return virtualGroupMembershipDao.findAllSelectableTeams(DevGroupConstants.PUBLIC_GROUP_ID);
        }
        return virtualGroupMembershipDao.findSelectableTeamsByUserId(userId, DevGroupConstants.PUBLIC_GROUP_ID);
    }

    /** 当前用户是否为 ADMIN 型角色（可查看全部功能单元、选择「全部团队」）。 */
    public boolean canSeeAllGroups(String userId) {
        return userId != null && !userId.isBlank() && roleRepository.userHasActiveAdminTypeRole(userId);
    }

    /**
     * 解析创建功能单元时应绑定的团队（虚拟组）：
     * <ul>
     * <li><b>ADMIN</b>：可自由选择任意团队（含 Public）——用请求携带的 {@code virtualGroupIds}；
     * 未提供则回退到当前所选团队。</li>
     * <li><b>TECH_LEAD</b>：可选择「自己的团队 ∪ Public」——请求值经白名单过滤；
     * 未提供则回退到当前所选团队。</li>
     * <li><b>其余创建者（TEAM_LEAD）</b>：强制绑定「当前所选团队」（须为其成员），忽略请求携带值。</li>
     * </ul>
     * 无法确定合法目标团队时抛业务异常，避免创建出「无归属 → 无人可见」的孤儿功能单元。
     *
     * @throws IllegalStateException                      当前无有效用户
     * @throws FunctionUnitWorkspaceAccessDeniedException 未选择/无权选择目标团队
     */
    public List<String> resolveCreationTeamGroupIds(List<String> requested) {
        String userId = SecurityContextUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("No authenticated user"));
        Optional<String> selected = DevGroupContextHolder.getSelectedGroupId();
        List<String> memberships = virtualGroupMembershipDao.findVirtualGroupIdsByUserId(userId);
        boolean admin = roleRepository.userHasActiveAdminTypeRole(userId);
        boolean techLead = roleRepository.hasRoleByUserId(userId, ROLE_TECH_LEAD);
        if (admin || techLead) {
            Set<String> allowed = null; // admin: 任意
            if (!admin) {
                allowed = new HashSet<>();
                for (DevGroupOptionDTO t : getSelectableTeams(userId)) {
                    if (t.isSelectable()) {
                        allowed.add(t.getId());
                    }
                }
                allowed.add(DevGroupConstants.PUBLIC_GROUP_ID);
            }
            List<String> chosen = new java.util.ArrayList<>();
            if (requested != null) {
                for (String gid : requested) {
                    if (gid == null || gid.isBlank()) {
                        continue;
                    }
                    String trimmed = gid.trim();
                    if (allowed == null || allowed.contains(trimmed)) {
                        chosen.add(trimmed);
                    }
                }
            }
            if (!chosen.isEmpty()) {
                return chosen;
            }
            if (selected.isPresent() && (admin || memberships.contains(selected.get()))) {
                return List.of(selected.get());
            }
            throw new FunctionUnitWorkspaceAccessDeniedException(
                    "Please select a team for this function unit");
        }
        // 普通创建者（Team Lead）：绑定当前所选团队，必须是其成员
        if (selected.isPresent() && memberships.contains(selected.get())) {
            return List.of(selected.get());
        }
        throw new FunctionUnitWorkspaceAccessDeniedException(
                "Please select a team before creating a function unit");
    }

    private Set<Long> publicFunctionUnitIds() {
        return new HashSet<>(devGroupAssignmentRepository
                .findDistinctFunctionUnitIdsByVirtualGroupIdIn(List.of(DevGroupConstants.PUBLIC_GROUP_ID)));
    }

    private boolean isAssignedViaVirtualGroup(String userId, Long functionUnitId) {
        List<String> groups = virtualGroupMembershipDao.findVirtualGroupIdsByUserId(userId);
        if (groups.isEmpty()) {
            return false;
        }
        List<Long> unitIds = devGroupAssignmentRepository.findDistinctFunctionUnitIdsByVirtualGroupIdIn(groups);
        return unitIds.contains(functionUnitId);
    }

    /**
     * 列表/可见范围：
     * <ul>
     * <li><b>ADMIN</b>：未选择团队（或 __ALL__）→ 全部（返回 {@code null}）；选择团队或 Public →
     * 仅显示所选组。</li>
     * <li><b>其余用户</b>：选择 Public → 仅 Public；选择团队（若为其成员）→ 仅该团队；
     * 未选择或伪造团队 → 回退到其全部团队。</li>
     * </ul>
     * 返回 {@code null} 表示「全部可见」；返回空集表示「无可见项」。
     */
    public Set<Long> visibleFunctionUnitIds() {
        Optional<String> userIdOpt = SecurityContextUtils.getCurrentUserId();
        if (userIdOpt.isEmpty()) {
            return Collections.emptySet();
        }
        String userId = userIdOpt.get();
        Optional<String> selected = DevGroupContextHolder.getSelectedGroupId();
        if (roleRepository.userHasActiveAdminTypeRole(userId)) {
            if (selected.isEmpty()) {
                return null;
            }
            return new HashSet<>(devGroupAssignmentRepository
                    .findDistinctFunctionUnitIdsByVirtualGroupIdIn(List.of(selected.get())));
        }
        if (selected.filter(DevGroupConstants.PUBLIC_GROUP_ID::equals).isPresent()) {
            return publicFunctionUnitIds();
        }
        List<String> memberships = virtualGroupMembershipDao.findVirtualGroupIdsByUserId(userId);
        // 所选团队必须是用户真实成员的组才据此收窄（防伪造）；否则回退到全部团队。
        Collection<String> scope = (selected.isPresent() && memberships.contains(selected.get()))
                ? List.of(selected.get())
                : memberships;
        Set<Long> ids = new HashSet<>();
        if (!scope.isEmpty()) {
            ids.addAll(devGroupAssignmentRepository.findDistinctFunctionUnitIdsByVirtualGroupIdIn(scope));
        }
        return ids;
    }
}