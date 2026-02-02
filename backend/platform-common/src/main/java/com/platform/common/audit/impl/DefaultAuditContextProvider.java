package com.platform.common.audit.impl;

import com.platform.common.audit.AuditContext;
import com.platform.common.audit.AuditContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Default implementation of AuditContextProvider.
 * Extracts audit context from HTTP request and security context.
 * 
 * Validates: Requirements 3.8, 4.8, 13.3
 */
@Slf4j
public class DefaultAuditContextProvider implements AuditContextProvider {
    
    @Override
    public AuditContext getContext() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return createDefaultContext();
            }
            
            HttpServletRequest request = attributes.getRequest();
            
            return AuditContext.builder()
                    .userId(extractUserId(request))
                    .username(extractUsername(request))
                    .ipAddress(extractIpAddress(request))
                    .userAgent(request.getHeader("User-Agent"))
                    .traceId(extractTraceId(request))
                    .module(extractModule(request))
                    .build();
                    
        } catch (Exception e) {
            log.warn("Failed to extract audit context: {}", e.getMessage());
            return createDefaultContext();
        }
    }
    
    private AuditContext createDefaultContext() {
        return AuditContext.builder()
                .userId("system")
                .username("system")
                .ipAddress("127.0.0.1")
                .userAgent("system")
                .traceId(UUID.randomUUID().toString())
                .module("platform")
                .build();
    }
    
    private String extractUserId(HttpServletRequest request) {
        // Try to get from security context first
        try {
            // This would typically use Spring Security's SecurityContextHolder
            // For now, use a simple header-based approach
            String userId = request.getHeader("X-User-Id");
            if (userId != null && !userId.isEmpty()) {
                return userId;
            }
        } catch (Exception e) {
            log.debug("Could not extract user ID from security context: {}", e.getMessage());
        }
        
        // Fallback to request parameter or default
        String userId = request.getParameter("userId");
        return userId != null ? userId : "anonymous";
    }
    
    private String extractUsername(HttpServletRequest request) {
        // Try to get from security context first
        try {
            String username = request.getHeader("X-Username");
            if (username != null && !username.isEmpty()) {
                return username;
            }
        } catch (Exception e) {
            log.debug("Could not extract username from security context: {}", e.getMessage());
        }
        
        // Fallback to request parameter or default
        String username = request.getParameter("username");
        return username != null ? username : "anonymous";
    }
    
    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private String extractTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId != null && !traceId.isEmpty()) {
            return traceId;
        }
        
        // Generate a new trace ID if not present
        return UUID.randomUUID().toString();
    }
    
    private String extractModule(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/admin")) return "admin-center";
        if (requestURI.startsWith("/portal")) return "user-portal";
        if (requestURI.startsWith("/developer")) return "developer-workstation";
        if (requestURI.startsWith("/workflow")) return "workflow-engine";
        if (requestURI.startsWith("/api")) return "api-gateway";
        return "platform";
    }
}