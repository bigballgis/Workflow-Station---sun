package com.portal.component;

import com.platform.common.assignment.AssignmentPostAction;
import com.platform.common.exception.GlobalExceptionHandler;
import com.platform.common.util.JsonUtils;
import com.portal.controller.AssignmentPostActionSnapshotController;
import com.portal.repository.AssignmentPostActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssignmentPostActionSnapshotControllerTest {
    final AssignmentPostActionRepository repository=mock(AssignmentPostActionRepository.class);
    final com.platform.common.assignment.AssignmentCoordinationVerifier coordination=mock(com.platform.common.assignment.AssignmentCoordinationVerifier.class);
    final AssignmentPostActionSnapshotController controller=new AssignmentPostActionSnapshotController(repository);
    final String token=UUID.randomUUID().toString();
    final AssignmentPostAction.SnapshotRequest request=new AssignmentPostAction.SnapshotRequest(UUID.randomUUID(),0,"event","instance","actor");
    MockMvc mvc;
    @BeforeEach void configure() {
        ReflectionTestUtils.setField(controller,"serviceToken",token);
        ReflectionTestUtils.setField(controller,"coordination",coordination);
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void aUserHeaderCannotReadPrivateFrozenBusinessVariables() throws Exception {
        mvc.perform(post("/internal/assignment/post-action-snapshot").header("Authorization","Bearer synthetic-user-token")
                .contentType(MediaType.APPLICATION_JSON).content(JsonUtils.toJson(request))).andExpect(status().isForbidden());
        verifyNoInteractions(repository,coordination);
    }
    @Test void aServiceSnapshotEchoesItsExactOperationBinding() throws Exception {
        when(repository.variables(request)).thenReturn(Map.of("recipient","original"));
        mvc.perform(post("/internal/assignment/post-action-snapshot").header("X-Internal-Token",token)
                .contentType(MediaType.APPLICATION_JSON).content(JsonUtils.toJson(request))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request.operationId").value(request.operationId().toString()))
                .andExpect(jsonPath("$.data.variables.recipient").value("original"));
        var ordered=inOrder(coordination,repository);
        ordered.verify(coordination).requireSamePrimary(); ordered.verify(repository).variables(request);
    }
    @Test void failedPhysicalProofCannotReadBusinessVariables() throws Exception {
        doThrow(new com.platform.common.assignment.AssignmentFailure(
                com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE))
                .when(coordination).requireSamePrimary();
        mvc.perform(post("/internal/assignment/post-action-snapshot").header("X-Internal-Token",token)
                .contentType(MediaType.APPLICATION_JSON).content(JsonUtils.toJson(request))).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(repository);
    }
}
