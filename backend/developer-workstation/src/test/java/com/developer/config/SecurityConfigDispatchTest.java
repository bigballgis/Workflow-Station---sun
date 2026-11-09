package com.developer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.UserPrincipal;
import com.platform.security.config.JwtProperties;
import com.platform.security.filter.JwtAuthenticationFilter;
import com.platform.security.service.JwtTokenService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real DW security chain (platform access model §6.1): sign-in is required outside the public list, the file
 * store stays public, and the async re-dispatch that ends an SSE stream is let through (it continues a request
 * that was already authorized; the JWT filter does not run on it, so it carries no security context).
 */
@SpringJUnitWebConfig(SecurityConfigDispatchTest.Config.class)
@ActiveProfiles(SecurityConfigDispatchTest.PROFILE)
class SecurityConfigDispatchTest {

    /** The application's component scan reaches these nested classes; the profile keeps them out of other tests. */
    static final String PROFILE = "security-chain-test";

    private static final String TOKEN = "signed-in";

    @Configuration
    @Profile(PROFILE)
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class Config {

        @Bean
        JwtTokenService jwtTokenService() {
            JwtTokenService tokens = mock(JwtTokenService.class);
            when(tokens.validateToken(TOKEN)).thenReturn(true);
            when(tokens.extractUserPrincipal(TOKEN)).thenReturn(
                    UserPrincipal.builder().userId("u1").username("u1").roles(List.of("DEVELOPER")).build());
            return tokens;
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenService tokens) {
            return new JwtAuthenticationFilter(tokens, new JwtProperties(), new ObjectMapper(), "");
        }

        @Bean
        Endpoints endpoints() {
            return new Endpoints();
        }
    }

    @RestController
    @Profile(PROFILE)
    static class Endpoints {

        @GetMapping("/ai-generation/events/1")
        SseEmitter events() throws IOException {
            SseEmitter emitter = new SseEmitter();
            emitter.send(SseEmitter.event().name("done").data("ok"));
            emitter.complete();
            return emitter;
        }

        @GetMapping("/function-units")
        String units() {
            return "units";
        }

        @GetMapping("/upload/files/a.txt")
        String file() {
            return "file";
        }

        @GetMapping("/api-docs")
        String docs() {
            return "docs";
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @Test
    void anSseStreamEndsCleanlyAfterItsAsyncRedispatch() throws Exception {
        MvcResult started = mvc.perform(get("/ai-generation/events/1").header("Authorization", "Bearer " + TOKEN))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:done")));
    }

    @Test
    void anAnonymousCallerIsRefusedOutsideThePublicList() throws Exception {
        mvc.perform(get("/function-units")).andExpect(status().isUnauthorized());
        mvc.perform(get("/function-units").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void theFileStoreAndTheApiDocsStayPublic() throws Exception {
        mvc.perform(get("/upload/files/a.txt")).andExpect(status().isOk());
        mvc.perform(get("/api-docs")).andExpect(status().isOk());
    }
}
