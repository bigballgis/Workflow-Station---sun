package com.platform.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constant.PlatformConstants;
import com.platform.common.dto.ApiResponse;
import com.platform.common.dto.UserPrincipal;
import com.platform.security.config.JwtProperties;
import com.platform.security.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT Authentication Filter for validating JWT tokens in requests.
 *
 * <p>A request whose presented tokens all fail validation (expired, blacklisted, bad signature) is
 * answered with 401 here instead of continuing anonymously: the frontends renew the session on a 401,
 * so an expired access token must surface as one, and a revoked session must stop working. Requests
 * proving a first-party service call ({@code X-Service-Token}) are exempt, because background work
 * such as a DW deployment forwards a user token that may have expired mid-way while the service token
 * still identifies the call. Requests without any token are left to each service's own rules.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Request attribute holding the token this request was authenticated with, for services that forward
     * the caller's identity downstream (they must send the token that was accepted, not merely the first present).
     */
    public static final String AUTHENTICATED_TOKEN_ATTRIBUTE = "authenticatedAccessToken";

    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;
    private final String serviceInternalToken;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   JwtProperties jwtProperties,
                                   ObjectMapper objectMapper,
                                   @Value("${service.internal-token:}") String serviceInternalToken) {
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
        this.serviceInternalToken = serviceInternalToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        List<String> tokens = extractTokens(request);

        if (tokens.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        for (String token : tokens) {
            if (authenticate(token, request)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        if (!isTrustedServiceCall(request)) {
            rejectInvalidToken(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean authenticate(String token, HttpServletRequest request) {
        try {
            if (!jwtTokenService.validateToken(token)) {
                return false;
            }
            UserPrincipal principal = jwtTokenService.extractUserPrincipal(token);

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();

            // Add role authorities
            if (principal.getRoles() != null) {
                principal.getRoles().forEach(role ->
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }

            // Add permission authorities
            if (principal.getPermissions() != null) {
                principal.getPermissions().forEach(permission ->
                        authorities.add(new SimpleGrantedAuthority(permission)));
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Set user info in request attributes for easy access
            request.setAttribute("userPrincipal", principal);
            request.setAttribute("userId", principal.getUserId());
            request.setAttribute(AUTHENTICATED_TOKEN_ATTRIBUTE, token);
            return true;
        } catch (Exception e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isTrustedServiceCall(HttpServletRequest request) {
        if (serviceInternalToken == null || serviceInternalToken.isBlank()) {
            return false;
        }
        String provided = request.getHeader(PlatformConstants.HEADER_SERVICE_TOKEN);
        return provided != null && !provided.isEmpty()
                && MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                serviceInternalToken.getBytes(StandardCharsets.UTF_8));
    }

    private void rejectInvalidToken(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error("AUTH_TOKEN_INVALID", "Access token is invalid or expired"));
    }

    /**
     * Candidate tokens in priority order: an explicit Bearer header alone, otherwise every cookie listed in
     * platform.security.jwt.cookie-names (first entry is this service's name; see JwtProperties#cookieNames).
     * The first valid one wins. Access cookies live as long as the session, so another app's expired cookie
     * may be present on the same origin (e.g. a DW page calling Admin while an old ac_access_token lingers);
     * skipping it keeps the fallback to the still-valid cookie of the calling app.
     */
    private List<String> extractTokens(HttpServletRequest request) {
        String bearerToken = request.getHeader(PlatformConstants.HEADER_AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith(PlatformConstants.HEADER_BEARER_PREFIX)) {
            return List.of(bearerToken.substring(PlatformConstants.HEADER_BEARER_PREFIX.length()));
        }
        List<String> tokens = new ArrayList<>();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            List<String> names = jwtProperties.getCookieNames();
            if (names == null || names.isEmpty()) {
                names = List.of("access_token");
            }
            for (String name : names) {
                for (Cookie cookie : cookies) {
                    if (name.equals(cookie.getName())) {
                        tokens.add(cookie.getValue());
                    }
                }
            }
        }
        return tokens;
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Skip filter for public endpoints (use servletPath which excludes context-path)
        return path.startsWith("/auth/") ||
               path.startsWith("/sso/") ||
               path.startsWith("/internal/sso/") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs");
    }
}
