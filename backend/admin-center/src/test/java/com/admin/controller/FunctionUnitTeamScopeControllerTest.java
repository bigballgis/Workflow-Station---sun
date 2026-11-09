package com.admin.controller;

import com.admin.component.DeploymentManagerComponent;
import com.admin.component.FunctionUnitDeploymentListQueryComponent;
import com.admin.component.FunctionUnitListQueryComponent;
import com.admin.component.FunctionUnitManagerComponent;
import com.admin.component.ProcessDeploymentComponent;
import com.admin.entity.FunctionUnit;
import com.admin.security.TeamScopeFixture;
import com.admin.service.FunctionUnitAccessService;
import com.admin.service.FunctionUnitAuditAccessService;
import com.admin.service.FunctionUnitTeamService;
import com.admin.service.UserReferenceResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-28 through the real authorization: a batch holding one unit of another team is refused as a whole before any
 * unit changes; a batch of the caller's own units goes through; the unit list a service call reads is not filtered.
 */
class FunctionUnitTeamScopeControllerTest {

    private final TeamScopeFixture fixture = new TeamScopeFixture();
    private final FunctionUnitManagerComponent manager = mock(FunctionUnitManagerComponent.class);
    private MockMvc api;

    @BeforeEach
    void setUp() {
        FunctionUnit a = fixture.unit("fu-a1", "FU_A", "TEAM_A");
        FunctionUnit a2 = fixture.unit("fu-a2", "FU_A2", "TEAM_A");
        FunctionUnit b = fixture.unit("fu-b1", "FU_B", "TEAM_B");
        when(manager.setEnabled(anyString(), anyBoolean(), anyString(), anyString())).thenReturn(a);
        when(manager.listLatestDeployedFunctionUnits()).thenReturn(List.of(a, a2, b));
        api = fixture.mvc(new FunctionUnitController(manager, mock(DeploymentManagerComponent.class),
                mock(ProcessDeploymentComponent.class), mock(FunctionUnitAccessService.class),
                mock(FunctionUnitAuditAccessService.class), mock(UserReferenceResolver.class), fixture.i18n,
                mock(FunctionUnitListQueryComponent.class), mock(FunctionUnitDeploymentListQueryComponent.class),
                mock(FunctionUnitTeamService.class), fixture.scope));
    }

    @AfterEach
    void tearDown() {
        TeamScopeFixture.signOut();
    }

    @Test
    void aBatchWithOneUnitOfAnotherTeamIsRefusedWholeAndNothingChanges() throws Exception {
        fixture.signIn("tl-a", List.of("TECH_LEAD"), "TEAM_A");

        api.perform(put("/function-units/batch/enabled").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"fu-a1\",\"fu-b1\"],\"enabled\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_ACCESS_DENIED"));
        api.perform(delete("/function-units/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"fu-a1\",\"fu-b1\"]}"))
                .andExpect(status().isForbidden());

        verify(manager, never()).setEnabled(anyString(), anyBoolean(), anyString(), anyString());
        verify(manager, never()).archiveFunctionUnitByCode(anyString());
    }

    @Test
    void aBatchOfTheCallersOwnUnitsGoesThrough() throws Exception {
        fixture.signIn("tl-a", List.of("TECH_LEAD"), "TEAM_A");

        api.perform(put("/function-units/batch/enabled").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"fu-a1\",\"fu-a2\"],\"enabled\":false}"))
                .andExpect(status().isOk());
        api.perform(delete("/function-units/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"fu-a1\",\"fu-a2\"]}"))
                .andExpect(status().isOk());

        verify(manager).setEnabled(eq("fu-a2"), eq(false), anyString(), anyString());
        verify(manager).archiveFunctionUnitByCode("fu-a1");
        verify(manager).archiveFunctionUnitByCode("fu-a2");
    }

    @Test
    void aSingleUnitOfAnotherTeamIsRefusedByItsPath() throws Exception {
        fixture.signIn("tl-a", List.of("TECH_LEAD"), "TEAM_A");

        api.perform(put("/function-units/fu-b1/enabled").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        api.perform(delete("/function-units/fu-b1")).andExpect(status().isForbidden());

        verify(manager, never()).setEnabled(anyString(), anyBoolean(), anyString(), anyString());
        verify(manager, never()).archiveFunctionUnitByCode(anyString());
    }

    @Test
    void theLinkableUnitListShowsTheTeamsUnitsToALeadAndEveryUnitToAServiceCall() throws Exception {
        fixture.signIn("tl-a", List.of("TECH_LEAD"), "TEAM_A");
        api.perform(get("/function-units/deployed/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].canManage").value(true));

        TeamScopeFixture.signOut();
        api.perform(get("/function-units/deployed/latest").header("X-Service-Token", "svc-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }
}
