package com.workflow.assignment;

import com.platform.common.exception.GlobalExceptionHandler;
import com.workflow.controller.AssignmentCapabilityController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssignmentCapabilityControllerTest {
    final AssignmentCapabilityController controller=new AssignmentCapabilityController(new AssignmentRuntimeSupport());
    final String token=java.util.UUID.randomUUID().toString();
    MockMvc mvc;
    @BeforeEach void configure() {
        ReflectionTestUtils.setField(controller,"token",token);
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void configuredServiceTokenReportsTheImplementedRuntimeContract() throws Exception {
        mvc.perform(get("/api/v1/internal/assignment/capabilities").header("X-Internal-Token",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.contractVersion").value(1))
                .andExpect(jsonPath("$.data.ready").value(true));
    }
    @Test void anOrdinaryUserTokenDoesNotAuthorizeTheServiceOnlyProbe() throws Exception {
        mvc.perform(get("/api/v1/internal/assignment/capabilities").header("Authorization","Bearer synthetic-user-token"))
                .andExpect(status().isForbidden());
    }
    @Test void aMissingServiceSecretCannotOpenTheProbe() throws Exception {
        ReflectionTestUtils.setField(controller,"token","");
        mvc.perform(get("/api/v1/internal/assignment/capabilities")).andExpect(status().isServiceUnavailable());
    }
}
