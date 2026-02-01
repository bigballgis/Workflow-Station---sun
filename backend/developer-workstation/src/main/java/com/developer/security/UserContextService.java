package com.developer.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for managing user context and Spring Security integration.
 * Provides reliable user identification for permission checks.
 * 
 * Requirements: 3.5, 4.1, 4.2, 4.3, 4.5
 */
@Service
@Slf4j
public class UserContextService {
    
    private final SecurityAuditLogger auditLogger;
    
    public UserContextService(@Autowired(required = false) SecurityAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }
    
    /**
     * Get the current authenticated user's username from Spring Security context.
     * 
     * @return Optional containing username if authenticated, empty otherwise
     */
    public Optional<String> getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null) {
                if (auditLogger != null) {
                    auditLogger.logAuthenticationIssue("get_current_username", "no_authentication_in_context");
                }
                log.debug("No authentication found in SecurityContext");
                return Optional.empty();
            }
            
            if (!authentication.isAuthenticated()) {
                if (auditLogger != null) {
                    auditLogger.logAuthenticationIssue("get_current_username", "user_not_authenticated");
                }
                log.debug("User is not authenticated");
                return Optional.empty();
            }
            
            String username = authentication.getName();
            if (username == null || username.trim().isEmpty()) {
                if (auditLogger != null) {
                    auditLogger.logAuthenticationIssue("get_current_username", "username_null_or_empty");
                }
                log.debug("Authentication exists but username is null or empty");
                return Optional.empty();
            }
            
            log.debug("Current authenticated user: {}", username);
            return Optional.of(username);
            
        } catch (Exception e) {
            if (auditLogger != null) {
                auditLogger.logSystemError(null, "get_current_username", e);
            }
            log.error("Error retrieving current user from SecurityContext: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Get the current authenticated user's username, with fallback to provided username.
     * This method is useful when a username is explicitly provided but we want to
     * validate it against the current security context.
     * 
     * @param providedUsername the username provided by the caller
     * @return the current authenticated username if available, otherwise the provided username
     */
    public String getCurrentUsernameOrFallback(String providedUsername) {
        Optional<String> currentUsername = getCurrentUsername();
        
        if (currentUsername.isPresent()) {
            String authenticated = currentUsername.get();
            
            // If provided username differs from authenticated user, log a warning
            if (providedUsername != null && !providedUsername.equals(authenticated)) {
                log.warn("Provided username '{}' differs from authenticated user '{}', using authenticated user", 
                        providedUsername, authenticated);
            }
            
            return authenticated;
        }
        
        // No authenticated user, use provided username (may be null)
        if (providedUsername == null || providedUsername.trim().isEmpty()) {
            log.debug("No authenticated user and no valid provided username");
            return null;
        }
        
        log.debug("Using provided username '{}' as no authenticated user found", providedUsername);
        return providedUsername;
    }
    
    /**
     * Check if there is currently an authenticated user.
     * 
     * @return true if user is authenticated, false otherwise
     */
    public boolean isUserAuthenticated() {
        return getCurrentUsername().isPresent();
    }
    
    /**
     * Check if the current user matches the provided username.
     * 
     * @param username the username to check against current user
     * @return true if current user matches provided username, false otherwise
     */
    public boolean isCurrentUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        
        Optional<String> currentUsername = getCurrentUsername();
        return currentUsername.isPresent() && currentUsername.get().equals(username);
    }
    
    /**
     * Validate that a user exists and is accessible for permission checks.
     * This method can be extended to check user existence in the database.
     * 
     * @param username the username to validate
     * @return true if user is valid for permission checks, false otherwise
     */
    public boolean isValidUserForPermissionCheck(String username) {
        if (username == null || username.trim().isEmpty()) {
            log.debug("Username is null or empty, invalid for permission check");
            return false;
        }
        
        // Basic validation - username should not contain suspicious characters
        if (username.contains("'") || username.contains("\"") || username.contains(";") || 
            username.contains("--") || username.contains("/*") || username.contains("*/")) {
            log.warn("Username '{}' contains suspicious characters, rejecting", username);
            return false;
        }
        
        // Additional validation can be added here, such as:
        // - Check if user exists in database
        // - Check if user account is active
        // - Check if user is not locked
        
        log.debug("Username '{}' is valid for permission check", username);
        return true;
    }
    
    /**
     * Handle session expiration gracefully.
     * This method can be called when session-related errors occur.
     * 
     * @param username the username whose session may have expired
     * @return true if session handling was successful, false otherwise
     */
    public boolean handleSessionExpiration(String username) {
        try {
            log.info("Handling potential session expiration for user: {}", username);
            
            // Check if current authentication is still valid
            Optional<String> currentUser = getCurrentUsername();
            if (currentUser.isEmpty()) {
                if (auditLogger != null) {
                    auditLogger.logAuthenticationIssue("session_expiration", "no_current_authentication");
                }
                log.info("No current authentication found, session likely expired for user: {}", username);
                return false;
            }
            
            if (!currentUser.get().equals(username)) {
                if (auditLogger != null) {
                    auditLogger.logAuthenticationIssue("session_expiration", 
                            "user_mismatch_expected_" + username + "_actual_" + currentUser.get());
                }
                log.warn("Current authenticated user '{}' differs from expected user '{}', possible session issue", 
                        currentUser.get(), username);
                return false;
            }
            
            log.debug("Session appears valid for user: {}", username);
            return true;
            
        } catch (Exception e) {
            if (auditLogger != null) {
                auditLogger.logSystemError(username, "session_expiration", e);
            }
            log.error("Error handling session expiration for user {}: {}", username, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get authentication details for debugging purposes.
     * This method provides detailed information about the current authentication state.
     * 
     * @return string containing authentication details
     */
    public String getAuthenticationDebugInfo() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null) {
                return "No authentication in SecurityContext";
            }
            
            StringBuilder info = new StringBuilder();
            info.append("Authentication: ").append(authentication.getClass().getSimpleName());
            info.append(", Principal: ").append(authentication.getPrincipal());
            info.append(", Name: ").append(authentication.getName());
            info.append(", Authenticated: ").append(authentication.isAuthenticated());
            info.append(", Authorities: ").append(authentication.getAuthorities());
            
            return info.toString();
            
        } catch (Exception e) {
            return "Error getting authentication info: " + e.getMessage();
        }
    }
}