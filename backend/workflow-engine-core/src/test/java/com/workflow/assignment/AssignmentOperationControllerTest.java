package com.workflow.assignment;

import com.platform.common.exception.GlobalExceptionHandler;
import com.workflow.controller.AssignmentOperationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssignmentOperationControllerTest {
    final AssignmentAcceptedCompletion completion = mock(AssignmentAcceptedCompletion.class);
    final AssignmentOperationRecovery recovery = mock(AssignmentOperationRecovery.class);
    final AssignmentOperationController controller = new AssignmentOperationController(completion, recovery);
    final String token = java.util.UUID.randomUUID().toString();
    MockMvc mvc;

    @BeforeEach void configure() {
        ReflectionTestUtils.setField(controller, "serviceToken", token);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void aUserAuthorizationHeaderDoesNotGrantAccessToAcceptedEffects() throws Exception {
        mvc.perform(get("/api/v1/internal/assignment/operations").header("Authorization", "Bearer synthetic-user-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(completion, recovery);
    }

    @Test void unconfiguredServiceAuthenticationCannotSilentlyOpenTheEndpoint() throws Exception {
        ReflectionTestUtils.setField(controller, "serviceToken", "");
        mvc.perform(get("/api/v1/internal/assignment/operations")).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(completion, recovery);
    }

    @Test void explicitServiceTokenCanReadTheBoundedRecoveryQueue() throws Exception {
        when(recovery.pending(50)).thenReturn(java.util.List.of());
        mvc.perform(get("/api/v1/internal/assignment/operations").header("X-Internal-Token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(recovery).pending(50);
    }
}
