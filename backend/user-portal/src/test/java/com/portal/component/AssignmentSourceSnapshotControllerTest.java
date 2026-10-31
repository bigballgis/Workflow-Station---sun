package com.portal.component;

import com.platform.common.exception.GlobalExceptionHandler;
import com.portal.controller.AssignmentSourceSnapshotController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentSourceSnapshotControllerTest {
    final AssignmentSourceSnapshotComponent component=mock(AssignmentSourceSnapshotComponent.class);
    final AssignmentSourceSnapshotController controller=new AssignmentSourceSnapshotController(component);
    final String token=java.util.UUID.randomUUID().toString();
    final String body="{\"caseId\":\"00000000-0000-0000-0000-000000000001\",\"instanceId\":\"instance\",\"definitionId\":\"frozen\",\"fields\":[\"reviewer\"]}";
    MockMvc mvc;
    @BeforeEach void configure() {
        ReflectionTestUtils.setField(controller,"serviceToken",token);
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void userTokenCannotReadCommittedSourceValues() throws Exception {
        mvc.perform(post("/internal/assignment/source-snapshot").contentType("application/json").content(body)
                .header("Authorization","Bearer synthetic-user")).andExpect(status().isForbidden());
        verifyNoInteractions(component);
    }
    @Test void missingServiceConfigurationCannotOpenTheEndpoint() throws Exception {
        ReflectionTestUtils.setField(controller,"serviceToken","");
        mvc.perform(post("/internal/assignment/source-snapshot").contentType("application/json").content(body)).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(component);
    }
    @Test void malformedFieldSelectionIsRejectedBeforeReadingPortalData() throws Exception {
        mvc.perform(post("/internal/assignment/source-snapshot").contentType("application/json").content(body.replace("[\"reviewer\"]","[]"))
                .header("X-Internal-Token",token)).andExpect(status().isBadRequest());
        verifyNoInteractions(component);
    }
}
