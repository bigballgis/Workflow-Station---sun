package com.platform.security.filter;

import com.platform.common.constant.PlatformConstants;
import com.platform.common.dto.UserPrincipal;
import com.platform.security.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader(PlatformConstants.HEADER_AUTHORIZATION);
        
        if (authHeader == null || !authHeader.startsWith(PlatformConstants.HEADER_BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String token = authHeader.substring(PlatformConstants.HEADER_BEARER_PREFIX.length());
        
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
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip filter for public endpoints
        return path.startsWith("/api/auth/") || 
               path.startsWith("/actuator/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs");
    }
}
