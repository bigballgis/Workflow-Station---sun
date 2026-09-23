package com.portal.config;

import com.platform.security.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
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
                // Kong and JwtAuthenticationFilter establish identity. Most legacy routes retain
                // their existing controller-level authorization; the BI facade is explicitly
                // fail-closed here and also resolves its user solely from SecurityContext.
                .requestMatchers("/health/**", "/.well-known/health").permitAll()
                .requestMatchers("/bi/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required")))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(portalSelfServiceAccessFilter, JwtAuthenticationFilter.class);

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
