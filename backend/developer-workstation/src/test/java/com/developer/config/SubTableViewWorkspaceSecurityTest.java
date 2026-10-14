package com.developer.config;

import com.developer.controller.SubTableViewController;
import com.developer.controller.RelationTableViewController;
import com.developer.entity.FormDefinition;
import com.developer.entity.FormTableBinding;
import com.developer.entity.FunctionUnit;
import com.developer.repository.FormTableBindingRepository;
import com.developer.security.FunctionUnitWorkspaceAccessDeniedException;
import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.security.FormBindingWorkspaceAccessService;
import com.developer.security.WorkspaceAccessAction;
import com.developer.service.SubTableViewService;
import com.developer.service.RelationViewService;
import com.platform.common.dto.UserPrincipal;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubTableViewWorkspaceSecurityTest {
    private final FunctionUnitWorkspaceAccessService access = mock(FunctionUnitWorkspaceAccessService.class);
    private final SubTableViewService views = mock(SubTableViewService.class);
    private final RelationViewService relationViews = mock(RelationViewService.class);
    private final FormTableBindingRepository bindings = mock(FormTableBindingRepository.class);
    private MockMvc mvc;
    private static final String BASE = "/api/forms/11/sub-table-views/19";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        var unit = FunctionUnit.builder().id(7L).build();
        var form = FormDefinition.builder().id(11L).functionUnit(unit).build();
        var binding = FormTableBinding.builder().id(19L).form(form).build();
        when(bindings.findByIdWithTable(19L)).thenReturn(Optional.of(binding));
        when(bindings.findById(19L)).thenReturn(Optional.of(binding));
        var interceptor = new FunctionUnitWorkspaceAccessInterceptor(access, mock(I18nService.class),
                new FormBindingWorkspaceAccessService(bindings, access));
        mvc = MockMvcBuilders.standaloneSetup(new SubTableViewController(views, bindings),
                        new RelationTableViewController(relationViews, bindings))
                .addInterceptors(interceptor).build();
        var principal = UserPrincipal.builder().userId("readonly-user").username("readonly-user").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "unused", List.of()));
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    private void perform(MockHttpServletRequestBuilder request, String path, int expected) throws Exception {
        mvc.perform(request.servletPath(path)).andExpect(status().is(expected));
    }

    @Test
    void readOnlyBlocksEveryMutationIncludingGetOrCreate() throws Exception {
        doThrow(new FunctionUnitWorkspaceAccessDeniedException("Read-only workspace"))
                .when(access).assertCanAccess(7L, WorkspaceAccessAction.MODIFY);
        perform(get(BASE + "/or-create"), BASE + "/or-create", 403);
        perform(post(BASE + "/default"), BASE + "/default", 403);
        perform(put(BASE).contentType("application/json").content("[]"), BASE, 403);
        verifyNoInteractions(views);
    }

    @Test
    void viewAndFieldsRequireViewOfOwningUnit() throws Exception {
        perform(get(BASE), BASE, 200);
        perform(get(BASE + "/fields"), BASE + "/fields", 200);
        verify(access, times(2)).assertCanAccess(7L, WorkspaceAccessAction.VIEW);
        verify(access, never()).assertCanAccess(7L, WorkspaceAccessAction.MODIFY);
    }

    @Test
    void spoofedParentFormIsRejected() throws Exception {
        String wrong = "/api/forms/12/sub-table-views/19";
        perform(get(wrong), wrong, 404);
        verifyNoInteractions(views);
    }

    @Test
    void inaccessibleOwningWorkspaceCannotBeRead() throws Exception {
        doThrow(new FunctionUnitWorkspaceAccessDeniedException("Outside workspace"))
                .when(access).assertCanAccess(7L, WorkspaceAccessAction.VIEW);
        perform(get(BASE), BASE, 403);
        verifyNoInteractions(views);
    }

    @Test
    void relationViewWritesUseTheSameOwnershipBoundary() throws Exception {
        doThrow(new FunctionUnitWorkspaceAccessDeniedException("Read-only workspace"))
                .when(access).assertCanAccess(7L, WorkspaceAccessAction.MODIFY);
        String path = "/api/forms/11/relation-views/19";
        perform(put(path).contentType("application/json").content("[]"), path, 403);
        verifyNoInteractions(relationViews);
    }

    @Test
    void authorizedWorkspaceStillAllowsExplicitWrites() throws Exception {
        perform(put(BASE).contentType("application/json").content("[]"), BASE, 200);
        perform(post(BASE + "/default"), BASE + "/default", 200);
        perform(get(BASE + "/or-create"), BASE + "/or-create", 200);
        verify(access, times(3)).assertCanAccess(7L, WorkspaceAccessAction.MODIFY);
        verify(views).saveViewConfig(19L, List.of());
        verify(views).createDefaultViewConfig(19L);
        verify(views).getOrCreateViewConfig(19L);
    }
}
