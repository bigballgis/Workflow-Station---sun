package com.platform.security.filter;

import com.platform.common.constant.PlatformConstants;
import com.platform.common.dto.UserPrincipal;
import com.platform.security.config.JwtProperties;
import com.platform.security.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT Authentication Filter for validating JWT tokens in requests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String token = extractToken(request);
        
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            if (jwtTokenService.validateToken(token)) {
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
            }
        } catch (Exception e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
            // Don't set authentication - let the security chain handle unauthorized access
        }
        
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(PlatformConstants.HEADER_AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith(PlatformConstants.HEADER_BEARER_PREFIX)) {
            return bearerToken.substring(PlatformConstants.HEADER_BEARER_PREFIX.length());
        }
        // Fallback: read cookies per platform.security.jwt.cookie-names (first entry is this service's name)
        // to avoid cross-app overwrite when all share access_token (see JwtProperties#cookieNames).
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            List<String> names = jwtProperties.getCookieNames();
            if (names == null || names.isEmpty()) {
                names = List.of("access_token");
            }
            for (String name : names) {
                for (Cookie cookie : cookies) {
                    if (name.equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }
        }
        return null;
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Skip filter only for truly public auth endpoints.
        // Do NOT skip all /auth/**, otherwise cookie-based /auth/me and /auth/workspace-contexts
        // never populate SecurityContext and will always return 401.
        boolean isPublicAuthEndpoint =
                "/auth/login".equals(path) ||
                "/auth/refresh".equals(path) ||
                "/auth/validate".equals(path) ||
                "/auth/sso/exchange".equals(path) ||
                "/auth/logout".equals(path);
        // use servletPath which excludes context-path
        return isPublicAuthEndpoint ||
               path.startsWith("/sso/") ||
               path.startsWith("/internal/sso/") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs");
    }
}
