package com.admin.config;

import com.admin.audit.AuditRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 安全配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret:my-super-secret-jwt-key-for-development-only-32chars}")
    private String jwtSecret;

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
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public OncePerRequestFilter jwtAuthenticationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    try {
                        SecretKey key = getSigningKey();
                        Claims claims = Jwts.parser()
                                .verifyWith(key)
                                .build()
                                .parseSignedClaims(token)
                                .getPayload();

                        String userId = claims.get("userId", String.class);
                        if (userId == null) userId = claims.getSubject();
                        String username = claims.get("username", String.class);
                        String displayName = claims.get("displayName", String.class);

                        @SuppressWarnings("unchecked")
                        List<String> roles = claims.get("roles", List.class);
                        @SuppressWarnings("unchecked")
                        List<String> permissions = claims.get("permissions", List.class);

                        UserPrincipal principal = UserPrincipal.builder()
                                .userId(userId)
                                .username(username != null ? username : userId)
                                .displayName(displayName)
                                .roles(roles != null ? roles : Collections.emptyList())
                                .permissions(permissions != null ? permissions : Collections.emptyList())
                                .build();

                        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                        if (roles != null) {
                            roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
                        }
                        if (permissions != null) {
                            permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
                        }

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } catch (Exception e) {
                        // Log JWT parsing failures for debugging
                        org.slf4j.LoggerFactory.getLogger("JwtFilter")
                                .warn("JWT auth failed: {}", e.getMessage());
                    }
                }
                // Fallback: if no authentication set, try X-Username / X-User-Id headers
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
                    }
                }
                filterChain.doFilter(request, response);
            }

            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                String path = request.getRequestURI();
                return path.contains("/actuator/") ||
                       path.contains("/swagger-ui/") ||
                       path.contains("/v3/api-docs");
            }
        };
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        return Keys.hmacShaKeyFor(keyBytes);
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
}
