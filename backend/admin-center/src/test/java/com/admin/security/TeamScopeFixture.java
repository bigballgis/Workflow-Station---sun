package com.admin.security;

import com.admin.bi.repository.BiDataViewMetadataRepository;
import com.admin.config.InternalServiceAuthenticator;
import com.admin.entity.FunctionUnit;
import com.admin.exception.AdminApiExceptionHandler;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.servicetask.ApWorkspaceSql;
import com.admin.servicetask.config.ServiceTaskProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.UserPrincipal;
import com.platform.common.exception.GlobalExceptionHandler;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.service.UserRoleService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Controller tests through the real Admin authorization: the real {@link AdminAccessInterceptor} and a real
 * {@link AdminTeamScope} over stubbed repositories with two active teams, TEAM_A and TEAM_B. Function units, relation
 * tables, BI units and assignments are registered per test with the team that owns them.
 */
public final class TeamScopeFixture {

    public final VirtualGroupMemberRepository members = mock(VirtualGroupMemberRepository.class);
    public final VirtualGroupRepository groups = mock(VirtualGroupRepository.class);
    public final FunctionUnitRepository units = mock(FunctionUnitRepository.class);
    public final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    public final BiDataViewMetadataRepository biMetadata = mock(BiDataViewMetadataRepository.class);
    public final I18nService i18n = mock(I18nService.class);
    public final AdminTeamScope scope;
    public final AdminAccessInterceptor interceptor;

    private final Map<String, String> teamOfCode = new java.util.HashMap<>();

    public TeamScopeFixture() {
        when(i18n.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(i18n.getMessage(anyString(), any(Object[].class))).thenAnswer(inv -> inv.getArgument(0) + " "
                + Arrays.toString(Arrays.copyOfRange(inv.getArguments(), 1, inv.getArguments().length)));
        team("TEAM_A", "Team A");
        team("TEAM_B", "Team B");
        ApWorkspaceSql workspaceSql = mock(ApWorkspaceSql.class);
        when(workspaceSql.joinClause()).thenReturn("");
        when(workspaceSql.joinParams()).thenReturn(List.of());
        when(units.findCodesOfTeams(any())).thenAnswer(inv -> {
            Collection<String> teams = inv.getArgument(0);
            List<String> codes = new ArrayList<>();
            teamOfCode.forEach((code, team) -> {
                if (team != null && teams.contains(team)) {
                    codes.add(code);
                }
            });
            return codes;
        });
        scope = new AdminTeamScope(members, groups, units, jdbc, workspaceSql, new ServiceTaskProperties(),
                biMetadata, i18n);
        interceptor = new AdminAccessInterceptor(new InternalServiceAuthenticator("svc-secret"),
                mock(UserRoleService.class), scope, i18n, new ObjectMapper().findAndRegisterModules());
    }

    /** The controllers behind the interceptor and both exception handlers, as the application wires them. */
    public MockMvc mvc(Object... controllers) {
        return MockMvcBuilders.standaloneSetup(controllers)
                .addInterceptors(interceptor)
                .setControllerAdvice(new AdminApiExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    /** Signs in a user with these platform roles, leading these teams. */
    public void signIn(String userId, List<String> roles, String... ledTeams) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UserPrincipal.builder().userId(userId).username(userId).roles(roles).permissions(List.of()).build(),
                null, List.of()));
        List<VirtualGroupMemberRepository.LedTeam> teams = new ArrayList<>();
        for (String code : ledTeams) {
            teams.add(new VirtualGroupMemberRepository.LedTeam() {
                @Override
                public String getId() {
                    return "vg-" + code;
                }

                @Override
                public String getCode() {
                    return code;
                }
            });
        }
        when(members.findTeamsLedBy(userId)).thenReturn(teams);
    }

    /** A catalog row (version) of a function unit code owned by a team. */
    public FunctionUnit unit(String id, String code, String teamCode) {
        FunctionUnit unit = FunctionUnit.builder().id(id).code(code).name(code).build();
        when(units.findById(id)).thenReturn(Optional.of(unit));
        when(units.findTeamCodeOfLatestImport(code)).thenReturn(Optional.ofNullable(teamCode));
        teamOfCode.put(code, teamCode);
        return unit;
    }

    /** A relation table linked to these function unit codes (none = Common). */
    public void relationTable(long id, String... linkedCodes) {
        when(jdbc.queryForObject(AdminTeamScope.RELATION_TABLE_EXISTS_SQL, Integer.class, id)).thenReturn(1);
        when(jdbc.queryForList(eq(AdminTeamScope.RELATION_TABLE_OWNER_CODES_SQL), eq(String.class), eq(id)))
                .thenReturn(Arrays.asList(linkedCodes));
    }

    /** A BI data view assignment binding a unit of a code. */
    public void assignment(String id, String code) {
        when(biMetadata.findFunctionUnitCodeOfAssignment(id)).thenReturn(Optional.of(code));
    }

    public static void signOut() {
        SecurityContextHolder.clearContext();
    }

    private void team(String code, String name) {
        when(groups.findByCode(code)).thenReturn(Optional.of(VirtualGroup.builder().id("vg-" + code).code(code)
                .name(name).type("DEVELOPER").status("ACTIVE").build()));
    }
}
