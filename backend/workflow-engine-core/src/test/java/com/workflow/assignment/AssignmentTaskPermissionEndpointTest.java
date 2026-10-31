package com.workflow.assignment;

import com.platform.common.dto.UserPrincipal;
import com.platform.common.assignment.AssignmentTaskWriteReadSnapshot;
import com.workflow.component.TaskManagerComponent;
import com.workflow.controller.TaskPermissionController;
import com.workflow.dto.response.TaskListResult;
import com.workflow.enums.AssignmentType;
import com.workflow.service.UserPermissionService;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Public HTTP contract: no privilege grant, no actor substitution, no responsibility history exposed. */
class AssignmentTaskPermissionEndpointTest {
    final TaskManagerComponent tasks=mock(TaskManagerComponent.class);
    final UserPermissionService permissions=mock(UserPermissionService.class);
    final AssignmentTaskPermissionRead reads=mock(AssignmentTaskPermissionRead.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        mvc=MockMvcBuilders.standaloneSetup(new TaskPermissionController(tasks,permissions,reads)).build();
    }
    @AfterEach void clearActor() { SecurityContextHolder.clearContext(); }
    @Test void unauthenticatedAndAnotherActorCannotProbeEligibility() throws Exception {
        mvc.perform(get("/api/v1/tasks/task/check-permission").param("userId","actor")).andExpect(status().isUnauthorized());
        authenticate();
        mvc.perform(get("/api/v1/tasks/task/check-permission").param("userId","another")).andExpect(status().isForbidden());
        verifyNoInteractions(tasks,permissions,reads);
    }
    @Test void originalRoleDenialCannotBeOverriddenByMutualExclusionPermission() throws Exception {
        task(false); when(reads.read("task","actor")).thenReturn(new AssignmentTaskWriteReadSnapshot(1,true,true,"CURRENT",null));
        mvc.perform(get("/api/v1/tasks/task/check-permission").param("userId","actor"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasPermission").value(false))
                .andExpect(jsonPath("$.data.assignmentAccess.exclusionAllowsWrite").value(true));
    }
    @Test void exclusionVetoOverridesOldRolePermissionWithoutReturningResponsibilityHistory() throws Exception {
        task(true); when(reads.read("task","actor")).thenReturn(new AssignmentTaskWriteReadSnapshot(1,null,false,"ASSIGNMENT_EXCLUDED",null));
        mvc.perform(get("/api/v1/tasks/task/check-permission").param("userId","actor"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hasPermission").value(false))
                .andExpect(jsonPath("$.data.assignmentAccess.reason").value("ASSIGNMENT_EXCLUDED"))
                .andExpect(jsonPath("$.data.assignmentAccess.excluded").doesNotExist());
    }
    @Test void positiveLegacyKeepsExistingFieldsAndPermission() throws Exception {
        task(true); when(reads.read("task","actor")).thenReturn(new AssignmentTaskWriteReadSnapshot(1,false,true,"LEGACY",null));
        mvc.perform(get("/api/v1/tasks/task/check-permission").param("userId","actor"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.hasPermission").value(true)).andExpect(jsonPath("$.data.taskId").value("task"))
                .andExpect(jsonPath("$.data.userId").value("actor")).andExpect(jsonPath("$.data.assignmentType").value("USER"))
                .andExpect(jsonPath("$.data.assignmentTarget").value("actor"));
    }
    private void task(boolean allowed) {
        authenticate();
        var task=TaskListResult.TaskInfo.builder().taskId("task").assignmentType(AssignmentType.USER).assignmentTarget("actor").build();
        when(tasks.getTaskInfo("task")).thenReturn(task);
        when(permissions.hasTaskPermission("actor",AssignmentType.USER,"actor")).thenReturn(allowed);
    }
    private void authenticate() {
        var actor=UserPrincipal.builder().userId("actor").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));
    }
}
