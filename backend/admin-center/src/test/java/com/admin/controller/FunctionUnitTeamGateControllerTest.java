package com.admin.controller;

import com.admin.component.DeploymentManagerComponent;
import com.admin.component.FunctionUnitManagerComponent;
import com.admin.component.ProcessDeploymentComponent;
import com.admin.entity.FunctionUnit;
import com.admin.exception.AdminApiExceptionHandler;
import com.admin.exception.FunctionUnitTeamForbiddenException;
import com.admin.service.FunctionUnitTeamService;
import com.platform.common.dto.UserPrincipal;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The three endpoints a DW deployment walks through refuse a deploying user outside the unit's team with 403, before
 * anything is imported, validated or deployed (platform access model §8.1).
 */
class FunctionUnitTeamGateControllerTest {

    private final FunctionUnitManagerComponent manager = mock(FunctionUnitManagerComponent.class);
    private final ProcessDeploymentComponent processDeployment = mock(ProcessDeploymentComponent.class);
    private final FunctionUnitTeamService teamService = mock(FunctionUnitTeamService.class);
    private final I18nService i18n = mock(I18nService.class);
    private MockMvc importApi;
    private MockMvc catalogApi;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UserPrincipal.builder().userId("u-dev").username("dev").roles(List.of()).permissions(List.of()).build(),
                null, List.of()));
        when(i18n.getMessage(anyString())).thenAnswer(call -> call.getArgument(0));
        when(manager.getFunctionUnitById("fu-1")).thenReturn(FunctionUnit.builder().id("fu-1").code("fu-x").build());
        doThrow(new FunctionUnitTeamForbiddenException("You cannot deploy function units of team Team A (TEAM_A)"))
                .when(teamService).assertMayDeploy("fu-x", "u-dev");
        importApi = MockMvcBuilders.standaloneSetup(new FunctionUnitImportController(manager,
                        mock(DeploymentManagerComponent.class), processDeployment, i18n, teamService))
                .setControllerAdvice(new AdminApiExceptionHandler()).build();
        catalogApi = MockMvcBuilders.standaloneSetup(new FunctionUnitController(manager,
                        mock(DeploymentManagerComponent.class), processDeployment,
                        mock(com.admin.service.FunctionUnitAccessService.class),
                        mock(com.admin.service.FunctionUnitAuditAccessService.class),
                        mock(com.admin.service.UserReferenceResolver.class), i18n,
                        mock(com.admin.component.FunctionUnitListQueryComponent.class),
                        mock(com.admin.component.FunctionUnitDeploymentListQueryComponent.class), teamService, com.admin.security.TestTeamScopes.everything()))
                .setControllerAdvice(new AdminApiExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deployingAnotherTeamsUnitIsRefusedBeforeAnythingIsDeployed() throws Exception {
        importApi.perform(post("/function-units-import/fu-1/deploy").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FU_TEAM_FORBIDDEN"));
        verify(processDeployment, never()).deployFunctionUnitProcess(any());
    }

    @Test
    void validatingAnotherTeamsUnitIsRefused() throws Exception {
        catalogApi.perform(post("/function-units/fu-1/validate"))
                .andExpect(status().isForbidden());
        verify(manager, never()).validateFunctionUnit(any(), any());
    }

    @Test
    void importingIntoATeamTheUserIsNotOnIsRefusedNotReportedAsAFailedImport() throws Exception {
        when(manager.importFunctionPackage(any(), any(), any()))
                .thenThrow(new FunctionUnitTeamForbiddenException("You cannot deploy function units of team Team A"));

        importApi.perform(multipart("/function-units-import/import")
                        .file(new MockMultipartFile("file", "fu.zip", "application/zip", new byte[]{1, 2, 3})))
                .andExpect(status().isForbidden());
    }
}
