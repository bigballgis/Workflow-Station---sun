package com.platform.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.enums.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerNotFoundTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void missingEndpointReturns404RatherThan500() throws Exception {
        var response = mvc.perform(get("/missing-endpoint")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(404);
        var body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("error").get("code").asText()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
    }

    @Test
    void missingStaticResourceReturns404RatherThan500() throws Exception {
        var response = mvc.perform(get("/missing-resource")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(404);
        var body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("error").get("code").asText()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
    }

    @Test
    void actualServerFailureStillReturns500() throws Exception {
        var response = mvc.perform(get("/server-failure")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(500);
        var body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("error").get("code").asText()).isEqualTo("SYS_INTERNAL_ERROR");
    }

    @Test
    void validEndpointIsUnchanged() throws Exception {
        assertThat(mvc.perform(get("/valid")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @RestController
    static class ProbeController {
        @GetMapping("/valid")
        String valid() { return "ok"; }

        @GetMapping("/missing-resource")
        void missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "missing.txt");
        }

        @GetMapping("/server-failure")
        void serverFailure() throws Exception { throw new Exception("verification failure"); }
    }
}
