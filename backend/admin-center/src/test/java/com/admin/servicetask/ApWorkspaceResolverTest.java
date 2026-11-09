package com.admin.servicetask;

import com.admin.exception.ApWorkspaceAccessDeniedException;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.platform.common.dto.UserPrincipal;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Automation workspace = DW 开发组 → AP project 的映射与准入。
 *
 * <p>钉住不可回退的性质：不在名单上 <b>403 而不是回落 Public</b>；Public 对非 SYS_ADMIN
 * 以只读角色签会话（AP 侧 rbac 据此真的拒写）；团队 workspace 落到独立的
 * {@code externalProjectId}（隔离由 AP 的 project 成员判定兜底）；与 DW 同一口径（平台访问模型 D-20）：
 * 团队名单上的人可写、Auditor 只读、只有 DEVELOPER 团队是 workspace。</p>
 */
class ApWorkspaceResolverTest {

    private static final String TEAM_ID = "vg-team-alpha";
    private static final String USER_ID = "44027893";

    private final VirtualGroupRepository groupRepository = mock(VirtualGroupRepository.class);
    private final VirtualGroupMemberRepository memberRepository = mock(VirtualGroupMemberRepository.class);
    private final ServiceTaskProperties properties = new ServiceTaskProperties();
    private final ApWorkspaceResolver resolver =
            new ApWorkspaceResolver(properties, groupRepository, memberRepository);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void memberGetsTheirTeamProjectAndCanWrite() {
        authenticate(List.of("DEVELOPER"));
        stubTeam("ACTIVE", "DEVELOPER");
        onTeamList(true);

        ApWorkspaceResolver.ApWorkspace workspace = resolver.resolve(USER_ID, TEAM_ID);

        assertEquals("hermes-dg-" + TEAM_ID, workspace.externalProjectId());
        assertEquals("Admin", workspace.projectRole());
        // 平台角色必须是 MEMBER：AP 对平台 ADMIN 直接给全平台 project 的 Admin，会旁路隔离。
        assertEquals("MEMBER", workspace.platformRole());
        assertTrue(workspace.canWrite());
        assertFalse(workspace.publicWorkspace());
    }

    /** 伪造他队 id 必须 403——回落 Public 会让人以为看到的是那个团队的 flow。 */
    @Test
    void nonMemberIsDeniedInsteadOfFallingBackToPublic() {
        authenticate(List.of("DEVELOPER"));
        stubTeam("ACTIVE", "DEVELOPER");
        onTeamList(false);

        ApWorkspaceAccessDeniedException e = assertThrows(ApWorkspaceAccessDeniedException.class,
                () -> resolver.resolve(USER_ID, TEAM_ID));

        assertEquals(TEAM_ID, e.getGroupId());
    }

    @Test
    void sysAdminMayEnterAnyTeamWorkspace() {
        authenticate(List.of("SYS_ADMIN"));
        stubTeam("ACTIVE", "DEVELOPER");
        onTeamList(false);

        ApWorkspaceResolver.ApWorkspace workspace = resolver.resolve(USER_ID, TEAM_ID);

        assertEquals("hermes-dg-" + TEAM_ID, workspace.externalProjectId());
        assertEquals("ADMIN", workspace.platformRole());
        assertTrue(workspace.canWrite());
    }

    @Test
    void inactiveTeamIsDenied() {
        authenticate(List.of("DEVELOPER"));
        stubTeam("INACTIVE", "DEVELOPER");
        onTeamList(true);

        assertThrows(ApWorkspaceAccessDeniedException.class, () -> resolver.resolve(USER_ID, TEAM_ID));
    }

    /** 任务池等 SYSTEM 组不是开发团队，不能当 Automation workspace 用。 */
    @Test
    void nonTeamGroupIsDenied() {
        authenticate(List.of("DEVELOPER"));
        stubTeam("ACTIVE", "SYSTEM");
        onTeamList(true);

        assertThrows(ApWorkspaceAccessDeniedException.class, () -> resolver.resolve(USER_ID, TEAM_ID));
    }

    /** 未选 workspace / 管理员的「全部团队」→ Public（历史共享 project，存量 flow 零迁移）。 */
    @Test
    void unsetAndAllSentinelResolveToTheLegacySharedProject() {
        authenticate(List.of("DEVELOPER"));
        when(groupRepository.findById(eq("vg-dev-public"))).thenReturn(Optional.empty());

        for (String requested : new String[] {null, "", "  ", "__ALL__", "vg-dev-public"}) {
            ApWorkspaceResolver.ApWorkspace workspace = resolver.resolve(USER_ID, requested);
            assertEquals("hermes-main", workspace.externalProjectId(), "requested=" + requested);
            assertTrue(workspace.publicWorkspace(), "requested=" + requested);
        }
    }

    /** Public 与 FU 的 Public 组同规则：仅 SYS_ADMIN 可改，其余人拿只读角色（AP 侧真拒写）。 */
    @Test
    void publicWorkspaceIsReadOnlyExceptForSysAdmin() {
        authenticate(List.of("TECH_LEAD"));
        when(groupRepository.findById(eq("vg-dev-public"))).thenReturn(Optional.empty());
        ApWorkspaceResolver.ApWorkspace readOnly = resolver.resolve(USER_ID, null);
        assertEquals("Viewer", readOnly.projectRole());
        // Viewer 只有在平台角色是 MEMBER 时才生效——平台 ADMIN 会覆盖成 project Admin。
        assertEquals("MEMBER", readOnly.platformRole());
        assertFalse(readOnly.canWrite());

        SecurityContextHolder.clearContext();
        authenticate(List.of("SYS_ADMIN"));
        ApWorkspaceResolver.ApWorkspace writable = resolver.resolve(USER_ID, null);
        assertEquals("Admin", writable.projectRole());
        assertEquals("ADMIN", writable.platformRole());
        assertTrue(writable.canWrite());
    }

    /**
     * An Auditor reads every team workspace (D-20) and gets a read-only session — read-only by AP itself, not only by
     * hidden buttons.
     */
    @Test
    void anAuditorEntersAnyTeamWorkspaceReadOnly() {
        authenticate(List.of("AUDITOR"));
        stubTeam("ACTIVE", "DEVELOPER");
        onTeamList(false);

        ApWorkspaceResolver.ApWorkspace workspace = resolver.resolve(USER_ID, TEAM_ID);

        assertEquals("hermes-dg-" + TEAM_ID, workspace.externalProjectId());
        assertFalse(workspace.canWrite());
        assertEquals("Viewer", workspace.projectRole());
        assertEquals("MEMBER", workspace.platformRole());
    }

    /** A global TECH_LEAD role from another team writes nothing here: only this team's lists count (§4.4). */
    @Test
    void aRoleHeldInAnotherTeamDoesNotOpenThisOne() {
        authenticate(List.of("TECH_LEAD"));
        stubTeam("ACTIVE", "DEVELOPER");
        onTeamList(false);

        assertThrows(ApWorkspaceAccessDeniedException.class, () -> resolver.resolve(USER_ID, TEAM_ID));
    }

    /** A CUSTOM group is a Portal access group, not a workspace, even for its members. */
    @Test
    void aCustomGroupIsNotAWorkspace() {
        authenticate(List.of("DEVELOPER"));
        stubTeam("ACTIVE", "CUSTOM");
        onTeamList(true);

        assertThrows(ApWorkspaceAccessDeniedException.class, () -> resolver.resolve(USER_ID, TEAM_ID));
    }

    /** 管理面对既有 flow 动作时的反查：externalProjectId → workspace，判据与正向解析同一套。 */
    @Test
    void externalProjectIdResolvesBackToItsWorkspace() {
        authenticate(List.of("SYS_ADMIN"));
        stubTeam("ACTIVE", "DEVELOPER");

        ApWorkspaceResolver.ApWorkspace team =
                resolver.resolveByExternalProjectId(USER_ID, "hermes-dg-" + TEAM_ID);
        assertEquals(TEAM_ID, team.groupId());

        ApWorkspaceResolver.ApWorkspace shared =
                resolver.resolveByExternalProjectId(USER_ID, "hermes-main");
        assertTrue(shared.publicWorkspace());
    }

    /** AP 个人沙箱等没有 externalId 的 project 不是 workspace：显式拒绝，不猜成 Public。 */
    @Test
    void projectOutsideEveryWorkspaceIsRejected() {
        authenticate(List.of("SYS_ADMIN"));

        assertThrows(ApWorkspaceAccessDeniedException.class,
                () -> resolver.resolveByExternalProjectId(USER_ID, null));
        assertThrows(ApWorkspaceAccessDeniedException.class,
                () -> resolver.resolveByExternalProjectId(USER_ID, "some-other-project"));
    }

    private void onTeamList(boolean onList) {
        when(memberRepository.isActiveUserOnLists(TEAM_ID, USER_ID,
                List.of(VirtualGroupMember.LIST_TECH_LEAD, VirtualGroupMember.LIST_DEVELOPER))).thenReturn(onList);
    }

    private void stubTeam(String status, String type) {
        VirtualGroup group = new VirtualGroup();
        group.setId(TEAM_ID);
        group.setName("Alpha Team");
        group.setType(type);
        group.setStatus(status);
        when(groupRepository.findById(any())).thenReturn(Optional.of(group));
    }

    private static void authenticate(List<String> roles) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(ApWorkspaceResolverTest.USER_ID)
                .username("zhangsan")
                .displayName("Zhang San")
                .roles(roles)
                .permissions(Collections.emptyList())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList()));
    }
}
