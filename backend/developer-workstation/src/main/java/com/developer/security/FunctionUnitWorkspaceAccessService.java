package com.developer.security;

import com.developer.repository.FunctionUnitDevGroupAssignmentRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.RoleRepository;
import com.developer.repository.VirtualGroupMembershipDao;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 功能单元工作区隔离 —— 二维模型（团队 scope × 能力角色）。
 *
 * <p>维度：
 * <ul>
 *   <li><b>Scope（团队）</b>：FU 通过 {@code dw_function_unit_dev_groups} 分配给虚拟组；
 *       用户属于该虚拟组（{@code sys_virtual_group_members}）即获得该 FU 的可见性。</li>
 *   <li><b>Capability（能力）</b>：{@code TEAM_LEAD} / {@code DEVELOPER} 决定能否编辑，
 *       但仅在团队 scope 内生效（编辑 ⊆ 可见）。</li>
 * </ul>
 *
 * <p>豁免：{@code ADMIN} 型角色（如 {@code SYS_ADMIN}）与 {@code TECH_LEAD} 为平台级超级视角，
 * 不受团队隔离约束（全局可见/可改）。</p>
 *
 * <p>仅拥有团队成员身份、无 {@code TEAM_LEAD}/{@code DEVELOPER} 能力角色的用户（如绑定
 * {@code FU_VIEWER} 的团队组成员）只获得团队 FU 的<b>只读</b>访问。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionUnitWorkspaceAccessService {

    private static final String ROLE_TECH_LEAD = "TECH_LEAD";
    private static final String ROLE_TEAM_LEAD = "TEAM_LEAD";
    private static final String ROLE_DEVELOPER = "DEVELOPER";
    private static final String ROLE_FU_VIEWER = "FU_VIEWER";

    private final RoleRepository roleRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitDevGroupAssignmentRepository devGroupAssignmentRepository;
    private final VirtualGroupMembershipDao virtualGroupMembershipDao;

    public void assertCanAccess(Long functionUnitId, WorkspaceAccessAction action) {
        if (!canAccess(functionUnitId, action)) {
            log.warn("Workspace denied: functionUnitId={}, action={}, userId={}",
                    functionUnitId, action, SecurityContextUtils.getCurrentUserId().orElse("?"));
            throw new FunctionUnitWorkspaceAccessDeniedException("Not authorized to perform this operation on this function unit");
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

        // ADMIN / TECH_LEAD 全局豁免团队隔离
        if (roleRepository.userHasActiveAdminTypeRole(userId)
                || roleRepository.hasRoleByUserId(userId, ROLE_TECH_LEAD)) {
            return true;
        }

        // 团队 scope：FU 是否分配到用户所属的任一虚拟组。编辑 ⊆ 可见 —— 不在 scope 内即不可做任何操作。
        if (!isAssignedViaVirtualGroup(userId, functionUnitId)) {
            return false;
        }

        boolean teamLead = roleRepository.hasRoleByUserId(userId, ROLE_TEAM_LEAD);
        boolean developer = roleRepository.hasRoleByUserId(userId, ROLE_DEVELOPER);

        return switch (action) {
            // 团队成员（含只读 FU_VIEWER）均可查看团队 FU
            case VIEW -> true;
            // 编辑/设计/发布/部署/回滚：需要能力角色
            case MODIFY -> teamLead || developer;
            // 删除、维护团队分配：团队内的 Team Lead
            case DELETE, ASSIGN_DEV_GROUPS -> teamLead;
        };
    }

    /**
     * 是否为「拥有功能单元的团队」成员：用户所属任一虚拟组被分配了 ≥1 个功能单元
     * （{@code dw_function_unit_dev_groups}）。
     *
     * <p>用于将「团队成员身份」解耦为 DW 只读能力——团队组无需再绑定 {@code FU_VIEWER}
     * 等 DW 角色（admin-center 的 VG 角色下拉框仅允许绑 BU 类角色，无法绑 DW 角色）。
     * 具体某个 FU 能否被访问仍由 {@link #canAccess} / 拦截器按团队 scope 逐 FU 校验。</p>
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
     * 能否进入 DW 功能单元工作区（能力门禁）：
     * <ul>
     *   <li>{@code ADMIN} 型 / {@code TECH_LEAD} / {@code TEAM_LEAD} / {@code DEVELOPER} /
     *       {@code FU_VIEWER} 能力角色；或</li>
     *   <li>「拥有功能单元的团队」成员（团队成员身份 → 只读基线）。</li>
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
                || roleRepository.hasRoleByUserId(userId, ROLE_DEVELOPER)
                || roleRepository.hasRoleByUserId(userId, ROLE_FU_VIEWER)) {
            return true;
        }
        return isMemberOfFunctionUnitOwningTeam(userId);
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
     * 列表/可见范围：{@code ADMIN} / {@code TECH_LEAD} = 全部（返回 {@code null}）；
     * 否则为「用户所属虚拟组被分配到的 FU 集合」（团队 scope）。
     */
    public Set<Long> visibleFunctionUnitIds() {
        Optional<String> userIdOpt = SecurityContextUtils.getCurrentUserId();
        if (userIdOpt.isEmpty()) {
            return Collections.emptySet();
        }
        String userId = userIdOpt.get();

        if (roleRepository.userHasActiveAdminTypeRole(userId)
                || roleRepository.hasRoleByUserId(userId, ROLE_TECH_LEAD)) {
            return null;
        }

        List<String> groups = virtualGroupMembershipDao.findVirtualGroupIdsByUserId(userId);
        if (groups.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(devGroupAssignmentRepository.findDistinctFunctionUnitIdsByVirtualGroupIdIn(groups));
    }
}
