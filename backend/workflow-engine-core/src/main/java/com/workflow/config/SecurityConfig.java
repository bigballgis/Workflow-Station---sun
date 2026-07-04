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
                // Flowable management APIs — Kong does not expose these paths in deployed environments.
                .requestMatchers(new AntPathRequestMatcher("/process-api/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/cmmn-api/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/dmn-api/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/idm-api/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/form-api/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/content-api/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/app-api/**")).permitAll()
                // Process definition deploy/delete - internal service-to-service calls from admin-center
                .requestMatchers(new AntPathRequestMatcher("/api/v1/processes/definitions/deploy")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/v1/processes/definitions/deployments/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/v1/processes/definitions/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/v1/processes/instances/*/purge")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/v1/processes/instances")).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // Anti-clickjacking (Frameable Login Page): allow same-origin framing only.
        // HSTS: only enabled in production (HTTPS); setting it on HTTP dev env breaks browser access.
        http.headers(headers -> {
            headers.frameOptions(frame -> frame.sameOrigin());
            headers.contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'self'"));
            if (hstsEnabled) {
                headers.httpStrictTransportSecurity(hsts -> hsts
                        .maxAgeInSeconds(31536000)
                        .includeSubDomains(true)
                        .preload(false));
            }
        });

        return http.build();
    }
}
