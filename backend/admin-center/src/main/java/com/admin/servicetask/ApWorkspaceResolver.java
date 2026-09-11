package com.admin.servicetask;

import com.admin.exception.ApWorkspaceAccessDeniedException;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Automation workspace 解析 —— 把 DW 的「开发组（团队）」映射到一个 AP project。
 *
 * <p>DW 的工作区模型是「团队 scope × 能力角色」（见 DW 的
 * {@code FunctionUnitWorkspaceAccessService}）。Automation 沿用同一把尺：
 * <ul>
 *   <li>团队组 {@code <id>} → AP {@code externalProjectId = <prefix><id>}，AP 的
 *       managed-authn 首次见到即自建 TEAM project 并写入成员；</li>
 *   <li>Public 组（{@code vg-dev-public}）→ 历史共享 project（{@code hermes-main}），
 *       故<b>存量 flow 零迁移</b>即成为 Public workspace 的内容；</li>
 *   <li>未指定 workspace → Public（兼容既有调用方）。</li>
 * </ul>
 *
 * <p><b>隔离是真边界而非 UI 过滤</b>：会话 token 由服务端按<i>校验通过后</i>的 project 签发，
 * 且 AP 侧 rbac 在路由层要求 principal 在该 project 有成员角色
 * （{@code core/security/v2/authz}）——前端伪造组 id 拿不到 token，拿着别的 token 也访问不到
 * 他队 project 的 flow。</p>
 *
 * <p><b>平台角色同样是隔离的一部分</b>：非 SYS_ADMIN 一律签成 AP {@code MEMBER}。AP 对平台
 * {@code ADMIN} 直接授予全平台 project 的 Admin 角色，人人 ADMIN 会让 project 成员表形同虚设。
 *
 * <p>Public 的写权限收敛到 {@code SYS_ADMIN}（与 FU 的 Public 组规则一致）：其余人以
 * {@code Viewer} 角色签会话，AP 路由层直接拒绝一切写操作（{@code Viewer} 只有 READ_*）。</p>
 *
 * <p><b>团队 workspace 内的写权限＝能力角色</b>（FR-B23，与 FU 的二维模型对齐）：团队成员身份
 * 决定<i>看得见</i>，{@code TECH_LEAD} / {@code TEAM_LEAD} / {@code DEVELOPER} 才决定<i>改得动</i>。
 * 无能力角色的纯团队成员同样以 {@code Viewer} 签会话——只读进入不是靠前端收按钮，AP 侧照样拒写。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApWorkspaceResolver {

    /** DW 团队组的虚拟组类型；其余类型（SYSTEM 等）不是开发团队，不能作为 workspace。 */
    private static final Set<String> TEAM_TYPES = Set.of("CUSTOM", "DEVELOPER");

    /** DW 前端「全部团队」哨兵（管理员）：不按单一团队过滤，Automation 侧按 Public 处理。 */
    private static final String ALL_SENTINEL = "__ALL__";

    private static final String ROLE_SYS_ADMIN = "SYS_ADMIN";

    /** DW 能力角色：在团队 scope 内决定"改得动"（与 FunctionUnitWorkspaceAccessService 同一套）。 */
    private static final Set<String> CAPABILITY_ROLES = Set.of("TECH_LEAD", "TEAM_LEAD", "DEVELOPER");

    private final ServiceTaskProperties properties;
    private final VirtualGroupRepository virtualGroupRepository;
    private final VirtualGroupMemberRepository virtualGroupMemberRepository;

    /**
     * 一个已解析的 Automation workspace。
     *
     * @param groupId          虚拟组 id（Public 为配置的 public-group-id）
     * @param name             展示名（组名；Public 组不存在时回落 "Public"）
     * @param externalProjectId AP 的 {@code externalProjectId}
     * @param projectRole      本次会话在该 project 里的 AP 角色（{@code project_role.name}）
     * @param platformRole     AP 平台角色。仅 SYS_ADMIN 为 {@code ADMIN}：AP 对平台 ADMIN
     *                         直接授予全平台 project 的 Admin，会旁路整个 workspace 隔离
     * @param canWrite         当前用户在该 workspace 是否可写（前端据此收起写操作入口）
     * @param publicWorkspace  是否为 Public（共享/存量）workspace
     */
    public record ApWorkspace(String groupId, String name, String externalProjectId,
                              String projectRole, String platformRole,
                              boolean canWrite, boolean publicWorkspace) {
    }

    /**
     * 解析请求的 workspace 并校验当前用户有权进入。
     *
     * @param userId           当前用户 id（成员校验用）
     * @param requestedGroupId 前端 {@code X-Dev-Group-Id} 传入的组 id；空 / {@code __ALL__} / Public 组
     *                         id 一律解析为 Public workspace
     * @throws ApWorkspaceAccessDeniedException 组不存在 / 非开发团队 / 已停用 / 非成员（且非 SYS_ADMIN）
     */
    public ApWorkspace resolve(String userId, String requestedGroupId) {
        boolean sysAdmin = isSysAdmin();
        String groupId = requestedGroupId == null ? "" : requestedGroupId.trim();
        if (groupId.isEmpty() || ALL_SENTINEL.equals(groupId)
                || groupId.equals(properties.getManaged().getPublicGroupId())) {
            return publicWorkspace(sysAdmin);
        }

        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new ApWorkspaceAccessDeniedException(groupId,
                        "Automation workspace '" + groupId + "' does not exist"));
        if (!TEAM_TYPES.contains(group.getType())) {
            throw new ApWorkspaceAccessDeniedException(groupId,
                    "Virtual group '" + groupId + "' is not a developer team and cannot be an Automation workspace");
        }
        if (!"ACTIVE".equals(group.getStatus())) {
            throw new ApWorkspaceAccessDeniedException(groupId,
                    "Automation workspace '" + group.getName() + "' is inactive");
        }
        if (!sysAdmin && !isMember(userId, groupId)) {
            // 非成员：显式 403，不回落 Public——静默降级会让人以为看到的是该团队的 flow。
            throw new ApWorkspaceAccessDeniedException(groupId,
                    "You are not a member of Automation workspace '" + group.getName() + "'");
        }

        // 团队成员身份 → 可见；能力角色 → 可写。纯成员拿 Viewer 会话（AP 侧拒写），
        // 这样 FR-B15 的只读兜底对 Automation 才是安全的：能进，但改不动。
        boolean canWrite = sysAdmin || hasAnyCapabilityRole();
        return new ApWorkspace(groupId, group.getName(),
                properties.getManaged().getTeamProjectExternalIdPrefix() + groupId,
                projectRoleFor(canWrite), platformRoleFor(sysAdmin), canWrite, false);
    }

    private String projectRoleFor(boolean canWrite) {
        ServiceTaskProperties.Managed managed = properties.getManaged();
        return canWrite ? managed.getProjectRole() : managed.getReadOnlyProjectRole();
    }

    private boolean hasAnyCapabilityRole() {
        return CAPABILITY_ROLES.stream().anyMatch(SecurityContextUtils::hasRole);
    }

    /**
     * 反向解析：AP 的 {@code externalProjectId} → workspace（校验规则与 {@link #resolve} 完全一致）。
     *
     * <p>管理面对<b>某条已存在的 flow</b> 动作（启停、删除、转让）时用它换该 flow 所在 project 的会话
     * ——AP 的 {@code entitiesMustBeOwnedByCurrentProject} 按 token 携带的 project 判定，拿 Public
     * 会话去动团队 workspace 的 flow 会被拒。</p>
     *
     * @throws ApWorkspaceAccessDeniedException project 不属于任何 HERMES workspace（如 AP 个人沙箱），
     *                                          或当前用户无权进入该 workspace
     */
    public ApWorkspace resolveByExternalProjectId(String userId, String externalProjectId) {
        ServiceTaskProperties.Managed managed = properties.getManaged();
        if (externalProjectId == null || externalProjectId.isBlank()) {
            throw new ApWorkspaceAccessDeniedException(null,
                    "That flow lives in an Activepieces project outside every HERMES workspace "
                            + "(no externalId); manage it in Activepieces directly");
        }
        if (externalProjectId.equals(managed.getProjectExternalId())) {
            return resolve(userId, managed.getPublicGroupId());
        }
        String prefix = managed.getTeamProjectExternalIdPrefix();
        if (!externalProjectId.startsWith(prefix)) {
            throw new ApWorkspaceAccessDeniedException(null,
                    "Activepieces project '" + externalProjectId + "' is not a HERMES Automation workspace");
        }
        return resolve(userId, externalProjectId.substring(prefix.length()));
    }

    /** 管理面可选的 workspace（Public + 全部 ACTIVE 团队组），供 flow 导入选目标用。 */
    public List<ApWorkspace> listSelectableWorkspaces() {
        boolean sysAdmin = isSysAdmin();
        List<ApWorkspace> options = new ArrayList<>();
        options.add(publicWorkspace(sysAdmin));
        String prefix = properties.getManaged().getTeamProjectExternalIdPrefix();
        for (String type : TEAM_TYPES) {
            for (VirtualGroup group : virtualGroupRepository.findByTypeAndStatus(type, "ACTIVE")) {
                if (group.getId().equals(properties.getManaged().getPublicGroupId())) {
                    continue;
                }
                boolean canWrite = sysAdmin || hasAnyCapabilityRole();
                options.add(new ApWorkspace(group.getId(), group.getName(), prefix + group.getId(),
                        projectRoleFor(canWrite), platformRoleFor(sysAdmin), canWrite, false));
            }
        }
        options.sort(Comparator.comparing(ApWorkspace::publicWorkspace).reversed()
                .thenComparing(ApWorkspace::name));
        return options;
    }

    /**
     * 服务层写路径（flow 导入/启停/删除、连接检查）使用的默认 workspace：Public（共享 project）。
     *
     * <p>平台角色<b>按当前操作人</b>算，不是常量：这些路径的控制器都要求 SYS_ADMIN，真到了
     * 非管理员发起的调用（例如日后新增的服务间路径），拿到的是 Viewer 会话、被 AP 明确拒写，
     * 而不是悄悄把该用户的影子账号升级成平台 ADMIN——那会连带废掉所有 workspace 隔离。</p>
     */
    public ApWorkspace defaultWorkspace() {
        return publicWorkspace(isSysAdmin());
    }

    private ApWorkspace publicWorkspace(boolean sysAdmin) {
        ServiceTaskProperties.Managed managed = properties.getManaged();
        String publicGroupId = managed.getPublicGroupId();
        String name = virtualGroupRepository.findById(publicGroupId)
                .map(VirtualGroup::getName)
                .orElse("Public");
        // Public 承载历史/共享 flow：仅 SYS_ADMIN 可写，其余人以 Viewer 签会话（AP 侧拒写）。
        String role = sysAdmin ? managed.getProjectRole() : managed.getReadOnlyProjectRole();
        return new ApWorkspace(publicGroupId, name, managed.getProjectExternalId(), role,
                platformRoleFor(sysAdmin), sysAdmin, true);
    }

    /**
     * 平台角色是隔离的前提，不是装饰：AP 的 {@code getRole} 见到平台 {@code ADMIN} 就返回全平台
     * project 的 {@code Admin}（{@code OPERATOR} → {@code Editor}），根本不看 {@code project_member}。
     * 因此只有 SYS_ADMIN 能拿 ADMIN，其余人一律 MEMBER，否则 workspace 隔离与 Public 只读全部失效。
     */
    private String platformRoleFor(boolean sysAdmin) {
        ServiceTaskProperties.Managed managed = properties.getManaged();
        return sysAdmin ? managed.getPlatformRole() : managed.getMemberPlatformRole();
    }

    private boolean isMember(String userId, String groupId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return virtualGroupMemberRepository.findByGroupIdAndUserId(groupId, userId).isPresent();
    }

    private boolean isSysAdmin() {
        return SecurityContextUtils.isSuperAdmin()
                || SecurityContextUtils.hasRole(ROLE_SYS_ADMIN)
                || SecurityContextUtils.hasRole("SUPER_ADMIN");
    }
}
