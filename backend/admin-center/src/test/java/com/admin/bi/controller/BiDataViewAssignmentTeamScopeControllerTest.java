package com.admin.bi.controller;

import com.admin.bi.component.BiAdminAccessGuard;
import com.admin.bi.component.BiAssignmentConfigurationComponent;
import com.admin.bi.component.BiDataViewAssignmentListQueryComponent;
import com.admin.bi.dto.response.DataViewAssignmentResponse;
import com.admin.bi.dto.response.DataViewFunctionUnitOptionResponse;
import com.admin.bi.service.BiDataViewAssignmentService;
import com.admin.security.TeamScopeFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-32 through the real authorization: a Technical Lead of Team A binds dashboards to Views of Team A's units only,
 * whatever the dashboard; a System Administrator binds to any unit. BI unit 7 is FU_A (Team A), 8 is FU_B (Team B);
 * assignment asg-a binds FU_A, asg-b binds FU_B.
 */
class BiDataViewAssignmentTeamScopeControllerTest {

    private final TeamScopeFixture fixture = new TeamScopeFixture();
    private final BiDataViewAssignmentService service = mock(BiDataViewAssignmentService.class);
    private final BiAssignmentConfigurationComponent configuration = mock(BiAssignmentConfigurationComponent.class);
    private MockMvc api;

    @BeforeEach
    void setUp() {
        fixture.unit("fu-a1", "FU_A", "TEAM_A");
        fixture.unit("fu-b1", "FU_B", "TEAM_B");
        fixture.assignment("asg-a", "FU_A");
        fixture.assignment("asg-b", "FU_B");
        when(service.create(any())).thenReturn(DataViewAssignmentResponse.builder().id("new").build());
        when(service.createBatch(any())).thenReturn(List.of());
        when(service.update(anyString(), any())).thenReturn(DataViewAssignmentResponse.builder().id("u").build());
        api = fixture.mvc(new BiDataViewAssignmentController(service, new BiAdminAccessGuard(), configuration,
                mock(BiDataViewAssignmentListQueryComponent.class), fixture.scope));
    }

    @AfterEach
    void tearDown() {
        TeamScopeFixture.signOut();
    }

    @Test
    void aTechnicalLeadBindsToTheirTeamsViewAndNotToAnotherTeams() throws Exception {
        fixture.signIn("tl-a", List.of("TECH_LEAD"), "TEAM_A");

        api.perform(post("/bi/data-view-assignments").contentType(MediaType.APPLICATION_JSON).content(body("FU_B")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_ACCESS_DENIED"));
        api.perform(post("/bi/data-view-assignments/batch").contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody("FU_B")))
                .andExpect(status().isForbidden());
        verify(service, never()).create(any());
        verify(service, never()).createBatch(any());

        api.perform(post("/bi/data-view-assignments").contentType(MediaType.APPLICATION_JSON).content(body("FU_A")))
                .andExpect(status().isOk());
        verify(service).create(any());
    }

    @Test
    void anUpdateIsCheckedOnTheRowAndOnTheUnitItMovesTo() throws Exception {
        fixture.signIn("tl-a", List.of("TECH_LEAD"), "TEAM_A");

        api.perform(put("/bi/data-view-assignments/asg-a").contentType(MediaType.APPLICATION_JSON).content(body("FU_B")))
                .andExpect(status().isForbidden());
        api.perform(put("/bi/data-view-assignments/asg-b").contentType(MediaType.APPLICATION_JSON).content(body("FU_A")))
                .andExpect(status().isForbidden());
        api.perform(delete("/bi/data-view-assignments/asg-b")).andExpect(status().isForbidden());
        api.perform(put("/bi/data-view-assignments/asg-b/enabled").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        verify(service, never()).update(anyString(), any());
        verify(service, never()).delete(anyString());
        verify(configuration, never()).setDataViewEnabled(anyString(), anyBoolean());

        api.perform(put("/bi/data-view-assignments/asg-a").contentType(MediaType.APPLICATION_JSON).content(body("FU_A")))
                .andExpect(status().isOk());
    }

    @Test
    void aSystemAdministratorBindsToAnyUnit() throws Exception {
        fixture.signIn("admin", List.of("SYS_ADMIN"));

        api.perform(post("/bi/data-view-assignments").contentType(MediaType.APPLICATION_JSON).content(body("FU_B")))
                .andExpect(status().isOk());
    }

    @Test
    void theUnitDropdownOffersTheTeamsUnitsOnly() throws Exception {
        fixture.signIn("tl-a", List.of("TECH_LEAD"), "TEAM_A");
        when(service.listFunctionUnits()).thenReturn(List.of(new DataViewFunctionUnitOptionResponse("FU_A", "A"),
                new DataViewFunctionUnitOptionResponse("FU_B", "B")));

        api.perform(get("/bi/data-view-assignments/function-units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("FU_A"));
        api.perform(get("/bi/data-view-assignments/function-units/FU_B/tables")).andExpect(status().isForbidden());
        api.perform(get("/bi/data-view-assignments/function-units/FU_B/tables/cases/views"))
                .andExpect(status().isForbidden());
    }

    private static String body(String functionUnitCode) {
        return "{\"dashboardId\":\"d-1\",\"functionUnitCode\":\"" + functionUnitCode + "\",\"viewName\":\"Open cases\"}";
    }

    private static String batchBody(String functionUnitCode) {
        return "{\"dashboardIds\":[\"d-1\"],\"functionUnitCode\":\"" + functionUnitCode
                + "\",\"viewName\":\"Open cases\"}";
    }
}
