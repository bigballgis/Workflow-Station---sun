package com.workflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security configuration for workflow engine.
 * Allows anonymous access to actuator endpoints for health checks.
 *
 * <p>Currently all endpoints use permitAll mode. In production, the following
 * N8N-related endpoints require specific security considerations:
 * <ul>
 *   <li>{@code POST /api/workflow/n8n/callback} - N8N callback endpoint, MUST remain
 *       permitAll since N8N calls it directly without authentication. Request
 *       authenticity is verified via callbackToken.</li>
 *   <li>{@code POST /api/v1/n8n/execute} - Internal API for N8N Action execution,
 *       called by user-portal via RestTemplate (inter-service communication).</li>
 *   <li>{@code GET /api/workflow/n8n/executions/**} - Execution record queries,
 *       should require authentication in production.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/actuator/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/swagger-ui/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/v3/api-docs/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/process-api/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/cmmn-api/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/dmn-api/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/idm-api/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/form-api/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/content-api/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/app-api/**")).permitAll()
                // N8N callback endpoint - must remain permitAll (N8N calls directly, auth via callbackToken)
                .requestMatchers(new AntPathRequestMatcher("/api/workflow/n8n/callback")).permitAll()
                // N8N internal execution endpoint - inter-service communication
                .requestMatchers(new AntPathRequestMatcher("/api/v1/n8n/execute")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/**")).permitAll()
                // DESIGN NOTE: Authentication is handled by Kong Gateway (JWT plugin) as the first line of defense,
                // and JwtAuthenticationFilter as the second line. Spring Security's authorizeHttpRequests is intentionally
                // set to permitAll() because the authentication decision is made by the JWT filter, not by Spring Security.
                // In production, Kong rejects unauthenticated requests before they reach this service.
                .anyRequest().permitAll()
            );
        
        return http.build();
    }
}
