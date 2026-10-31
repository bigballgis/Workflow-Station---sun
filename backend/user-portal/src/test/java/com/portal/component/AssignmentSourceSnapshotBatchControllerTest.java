package com.portal.component;

import com.platform.common.assignment.*;
import com.platform.common.exception.GlobalExceptionHandler;
import com.platform.common.util.JsonUtils;
import com.portal.controller.AssignmentSourceSnapshotController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentSourceSnapshotBatchControllerTest {
    final AssignmentSourceSnapshotComponent component=mock(AssignmentSourceSnapshotComponent.class);
    final AssignmentCoordinationVerifier coordination=mock(AssignmentCoordinationVerifier.class);
    final AssignmentSourceSnapshotController controller=new AssignmentSourceSnapshotController(component);
    final String token=UUID.randomUUID().toString();
    final AssignmentSourceSnapshot.Request request=new AssignmentSourceSnapshot.Request(UUID.randomUUID(),"instance","frozen",List.of("role"),null);
    MockMvc mvc;
    @BeforeEach void configure() {
        ReflectionTestUtils.setField(controller,"serviceToken",token);
        ReflectionTestUtils.setField(controller,"coordination",coordination);
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void serviceTokenAndCoordinationPrecedeTheBatchedRead() throws Exception {
        mvc.perform(post("/internal/assignment/source-snapshots").header("X-Internal-Token",token)
                .contentType("application/json").content(body(List.of(request)))).andExpect(status().isOk());
        var order=inOrder(coordination,component); order.verify(coordination).requireSamePrimary(); order.verify(component).readMany(List.of(request));
    }
    @Test void aUserTokenCannotReadTheBatch() throws Exception {
        mvc.perform(post("/internal/assignment/source-snapshots").header("Authorization","Bearer synthetic-user")
                .contentType("application/json").content(body(List.of(request)))).andExpect(status().isForbidden());
        verifyNoInteractions(component,coordination);
    }
    @Test void emptyOversizedAndInvalidNestedRequestsAreRejected() throws Exception {
        for (String body:List.of(body(List.of()),body(Collections.nCopies(1001,request)),"{\"requests\":[null]}",
                body(List.of(new AssignmentSourceSnapshot.Request(request.caseId(),"instance","frozen",List.of(),null))))) {
            mvc.perform(post("/internal/assignment/source-snapshots").header("X-Internal-Token",token)
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(component,coordination);
    }
    private String body(List<AssignmentSourceSnapshot.Request> requests) { return JsonUtils.toJson(Map.of("requests",requests)); }
}
