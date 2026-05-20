package com.admin.config;

import com.admin.audit.AuditRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.UserPrincipal;
import com.platform.security.filter.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 安全配置
 */
@Slf4j
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/data-api/**").permitAll()
                // DESIGN NOTE: Authentication is handled by Kong Gateway (JWT plugin) as the first line of defense,
                // and JwtAuthenticationFilter as the second line. Spring Security's authorizeHttpRequests is intentionally
                // set to permitAll() because the authentication decision is made by the JWT filter, not by Spring Security.
                // In production, Kong rejects unauthenticated requests before they reach this service.
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(serviceCallAuthenticationFilter(), JwtAuthenticationFilter.class);

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

    /**
     * Fallback filter for service-to-service calls that carry X-Username / X-User-Id headers
     * instead of a JWT token. Executes AFTER JwtAuthenticationFilter so that JWT-authenticated
     * requests are not overridden.
     */
    @Bean
    public OncePerRequestFilter serviceCallAuthenticationFilter() {
        return new ServiceCallAuthenticationFilter();
    }

    @Bean
    public FilterRegistrationBean<AuditRequestFilter> auditRequestFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<AuditRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuditRequestFilter(objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("auditRequestFilter");
        return registration;
    }

    /**
     * Handles X-Username / X-User-Id header fallback for service-to-service calls.
     * Only activates when SecurityContext has no authentication AND request carries
     * X-Username or X-User-Id header. Preserves original behavior: excludes
     * xUserId == "system", creates UserPrincipal with empty roles/permissions.
     */
    @Slf4j
    static class ServiceCallAuthenticationFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String xUsername = request.getHeader("X-Username");
                String xUserId = request.getHeader("X-User-Id");
                String fallbackName = (xUsername != null && !xUsername.isEmpty()) ? xUsername
                        : (xUserId != null && !"system".equals(xUserId)) ? xUserId : null;
                if (fallbackName != null) {
                    UserPrincipal fallbackPrincipal = UserPrincipal.builder()
                            .userId(xUserId != null ? xUserId : fallbackName)
                            .username(fallbackName)
                            .displayName(fallbackName)
                            .roles(Collections.emptyList())
                            .permissions(Collections.emptyList())
                            .build();
                    UsernamePasswordAuthenticationToken fallbackAuth =
                            new UsernamePasswordAuthenticationToken(fallbackPrincipal, null, Collections.emptyList());
                    fallbackAuth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(fallbackAuth);
                    log.debug("Service call authentication set for user: {}", fallbackName);
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}
