package com.admin.config;

import com.admin.audit.AuditContextHolder;
import com.admin.audit.AuditRequestFilter;
import com.platform.security.util.SecurityContextUtils;
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
 * Security configuration
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @org.springframework.beans.factory.annotation.Value("${hsts.enabled:false}")
    private boolean hstsEnabled;

    /**
     * Shared secret proving the caller is a trusted first-party HERMES service.
     * See docs/ap-integration/DECISIONS.md#d6 (compensating control C-3). The bare
     * X-User-Id / X-Username identity fallback below is only honored when the request
     * carries this token. A user-programmable execution surface (Activepieces) is NOT
     * given this secret and therefore cannot forge an identity by reaching this service
     * over the shared network — closing threat P-1 at its root, independent of the
     * network-layer egress controls that piece code can bypass.
     * Fail-closed: when unset, the header identity fallback is disabled entirely.
     */
    @org.springframework.beans.factory.annotation.Value("${service.internal-token:}")
    private String serviceInternalToken;

    /**
     * Constant-time check that a request carries the configured service token.
     * Returns false when the token is not configured (fail-closed) or does not match.
     */
    private static boolean isTrustedServiceCall(HttpServletRequest request, String expectedToken) {
        if (expectedToken == null || expectedToken.isBlank()) {
            return false;
        }
        String provided = request.getHeader(com.platform.common.constant.PlatformConstants.HEADER_SERVICE_TOKEN);
        if (provided == null || provided.isEmpty()) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expectedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

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
                .requestMatchers("/health/**").permitAll()
                .requestMatchers("/.well-known/health").permitAll()
                .requestMatchers("/data-api/**").permitAll()
                // DESIGN NOTE: Authentication is handled by Kong Gateway (JWT plugin) as the first line of defense,
                // and JwtAuthenticationFilter as the second line. Spring Security's authorizeHttpRequests is intentionally
                // set to permitAll() because the authentication decision is made by the JWT filter, not by Spring Security.
                // In production, Kong rejects unauthenticated requests before they reach this service.
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(serviceCallAuthenticationFilter(), JwtAuthenticationFilter.class)
            .addFilterAfter(auditContextEnrichmentFilter(), ServiceCallAuthenticationFilter.class);

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

    /**
     * Fallback filter for service-to-service calls that carry X-Username / X-User-Id headers
     * instead of a JWT token. Executes AFTER JwtAuthenticationFilter so that JWT-authenticated
     * requests are not overridden.
     */
    @Bean
    public OncePerRequestFilter serviceCallAuthenticationFilter() {
        return new ServiceCallAuthenticationFilter(serviceInternalToken);
    }

    /**
     * After JWT / header auth, merge SecurityContext identity into AuditContextHolder
     * so AdminAuditAspect records the real operator (cookie JWT does not pass Authorization header).
     */
    @Bean
    public OncePerRequestFilter auditContextEnrichmentFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                AuditContextHolder.AuditContext ctx = AuditContextHolder.get();
                if (ctx == null) {
                    ctx = new AuditContextHolder.AuditContext();
                    AuditContextHolder.set(ctx);
                }
                SecurityContextUtils.getCurrentUserId()
                        .filter(id -> id != null && !id.isBlank())
                        .ifPresent(ctx::setUserId);
                SecurityContextUtils.getCurrentUsername()
                        .filter(name -> name != null && !name.isBlank())
                        .ifPresent(ctx::setUserName);
                // C-3: audit attribution from bare headers is only trusted for genuine
                // first-party service calls; otherwise a caller could forge the operator
                // recorded in the audit log (repudiation). JWT-authenticated requests are
                // unaffected — they populate ctx above and never reach this fallback.
                if (isTrustedServiceCall(request, serviceInternalToken)) {
                    if (ctx.getUserId() == null || ctx.getUserId().isBlank()) {
                        String headerUserId = request.getHeader(com.platform.common.constant.PlatformConstants.HEADER_USER_ID);
                        if (headerUserId != null && !headerUserId.isBlank()) {
                            ctx.setUserId(headerUserId);
                        }
                    }
                    if (ctx.getUserName() == null || ctx.getUserName().isBlank()) {
                        String headerUsername = request.getHeader("X-Username");
                        if (headerUsername != null && !headerUsername.isBlank()) {
                            ctx.setUserName(headerUsername);
                        }
                    }
                }
                filterChain.doFilter(request, response);
            }
        };
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
     *
     * <p>C-3 (docs/ap-integration/DECISIONS.md#d6): this header identity is only minted
     * for a genuine first-party service call, proven by a valid X-Service-Token. Without
     * it the fallback is skipped, so a user-programmable surface (Activepieces) sharing
     * the network cannot forge an identity here. JWT-authenticated requests never reach
     * this branch (getAuthentication() != null), so trusted callers that forward a JWT
     * are unaffected.
     */
    @Slf4j
    static class ServiceCallAuthenticationFilter extends OncePerRequestFilter {

        private final String serviceInternalToken;

        ServiceCallAuthenticationFilter(String serviceInternalToken) {
            this.serviceInternalToken = serviceInternalToken;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            if (SecurityContextHolder.getContext().getAuthentication() == null
                    && isTrustedServiceCall(request, serviceInternalToken)) {
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
