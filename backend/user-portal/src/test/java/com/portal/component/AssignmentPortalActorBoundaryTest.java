package com.portal.component;

import com.platform.common.assignment.AssignmentReopenReceipt;
import com.platform.common.dto.UserPrincipal;
import com.platform.common.exception.GlobalExceptionHandler;
import com.portal.controller.AssignmentReopenController;
import com.portal.security.CurrentUserIdArgumentResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentPortalActorBoundaryTest {
    final AssignmentReopenPortal component=mock(AssignmentReopenPortal.class);
    final AssignmentReopenController controller=new AssignmentReopenController(component);
    final UUID operation=UUID.randomUUID();
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void aForgedUserIdHeaderCannotInvokeProtectedReopenWithoutAUserPrincipal() throws Exception {
        SecurityContextHolder.clearContext();
        var mvc=MockMvcBuilders.standaloneSetup(controller).setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/processes/source/reopen").header("X-User-Id","victim").contentType("application/json")
                .content("{\"operationId\":\""+operation+"\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(component);
    }
    @Test void authenticatedIdentityTakesPrecedenceOverAConflictingUserIdHeader() throws Exception {
        var principal=UserPrincipal.builder().userId("authenticated").username("synthetic").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
        when(component.reopen("source",operation,"authenticated")).thenReturn(new AssignmentReopenReceipt.Result(operation,"instance","frozen","READY"));
        var mvc=MockMvcBuilders.standaloneSetup(controller).setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/processes/source/reopen").header("X-User-Id","victim").contentType("application/json")
                .content("{\"operationId\":\""+operation+"\"}")).andExpect(status().isOk());
        verify(component).reopen("source",operation,"authenticated");
    }
    @Test void aForgedHeaderCannotHandoffTasksOrReadAnotherActorsCompletionState() throws Exception {
        SecurityContextHolder.clearContext();
        var transport=mock(com.portal.client.AssignmentEngineTransport.class);
        var completion=mock(AssignmentCompletionOrchestrator.class);
        var mvc=MockMvcBuilders.standaloneSetup(new com.portal.controller.AssignmentHandoffController(transport),
                        new com.portal.controller.AssignmentOperationStatusController(completion))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/tasks/task/assignment/handoff").header("X-User-Id","victim").contentType("application/json")
                .content("{\"target\":\"another\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/tasks/assignment/operations/"+operation).header("X-User-Id","victim")).andExpect(status().isForbidden());
        verifyNoInteractions(transport,completion);
    }
}
