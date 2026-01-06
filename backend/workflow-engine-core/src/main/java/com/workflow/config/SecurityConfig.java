package com.workflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Security configuration for workflow engine.
 * Allows anonymous access to actuator endpoints for health checks.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
                .requestMatchers(new AntPathRequestMatcher("/api/**")).permitAll()
                .anyRequest().permitAll()
            );
        
        return http.build();
    }
}
