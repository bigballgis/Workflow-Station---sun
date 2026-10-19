package com.workflow.controller;

import com.platform.common.dto.UserPrincipal;
import com.platform.security.config.JwtProperties;
import com.platform.security.filter.JwtAuthenticationFilter;
import com.platform.security.service.JwtTokenService;
import com.workflow.component.InstanceBpmnReader;
import com.workflow.component.ProcessEngineComponent;
import com.workflow.config.SecurityConfig;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InstanceBpmnSecurityTest {
    @Test
    void pinnedStartsRequireAuthenticationAndANonblankPinBeforeCallingTheComponent() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext()); context.register(TestConfig.class); context.refresh();
            var processes = context.getBean(Fixtures.class).processes();
            var tokens = context.getBean(JwtTokenService.class);
            when(tokens.validateToken("synthetic-test-token")).thenReturn(true);
            when(tokens.extractUserPrincipal("synthetic-test-token"))
                    .thenReturn(UserPrincipal.builder().userId("synthetic-user").build());
            var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
            String path = "/api/v1/processes/instances/pinned";
            mvc.perform(post(path).contentType("application/json").content("{\"processDefinitionKey\":\"synthetic\"}"))
                    .andExpect(status().isForbidden());
            for (String pin : new String[] { "", " ", "null" }) {
                String value = "null".equals(pin) ? "null" : "\"" + pin + "\"";
                mvc.perform(post(path).header("Authorization", "Bearer synthetic-test-token").contentType("application/json")
                                .content("{\"processDefinitionKey\":\"synthetic\",\"expectedProcessDefinitionId\":" + value + "}"))
                        .andExpect(status().isBadRequest());
            }
            verifyNoInteractions(processes);
        }
    }
    @Test
    void onlyInstanceBoundReadRequiresAuthenticationThroughTheRealSecurityChain() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestConfig.class);
            context.refresh();
            var reader = context.getBean(Fixtures.class).reader();
            var processes = context.getBean(Fixtures.class).processes();
            var tokens = context.getBean(JwtTokenService.class);
            when(tokens.validateToken("synthetic-test-token")).thenReturn(true);
            when(tokens.extractUserPrincipal("synthetic-test-token"))
                    .thenReturn(UserPrincipal.builder().userId("synthetic-user").build());
            when(reader.read("synthetic-process", "synthetic-instance"))
                    .thenReturn(new InstanceBpmnReader.Source("synthetic-instance", "synthetic:1", "<bound/>"));
            when(processes.getBpmnXml("synthetic-process")).thenReturn("<latest/>");
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean(FilterChainProxy.class)).build();
            String path = "/api/v1/processes/definitions/synthetic-process/bpmn";
            mvc.perform(get(path).param("processInstanceId", "synthetic-instance"))
                    .andExpect(status().isForbidden());
            mvc.perform(head(path).param("processInstanceId", "synthetic-instance"))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).param("processInstanceId", ""))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(reader);
            mvc.perform(get(path).param("processInstanceId", "synthetic-instance")
                            .header("Authorization", "Bearer synthetic-test-token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.bpmnXml").value("<bound/>"));
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.bpmnXml").value("<latest/>"));
        }
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean JwtTokenService tokens() { return mock(JwtTokenService.class); }
        @Bean JwtAuthenticationFilter jwtFilter(JwtTokenService tokens) {
            return new JwtAuthenticationFilter(tokens, new JwtProperties());
        }
        @Bean Fixtures fixtures() {
            return new Fixtures(mock(ProcessEngineComponent.class), mock(InstanceBpmnReader.class));
        }
        @Bean ProcessController controller(Fixtures fixtures) {
            return new ProcessController(fixtures.processes(), mock(RuntimeService.class), fixtures.reader());
        }
    }

    record Fixtures(ProcessEngineComponent processes, InstanceBpmnReader reader) { }
}
