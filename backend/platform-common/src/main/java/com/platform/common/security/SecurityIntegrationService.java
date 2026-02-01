package com.platform.common.security;

import com.platform.common.config.SecurityConfig;
import com.platform.common.config.security.ConfigurationAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Security Integration Service
 * 
 * Integrates authentication and authorization security managers to provide
 * a unified security interface. Handles session-based authorization,
 * security event correlation, and comprehensive security monitoring.
 * 
 * @author Platform Team
 * @version 1.0
 */
@Service
public class SecurityIntegrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityIntegrationService.class);
    
    private final AuthenticationSecurityManager authenticationManager;
    private final AuthorizationSecurityManager authorizationManager;
    private final SecurityConfig securityConfig;
    private final ConfigurationAuditLogger auditLogger;
    private final SecurityAuditLogger securityAuditLogger;
    
    @Autowired
    public SecurityIntegrationService(AuthenticationSecurityManager authenticationManager,
                                    AuthorizationSecurityManager authorizationManager,
                                    SecurityConfig securityConfig,
                                    ConfigurationAuditLogger auditLogger,
                                    SecurityAuditLogger securityAuditLogger) {
        this.authenticationManager = authenticationManager;
        this.authorizationManager = authorizationManager;
        this.securityConfig = securityConfig;
        this.auditLogger = auditLogger;
        this.securityAuditLogger = securityAuditLogger;
        
        logger.info("Security integration service initialized");
    }
    
    /**
     * Comprehensive Security Check Result
     */
    public static class SecurityCheckResult {
        private final boolean authenticated;
        private final boolean authorized;
        private final String sessionId;
        private final String username;
        private final Set<String> userRoles;
        private final String message;
        private final Map<String, Object> securityMetadata;
        
        public SecurityCheckResult(boolean authenticated, boolean authorized, String sessionId,
                                 String username, Set<String> userRoles, String message,
                                 Map<String, Object> metadata) {
            this.authenticated = authenticated;
            this.authorized = authorized;
            this.sessionId = sessionId;
            this.username = username;
            this.userRoles = userRoles;
            this.message = message;
            this.securityMetadata = metadata != null ? metadata : new HashMap<>();
        }
        
        public static SecurityCheckResult success(String sessionId, String username, Set<String> roles,
                                                Map<String, Object> metadata) {
            return new SecurityCheckResult(true, true, sessionId, username, roles,
                                         "Authentication and authorization successful", metadata);
        }
        
        public static SecurityCheckResult authenticationFailure(String message, Map<String, Object> metadata) {
            return new SecurityCheckResult(false, false, null, null, null, message, metadata);
        }
        
        public static SecurityCheckResult authorizationFailure(String sessionId, String username,
                                                             String message, Map<String, Object> metadata) {
            return new SecurityCheckResult(true, false, sessionId, username, null, message, metadata);
        }
        
        // Getters
        public boolean isAuthenticated() { return authenticated; }
        public boolean isAuthorized() { return authorized; }
        public boolean isSecurityCheckPassed() { return authenticated && authorized; }
        public String getSessionId() { return sessionId; }
        public String getUsername() { return username; }
        public Set<String> getUserRoles() { return userRoles; }
        public String getMessage() { return message; }
        public Map<String, Object> getSecurityMetadata() { return securityMetadata; }
    }
    
    /**
     * Perform comprehensive security check (authentication + authorization)
     * 
     * @param username User identifier
     * @param password User password (for authentication)
     * @param resource Resource being accessed
     * @param action Action being performed
     * @param ipAddress Client IP address
     * @param userAgent Client user agent
     * @param context Additional context
     * @return Comprehensive security check result
     */
    public SecurityCheckResult performSecurityCheck(String username, String password, String resource,
                                                  String action, String ipAddress, String userAgent,
                                                  Map<String, Object> context) {
        Map<String, Object> securityMetadata = new HashMap<>();
        securityMetadata.put("resource", resource);
        securityMetadata.put("action", action);
        securityMetadata.put("ipAddress", ipAddress);
        securityMetadata.put("timestamp", LocalDateTime.now());
        
        if (context != null) {
            securityMetadata.putAll(context);
        }
        
        try {
            // Step 1: Authenticate user
            AuthenticationSecurityManager.AuthenticationSecurityResult authResult = 
                    authenticationManager.authenticateUser(username, password, ipAddress, userAgent);
            
            if (!authResult.isSuccess()) {
                securityAuditLogger.logAuthenticationEvent("AUTHENTICATION_FAILED", 
                        authResult.getMessage(), username, ipAddress, userAgent, false, 
                        Map.of("reason", authResult.getMessage()));
                logSecurityEvent("SECURITY_CHECK_FAILED", "Authentication failed", securityMetadata);
                return SecurityCheckResult.authenticationFailure(authResult.getMessage(), securityMetadata);
            }
            
            String sessionId = authResult.getSessionId();
            securityMetadata.put("sessionId", sessionId);
            
            // Step 2: Check authorization
            AuthorizationSecurityManager.AuthorizationResult authzResult = 
                    authorizationManager.checkAuthorization(username, resource, action, context);
            
            if (!authzResult.isAuthorized()) {
                securityAuditLogger.logAuthorizationEvent("AUTHORIZATION_FAILED", 
                        authzResult.getMessage(), username, resource, action, false,
                        Map.of("reason", authzResult.getMessage(), "sessionId", sessionId));
                logSecurityEvent("SECURITY_CHECK_FAILED", "Authorization failed", securityMetadata);
                return SecurityCheckResult.authorizationFailure(sessionId, username, 
                                                              authzResult.getMessage(), securityMetadata);
            }
            
            // Step 3: Get user roles for response
            Set<String> userRoles = authorizationManager.getUserRolesWithHierarchy(username);
            securityMetadata.put("userRoles", userRoles);
            securityMetadata.put("grantedBy", authzResult.getGrantedBy());
            
            securityAuditLogger.logAuthenticationEvent("AUTHENTICATION_SUCCESS", 
                    "User successfully authenticated", username, ipAddress, userAgent, true,
                    Map.of("sessionId", sessionId));
            securityAuditLogger.logAuthorizationEvent("AUTHORIZATION_SUCCESS", 
                    "User successfully authorized", username, resource, action, true,
                    Map.of("sessionId", sessionId, "grantedBy", authzResult.getGrantedBy().toString()));
            
            logSecurityEvent("SECURITY_CHECK_SUCCESS", "Authentication and authorization successful", securityMetadata);
            
            return SecurityCheckResult.success(sessionId, username, userRoles, securityMetadata);
            
        } catch (Exception e) {
            logger.error("Security check failed for user: {} on resource: {}", username, resource, e);
            securityMetadata.put("error", e.getMessage());
            logSecurityEvent("SECURITY_CHECK_ERROR", "Security check process failed", securityMetadata);
            return SecurityCheckResult.authenticationFailure("Security check process failed", securityMetadata);
        }
    }
    
    /**
     * Validate existing session and check authorization
     * 
     * @param sessionId Session identifier
     * @param resource Resource being accessed
     * @param action Action being performed
     * @param ipAddress Client IP address
     * @param userAgent Client user agent
     * @param context Additional context
     * @return Security check result
     */
    public SecurityCheckResult validateSessionAndAuthorize(String sessionId, String resource, String action,
                                                         String ipAddress, String userAgent,
                                                         Map<String, Object> context) {
        Map<String, Object> securityMetadata = new HashMap<>();
        securityMetadata.put("sessionId", sessionId);
        securityMetadata.put("resource", resource);
        securityMetadata.put("action", action);
        securityMetadata.put("ipAddress", ipAddress);
        securityMetadata.put("timestamp", LocalDateTime.now());
        
        if (context != null) {
            securityMetadata.putAll(context);
        }
        
        try {
            // Step 1: Validate session
            boolean sessionValid = authenticationManager.validateSession(sessionId, ipAddress, userAgent);
            
            if (!sessionValid) {
                securityAuditLogger.logAuthenticationEvent("SESSION_VALIDATION_FAILED", 
                        "Session validation failed", "unknown", ipAddress, userAgent, false,
                        Map.of("sessionId", sessionId, "reason", "Invalid or expired session"));
                logSecurityEvent("SESSION_VALIDATION_FAILED", "Session validation failed", securityMetadata);
                return SecurityCheckResult.authenticationFailure("Invalid or expired session", securityMetadata);
            }
            
            // Step 2: Get session information
            AuthenticationSecurityManager.SecureSession session = authenticationManager.getSession(sessionId);
            if (session == null) {
                logSecurityEvent("SESSION_NOT_FOUND", "Session not found", securityMetadata);
                return SecurityCheckResult.authenticationFailure("Session not found", securityMetadata);
            }
            
            String username = session.getUsername();
            securityMetadata.put("username", username);
            
            // Step 3: Check authorization
            AuthorizationSecurityManager.AuthorizationResult authzResult = 
                    authorizationManager.checkAuthorization(username, resource, action, context);
            
            if (!authzResult.isAuthorized()) {
                securityAuditLogger.logAuthorizationEvent("AUTHORIZATION_FAILED", 
                        authzResult.getMessage(), username, resource, action, false,
                        Map.of("sessionId", sessionId, "reason", authzResult.getMessage()));
                logSecurityEvent("AUTHORIZATION_FAILED", "Authorization failed for valid session", securityMetadata);
                return SecurityCheckResult.authorizationFailure(sessionId, username, 
                                                              authzResult.getMessage(), securityMetadata);
            }
            
            // Step 4: Get user roles
            Set<String> userRoles = authorizationManager.getUserRolesWithHierarchy(username);
            securityMetadata.put("userRoles", userRoles);
            securityMetadata.put("grantedBy", authzResult.getGrantedBy());
            
            securityAuditLogger.logAuthorizationEvent("AUTHORIZATION_SUCCESS", 
                    "Session validation and authorization successful", username, resource, action, true,
                    Map.of("sessionId", sessionId, "grantedBy", authzResult.getGrantedBy().toString()));
            
            logSecurityEvent("SESSION_AUTHORIZATION_SUCCESS", "Session validation and authorization successful", securityMetadata);
            
            return SecurityCheckResult.success(sessionId, username, userRoles, securityMetadata);
            
        } catch (Exception e) {
            logger.error("Session validation and authorization failed for session: {}", sessionId, e);
            securityMetadata.put("error", e.getMessage());
            logSecurityEvent("SESSION_AUTHORIZATION_ERROR", "Session validation process failed", securityMetadata);
            return SecurityCheckResult.authenticationFailure("Session validation process failed", securityMetadata);
        }
    }
    
    /**
     * Invalidate user session
     * 
     * @param sessionId Session identifier
     * @param reason Reason for invalidation
     */
    public void invalidateSession(String sessionId, String reason) {
        authenticationManager.invalidateSession(sessionId, reason);
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("reason", reason);
        
        securityAuditLogger.logSecurityEvent("SESSION_INVALIDATED", "Session invalidated", 
                Map.of("sessionId", sessionId, "reason", reason));
        
        logSecurityEvent("SESSION_INVALIDATED", "Session invalidated", metadata);
    }
    
    /**
     * Assign role to user with security validation
     * 
     * @param targetUsername User to assign role to
     * @param role Role to assign
     * @param assignerSessionId Session of user performing assignment
     * @param ipAddress IP address of assigner
     * @param userAgent User agent of assigner
     * @return true if assignment successful
     */
    public boolean assignRoleSecurely(String targetUsername, String role, String assignerSessionId,
                                    String ipAddress, String userAgent) {
        try {
            // Validate assigner session
            boolean sessionValid = authenticationManager.validateSession(assignerSessionId, ipAddress, userAgent);
            if (!sessionValid) {
                return false;
            }
            
            AuthenticationSecurityManager.SecureSession session = authenticationManager.getSession(assignerSessionId);
            if (session == null) {
                return false;
            }
            
            String assignerUsername = session.getUsername();
            
            // Check if assigner has permission to assign roles
            AuthorizationSecurityManager.AuthorizationResult authzResult = 
                    authorizationManager.checkAuthorization(assignerUsername, "USER", "ASSIGN_ROLE", null);
            
            if (!authzResult.isAuthorized()) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("assignerUsername", assignerUsername);
                metadata.put("targetUsername", targetUsername);
                metadata.put("role", role);
                
                logSecurityEvent("ROLE_ASSIGNMENT_DENIED", "Insufficient permissions to assign role", metadata);
                return false;
            }
            
            // Perform role assignment
            authorizationManager.assignRole(targetUsername, role, assignerUsername);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Secure role assignment failed", e);
            return false;
        }
    }
    
    /**
     * Get comprehensive security status for a user
     * 
     * @param username User identifier
     * @return Security status information
     */
    public Map<String, Object> getUserSecurityStatus(String username) {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Get user roles
            Set<String> roles = authorizationManager.getUserRolesWithHierarchy(username);
            status.put("roles", roles);
            
            // Get active sessions
            var sessions = authenticationManager.getUserSessions(username);
            status.put("activeSessions", sessions.size());
            status.put("sessionDetails", sessions.stream()
                    .map(session -> Map.of(
                            "sessionId", session.getSessionId(),
                            "ipAddress", session.getIpAddress(),
                            "createdAt", session.getCreatedAt(),
                            "lastAccessed", session.getLastAccessedAt()
                    )).toList());
            
            // Get access statistics
            Map<String, Object> accessStats = authorizationManager.getAccessStatistics(username);
            status.put("accessStatistics", accessStats);
            
            // Check account status
            boolean accountLocked = authenticationManager.isAccountLocked(username);
            status.put("accountLocked", accountLocked);
            
            status.put("lastStatusCheck", LocalDateTime.now());
            
        } catch (Exception e) {
            logger.error("Failed to get security status for user: {}", username, e);
            status.put("error", e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Scheduled task to clean up expired sessions
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void cleanupExpiredSessions() {
        try {
            authenticationManager.cleanupExpiredSessions();
        } catch (Exception e) {
            logger.error("Failed to cleanup expired sessions", e);
        }
    }
    
    /**
     * Generate security report
     * 
     * @return Security report with statistics
     */
    public Map<String, Object> generateSecurityReport() {
        Map<String, Object> report = new HashMap<>();
        
        try {
            // Add timestamp
            report.put("reportGeneratedAt", LocalDateTime.now());
            
            // Add configuration status
            report.put("securityConfiguration", Map.of(
                    "inputValidationEnabled", securityConfig.isEnableInputValidation(),
                    "sqlInjectionDetectionEnabled", securityConfig.isEnableSqlInjectionDetection(),
                    "xssProtectionEnabled", securityConfig.isEnableXssProtection(),
                    "auditLoggingEnabled", securityConfig.isEnableSecurityAuditLogging(),
                    "sessionTimeoutMinutes", securityConfig.getSessionTimeoutMinutes(),
                    "maxConcurrentSessions", securityConfig.getMaxConcurrentSessions(),
                    "maxFailedAttempts", securityConfig.getMaxFailedAttempts(),
                    "lockoutDurationMinutes", securityConfig.getLockoutDurationMinutes()
            ));
            
            // Add security audit metrics
            Map<String, Object> auditMetrics = securityAuditLogger.getSecurityMetrics();
            report.put("securityAuditMetrics", auditMetrics);
            
            // Add system status
            report.put("systemStatus", "OPERATIONAL");
            
            securityAuditLogger.logSecurityEvent("SECURITY_REPORT_GENERATED", 
                    "Security report generated", Map.of("reportType", "comprehensive"));
            
            logSecurityEvent("SECURITY_REPORT_GENERATED", "Security report generated", report);
            
        } catch (Exception e) {
            logger.error("Failed to generate security report", e);
            report.put("error", e.getMessage());
            report.put("systemStatus", "ERROR");
        }
        
        return report;
    }
    
    /**
     * Get security audit trail
     * 
     * @param limit Maximum number of events to return
     * @return List of recent security audit events
     */
    public Map<String, Object> getSecurityAuditTrail(int limit) {
        Map<String, Object> auditData = new HashMap<>();
        
        try {
            var auditEvents = securityAuditLogger.getAuditTrail(limit);
            auditData.put("events", auditEvents);
            auditData.put("totalEvents", auditEvents.size());
            auditData.put("retrievedAt", LocalDateTime.now());
            
            securityAuditLogger.logSecurityEvent("AUDIT_TRAIL_ACCESSED", 
                    "Security audit trail accessed", Map.of("limit", String.valueOf(limit)));
            
        } catch (Exception e) {
            logger.error("Failed to retrieve security audit trail", e);
            auditData.put("error", e.getMessage());
        }
        
        return auditData;
    }
    
    /**
     * Search security audit trail
     * 
     * @param username User to search for (optional)
     * @param eventType Event type to search for (optional)
     * @param fromTime Start time for search (optional)
     * @param toTime End time for search (optional)
     * @return Search results
     */
    public Map<String, Object> searchSecurityAuditTrail(String username, String eventType,
                                                       LocalDateTime fromTime, LocalDateTime toTime) {
        Map<String, Object> searchResults = new HashMap<>();
        
        try {
            var events = securityAuditLogger.searchAuditTrail(username, eventType, fromTime, toTime);
            searchResults.put("events", events);
            searchResults.put("totalMatches", events.size());
            searchResults.put("searchCriteria", Map.of(
                    "username", username != null ? username : "any",
                    "eventType", eventType != null ? eventType : "any",
                    "fromTime", fromTime != null ? fromTime.toString() : "any",
                    "toTime", toTime != null ? toTime.toString() : "any"
            ));
            searchResults.put("searchedAt", LocalDateTime.now());
            
            securityAuditLogger.logSecurityEvent("AUDIT_TRAIL_SEARCHED", 
                    "Security audit trail searched", Map.of(
                            "username", username != null ? username : "any",
                            "eventType", eventType != null ? eventType : "any",
                            "matches", String.valueOf(events.size())
                    ));
            
        } catch (Exception e) {
            logger.error("Failed to search security audit trail", e);
            searchResults.put("error", e.getMessage());
        }
        
        return searchResults;
    }
    
    /**
     * Validate and audit input for security
     * 
     * @param fieldName Name of the input field
     * @param input Input value to validate
     * @param context Context of the validation (e.g., operation type)
     * @throws SecurityException if input validation fails
     */
    public void validateAndAuditInput(String fieldName, String input, String context) {
        if (input == null || input.trim().isEmpty()) {
            return; // Allow empty inputs - validation should be handled by business logic
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fieldName", fieldName);
        metadata.put("context", context);
        metadata.put("inputLength", input.length());
        
        try {
            // Basic security validation - check for common injection patterns
            if (containsInjectionPatterns(input)) {
                metadata.put("threatType", "INJECTION_ATTEMPT");
                Map<String, String> stringMetadata = convertToStringMap(metadata);
                securityAuditLogger.logSecurityViolation("INPUT_INJECTION_DETECTED", 
                        "Potential injection attempt detected in input", null, null, fieldName, stringMetadata);
                throw new SecurityException("Input contains potentially malicious content");
            }
            
            // Check for XSS patterns
            if (containsXSSPatterns(input)) {
                metadata.put("threatType", "XSS_ATTEMPT");
                Map<String, String> stringMetadata = convertToStringMap(metadata);
                securityAuditLogger.logSecurityViolation("INPUT_XSS_DETECTED", 
                        "Potential XSS attempt detected in input", null, null, fieldName, stringMetadata);
                throw new SecurityException("Input contains potentially malicious script content");
            }
            
            // Log successful validation
            securityAuditLogger.logSecurityEvent("INPUT_VALIDATION_SUCCESS", 
                    "Input validation passed", Map.of(
                            "fieldName", fieldName,
                            "context", context,
                            "inputLength", String.valueOf(input.length())
                    ));
            
        } catch (SecurityException e) {
            // Re-throw security exceptions
            throw e;
        } catch (Exception e) {
            // Log validation errors
            metadata.put("error", e.getMessage());
            logSecurityEvent("INPUT_VALIDATION_ERROR", "Input validation failed", metadata);
            throw new SecurityException("Input validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check for common injection patterns
     */
    private boolean containsInjectionPatterns(String input) {
        if (input == null) return false;
        
        String lowerInput = input.toLowerCase();
        
        // SQL injection patterns
        String[] sqlPatterns = {
                "select", "insert", "update", "delete", "drop", "create", "alter",
                "union", "exec", "execute", "sp_", "xp_", "--|", "/*", "*/",
                "char(", "ascii(", "substring(", "waitfor", "delay"
        };
        
        for (String pattern : sqlPatterns) {
            if (lowerInput.contains(pattern)) {
                return true;
            }
        }
        
        // Command injection patterns
        String[] cmdPatterns = {
                "cmd", "powershell", "bash", "sh", "exec", "system",
                "&&", "||", "|", ";", "`", "$("
        };
        
        for (String pattern : cmdPatterns) {
            if (lowerInput.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check for XSS patterns
     */
    private boolean containsXSSPatterns(String input) {
        if (input == null) return false;
        
        String lowerInput = input.toLowerCase();
        
        // XSS patterns
        String[] xssPatterns = {
                "<script", "</script>", "javascript:", "vbscript:", "onload=", "onerror=",
                "onclick=", "onmouseover=", "onfocus=", "onblur=", "onchange=",
                "eval(", "alert(", "confirm(", "prompt(", "document.cookie",
                "window.location", "document.write"
        };
        
        for (String pattern : xssPatterns) {
            if (lowerInput.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Log security event with comprehensive metadata
     * 
     * @param eventType Type of security event
     * @param description Event description
     * @param metadata Event metadata
     */
    private void logSecurityEvent(String eventType, String description, Map<String, Object> metadata) {
        Map<String, String> auditMetadata = convertToStringMap(metadata);
        auditLogger.logSecurityEvent(eventType, description, auditMetadata);
    }
    
    /**
     * Convert metadata map to string map for audit logging
     */
    private Map<String, String> convertToStringMap(Map<String, Object> metadata) {
        Map<String, String> stringMap = new HashMap<>();
        metadata.forEach((key, value) -> 
            stringMap.put(key, value != null ? value.toString() : "null"));
        return stringMap;
    }
}