package com.admin.controller;

import com.admin.component.UserPortalAuditClient;
import com.admin.dto.request.UserPortalAuditListQueryRequest;
import com.admin.security.TeamScopeFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * User Portal audit for a Technical Lead (§8.2): Admin, not the browser, decides which units the Portal filters by, and
 * a page holding another team's rows (a Portal that did not filter) is withheld, never passed on.
 */
class UserPortalAuditTeamScopeControllerTest {

    private static final String LIST = "/security/user-portal-audit-logs/list-query";

    private final TeamScopeFixture fixture = new TeamScopeFixture();
    private final UserPortalAuditClient client = mock(UserPortalAuditClient.class);
    private MockMvc api;

    @BeforeEach
    void setUp() {
        fixture.unit("fu-a1", "FU_A", "TEAM_A");
        fixture.unit("fu-b1", "FU_B", "TEAM_B");
        api = fixture.mvc(new UserPortalAuditController(client, fixture.scope, fixture.i18n));
    }

    @AfterEach
    void tearDown() {
        TeamScopeFixture.signOut();
    }

    @Test
    void theUnitsTheBrowserSendsAreReplacedByTheCallersTeams() throws Exception {
        fixture.signIn("tl-a", List.of("TECH_LEAD"), "TEAM_A");
        when(client.queryAuditLogList(any())).thenReturn(page(row("FU_A")));

        api.perform(post(LIST).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":0,\"size\":20,\"functionUnitCodes\":[\"FU_B\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].functionUnitCode").value("FU_A"));

        ArgumentCaptor<UserPortalAuditListQueryRequest> sent = ArgumentCaptor.forClass(UserPortalAuditListQueryRequest.class);
        verify(client).queryAuditLogList(sent.capture());
        assertThat(sent.getValue().functionUnitCodes()).containsExactly("FU_A");
    }

    @Test
    void aPageWithAnotherTeamsRowsIsWithheld() throws Exception {
        fixture.signIn("tl-a", List.of("TECH_LEAD"), "TEAM_A");
        when(client.queryAuditLogList(any())).thenReturn(page(row("FU_A"), row("FU_B"), row(null)));

        String body = api.perform(post(LIST).contentType(MediaType.APPLICATION_JSON).content("{\"page\":0,\"size\":20}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_SCOPE_NOT_APPLIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("FU_B");
    }

    @Test
    void anAuditorReadsEveryUnitAndIsNotChecked() throws Exception {
        fixture.signIn("aud", List.of("AUDITOR"));
        when(client.queryAuditLogList(any())).thenReturn(page(row("FU_A"), row("FU_B")));

        api.perform(post(LIST).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":0,\"size\":20,\"functionUnitCodes\":[\"FU_A\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));

        ArgumentCaptor<UserPortalAuditListQueryRequest> sent = ArgumentCaptor.forClass(UserPortalAuditListQueryRequest.class);
        verify(client).queryAuditLogList(sent.capture());
        assertThat(sent.getValue().functionUnitCodes()).as("null = every unit").isNull();
    }

    @Test
    void theUnitDropdownOffersTheTeamsUnitsOnly() throws Exception {
        fixture.signIn("tl-a", List.of("TECH_LEAD"), "TEAM_A");
        when(client.getFunctionUnitCodes()).thenReturn(List.of(Map.of("code", "FU_A"), Map.of("code", "FU_B")));

        api.perform(get("/security/user-portal-audit-logs/function-units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("FU_A"));
    }

    private static Map<String, Object> page(Map<?, ?>... rows) {
        return Map.of("columns", List.of(), "content", List.of(rows), "page", 0, "size", 20,
                "totalElements", rows.length);
    }

    private static Map<String, Object> row(String code) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", 1);
        row.put("functionUnitCode", code);
        return row;
    }
}
