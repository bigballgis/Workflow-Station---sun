package com.platform.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerOptimisticLockTest {
    @Test
    void concurrentWriteReturnsConflictWithoutExposingEntityDetails() throws Exception {
        var response = MockMvcBuilders.standaloneSetup(new ConflictingWrite()).setControllerAdvice(new GlobalExceptionHandler())
                .build().perform(post("/concurrent-write")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(409);
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("DATA_CONFLICT");
        assertThat(body.path("error").path("message").asText()).isEqualTo("Data was modified by another save; reload and retry");
    }

    @RestController
    static class ConflictingWrite {
        @PostMapping("/concurrent-write")
        void write() { throw new OptimisticLockingFailureException("private entity identity"); }
    }
}
