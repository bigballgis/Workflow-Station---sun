package com.developer.exception;

import com.developer.controller.FunctionUnitController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkspaceExceptionHandlerMissingParameterTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new FunctionUnitController(null))
            .setControllerAdvice(new WorkspaceExceptionHandler())
            .build();

    @Test
    void compareV2RejectsMissingTargetVersionAsClientError() throws Exception {
        mvc.perform(get("/function-units/50030/versions/compare-v2")
                        .param("versionId1", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_INVALID_INPUT"));
    }

    @Test
    void compareV2RejectsMissingBaseVersionAsClientError() throws Exception {
        mvc.perform(get("/function-units/50030/versions/compare-v2")
                        .param("versionId2", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VAL_INVALID_INPUT"));
    }
}
