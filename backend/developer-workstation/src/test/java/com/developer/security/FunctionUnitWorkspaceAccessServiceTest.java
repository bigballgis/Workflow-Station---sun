package com.developer.security;

import com.developer.dto.DevGroupOptionDTO;
import com.developer.entity.FunctionUnitDevGroupAssignment;
import com.developer.exception.DeveloperBusinessException;
import com.developer.repository.FunctionUnitDevGroupAssignmentRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.VirtualGroupMembershipDao;
import com.developer.repository.VirtualGroupMembershipDao.TeamMembership;
import com.developer.repository.VirtualGroupMembershipDao.WorkspaceGroup;
import com.developer.security.FunctionUnitWorkspaceAccessService.FunctionUnitAccess;
import com.developer.security.FunctionUnitWorkspaceAccessService.WorkspaceStatus;
import com.platform.common.dto.UserPrincipal;
import com.platform.security.service.UserRoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Workspace rules of the platform access model (§6.1–§6.3, AC-01..05, AC-21, AC-30): what a user may do with a
 * function unit depends on the list they are on in <b>that</b> team; SYS_ADMIN does everything, AUDITOR only reads,
 * Public is read-only for everyone else, disabled and deleted teams are seen by SYS_ADMIN and AUDITOR only.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FunctionUnitWorkspaceAccessServiceTest {
    private static final String USER = "u-1";
    private static final String PUBLIC = DevGroupConstants.PUBLIC_GROUP_ID;
    private static final Set<WorkspaceAccessAction> LEAD_ONLY = EnumSet.of(WorkspaceAccessAction.DELETE,
            WorkspaceAccessAction.RESTORE, WorkspaceAccessAction.ASSIGN_DEV_GROUPS,
            WorkspaceAccessAction.IMPORT_VERSION);

    @Mock private UserRoleService userRoleService;
    @Mock private FunctionUnitRepository functionUnitRepository;
    @Mock private FunctionUnitDevGroupAssignmentRepository assignments;
    @Mock private VirtualGroupMembershipDao membershipDao;
    private FunctionUnitWorkspaceAccessService service;

    private final Map<String, WorkspaceGroup> groups = new HashMap<>();
    private final Map<Long, String> unitGroup = new HashMap<>();
    private final List<TeamMembership> memberships = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new FunctionUnitWorkspaceAccessService(userRoleService, functionUnitRepository, assignments, membershipDao);
        when(functionUnitRepository.existsById(any())).thenAnswer(inv -> unitGroup.containsKey(inv.<Long>getArgument(0)));
        when(assignments.findByFunctionUnitIdIn(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().filter(unitGroup::containsKey)
                    .map(id -> FunctionUnitDevGroupAssignment.builder().functionUnitId(id).virtualGroupId(unitGroup.get(id)).build())
                    .toList();
        });
        when(assignments.findByFunctionUnitId(any())).thenAnswer(inv -> unitGroup.containsKey(inv.<Long>getArgument(0))
                ? List.of(FunctionUnitDevGroupAssignment.builder().virtualGroupId(unitGroup.get(inv.<Long>getArgument(0))).build())
                : List.of());
        when(assignments.findDistinctFunctionUnitIdsByVirtualGroupIdIn(anyCollection())).thenAnswer(inv -> {
            Collection<String> wanted = inv.getArgument(0);
            return unitGroup.entrySet().stream().filter(e -> wanted.contains(e.getValue())).map(Map.Entry::getKey).toList();
        });
        when(membershipDao.findGroups(anyCollection())).thenAnswer(inv -> {
            Map<String, WorkspaceGroup> found = new HashMap<>();
            for (Object id : inv.<Collection<?>>getArgument(0)) {
                if (groups.containsKey(id)) {
                    found.put((String) id, groups.get(id));
                }
            }
            return found;
        });
        when(membershipDao.findGroupByCode(anyString())).thenAnswer(inv -> groups.values().stream()
                .filter(g -> g.code().equals(inv.getArgument(0))).findFirst());
        when(membershipDao.findTeamMemberships(eq(USER), eq(PUBLIC))).thenReturn(memberships);
        when(membershipDao.findAllTeams(PUBLIC)).thenAnswer(inv -> groups.values().stream()
                .filter(g -> "DEVELOPER".equals(g.type()) && !g.id().equals(PUBLIC))
                .map(g -> new DevGroupOptionDTO(g.id(), g.name(), g.status())).toList());

        group(PUBLIC, "DEV_TEAM_PUBLIC", "Public", "DEVELOPER", "ACTIVE");
        group("vg-a", "TEAM_A", "Team A", "DEVELOPER", "ACTIVE");
        group("vg-b", "TEAM_B", "Team B", "DEVELOPER", "ACTIVE");
        group("vg-c", "TEAM_C", "Team C", "DEVELOPER", "ACTIVE");
        group("vg-off", "TEAM_OFF", "Team Off", "DEVELOPER", "INACTIVE");
        group("vg-custom", "FINANCE", "Finance", "CUSTOM", "ACTIVE");
        unit(1L, "vg-a");
        unit(2L, "vg-b");
        unit(3L, "vg-c");
        unit(4L, PUBLIC);
        unit(5L, "vg-off");
        unit(6L, "vg-deleted");
        authenticate();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    // ---------- rules per action ----------

    @Test
    void aTechnicalLeadDoesEverythingInTheirTeam() {
        on("vg-a", true, false);

        for (WorkspaceAccessAction action : WorkspaceAccessAction.values()) {
            assertThat(service.canAccess(1L, action)).as(action.name()).isTrue();
        }
    }

    @Test
    void aDeveloperViewsAndModifiesButNeverManages() {
        on("vg-b", false, true);

        assertThat(service.canAccess(2L, WorkspaceAccessAction.VIEW)).isTrue();
        assertThat(service.canAccess(2L, WorkspaceAccessAction.MODIFY)).isTrue();
        for (WorkspaceAccessAction action : LEAD_ONLY) {
            assertThat(service.canAccess(2L, action)).as(action.name()).isFalse();
        }
    }

    @Test
    void leadingOneTeamGivesNothingExtraInAnother() {
        on("vg-a", true, false);
        on("vg-b", false, true);

        assertThat(service.canAccess(1L, WorkspaceAccessAction.DELETE)).isTrue();
        assertThat(service.canAccess(2L, WorkspaceAccessAction.DELETE)).isFalse();
        assertThat(service.canAccess(2L, WorkspaceAccessAction.MODIFY)).isTrue();
        assertThat(service.canAccess(3L, WorkspaceAccessAction.VIEW)).isFalse();
    }

    @Test
    void bothListsInOneTeamMeansTechnicalLead() {
        on("vg-a", true, true);

        assertThat(service.canAccess(1L, WorkspaceAccessAction.DELETE)).isTrue();
    }

    @Test
    void publicIsReadOnlyForEveryoneButSystemAdministrators() {
        on("vg-a", true, false);

        assertThat(service.canAccess(4L, WorkspaceAccessAction.VIEW)).isTrue();
        assertThat(service.canAccess(4L, WorkspaceAccessAction.MODIFY)).isFalse();
        assertThat(service.canAccess(4L, WorkspaceAccessAction.DELETE)).isFalse();
    }

    @Test
    void someoneOnNoListSeesNothingNotEvenPublic() {
        assertThat(service.canAccess(4L, WorkspaceAccessAction.VIEW)).isFalse();
        assertThat(service.canAccess(1L, WorkspaceAccessAction.VIEW)).isFalse();
    }

    @Test
    void aDisabledTeamIsInvisibleToItsMembers() {
        on("vg-off", true, false);

        assertThat(service.canAccess(5L, WorkspaceAccessAction.VIEW)).isFalse();
        assertThat(service.getSelectableTeams(USER)).isEmpty();
    }

    @Test
    void aSystemAdministratorDoesEverythingEverywhereWithoutMembership() {
        role("SYS_ADMIN");

        for (long unit = 1; unit <= 6; unit++) {
            for (WorkspaceAccessAction action : WorkspaceAccessAction.values()) {
                assertThat(service.canAccess(unit, action)).as(unit + " " + action).isTrue();
            }
        }
    }

    @Test
    void anAuditorReadsEverythingAndWritesNothing() {
        role("AUDITOR");

        for (long unit = 1; unit <= 6; unit++) {
            assertThat(service.canAccess(unit, WorkspaceAccessAction.VIEW)).isTrue();
            assertThat(service.canAccess(unit, WorkspaceAccessAction.MODIFY)).isFalse();
        }
    }

    @Test
    void describeNamesTheWorkspaceAndItsState() {
        on("vg-a", false, true);

        Map<Long, FunctionUnitAccess> access = service.describe(List.of(1L, 4L, 5L, 6L));

        assertThat(access.get(1L).workspace().status()).isEqualTo(WorkspaceStatus.ACTIVE);
        assertThat(access.get(1L).workspace().name()).isEqualTo("Team A");
        assertThat(access.get(1L).canModify()).isTrue();
        assertThat(access.get(1L).canManage()).isFalse();
        assertThat(access.get(4L).workspace().status()).isEqualTo(WorkspaceStatus.PUBLIC);
        assertThat(access.get(5L).workspace().status()).isEqualTo(WorkspaceStatus.INACTIVE);
        assertThat(access.get(6L).workspace().status()).isEqualTo(WorkspaceStatus.MISSING);
    }

    // ---------- where a new unit lands ----------

    @Test
    void aNewUnitNeedsExactlyOneWorkspace() {
        role("SYS_ADMIN");

        assertError(() -> service.resolveTargetWorkspace(List.of()), "WORKSPACE_EXACTLY_ONE");
        assertError(() -> service.resolveTargetWorkspace(null), "WORKSPACE_EXACTLY_ONE");
        assertError(() -> service.resolveTargetWorkspace(List.of("vg-a", "vg-b")), "WORKSPACE_EXACTLY_ONE");
        assertThat(service.resolveTargetWorkspace(List.of("vg-a", " vg-a "))).isEqualTo("vg-a");
    }

    @Test
    void onlyATechnicalLeadOfTheTargetTeamMayAddToIt() {
        on("vg-a", true, false);
        on("vg-b", false, true);

        assertThat(service.resolveTargetWorkspace(List.of("vg-a"))).isEqualTo("vg-a");
        assertThatThrownBy(() -> service.resolveTargetWorkspace(List.of("vg-b")))
                .isInstanceOf(FunctionUnitWorkspaceAccessDeniedException.class);
        assertThatThrownBy(() -> service.resolveTargetWorkspace(List.of("vg-c")))
                .isInstanceOf(FunctionUnitWorkspaceAccessDeniedException.class);
        assertThatThrownBy(() -> service.resolveTargetWorkspace(List.of(PUBLIC)))
                .isInstanceOf(FunctionUnitWorkspaceAccessDeniedException.class);
    }

    @Test
    void aSystemAdministratorAddsToAnyActiveTeamOrPublic() {
        role("SYS_ADMIN");

        assertThat(service.resolveTargetWorkspace(List.of("vg-c"))).isEqualTo("vg-c");
        assertThat(service.resolveTargetWorkspace(List.of(PUBLIC))).isEqualTo(PUBLIC);
        assertError(() -> service.resolveTargetWorkspace(List.of("vg-off")), "WORKSPACE_INACTIVE");
        assertError(() -> service.resolveTargetWorkspace(List.of("vg-deleted")), "WORKSPACE_NOT_FOUND");
        assertError(() -> service.resolveTargetWorkspace(List.of("vg-custom")), "WORKSPACE_NOT_FOUND");
    }

    @Test
    void aPackageLandsInTheTeamWithItsCode() {
        on("vg-a", true, false);

        assertThat(service.resolvePackageWorkspace("TEAM_A", "Team A")).isEqualTo("vg-a");
        assertError(() -> service.resolvePackageWorkspace("PAYMENT_OPS", "Payment Ops"), "IMPORT_TEAM_NOT_FOUND");
        assertError(() -> service.resolvePackageWorkspace("TEAM_OFF", "Team Off"), "IMPORT_TEAM_INACTIVE");
        assertError(() -> service.resolvePackageWorkspace("FINANCE", "Finance"), "IMPORT_TEAM_NOT_FOUND");
        assertThatThrownBy(() -> service.resolvePackageWorkspace("TEAM_B", "Team B"))
                .isInstanceOf(FunctionUnitWorkspaceAccessDeniedException.class);
    }

    @Test
    void anExportPackageNamesTheUnitsTeamByCode() {
        assertThat(service.packageTeamOf(1L)).contains(new FunctionUnitWorkspaceAccessService.PackageTeam("TEAM_A", "Team A"));
        assertThat(service.packageTeamOf(4L)).contains(
                new FunctionUnitWorkspaceAccessService.PackageTeam("DEV_TEAM_PUBLIC", "Public"));
        assertThat(service.packageTeamOf(6L)).isEmpty();
    }

    // ---------- entry, switcher, list ----------

    @Test
    void onlySystemAdministratorsAuditorsAndTeamListsEnter() {
        assertThat(DeveloperWorkstationEntitlement.isEntitled(List.of("DEVELOPER"))).isTrue();
        assertThat(DeveloperWorkstationEntitlement.isEntitled(List.of("TECH_LEAD"))).isTrue();
        assertThat(DeveloperWorkstationEntitlement.isEntitled(List.of("AUDITOR"))).isTrue();
        assertThat(DeveloperWorkstationEntitlement.isEntitled(List.of("SYS_ADMIN"))).isTrue();
        assertThat(DeveloperWorkstationEntitlement.isEntitled(List.of("MANAGER"))).isFalse();
        assertThat(DeveloperWorkstationEntitlement.isEntitled(List.of())).isFalse();
        assertThat(DeveloperWorkstationEntitlement.isEntitled(null)).isFalse();
    }

    @Test
    void aMemberSwitchesBetweenTheirActiveTeamsOnly() {
        on("vg-a", true, false);
        on("vg-b", false, true);
        on("vg-off", true, false);

        List<DevGroupOptionDTO> teams = service.getSelectableTeams(USER);

        assertThat(teams).extracting(t -> t.getId() + "/" + t.isTechLead() + "/" + t.isDeveloper() + "/" + t.isCanCreate())
                .containsExactly("vg-a/true/false/true", "vg-b/false/true/false");
        assertThat(service.canSeeAllGroups(USER)).isFalse();
        assertThat(service.canWritePublic(USER)).isFalse();
    }

    @Test
    void aSystemAdministratorSeesEveryTeamAndCreatesInTheActiveOnes() {
        role("SYS_ADMIN");

        List<DevGroupOptionDTO> teams = service.getSelectableTeams(USER);

        assertThat(teams).extracting(t -> t.getId() + "/" + t.isCanCreate())
                .contains("vg-a/true", "vg-off/false")
                .doesNotContain(PUBLIC + "/true", PUBLIC + "/false");
        assertThat(service.canSeeAllGroups(USER)).isTrue();
        assertThat(service.canWritePublic(USER)).isTrue();
    }

    @Test
    void theListShowsTheSelectedTeamOrAllActiveTeamsOfAMember() {
        on("vg-a", true, false);
        on("vg-off", true, false);

        assertThat(service.visibleFunctionUnitIds()).containsExactly(1L);
        select("vg-b");
        assertThat(service.visibleFunctionUnitIds()).containsExactly(1L);
        select(PUBLIC);
        assertThat(service.visibleFunctionUnitIds()).containsExactly(4L);
    }

    @Test
    void systemAdministratorsAndAuditorsSeeAllUnlessTheyNarrowIt() {
        role("AUDITOR");

        assertThat(service.visibleFunctionUnitIds()).isNull();
        select("vg-off");
        assertThat(service.visibleFunctionUnitIds()).containsExactly(5L);
    }

    // ---------- helpers ----------

    private void group(String id, String code, String name, String type, String status) {
        groups.put(id, new WorkspaceGroup(id, code, name, type, status));
    }

    private void unit(Long id, String groupId) {
        unitGroup.put(id, groupId);
    }

    private void on(String teamId, boolean techLead, boolean developer) {
        WorkspaceGroup group = groups.get(teamId);
        memberships.add(new TeamMembership(teamId, group.name(), group.status(), techLead, developer));
    }

    private void role(String roleCode) {
        lenient().when(userRoleService.hasRole(USER, roleCode)).thenReturn(true);
    }

    private void select(String groupId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DevGroupContextHolder.HEADER, groupId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static void authenticate() {
        UserPrincipal principal = UserPrincipal.builder().userId(USER).username(USER).build();
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(
                new UsernamePasswordAuthenticationToken(principal, "n/a", Collections.emptyList()));
        SecurityContextHolder.setContext(context);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(DeveloperBusinessException.class)
                .extracting("errorCode").isEqualTo(code);
    }
}
