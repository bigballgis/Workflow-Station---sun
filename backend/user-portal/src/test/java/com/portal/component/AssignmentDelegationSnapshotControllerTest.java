package com.portal.component;

import com.platform.common.exception.GlobalExceptionHandler;
import com.portal.controller.AssignmentDelegationSnapshotController;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentDelegationSnapshotControllerTest {
    final AssignmentDelegationSnapshotComponent component=mock(AssignmentDelegationSnapshotComponent.class);
    final AssignmentDelegationSnapshotController controller=new AssignmentDelegationSnapshotController(component);
    final String body="{\"caseId\":\"00000000-0000-0000-0000-000000000001\",\"taskId\":\"task\",\"instanceId\":\"instance\",\"definitionId\":\"frozen\",\"actor\":\"actual\",\"nominalHolder\":\"nominal\"}";
    @Test void browserIdentityCannotMintADelegationProof() throws Exception {
        ReflectionTestUtils.setField(controller,"serviceToken",java.util.UUID.randomUUID().toString());
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/internal/assignment/delegation-snapshot").contentType("application/json").content(body)
                .header("Authorization","Bearer synthetic-user")).andExpect(status().isForbidden());
        verifyNoInteractions(component);
    }
    @Test void unknownServiceConfigurationFailsClosed() throws Exception {
        ReflectionTestUtils.setField(controller,"serviceToken","");
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/internal/assignment/delegation-snapshot").contentType("application/json").content(body)).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(component);
    }
}
