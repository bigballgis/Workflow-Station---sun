package com.platform.security.util;

import com.platform.common.dto.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Utility class for accessing security context information.
 */
public final class SecurityContextUtils {
    
    private SecurityContextUtils() {
        // Utility class
    }
    
    /**
     * Get the current authenticated user principal.
     * 
     * @return Optional containing UserPrincipal if authenticated, empty otherwise
     */
    public static Optional<UserPrincipal> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal) {
            return Optional.of((UserPrincipal) principal);
        }
        
        return Optional.empty();
    }
    
    /**
     * Get the current user ID.
     * 
     * @return Optional containing user ID if authenticated, empty otherwise
     */
    public static Optional<String> getCurrentUserId() {
        return getCurrentUser().map(UserPrincipal::getUserId);
    }
    
    /**
     * Get the current username.
     * 
     * @return Optional containing username if authenticated, empty otherwise
     */
    public static Optional<String> getCurrentUsername() {
        return getCurrentUser().map(UserPrincipal::getUsername);
    }
    
    /**
     * Get the current user's department ID.
     * 
     * @return Optional containing department ID if authenticated, empty otherwise
     */
    public static Optional<String> getCurrentDepartmentId() {
        return getCurrentUser().map(UserPrincipal::getDepartmentId);
    }
    
    /**
     * Get the current user's preferred language.
     * 
     * @return Language code, defaults to "en" if not authenticated
     */
    public static String getCurrentLanguage() {
        return getCurrentUser()
                .map(UserPrincipal::getLanguage)
                .orElse("en");
    }
    
    /**
     * Check if the current user has a specific role.
     * 
     * @param role Role to check
     * @return true if user has the role, false otherwise
     */
    public static boolean hasRole(String role) {
        return getCurrentUser()
                .map(user -> user.hasRole(role))
                .orElse(false);
    }
    
    /**
     * Check if the current user has a specific permission.
     * 
     * @param permission Permission to check
     * @return true if user has the permission, false otherwise
     */
    public static boolean hasPermission(String permission) {
        return getCurrentUser()
                .map(user -> user.hasPermission(permission))
                .orElse(false);
    }
    
    /**
     * Check if the current user is a super admin.
     * 
     * @return true if user is super admin, false otherwise
     */
    public static boolean isSuperAdmin() {
        return getCurrentUser()
                .map(UserPrincipal::isSuperAdmin)
                .orElse(false);
    }
    
    /**
     * Check if there is an authenticated user.
     * 
     * @return true if authenticated, false otherwise
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() 
                && authentication.getPrincipal() instanceof UserPrincipal;
    }
}
