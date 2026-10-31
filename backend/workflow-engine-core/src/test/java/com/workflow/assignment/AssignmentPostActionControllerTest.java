package com.workflow.assignment;

import com.platform.common.assignment.AssignmentPostAction;
import com.platform.common.exception.GlobalExceptionHandler;
import com.platform.common.util.JsonUtils;
import com.workflow.controller.AssignmentPostActionController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssignmentPostActionControllerTest {
    final AssignmentPostActionEnqueue component=mock(AssignmentPostActionEnqueue.class);
    final AssignmentPostActionController controller=new AssignmentPostActionController(component);
    final String token=UUID.randomUUID().toString();
    final AssignmentPostAction.Enqueue request=new AssignmentPostAction.Enqueue(UUID.randomUUID(),0);
    MockMvc mvc;
    @BeforeEach void configure() {
        ReflectionTestUtils.setField(controller,"serviceToken",token);
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void ordinaryUserCannotQueueCompletionMail() throws Exception {
        mvc.perform(post("/api/v1/internal/assignment/post-actions").header("Authorization","Bearer synthetic-user-token")
                .contentType(MediaType.APPLICATION_JSON).content(JsonUtils.toJson(request))).andExpect(status().isForbidden());
        verifyNoInteractions(component);
    }
    @Test void serviceTokenAllowsOnlyAValidatedOperationRuleRequest() throws Exception {
        mvc.perform(post("/api/v1/internal/assignment/post-actions").header("X-Internal-Token",token)
                .contentType(MediaType.APPLICATION_JSON).content(JsonUtils.toJson(request))).andExpect(status().isOk());
        verify(component).enqueue(request);
    }
}
