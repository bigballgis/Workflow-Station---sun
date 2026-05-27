package com.workflow.config;

import com.platform.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @org.springframework.beans.factory.annotation.Value("${hsts.enabled:false}")
    private boolean hstsEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/auth/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/actuator/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/health/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/.well-known/health")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/swagger-ui/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/v3/api-docs/**")).permitAll()
                // Flowable management APIs — in production, Kong Gateway does NOT route to these paths.
                // TODO: In production, disable via flowable.rest.app.enabled=false in application-prod.yml
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
                // Process definition deploy/delete - internal service-to-service calls from admin-center
                .requestMatchers(new AntPathRequestMatcher("/api/v1/processes/definitions/deploy")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/v1/processes/definitions/deployments/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/v1/processes/definitions/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/v1/processes/instances/*/purge")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/v1/processes/instances")).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // HSTS: only enabled in production (HTTPS). Setting on HTTP dev env breaks browser access.
        if (hstsEnabled) {
            http.headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                    .preload(false)));
        }

        return http.build();
    }
}
