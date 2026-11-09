package com.admin.controller;

import com.admin.component.RelationTableStructureListQueryComponent;
import com.admin.dto.response.RelationTableResponse;
import com.admin.entity.RelationTableAccess;
import com.admin.repository.RelationTableAccessRepository;
import com.admin.security.TeamScopeFixture;
import com.admin.service.RelationTableDeployService;
import com.admin.service.RelationTableStructureService;
import com.admin.service.impl.RelationTableAccessServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
 * Relation tables through the real authorization (D-47): a Technical Lead of Team A changes the tables of their team
 * only, links them only to their team's units, and cannot reach another table's grant through their own table's path.
 * Table 1 is Team A's, table 3 Team B's; grant acc-a belongs to table 1, acc-b to table 3.
 */
class RelationTableTeamScopeControllerTest {

    private final TeamScopeFixture fixture = new TeamScopeFixture();
    private final RelationTableStructureService structureService = mock(RelationTableStructureService.class);
    private final RelationTableAccessRepository accessRepository = mock(RelationTableAccessRepository.class);
    private final RelationTableAccessServiceImpl accessService = new RelationTableAccessServiceImpl(accessRepository);
    private MockMvc api;

    @BeforeEach
    void setUp() {
        fixture.unit("fu-a1", "FU_A", "TEAM_A");
        fixture.unit("fu-b1", "FU_B", "TEAM_B");
        fixture.relationTable(1L, "FU_A");
        fixture.relationTable(3L, "FU_B");
        fixture.signIn("tl-a", List.of("TECH_LEAD"), "TEAM_A");
        when(accessRepository.findById("acc-a")).thenReturn(Optional.of(grant("acc-a", 1L)));
        when(accessRepository.findById("acc-b")).thenReturn(Optional.of(grant("acc-b", 3L)));
        when(accessRepository.save(any(RelationTableAccess.class))).thenAnswer(inv -> inv.getArgument(0));
        api = fixture.mvc(new RelationTableStructureController(structureService, mock(RelationTableDeployService.class),
                accessService, mock(RelationTableStructureListQueryComponent.class), fixture.scope));
    }

    @AfterEach
    void tearDown() {
        TeamScopeFixture.signOut();
    }

    @Test
    void aGrantOfAnotherTableIsNotReachableThroughTheCallersOwnTable() throws Exception {
        api.perform(put("/relation-tables/structures/1/access/acc-b").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionLevel\":\"READONLY\"}"))
                .andExpect(status().isBadRequest());
        api.perform(delete("/relation-tables/structures/1/access/acc-b")).andExpect(status().isBadRequest());

        verify(accessRepository, never()).save(any());
        verify(accessRepository, never()).delete(any());
        verify(accessRepository, never()).deleteById(any());
    }

    @Test
    void anotherTeamsTableIsRefusedBeforeTheGrantIsLoaded() throws Exception {
        api.perform(put("/relation-tables/structures/3/access/acc-b").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionLevel\":\"READONLY\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_ACCESS_DENIED"));

        verify(accessRepository, never()).findById(any());
        verify(accessRepository, never()).save(any());
    }

    @Test
    void theCallersOwnGrantOfTheirOwnTableChanges() throws Exception {
        api.perform(put("/relation-tables/structures/1/access/acc-a").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionLevel\":\"READONLY\"}"))
                .andExpect(status().isOk());
        api.perform(delete("/relation-tables/structures/1/access/acc-a")).andExpect(status().isNoContent());

        verify(accessRepository).save(any(RelationTableAccess.class));
        verify(accessRepository).delete(any(RelationTableAccess.class));
    }

    @Test
    void theServiceRefusesAGrantOfAnotherTableWithoutWriting() {
        assertThatThrownBy(() -> accessService.updatePermissionLevel(1L, "acc-b", "READONLY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accessService.removeAccess(1L, "acc-b"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(accessRepository, never()).save(any());
        verify(accessRepository, never()).delete(any());
    }

    @Test
    void aNewTableIsLinkedOnlyToTheCallersUnitsAndToAtLeastOne() throws Exception {
        api.perform(post("/relation-tables/structures").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("[\"fu-a1\",\"fu-b1\"]")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_ACCESS_DENIED"));
        api.perform(post("/relation-tables/structures").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("[]")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("admin.team.rt_link_required"));
        verify(structureService, never()).createTable(any());

        when(structureService.createTable(any())).thenReturn(RelationTableResponse.builder().id(9L).build());
        api.perform(post("/relation-tables/structures").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("[\"fu-a1\"]")))
                .andExpect(status().isCreated());
    }

    @Test
    void relinkingTheCallersTableToAnotherTeamsUnitIsRefused() throws Exception {
        api.perform(put("/relation-tables/structures/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"functionUnitIds\":[\"fu-a1\",\"fu-b1\"]}"))
                .andExpect(status().isForbidden());
        api.perform(put("/relation-tables/structures/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"functionUnitIds\":[]}"))
                .andExpect(status().isForbidden());
        verify(structureService, never()).updateTable(eq(1L), any());

        when(structureService.updateTable(eq(1L), any())).thenReturn(RelationTableResponse.builder().id(1L).build());
        api.perform(put("/relation-tables/structures/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"links unchanged\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void theFullListKeepsCommonAndTheCallersTablesWithTheirWriteFlag() throws Exception {
        when(structureService.getTableList()).thenReturn(List.of(table(1L, "FU_A"), table(2L, "FU_A", "FU_B"),
                table(3L, "FU_B"), table(4L)));

        String body = api.perform(get("/relation-tables/structures")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"id\":1").contains("\"id\":2").contains("\"id\":4").doesNotContain("\"id\":3");
        api.perform(get("/relation-tables/structures"))
                .andExpect(jsonPath("$[0].canWrite").value(true))
                .andExpect(jsonPath("$[1].canWrite").value(false))
                .andExpect(jsonPath("$[2].canWrite").value(false));
    }

    private static String createBody(String functionUnitIds) {
        return "{\"tableName\":\"team_rates\",\"functionUnitIds\":" + functionUnitIds
                + ",\"fieldDefinitions\":[{\"fieldName\":\"rate\",\"dataType\":\"VARCHAR\"}]}";
    }

    private static RelationTableAccess grant(String id, Long tableId) {
        RelationTableAccess access = new RelationTableAccess();
        access.setId(id);
        access.setTableId(tableId);
        access.setTargetType("ROLE");
        access.setTargetId("r-1");
        return access;
    }

    private static RelationTableResponse table(long id, String... codes) {
        RelationTableResponse table = RelationTableResponse.builder().id(id).build();
        table.setFunctionUnits(java.util.Arrays.stream(codes)
                .map(code -> new RelationTableResponse.FunctionUnitBrief("id-" + code, code, code)).toList());
        return table;
    }
}
