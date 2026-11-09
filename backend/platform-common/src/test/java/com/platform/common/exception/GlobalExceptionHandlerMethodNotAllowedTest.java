package com.platform.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

class GlobalExceptionHandlerMethodNotAllowedTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void unsupportedMethodOnExistingPathReturns405WithAllowHeader() throws Exception {
        var response = mvc.perform(patch("/business-units")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).contains("GET").contains("POST").doesNotContain("PATCH");
        var body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("error").get("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(body.get("error").get("message").asText()).contains("PATCH");
    }

    @Test
    void supportedMethodIsUnchanged() throws Exception {
        assertThat(mvc.perform(get("/business-units")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void missingPathStillReturns404() throws Exception {
        assertThat(mvc.perform(patch("/no-such-path")).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @RestController
    static class ProbeController {
        @GetMapping("/business-units")
        String list() { return "ok"; }

        @PostMapping("/business-units")
        String create() { return "created"; }
    }
}
