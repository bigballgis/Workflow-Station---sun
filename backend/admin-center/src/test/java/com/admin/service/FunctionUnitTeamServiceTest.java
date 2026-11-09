package com.admin.service;

import com.admin.exception.AdminBusinessException;
import com.admin.exception.FunctionUnitTeamForbiddenException;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.service.FunctionUnitTeamService.ImportChannel;
import com.admin.service.FunctionUnitTeamService.PackageTeam;
import com.admin.service.FunctionUnitTeamService.TeamOption;
import com.admin.service.FunctionUnitTeamService.TeamState;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.service.UserRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which team an imported function unit lands in, and who may import or deploy it there (platform access model §8.1,
 * D-23, D-34).
 */
class FunctionUnitTeamServiceTest {

    private static final String ADMIN = "u-admin";
    private static final String USER = "u-dev";
    private static final List<String> DEPLOY_LISTS =
            List.of(VirtualGroupMember.LIST_TECH_LEAD, VirtualGroupMember.LIST_DEVELOPER);
    private static final List<String> LEAD_LIST = List.of(VirtualGroupMember.LIST_TECH_LEAD);

    private final FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);
    private final VirtualGroupRepository groupRepository = mock(VirtualGroupRepository.class);
    private final VirtualGroupMemberRepository memberRepository = mock(VirtualGroupMemberRepository.class);
    private final UserRoleService userRoleService = mock(UserRoleService.class);
    // Messages read back as "key arg1 arg2" so the tests can see which team a message names.
    private final I18nService i18n = mock(I18nService.class, invocation ->
            String.join(" ", Arrays.stream(invocation.getArguments()).map(String::valueOf).toList()));

    private FunctionUnitTeamService service;

    @BeforeEach
    void setUp() {
        service = new FunctionUnitTeamService(functionUnitRepository, groupRepository, memberRepository,
                userRoleService, i18n);
        when(userRoleService.hasRole(ADMIN, "SYS_ADMIN")).thenReturn(true);
        when(groupRepository.findByCode(anyString())).thenReturn(Optional.empty());
        team("g-a", "TEAM_A", "Team A", "ACTIVE");
        team("g-b", "TEAM_B", "Team B", "ACTIVE");
        team("g-off", "TEAM_OFF", "Team Off", "INACTIVE");
        when(groupRepository.findByCode("CREDIT")).thenReturn(Optional.of(VirtualGroup.builder()
                .id("g-credit").code("CREDIT").name("Credit").type("CUSTOM").status("ACTIVE").build()));
    }

    // ---------- the package's team (D-23) ----------

    @Test
    void aTeamMissingHereIsRefusedByNameEvenWhenAnotherIsChosen() {
        assertThatThrownBy(() -> service.resolveImportTeam(ImportChannel.MANUAL,
                new PackageTeam("PAYMENT_OPS", "Payment Ops"), "TEAM_A", null, ADMIN))
                .isInstanceOfSatisfying(AdminBusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo("FU_IMPORT_TEAM_NOT_FOUND");
                    assertThat(e.getErrorMessage()).contains("Payment Ops", "PAYMENT_OPS");
                });
    }

    @Test
    void aDisabledTeamIsRefusedUntilEnabled() {
        assertThatThrownBy(() -> service.resolveImportTeam(ImportChannel.DEPLOYMENT,
                new PackageTeam("TEAM_OFF", "Team Off"), null, null, ADMIN))
                .isInstanceOfSatisfying(AdminBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("FU_IMPORT_TEAM_INACTIVE"));
    }

    @Test
    void aGroupThatIsNotATeamCountsAsMissing() {
        assertThatThrownBy(() -> service.resolveImportTeam(ImportChannel.DEPLOYMENT,
                new PackageTeam("CREDIT", "Credit"), null, null, ADMIN))
                .isInstanceOfSatisfying(AdminBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("FU_IMPORT_TEAM_NOT_FOUND"));
    }

    // ---------- DW deployment ----------

    @Test
    void aDeploymentLandsInThePackageTeamForItsDeveloper() {
        onLists("g-a", DEPLOY_LISTS, true);

        assertThat(service.resolveImportTeam(ImportChannel.DEPLOYMENT, new PackageTeam("TEAM_A", "Team A"),
                null, "TEAM_A", USER)).isEqualTo("TEAM_A");
    }

    @Test
    void aDeploymentWithoutATeamInThePackageIsRefused() {
        assertThatThrownBy(() -> service.resolveImportTeam(ImportChannel.DEPLOYMENT, null, null, "TEAM_A", ADMIN))
                .isInstanceOfSatisfying(AdminBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("FU_IMPORT_TEAM_REQUIRED"));
    }

    @Test
    void someoneOffTheTeamListsCannotDeployIntoIt() {
        onLists("g-a", DEPLOY_LISTS, false);

        assertThatThrownBy(() -> service.resolveImportTeam(ImportChannel.DEPLOYMENT,
                new PackageTeam("TEAM_A", "Team A"), null, null, USER))
                .isInstanceOf(FunctionUnitTeamForbiddenException.class)
                .hasMessageContaining("Team A");
    }

    // ---------- moving a unit to another team (D-34) ----------

    @Test
    void aNewVersionMovingTeamsNeedsTheCurrentTeamToo() {
        onLists("g-b", DEPLOY_LISTS, true);
        onLists("g-a", DEPLOY_LISTS, false);

        assertThatThrownBy(() -> service.resolveImportTeam(ImportChannel.DEPLOYMENT,
                new PackageTeam("TEAM_B", "Team B"), null, "TEAM_A", USER))
                .isInstanceOf(FunctionUnitTeamForbiddenException.class)
                .hasMessageContaining("admin.fu.import_owner_forbidden")
                .hasMessageContaining("Team A");
    }

    @Test
    void someoneOnBothTeamsMovesTheUnit() {
        onLists("g-b", DEPLOY_LISTS, true);
        onLists("g-a", DEPLOY_LISTS, true);

        assertThat(service.resolveImportTeam(ImportChannel.DEPLOYMENT, new PackageTeam("TEAM_B", "Team B"),
                null, "TEAM_A", USER)).isEqualTo("TEAM_B");
    }

    @Test
    void aUnitWhoseTeamIsGoneMovesOnlyWithASystemAdministrator() {
        onLists("g-b", DEPLOY_LISTS, true);

        assertThatThrownBy(() -> service.resolveImportTeam(ImportChannel.DEPLOYMENT,
                new PackageTeam("TEAM_B", "Team B"), null, "TEAM_GONE", USER))
                .isInstanceOf(FunctionUnitTeamForbiddenException.class)
                .hasMessageContaining("TEAM_GONE");
        assertThat(service.resolveImportTeam(ImportChannel.DEPLOYMENT, new PackageTeam("TEAM_B", "Team B"),
                null, "TEAM_GONE", ADMIN)).isEqualTo("TEAM_B");
    }

    @Test
    void aUnitOfADisabledTeamSaysTheTeamIsDisabled() {
        onLists("g-b", DEPLOY_LISTS, true);

        assertThatThrownBy(() -> service.resolveImportTeam(ImportChannel.DEPLOYMENT,
                new PackageTeam("TEAM_B", "Team B"), null, "TEAM_OFF", USER))
                .isInstanceOf(FunctionUnitTeamForbiddenException.class)
                .hasMessageContaining("admin.fu.team_inactive_admin_only")
                .hasMessageContaining("Team Off");
    }

    @Test
    void aUnitImportedBeforeTeamsIsClaimedByItsFirstImportWithATeam() {
        onLists("g-a", DEPLOY_LISTS, true);

        assertThat(service.resolveImportTeam(ImportChannel.DEPLOYMENT, new PackageTeam("TEAM_A", "Team A"),
                null, null, USER)).isEqualTo("TEAM_A");
    }

    // ---------- Admin import dialog ----------

    @Test
    void theDialogNeedsAnActiveChosenTeam() {
        assertThatThrownBy(() -> service.resolveImportTeam(ImportChannel.MANUAL, null, " ", null, ADMIN))
                .isInstanceOfSatisfying(AdminBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("FU_IMPORT_TEAM_REQUIRED"));
        assertThatThrownBy(() -> service.resolveImportTeam(ImportChannel.MANUAL, null, "TEAM_OFF", null, ADMIN))
                .isInstanceOfSatisfying(AdminBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("FU_IMPORT_TEAM_INVALID"));
        assertThatThrownBy(() -> service.resolveImportTeam(ImportChannel.MANUAL, null, "CREDIT", null, ADMIN))
                .isInstanceOfSatisfying(AdminBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("FU_IMPORT_TEAM_INVALID"));
    }

    @Test
    void aSystemAdministratorPutsAPackageInAnyActiveTeam() {
        assertThat(service.resolveImportTeam(ImportChannel.MANUAL, new PackageTeam("TEAM_A", "Team A"),
                "TEAM_B", "TEAM_A", ADMIN)).isEqualTo("TEAM_B");
    }

    @Test
    void theDialogIsATechnicalLeadsAndOnlyForTheirTeams() {
        onLists("g-a", LEAD_LIST, false);
        assertThatThrownBy(() -> service.resolveImportTeam(ImportChannel.MANUAL, null, "TEAM_A", null, USER))
                .isInstanceOf(FunctionUnitTeamForbiddenException.class)
                .hasMessageContaining("admin.fu.import_team_lead_only");

        onLists("g-a", LEAD_LIST, true);
        assertThat(service.resolveImportTeam(ImportChannel.MANUAL, null, "TEAM_A", null, USER)).isEqualTo("TEAM_A");
    }

    /** D-61: a stock unit imported before teams (no team) is claimed by a Technical Lead's import into their team. */
    @Test
    void aTechnicalLeadClaimsAStockUnitWithoutATeamForTheirOwnTeam() {
        onLists("g-a", LEAD_LIST, true);

        assertThat(service.resolveImportTeam(ImportChannel.MANUAL, null, "TEAM_A", null, USER)).isEqualTo("TEAM_A");
        assertThat(service.resolveImportTeam(ImportChannel.MANUAL, new PackageTeam("TEAM_B", "Team B"), "TEAM_A",
                null, USER)).isEqualTo("TEAM_A");
    }

    @Test
    void aTechnicalLeadCannotImportANewVersionOfAnotherTeamsUnit() {
        onLists("g-a", LEAD_LIST, true);
        onLists("g-b", LEAD_LIST, false);

        assertThatThrownBy(() -> service.resolveImportTeam(ImportChannel.MANUAL, null, "TEAM_A", "TEAM_B", USER))
                .isInstanceOf(FunctionUnitTeamForbiddenException.class)
                .hasMessageContaining("Team B");
    }

    // ---------- validate / deploy ----------

    @Test
    void aSystemAdministratorDeploysAnyUnit() {
        service.assertMayDeploy("fu-x", ADMIN);
    }

    @Test
    void aUnitWithoutATeamIsDeployedOnlyByASystemAdministrator() {
        when(functionUnitRepository.findTeamCodeOfLatestImport("fu-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assertMayDeploy("fu-x", USER))
                .isInstanceOf(FunctionUnitTeamForbiddenException.class)
                .hasMessageContaining("admin.fu.deploy_without_team");
    }

    @Test
    void theTeamsListsDeployItsUnitsAndNobodyElse() {
        when(functionUnitRepository.findTeamCodeOfLatestImport("fu-x")).thenReturn(Optional.of("TEAM_A"));
        onLists("g-a", DEPLOY_LISTS, true);
        service.assertMayDeploy("fu-x", USER);

        onLists("g-a", DEPLOY_LISTS, false);
        assertThatThrownBy(() -> service.assertMayDeploy("fu-x", USER))
                .isInstanceOf(FunctionUnitTeamForbiddenException.class)
                .hasMessageContaining("Team A");
    }

    @Test
    void aDisabledTeamsUnitsWaitForASystemAdministrator() {
        when(functionUnitRepository.findTeamCodeOfLatestImport("fu-x")).thenReturn(Optional.of("TEAM_OFF"));
        onLists("g-off", DEPLOY_LISTS, true);

        assertThatThrownBy(() -> service.assertMayDeploy("fu-x", USER))
                .isInstanceOf(FunctionUnitTeamForbiddenException.class);
    }

    // ---------- import dialog options ----------

    @Test
    void describeTellsActiveDisabledAndMissingTeamsApart() {
        assertThat(service.describe("TEAM_A", "ignored")).isEqualTo(new TeamOption("TEAM_A", "Team A", TeamState.ACTIVE));
        assertThat(service.describe("TEAM_OFF", "x").state()).isEqualTo(TeamState.INACTIVE);
        assertThat(service.describe("PAYMENT_OPS", "Payment Ops"))
                .isEqualTo(new TeamOption("PAYMENT_OPS", "Payment Ops", TeamState.MISSING));
    }

    @Test
    void aSystemAdministratorChoosesAmongEveryActiveTeamATechnicalLeadAmongTheirs() {
        when(groupRepository.findByTypeAndStatus("DEVELOPER", "ACTIVE")).thenReturn(List.of(
                group("g-b", "TEAM_B", "Team B", "ACTIVE"), group("g-a", "TEAM_A", "Team A", "ACTIVE")));
        assertThat(service.selectableTeams(ADMIN)).extracting(TeamOption::code).containsExactly("TEAM_A", "TEAM_B");

        // Same list query as the import itself, so a team offered here is one the import accepts.
        onLists("g-b", LEAD_LIST, true);
        onLists("g-a", LEAD_LIST, false);
        assertThat(service.selectableTeams(USER)).extracting(TeamOption::code).containsExactly("TEAM_B");
    }

    private void team(String id, String code, String name, String status) {
        when(groupRepository.findByCode(code)).thenReturn(Optional.of(group(id, code, name, status)));
    }

    private static VirtualGroup group(String id, String code, String name, String status) {
        return VirtualGroup.builder().id(id).code(code).name(name).type("DEVELOPER").status(status).build();
    }

    private void onLists(String groupId, List<String> lists, boolean on) {
        when(memberRepository.isActiveUserOnLists(eq(groupId), eq(USER), eq(lists))).thenReturn(on);
    }
}
