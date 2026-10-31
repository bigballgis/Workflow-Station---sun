package com.workflow.controller;

import com.platform.common.dto.UserPrincipal;
import com.platform.common.exception.GlobalExceptionHandler;
import com.platform.common.util.JsonUtils;
import com.platform.security.config.JwtProperties;
import com.platform.security.filter.JwtAuthenticationFilter;
import com.platform.security.service.JwtTokenService;
import com.workflow.component.TaskPresentationReader;
import com.workflow.config.SecurityConfig;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TaskPresentationControllerTest {
    private static final String PATH="/api/v1/internal/assignment/task-presentations";
    private static final String TOKEN=UUID.randomUUID().toString();
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private TaskPresentationReader reader;

    @BeforeEach void open() {
        context=new AnnotationConfigWebApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "presentation-service-test",Map.of("service.internal-token",TOKEN)));
        context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
        reader=context.getBean(TaskPresentationReader.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }
    @AfterEach void close() { context.close(); }

    @Test void serviceTokenWorksThroughTheRealSecurityFilterChainWithoutAJwt() throws Exception {
        mvc.perform(post(PATH).header("X-Internal-Token",TOKEN).contentType("application/json")
                .content(body(List.of("instance"),List.of())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(reader).read(new TaskPresentationReader.Request(List.of("instance"),List.of()));
    }
    @Test void ordinaryJwtDoesNotReplaceTheServiceSecret() throws Exception {
        var tokens=context.getBean(JwtTokenService.class);
        when(tokens.validateToken("synthetic-user-token")).thenReturn(true);
        when(tokens.extractUserPrincipal("synthetic-user-token"))
                .thenReturn(UserPrincipal.builder().userId("actor").build());
        mvc.perform(post(PATH).header("Authorization","Bearer synthetic-user-token").contentType("application/json")
                .content(body(List.of(),List.of("task")))).andExpect(status().isForbidden());
        verifyNoInteractions(reader);
    }
    @Test void absentOrWrongServiceTokenIsDenied() throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content(body(List.of("instance"),List.of())))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH).header("X-Internal-Token","wrong").contentType("application/json")
                .content(body(List.of("instance"),List.of()))).andExpect(status().isForbidden());
        verifyNoInteractions(reader);
    }
    @Test void oversizedOrBlankSelectionsNeverReachTheReader() throws Exception {
        for(String body:List.of(body(Collections.nCopies(101,"instance"),List.of()),
                body(List.of(),Collections.nCopies(1001,"task")),body(List.of(" "),List.of()),
                "{\"processIds\":null,\"taskIds\":[]}")) {
            mvc.perform(post(PATH).header("X-Internal-Token",TOKEN).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(reader);
    }
    private String body(List<String> processes,List<String> tasks) {
        return JsonUtils.toJson(Map.of("processIds",processes,"taskIds",tasks));
    }

    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class Config {
        @Bean JwtTokenService tokens() { return mock(JwtTokenService.class); }
        @Bean JwtAuthenticationFilter jwtFilter(JwtTokenService tokens) { return new JwtAuthenticationFilter(tokens,new JwtProperties()); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean TaskPresentationReader reader() { return mock(TaskPresentationReader.class); }
        @Bean com.workflow.repository.ExtendedTaskInfoRepository extended() { return mock(com.workflow.repository.ExtendedTaskInfoRepository.class); }
        @Bean com.workflow.assignment.AssignmentSourceValues sources() { return mock(com.workflow.assignment.AssignmentSourceValues.class); }
        @Bean TaskPresentationController controller(TaskPresentationReader reader) {
            return new TaskPresentationController(reader);
        }
    }
}
