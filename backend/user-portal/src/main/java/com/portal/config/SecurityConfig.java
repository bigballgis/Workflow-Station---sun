package com.portal.config;

import com.platform.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for user-portal.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PortalSelfServiceAccessFilter portalSelfServiceAccessFilter;

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
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // DESIGN NOTE: Authentication is handled by Kong Gateway (JWT plugin) as the first line of defense,
                // and JwtAuthenticationFilter as the second line. Spring Security's authorizeHttpRequests is intentionally
                // set to permitAll() because the authentication decision is made by the JWT filter, not by Spring Security.
                // In production, Kong rejects unauthenticated requests before they reach this service.
                .requestMatchers("/health/**", "/.well-known/health").permitAll()
                .anyRequest().permitAll())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(portalSelfServiceAccessFilter, JwtAuthenticationFilter.class);

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
