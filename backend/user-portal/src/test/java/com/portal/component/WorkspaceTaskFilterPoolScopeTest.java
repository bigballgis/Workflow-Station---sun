package com.portal.component;

import com.platform.security.entity.BusinessUnit;
import com.platform.security.util.SecurityContextUtils;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskInfo;
import com.portal.repository.BusinessUnitRepository;
import com.portal.service.PortalWorkspaceAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Platform access model D-36: a claim-pool task shows only in the workspace of its BU + Role, held or not. The user
 * holds BU_A + R1 (current) and BU_B + R2.
 */
class WorkspaceTaskFilterPoolScopeTest {

    private static final String USER = "u1";

    private final PortalWorkspaceAuthService workspaces = mock(PortalWorkspaceAuthService.class);
    private final BusinessUnitRepository businessUnits = mock(BusinessUnitRepository.class);
    private final WorkspaceTaskFilterComponent filter = new WorkspaceTaskFilterComponent(
            mock(WorkflowEngineClient.class), mock(VirtualGroupAccessComponent.class), workspaces, businessUnits);
    private MockedStatic<SecurityContextUtils> security;

    @BeforeEach
    void setUp() {
        security = Mockito.mockStatic(SecurityContextUtils.class);
        security.when(SecurityContextUtils::getCurrentActiveBusinessUnitId).thenReturn(Optional.of("bu-a"));
        security.when(SecurityContextUtils::getCurrentActiveRoleId).thenReturn(Optional.of("role-1"));
        security.when(SecurityContextUtils::getCurrentUsername).thenReturn(Optional.of(USER));
        businessUnit("bu-a", "BU_A");
        businessUnit("bu-b", "BU_B");
        when(workspaces.listWorkspaceContexts(USER)).thenReturn(List.of(
                PortalWorkspaceAuthService.WorkspaceContextRow.builder()
                        .businessUnitId("bu-a").roleId("role-1").roleCode("R1").build(),
                PortalWorkspaceAuthService.WorkspaceContextRow.builder()
                        .businessUnitId("bu-b").roleId("role-2").roleCode("R2").build()));
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    private void businessUnit(String id, String code) {
        BusinessUnit bu = mock(BusinessUnit.class);
        when(bu.getCode()).thenReturn(code);
        when(businessUnits.findById(id)).thenReturn(Optional.of(bu));
    }

    private static TaskInfo pool(String id, String bu, String role, String assignee) {
        return TaskInfo.builder().taskId(id).bpmnAssigneeType("BU_ROLE").bpmnBusinessUnitId(bu)
                .bpmnRoleIds(List.of(role)).assignee(assignee)
                .candidateUserIds(assignee == null ? List.of(USER) : List.of()).build();
    }

    private List<String> visible(TaskInfo... tasks) {
        return filter.filterFixedBuRoleTasksForActiveWorkspace(List.of(tasks), USER).stream()
                .map(TaskInfo::getTaskId).toList();
    }

    @Test
    void thePoolOfTheCurrentWorkspaceShowsHeldOrNot() {
        assertThat(visible(pool("free", "BU_A", "R1", null), pool("mine", "BU_A", "R1", USER)))
                .containsExactly("free", "mine");
    }

    @Test
    void thePoolOfAnotherWorkspaceIsHiddenEvenWhenTheUserHoldsTheTask() {
        assertThat(visible(pool("free", "BU_B", "R2", null), pool("mine", "BU_B", "R2", USER))).isEmpty();
    }

    @Test
    void poolsDeployedWithIdsMatchTheSameWay() {
        assertThat(visible(pool("ids", "bu-a", "role-1", null), pool("other", "bu-b", "role-2", USER)))
                .containsExactly("ids");
    }

    /** E.g. orphan repair assigned it to the user directly: no workspace of theirs fits, so it stays on identity. */
    @Test
    void aPoolTheUserHoldsInNoWorkspaceStaysOnTheirIdentity() {
        assertThat(visible(pool("fallback", "BU_C", "R9", USER))).containsExactly("fallback");
    }

    private static TaskInfo miRow(String id, String bu, String role, String assignee) {
        return TaskInfo.builder().taskId(id).miAssigneeMode("role").miRoleCodes(List.of(role)).miBusinessUnitCode(bu)
                .assignee(assignee).build();
    }

    @Test
    void aMiRowAssignedByRoleAlsoMatchesItsBusinessUnit() {
        assertThat(visible(miRow("here", "BU_A", "R1", null), miRow("other", "BU_B", "R2", USER)))
                .containsExactly("here");
    }

    /** E.g. the user's BU_B + R1 was revoked, or the row was transferred across units: it must not vanish everywhere. */
    @Test
    void aMiRowTheUserHoldsInNoWorkspaceStaysOnTheirIdentityInEveryWorkspace() {
        TaskInfo mine = miRow("mine", "BU_B", "R1", USER);
        TaskInfo notMine = miRow("not-mine", "BU_B", "R1", "someone-else");

        assertThat(visible(mine, notMine)).containsExactly("mine");
        security.when(SecurityContextUtils::getCurrentActiveBusinessUnitId).thenReturn(Optional.of("bu-b"));
        security.when(SecurityContextUtils::getCurrentActiveRoleId).thenReturn(Optional.of("role-2"));
        assertThat(visible(mine, notMine)).containsExactly("mine");
    }

    /** A row assigned to several roles is the pool of each: the one the workspace holds is enough, whatever its place. */
    @Test
    void aMiRowAssignedToSeveralRolesMatchesTheWorkspaceOfAnyOfThem() {
        TaskInfo secondRoleHere = TaskInfo.builder().taskId("second-here").miAssigneeMode("role")
                .miRoleCodes(List.of("R9", "R1")).miBusinessUnitCode("BU_A").build();
        TaskInfo secondRoleElsewhere = TaskInfo.builder().taskId("second-elsewhere").miAssigneeMode("role")
                .miRoleCodes(List.of("R9", "R2")).miBusinessUnitCode("BU_B").build();

        assertThat(visible(secondRoleHere, secondRoleElsewhere)).containsExactly("second-here");
        security.when(SecurityContextUtils::getCurrentActiveBusinessUnitId).thenReturn(Optional.of("bu-b"));
        security.when(SecurityContextUtils::getCurrentActiveRoleId).thenReturn(Optional.of("role-2"));
        assertThat(visible(secondRoleHere, secondRoleElsewhere)).containsExactly("second-elsewhere");
    }
}
