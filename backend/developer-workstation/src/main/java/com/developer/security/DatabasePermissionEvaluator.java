package com.developer.security;

import com.developer.repository.PermissionRepository;
import com.developer.repository.RoleRepository;
import com.platform.common.security.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Map;

/**
 * Custom Spring Security PermissionEvaluator implementation.
 * Provides database-backed permission evaluation for Spring Security annotations.
 * 
 * Requirements: 1.1, 1.2, 1.3, 1.4, 3.2, 3.4
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DatabasePermissionEvaluator implements PermissionEvaluator {
    
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final SecurityCacheManager cacheManager;
    private final SecurityAuditLogger auditLogger;
    
    /**
     * Evaluate permission for a domain object.
     * This method is called by Spring Security when using @PreAuthorize with hasPermission().
     * 
     * @param authentication the current authentication
     * @param targetDomainObject the domain object being accessed
     * @param permission the permission to check
     * @return true if permission is granted, false otherwise
     */
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            Map<String, String> metadata = Map.of("issue", "no_authenticated_user");
            auditLogger.logSecurityEvent("AUTHENTICATION_ISSUE", "Permission evaluation failed: no authenticated user", metadata);
            log.debug("Permission denied: no authenticated user");
            return false;
        }
        
        String username = authentication.getName();
        String permissionStr = permission.toString();
        
        log.debug("Evaluating permission: user={}, permission={}, target={}", 
                username, permissionStr, targetDomainObject);
        
        return checkPermission(username, permissionStr);
    }
    
    /**
     * Evaluate permission for a target ID and type.
     * This method is called by Spring Security when using @PreAuthorize with hasPermission().
     * 
     * @param authentication the current authentication
     * @param targetId the ID of the target object
     * @param targetType the type of the target object
     * @param permission the permission to check
     * @return true if permission is granted, false otherwise
     */
    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, 
                                String targetType, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            Map<String, String> metadata = Map.of("issue", "no_authenticated_user");
            auditLogger.logSecurityEvent("AUTHENTICATION_ISSUE", "Permission evaluation failed: no authenticated user", metadata);
            log.debug("Permission denied: no authenticated user");
            return false;
        }
        
        String username = authentication.getName();
        String permissionStr = permission.toString();
        
        log.debug("Evaluating permission: user={}, permission={}, targetId={}, targetType={}", 
                username, permissionStr, targetId, targetType);
        
        return checkPermission(username, permissionStr);
    }
    
    /**
     * Check if a user has a specific permission.
     * Uses caching for performance optimization.
     * 
     * @param username the username to check
     * @param permission the permission to check
     * @return true if user has the permission, false otherwise
     */
    private boolean checkPermission(String username, String permission) {
        try {
            // Try to get cached result first
            var cachedResult = cacheManager.getCachedPermission(username, permission);
            if (cachedResult.isPresent()) {
                boolean result = cachedResult.get();
                Map<String, String> metadata = Map.of("from_cache", "true");
                auditLogger.logAuthorizationEvent("PERMISSION_CHECK", "Permission evaluation successful", username, permission, "evaluate", true, metadata);
                log.debug("Permission check cache hit: user={}, permission={}, result={}", 
                        username, permission, result);
                return result;
            }
            
            // Cache miss - log it
            log.debug("Permission cache miss for user={}, permission={}", username, permission);
            
            // Query database for permission
            boolean hasPermission = permissionRepository.hasPermission(username, permission);
            
            // Cache the result
            cacheManager.cachePermission(username, permission, hasPermission);
            
            // Log the result
            if (hasPermission) {
                Map<String, String> metadata = Map.of("from_cache", "false");
                auditLogger.logAuthorizationEvent("PERMISSION_CHECK", "Permission evaluation successful", username, permission, "evaluate", true, metadata);
            } else {
                Map<String, String> metadata = Map.of("reason", "insufficient_privileges");
                auditLogger.logAuthorizationEvent("PERMISSION_DENIED", "Permission evaluation denied", username, permission, "evaluate", false, metadata);
            }
            
            log.debug("Permission check database result: user={}, permission={}, result={}", 
                    username, permission, hasPermission);
            
            return hasPermission;
            
        } catch (Exception e) {
            Map<String, String> metadata = Map.of("error_type", e.getClass().getSimpleName(), "error_message", e.getMessage());
            auditLogger.logSecurityEvent("DATABASE_ERROR", "Database error during permission evaluation", metadata);
            log.error("Error checking permission for user {} and permission {}: {}", 
                    username, permission, e.getMessage(), e);
            
            // Fail-safe: deny permission on database errors
            return false;
        }
    }
    
    /**
     * Check if a user has a specific role.
     * Uses caching for performance optimization.
     * 
     * @param username the username to check
     * @param role the role to check
     * @return true if user has the role, false otherwise
     */
    public boolean hasRole(String username, String role) {
        try {
            // Try to get cached result first
            var cachedResult = cacheManager.getCachedRole(username, role);
            if (cachedResult.isPresent()) {
                boolean result = cachedResult.get();
                Map<String, String> metadata = Map.of("from_cache", "true");
                auditLogger.logAuthorizationEvent("ROLE_CHECK", "Role evaluation successful", username, role, "evaluate", true, metadata);
                log.debug("Role check cache hit: user={}, role={}, result={}", 
                        username, role, result);
                return result;
            }
            
            // Cache miss - log it
            log.debug("Role cache miss for user={}, role={}", username, role);
            
            // Query database for role
            boolean hasRole = roleRepository.hasRole(username, role);
            
            // Cache the result
            cacheManager.cacheRole(username, role, hasRole);
            
            // Log the result
            if (hasRole) {
                Map<String, String> metadata = Map.of("from_cache", "false");
                auditLogger.logAuthorizationEvent("ROLE_CHECK", "Role evaluation successful", username, role, "evaluate", true, metadata);
            } else {
                Map<String, String> metadata = Map.of("reason", "insufficient_privileges");
                auditLogger.logAuthorizationEvent("ROLE_DENIED", "Role evaluation denied", username, role, "evaluate", false, metadata);
            }
            
            log.debug("Role check database result: user={}, role={}, result={}", 
                    username, role, hasRole);
            
            return hasRole;
            
        } catch (Exception e) {
            Map<String, String> metadata = Map.of("error_type", e.getClass().getSimpleName(), "error_message", e.getMessage());
            auditLogger.logSecurityEvent("DATABASE_ERROR", "Database error during role evaluation", metadata);
            log.error("Error checking role for user {} and role {}: {}", 
                    username, role, e.getMessage(), e);
            
            // Fail-safe: deny role on database errors
            return false;
        }
    }
}