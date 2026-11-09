package com.admin.security;

import com.admin.bi.repository.BiDataViewMetadataRepository;
import com.admin.entity.FunctionUnit;
import com.admin.exception.AdminForbiddenException;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.servicetask.ApWorkspaceSql;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.VirtualGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Team scope decisions of {@link AdminTeamScope} (platform access model §8.2, D-43, D-45, D-47, D-48). */
class AdminTeamScopeTest {

    private static final String PUBLIC_ID = "vg-public";

    private final VirtualGroupMemberRepository memberRepository = mock(VirtualGroupMemberRepository.class);
    private final VirtualGroupRepository virtualGroupRepository = mock(VirtualGroupRepository.class);
    private final FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ApWorkspaceSql workspaceSql = mock(ApWorkspaceSql.class);
    private final BiDataViewMetadataRepository biMetadata = mock(BiDataViewMetadataRepository.class);
    private AdminTeamScope scope;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        I18nService i18n = mock(I18nService.class);
        when(i18n.getMessage(anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0) + Arrays.toString(Arrays.copyOfRange(
                        inv.getArguments(), 1, inv.getArguments().length)));
        when(i18n.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        ServiceTaskProperties properties = new ServiceTaskProperties();
        properties.getManaged().setPublicGroupId(PUBLIC_ID);
        when(workspaceSql.joinClause()).thenReturn(" JOIN-WORKSPACE");
        when(workspaceSql.joinParams()).thenReturn(List.of());
        scope = new AdminTeamScope(memberRepository, virtualGroupRepository, functionUnitRepository, jdbcTemplate,
                workspaceSql, properties, biMetadata, i18n);
        team("TEAM_A", "Team A", true);
        team("TEAM_B", "Team B", true);
        team("TEAM_OFF", "Team Off", false);
        unit("fu-a1", "FU_A", "TEAM_A");
        unit("fu-b1", "FU_B", "TEAM_B");
        unit("fu-old", "FU_OLD", null);
        unit("fu-off", "FU_OFF", "TEAM_OFF");
    }

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void sysAdminAndServiceCallsAreNeverFilteredNorLookedUp() {
        as(AdminCaller.ofUser("admin", List.of("SYS_ADMIN")));
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_ID, "fu-b1", true)).isEmpty();
        assertThat(scope.manageableFunctionUnitCodes()).isNull();
        assertThat(scope.visibleFunctionUnitCodes()).isNull();

        as(AdminCaller.ofService());
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_ID, "fu-old", true)).isEmpty();
        assertThat(scope.visibleFunctionUnitCodes()).isNull();
        verify(memberRepository, never()).findTeamsLedBy(anyString());
    }

    @Test
    void anAuditorReadsEveryTeamAndAnAuditorWhoLeadsWritesOnlyTheirTeam() {
        as(AdminCaller.ofUser("aud", List.of("AUDITOR")));
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_ID, "fu-old", false)).isEmpty();
        assertThat(scope.visibleFunctionUnitCodes()).isNull();

        as(AdminCaller.ofUser("aud-tl", List.of("AUDITOR", "TECH_LEAD")));
        leads("aud-tl", "TEAM_A");
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_ID, "fu-b1", false)).isEmpty();
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_ID, "fu-a1", true)).isEmpty();
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_ID, "fu-b1", true)).contains("admin.team.not_led[Team B, TEAM_B]");
        assertThat(scope.mayManageFunctionUnit("FU_A")).isTrue();
        assertThat(scope.mayManageFunctionUnit("FU_B")).isFalse();
    }

    @Test
    void aTechnicalLeadReadsAndWritesOnlyTheTeamsTheyLead() {
        as(AdminCaller.ofUser("tl", List.of("TECH_LEAD")));
        leads("tl", "TEAM_A");

        assertThat(refusal(AdminOwner.FUNCTION_UNIT_ID, "fu-a1", false)).isEmpty();
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_ID, "fu-a1", true)).isEmpty();
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_ID, "fu-b1", false)).contains("admin.team.not_led[Team B, TEAM_B]");
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_CODE, "FU_B", true)).contains("admin.team.not_led[Team B, TEAM_B]");
        assertThat(scope.visibleFunctionUnitCodes()).containsExactly("FU_A");
        assertThat(scope.manageableFunctionUnitCodes()).containsExactly("FU_A");
    }

    @Test
    void unitsWithoutAUsableTeamAreOnlyASystemAdministrators() {
        as(AdminCaller.ofUser("tl", List.of("TECH_LEAD")));
        leads("tl", "TEAM_A");

        assertThat(refusal(AdminOwner.FUNCTION_UNIT_ID, "fu-old", false)).contains("admin.team.no_team_admin_only");
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_ID, "fu-off", true))
                .contains("admin.team.inactive_admin_only[Team Off, TEAM_OFF]");
        unit("fu-gone", "FU_GONE", "TEAM_GONE");
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_ID, "fu-gone", true))
                .contains("admin.team.deleted_admin_only[TEAM_GONE]");
        // SLA and BI name codes Admin never imported: such a code has no team, it is not "missing".
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_CODE, "ONLY_IN_DW", true))
                .contains("admin.team.no_team_admin_only");
    }

    @Test
    void aMissingResourceIsLeftToTheHandlersNotFound() {
        as(AdminCaller.ofUser("tl", List.of("TECH_LEAD")));
        leads("tl", "TEAM_A");
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_ID, "no-such-id", true)).isEmpty();
        assertThat(refusal(AdminOwner.RELATION_TABLE_ID, "not-a-number", true)).isEmpty();
    }

    @Test
    void aLeadOfNoTeamSeesNothingWithoutQueryingTheCatalog() {
        as(AdminCaller.ofUser("tl", List.of("TECH_LEAD")));
        assertThat(scope.visibleFunctionUnitCodes()).isEmpty();
        verify(functionUnitRepository, never()).findCodesOfTeams(any());
    }

    @Test
    void theLedTeamsAreReadOncePerRequest() {
        as(AdminCaller.ofUser("tl", List.of("TECH_LEAD")));
        leads("tl", "TEAM_A");
        scope.visibleFunctionUnitCodes();
        scope.manageableFunctionUnitCodes();
        refusal(AdminOwner.FUNCTION_UNIT_ID, "fu-b1", true);
        verify(memberRepository, times(1)).findTeamsLedBy("tl");
    }

    @Test
    void relationTablesFollowTheirLinkedUnits() {
        as(AdminCaller.ofUser("tl", List.of("TECH_LEAD")));
        leads("tl", "TEAM_A");
        table(1L, "FU_A");
        table(2L, "FU_A", "FU_B");
        table(3L, "FU_B");
        table(4L);
        table(5L, "FU_A", null);

        assertThat(refusal(AdminOwner.RELATION_TABLE_ID, "1", true)).isEmpty();
        assertThat(refusal(AdminOwner.RELATION_TABLE_ID, "2", false)).as("shared: readable").isEmpty();
        assertThat(refusal(AdminOwner.RELATION_TABLE_ID, "2", true)).contains("admin.team.not_led[Team B, TEAM_B]");
        assertThat(refusal(AdminOwner.RELATION_TABLE_ID, "3", false)).contains("admin.team.not_led[Team B, TEAM_B]");
        assertThat(refusal(AdminOwner.RELATION_TABLE_ID, "4", false)).as("Common: readable (D-47)").isEmpty();
        assertThat(refusal(AdminOwner.RELATION_TABLE_ID, "4", true)).contains("admin.team.shared_admin_only");
        assertThat(refusal(AdminOwner.RELATION_TABLE_ID, "5", true)).as("link to a removed catalog row")
                .contains("admin.team.no_team_admin_only");
    }

    @Test
    @SuppressWarnings("unchecked")
    void automationFollowsTheProjectsWorkspaceAndPublicIsReadOnly() {
        as(AdminCaller.ofUser("tl", List.of("TECH_LEAD")));
        when(memberRepository.findTeamsLedBy("tl")).thenReturn(List.of(led("vg-a", "TEAM_A")));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            Object[] args = inv.getArguments();
            return switch ((String) args[args.length - 1]) {
                case "flow-a" -> List.<String[]>of(new String[]{"vg-a", "TEAM_A"});
                case "flow-b" -> List.<String[]>of(new String[]{"vg-b", "TEAM_B"});
                case "flow-public" -> List.<String[]>of(new String[]{PUBLIC_ID, "PUBLIC"});
                case "flow-sandbox" -> List.<String[]>of(new String[]{null, null});
                default -> List.<String[]>of();
            };
        });

        assertThat(refusal(AdminOwner.AUTOMATION_FLOW, "flow-a", false)).isEmpty();
        assertThat(refusal(AdminOwner.AUTOMATION_RUN, "flow-public", false)).as("Public (D-48)").isEmpty();
        assertThat(refusal(AdminOwner.AUTOMATION_FLOW, "flow-b", false)).contains("admin.team.not_led[Team B, TEAM_B]");
        assertThat(refusal(AdminOwner.AUTOMATION_FLOW, "flow-sandbox", false))
                .contains("admin.team.no_team_admin_only");
        assertThat(refusal(AdminOwner.AUTOMATION_RUN, "gone", false)).isEmpty();
    }

    @Test
    void dataViewAssignmentsFollowTheirUnitsCode() {
        as(AdminCaller.ofUser("tl", List.of("TECH_LEAD")));
        leads("tl", "TEAM_A");
        when(biMetadata.findFunctionUnitCodeOfAssignment("asg-b")).thenReturn(Optional.of("FU_B"));

        assertThat(refusal(AdminOwner.DATA_VIEW_ASSIGNMENT, "asg-b", true)).contains("admin.team.not_led[Team B, TEAM_B]");
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_CODE, "FU_A", true)).isEmpty();
        assertThat(refusal(AdminOwner.DATA_VIEW_ASSIGNMENT, "asg-none", true)).isEmpty();
    }

    @Test
    void requireThrowsA403NamingTheTeamAndBatchesRefuseWhole() {
        as(AdminCaller.ofUser("tl", List.of("TECH_LEAD")));
        leads("tl", "TEAM_A");
        assertThatThrownBy(() -> scope.require(AdminOwner.FUNCTION_UNIT_CODE, "FU_B", true))
                .isInstanceOf(AdminForbiddenException.class).hasMessageContaining("TEAM_B");
        assertThatThrownBy(() -> scope.requireManageFunctionUnits(List.of("fu-a1", "fu-b1")))
                .isInstanceOf(AdminForbiddenException.class);
        scope.requireManageFunctionUnits(List.of("fu-a1"));
    }

    @Test
    void relationTableRowFlagsFollowTheLinkedCodes() {
        as(AdminCaller.ofUser("tl", List.of("TECH_LEAD")));
        leads("tl", "TEAM_A");

        assertThat(scope.mayReadRelationTableLinkedTo(List.of("FU_A"))).isTrue();
        assertThat(scope.mayWriteRelationTableLinkedTo(List.of("FU_A"))).isTrue();
        assertThat(scope.mayReadRelationTableLinkedTo(List.of("FU_A", "FU_B"))).as("shared").isTrue();
        assertThat(scope.mayWriteRelationTableLinkedTo(List.of("FU_A", "FU_B"))).as("shared").isFalse();
        assertThat(scope.mayReadRelationTableLinkedTo(List.of("FU_B"))).isFalse();
        assertThat(scope.mayReadRelationTableLinkedTo(List.of())).as("Common (D-47)").isTrue();
        assertThat(scope.mayWriteRelationTableLinkedTo(List.of())).as("Common (D-47)").isFalse();
        assertThat(scope.mayWriteRelationTableLinkedTo(Arrays.asList("FU_A", null))).isFalse();

        as(AdminCaller.ofUser("aud", List.of("AUDITOR")));
        assertThat(scope.mayReadRelationTableLinkedTo(List.of("FU_B"))).isTrue();
        assertThat(scope.mayWriteRelationTableLinkedTo(List.of("FU_B"))).isFalse();
        as(AdminCaller.ofUser("admin", List.of("SYS_ADMIN")));
        assertThat(scope.mayWriteRelationTableLinkedTo(List.of())).isTrue();
    }

    @Test
    void aTechnicalLeadLinksTablesToTheirOwnUnitsAndAtLeastOne() {
        as(AdminCaller.ofUser("tl", List.of("TECH_LEAD")));
        leads("tl", "TEAM_A");

        scope.requireLinkableFunctionUnits(List.of("fu-a1"));
        assertThatThrownBy(() -> scope.requireLinkableFunctionUnits(List.of())).isInstanceOf(AdminForbiddenException.class)
                .hasMessage("admin.team.rt_link_required");
        assertThatThrownBy(() -> scope.requireLinkableFunctionUnits(null)).isInstanceOf(AdminForbiddenException.class);
        assertThatThrownBy(() -> scope.requireLinkableFunctionUnits(List.of("fu-a1", "fu-b1")))
                .isInstanceOf(AdminForbiddenException.class).hasMessageContaining("TEAM_B");

        as(AdminCaller.ofUser("admin", List.of("SYS_ADMIN")));
        scope.requireLinkableFunctionUnits(List.of());
    }

    @Test
    void aTechnicalLeadSeesTheWorkspacesOfTheirTeamsAndPublic() {
        as(AdminCaller.ofUser("tl", List.of("TECH_LEAD")));
        when(memberRepository.findTeamsLedBy("tl")).thenReturn(List.of(led("vg-a", "TEAM_A")));
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        scope.appendVisibleWorkspaces(where, params, "vg.id");
        assertThat(where.toString()).isEqualTo(" AND vg.id IN (?,?)");
        assertThat(params).containsExactly("vg-a", PUBLIC_ID);

        as(AdminCaller.ofUser("aud", List.of("AUDITOR")));
        StringBuilder all = new StringBuilder();
        scope.appendVisibleWorkspaces(all, new ArrayList<>(), "vg.id");
        assertThat(all).isEmpty();
    }

    @Test
    void aDeploymentIsOwnedLikeTheUnitItDeployed() {
        as(AdminCaller.ofUser("tl", List.of("TECH_LEAD")));
        leads("tl", "TEAM_A");
        when(jdbcTemplate.queryForList(AdminTeamScope.DEPLOYMENT_CODE_SQL, String.class, "dep-b"))
                .thenReturn(List.of("FU_B"));
        when(jdbcTemplate.queryForList(AdminTeamScope.DEPLOYMENT_CODE_SQL, String.class, "dep-a"))
                .thenReturn(List.of("FU_A"));

        assertThat(refusal(AdminOwner.FUNCTION_UNIT_DEPLOYMENT, "dep-a", true)).isEmpty();
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_DEPLOYMENT, "dep-b", true))
                .contains("admin.team.not_led[Team B, TEAM_B]");
        assertThat(refusal(AdminOwner.FUNCTION_UNIT_DEPLOYMENT, "dep-gone", true)).isEmpty();
    }

    @Test
    void codeFilterAppendsNothingForEveryCodeAndAFalseConditionForNone() {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        AdminTeamScope.appendCodeFilter(where, params, "fu.code", null);
        assertThat(where).isEmpty();
        AdminTeamScope.appendCodeFilter(where, params, "fu.code", Set.of());
        assertThat(where.toString()).isEqualTo(" AND 1 = 0");
        where.setLength(0);
        AdminTeamScope.appendCodeFilter(where, params, "fu.code", Set.of("FU_A"));
        assertThat(where.toString()).isEqualTo(" AND fu.code IN (?)");
        assertThat(params).containsExactly("FU_A");
    }

    private Optional<String> refusal(AdminOwner owner, String key, boolean write) {
        return scope.refusal(request, owner, key, write);
    }

    private void as(AdminCaller caller) {
        request = new MockHttpServletRequest("GET", "/x");
        caller.storeOn(request);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void leads(String userId, String... teamCodes) {
        List<VirtualGroupMemberRepository.LedTeam> teams = Arrays.stream(teamCodes)
                .map(code -> led("id-" + code, code)).toList();
        when(memberRepository.findTeamsLedBy(userId)).thenReturn(teams);
        when(functionUnitRepository.findCodesOfTeams(any())).thenAnswer(inv -> {
            java.util.Collection<String> codes = inv.getArgument(0);
            List<String> units = new ArrayList<>();
            if (codes.contains("TEAM_A")) {
                units.add("FU_A");
            }
            if (codes.contains("TEAM_B")) {
                units.add("FU_B");
            }
            return units;
        });
    }

    private static VirtualGroupMemberRepository.LedTeam led(String id, String code) {
        return new VirtualGroupMemberRepository.LedTeam() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getCode() {
                return code;
            }
        };
    }

    private void team(String code, String name, boolean active) {
        VirtualGroup group = VirtualGroup.builder().id("id-" + code).code(code).name(name).type("DEVELOPER")
                .status(active ? "ACTIVE" : "INACTIVE").build();
        when(virtualGroupRepository.findByCode(code)).thenReturn(Optional.of(group));
    }

    private void unit(String id, String code, String teamCode) {
        FunctionUnit unit = new FunctionUnit();
        unit.setId(id);
        unit.setCode(code);
        when(functionUnitRepository.findById(id)).thenReturn(Optional.of(unit));
        when(functionUnitRepository.findTeamCodeOfLatestImport(code)).thenReturn(Optional.ofNullable(teamCode));
    }

    private void table(long id, String... linkedCodes) {
        when(jdbcTemplate.queryForObject(AdminTeamScope.RELATION_TABLE_EXISTS_SQL, Integer.class, id)).thenReturn(1);
        when(jdbcTemplate.queryForList(eq(AdminTeamScope.RELATION_TABLE_OWNER_CODES_SQL), eq(String.class), eq(id)))
                .thenReturn(Arrays.asList(linkedCodes));
    }
}
