package com.portal.controller;

import com.platform.common.dto.UserPrincipal;
import com.portal.component.FunctionUnitAccessComponent;
import com.portal.security.CurrentUserIdArgumentResolver;
import com.portal.service.PortalMainTableViewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A function unit or view that is not open to the current BU + Role answers 403, not a server error (platform
 * access model §7.2: switching workspace can leave the page on a view the new pair may not see).
 */
class PortalMainTableViewAccessDeniedTest {

    private final PortalMainTableViewService views = mock(PortalMainTableViewService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UserPrincipal.builder().userId("u1").username("u1").build(), null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new PortalMainTableViewController(views))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aFunctionUnitClosedToTheCurrentPairIs403() throws Exception {
        when(views.listPublishedViews("u1", "fu-1")).thenThrow(
                new FunctionUnitAccessComponent.FunctionUnitAccessDeniedException("Access denied for function unit: fu-1"));

        mvc.perform(get("/main-table-views/function-units/fu-1/views"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("403"));
    }

    @Test
    void aDisabledFunctionUnitIs403() throws Exception {
        when(views.listPublishedViews("u1", "fu-2")).thenThrow(
                new FunctionUnitAccessComponent.FunctionUnitDisabledException("Function unit is disabled: fu-2"));

        mvc.perform(get("/main-table-views/function-units/fu-2/views"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("403"));
    }
}
