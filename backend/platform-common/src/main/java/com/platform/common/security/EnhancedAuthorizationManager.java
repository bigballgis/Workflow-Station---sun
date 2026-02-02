package com.platform.common.security;

import com.platform.common.config.SecurityConfig;
import com.platform.common.config.security.ConfigurationAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Authorization Security Manager
 * 
 * Provides enhanced authorization security features including:
 * - Fine-grained permission checking with resource-level access control
 * - Role-based access control (RBAC) with hierarchical roles
 * - Attribute-based access control (ABAC) support
 * - Dynamic permission evaluation with context awareness
 * - Permission inheritance and delegation
 * - Comprehensive authorization audit logging
 * - Resource access pattern monitoring
 * 
 * @author Platform Team
 * @version 1.0
 */
public class EnhancedAuthorizationManager {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedAuthorizationManager.class);
    
    private final SecurityConfig securityConfig;
    private final ConfigurationAuditLogger auditLogger;
    
    // Permission and role management
    private final Map<String, Set<String>> rolePermissions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userRoles = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> roleHierarchy = new ConcurrentHashMap<>();
    private final Map<String, ResourceAccessPolicy> resourcePolicies = new ConcurrentHashMap<>();
    
    // Access monitoring
    private final Map<String, List<AccessAttempt>> accessHistory = new ConcurrentHashMap<>();
    
    @Autowired
    public EnhancedAuthorizationManager(SecurityConfig securityConfig, 
                                      ConfigurationAuditLogger auditLogger) {
        this.securityConfig = securityConfig;
        this.auditLogger = auditLogger;
        
        initializeDefaultRoles();
        logger.info("Authorization security manager initialized with enhanced RBAC and ABAC support");
    }
    
    /**
     * Resource Access Policy for fine-grained access control
     */
    public static class ResourceAccessPolicy {
        private final String resourceType;
        private final Set<String> requiredPermissions;
        private final Set<String> requiredRoles;
        private final Map<String, Object> accessConditions;
        private final boolean inheritanceAllowed;
        
        public ResourceAccessPolicy(String resourceType, Set<String> requiredPermissions, 
                                  Set<String> requiredRoles, Map<String, Object> accessConditions,
                                  boolean inheritanceAllowed) {
            this.resourceType = resourceType;
            this.requiredPermissions = requiredPermissions != null ? requiredPermissions : new HashSet<>();
            this.requiredRoles = requiredRoles != null ? requiredRoles : new HashSet<>();
            this.accessConditions = accessConditions != null ? accessConditions : new HashMap<>();
            this.inheritanceAllowed = inheritanceAllowed;
        }
        
        // Getters
        public String getResourceType() { return resourceType; }
        public Set<String> getRequiredPermissions() { return requiredPermissions; }
        public Set<String> getRequiredRoles() { return requiredRoles; }
        public Map<String, Object> getAccessConditions() { return accessConditions; }
        public boolean isInheritanceAllowed() { return inheritanceAllowed; }
    }
    
    /**
     * Access Attempt record for monitoring and auditing
     */
    public static class AccessAttempt {
        private final String username;
        private final String resource;
        private final String action;
        private final boolean granted;
        private final String reason;
        private final LocalDateTime timestamp;
        private final Map<String, Object> context;
        
        public AccessAttempt(String username, String resource, String action, 
                           boolean granted, String reason, Map<String, Object> context) {
            this.username = username;
            this.resource = resource;
            this.action = action;
            this.granted = granted;
            this.reason = reason;
            this.timestamp = LocalDateTime.now();
            this.context = context != null ? context : new HashMap<>();
        }
        
        // Getters
        public String getUsername() { return username; }
        public String getResource() { return resource; }
        public String getAction() { return action; }
        public boolean isGranted() { return granted; }
        public String getReason() { return reason; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public Map<String, Object> getContext() { return context; }
    }
    
    /**
     * Authorization Result with detailed information
     */
    public static class AuthorizationResult {
        private final boolean authorized;
        private final String message;
        private final Set<String> grantedBy;
        private final Set<String> matchedPermissions;
        private final Map<String, Object> authorizationMetadata;
        
        public AuthorizationResult(boolean authorized, String message, Set<String> grantedBy,
                                 Set<String> matchedPermissions, Map<String, Object> metadata) {
            this.authorized = authorized;
            this.message = message;
            this.grantedBy = grantedBy != null ? grantedBy : new HashSet<>();
            this.matchedPermissions = matchedPermissions != null ? matchedPermissions : new HashSet<>();
            this.authorizationMetadata = metadata != null ? metadata : new HashMap<>();
        }
        
        public static AuthorizationResult granted(Set<String> grantedBy, Set<String> permissions, 
                                                Map<String, Object> metadata) {
            return new AuthorizationResult(true, "Access granted", grantedBy, permissions, metadata);
        }
        
        public static AuthorizationResult denied(String reason, Map<String, Object> metadata) {
            return new AuthorizationResult(false, reason, null, null, metadata);
        }
        
        // Getters
        public boolean isAuthorized() { return authorized; }
        public String getMessage() { return message; }
        public Set<String> getGrantedBy() { return grantedBy; }
        public Set<String> getMatchedPermissions() { return matchedPermissions; }
        public Map<String, Object> getAuthorizationMetadata() { return authorizationMetadata; }
    }
    
    /**
     * Check authorization with enhanced security features
     * 
     * @param username User identifier
     * @param resource Resource being accessed
     * @param action Action being performed
     * @param context Additional context for authorization decision
     * @return Authorization result with detailed information
     */
    public AuthorizationResult checkAuthorization(String username, String resource, String action, 
                                                 Map<String, Object> context) {
        Map<String, Object> authMetadata = new HashMap<>();
        authMetadata.put("username", username);
        authMetadata.put("resource", resource);
        authMetadata.put("action", action);
        authMetadata.put("timestamp", LocalDateTime.now());
        
        if (context != null) {
            authMetadata.putAll(context);
        }
        
        try {
            // Get user roles with hierarchy resolution
            Set<String> userRolesWithHierarchy = getUserRolesWithHierarchy(username);
            authMetadata.put("userRoles", userRolesWithHierarchy);
            
            if (userRolesWithHierarchy.isEmpty()) {
                recordAccessAttempt(username, resource, action, false, "No roles assigned", context);
                logAuthorizationEvent("AUTHORIZATION_DENIED", "User has no assigned roles", authMetadata);
                return AuthorizationResult.denied("User has no assigned roles", authMetadata);
            }
            
            // Check resource-specific policies first
            ResourceAccessPolicy policy = resourcePolicies.get(resource);
            if (policy != null) {
                AuthorizationResult policyResult = evaluateResourcePolicy(username, resource, action, 
                                                                        policy, userRolesWithHierarchy, context);
                if (!policyResult.isAuthorized()) {
                    recordAccessAttempt(username, resource, action, false, policyResult.getMessage(), context);
                    logAuthorizationEvent("AUTHORIZATION_DENIED", "Resource policy denied access", authMetadata);
                    return policyResult;
                } else {
                    // Policy passed - grant access
                    recordAccessAttempt(username, resource, action, true, "Resource policy granted access", context);
                    logAuthorizationEvent("AUTHORIZATION_GRANTED", "Resource policy granted access", authMetadata);
                    return policyResult;
                }
            }
            
            // No resource policy - check standard permissions
            Set<String> userPermissions = getUserPermissions(userRolesWithHierarchy);
            authMetadata.put("userPermissions", userPermissions);
            
            // Build required permission
            String requiredPermission = resource + ":" + action;
            
            // Check for exact permission match
            if (userPermissions.contains(requiredPermission)) {
                Set<String> grantedBy = findGrantingRoles(userRolesWithHierarchy, requiredPermission);
                recordAccessAttempt(username, resource, action, true, "Exact permission match", context);
                logAuthorizationEvent("AUTHORIZATION_GRANTED", "Exact permission match", authMetadata);
                return AuthorizationResult.granted(grantedBy, Set.of(requiredPermission), authMetadata);
            }
            
            // Check for wildcard permissions
            String resourceWildcard = resource + ":*";
            if (userPermissions.contains(resourceWildcard)) {
                Set<String> grantedBy = findGrantingRoles(userRolesWithHierarchy, resourceWildcard);
                recordAccessAttempt(username, resource, action, true, "Resource wildcard permission", context);
                logAuthorizationEvent("AUTHORIZATION_GRANTED", "Resource wildcard permission match", authMetadata);
                return AuthorizationResult.granted(grantedBy, Set.of(resourceWildcard), authMetadata);
            }
            
            // Check for global admin permissions
            if (userPermissions.contains("*:*")) {
                Set<String> grantedBy = findGrantingRoles(userRolesWithHierarchy, "*:*");
                recordAccessAttempt(username, resource, action, true, "Global admin permission", context);
                logAuthorizationEvent("AUTHORIZATION_GRANTED", "Global admin permission", authMetadata);
                return AuthorizationResult.granted(grantedBy, Set.of("*:*"), authMetadata);
            }
            
            // Check for action-specific wildcards
            String actionWildcard = "*:" + action;
            if (userPermissions.contains(actionWildcard)) {
                Set<String> grantedBy = findGrantingRoles(userRolesWithHierarchy, actionWildcard);
                recordAccessAttempt(username, resource, action, true, "Action wildcard permission", context);
                logAuthorizationEvent("AUTHORIZATION_GRANTED", "Action wildcard permission match", authMetadata);
                return AuthorizationResult.granted(grantedBy, Set.of(actionWildcard), authMetadata);
            }
            
            // Access denied
            recordAccessAttempt(username, resource, action, false, "No matching permissions", context);
            logAuthorizationEvent("AUTHORIZATION_DENIED", "No matching permissions found", authMetadata);
            return AuthorizationResult.denied("Insufficient permissions for this action", authMetadata);
            
        } catch (Exception e) {
            logger.error("Authorization check failed for user: {} on resource: {}", username, resource, e);
            authMetadata.put("error", e.getMessage());
            recordAccessAttempt(username, resource, action, false, "Authorization check error", context);
            logAuthorizationEvent("AUTHORIZATION_ERROR", "Authorization check failed", authMetadata);
            return AuthorizationResult.denied("Authorization check failed", authMetadata);
        }
    }
    
    /**
     * Evaluate resource-specific access policy
     * 
     * @param username User identifier
     * @param resource Resource being accessed
     * @param action Action being performed
     * @param policy Resource access policy
     * @param userRoles User roles
     * @param context Access context
     * @return Authorization result
     */
    private AuthorizationResult evaluateResourcePolicy(String username, String resource, String action,
                                                      ResourceAccessPolicy policy, Set<String> userRoles,
                                                      Map<String, Object> context) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("policyResource", policy.getResourceType());
        
        // Check required roles
        if (!policy.getRequiredRoles().isEmpty()) {
            boolean hasRequiredRole = userRoles.stream()
                    .anyMatch(role -> policy.getRequiredRoles().contains(role));
            
            if (!hasRequiredRole) {
                metadata.put("missingRoles", policy.getRequiredRoles());
                return AuthorizationResult.denied("Missing required roles for resource access", metadata);
            }
        }
        
        // Check required permissions (only if specified)
        if (!policy.getRequiredPermissions().isEmpty()) {
            Set<String> userPermissions = getUserPermissions(userRoles);
            boolean hasRequiredPermission = policy.getRequiredPermissions().stream()
                    .anyMatch(permission -> userPermissions.contains(permission));
            
            if (!hasRequiredPermission) {
                metadata.put("missingPermissions", policy.getRequiredPermissions());
                return AuthorizationResult.denied("Missing required permissions for resource access", metadata);
            }
        }
        
        // Evaluate access conditions (ABAC support)
        for (Map.Entry<String, Object> condition : policy.getAccessConditions().entrySet()) {
            if (!evaluateAccessCondition(condition.getKey(), condition.getValue(), context)) {
                metadata.put("failedCondition", condition.getKey());
                return AuthorizationResult.denied("Access condition not met: " + condition.getKey(), metadata);
            }
        }
        
        // If we reach here, the policy allows access
        return AuthorizationResult.granted(userRoles, policy.getRequiredPermissions(), metadata);
    }
    
    /**
     * Evaluate access condition for ABAC support
     * 
     * @param conditionName Name of the condition
     * @param expectedValue Expected value
     * @param context Access context
     * @return true if condition is met
     */
    private boolean evaluateAccessCondition(String conditionName, Object expectedValue, 
                                          Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        
        Object actualValue = context.get(conditionName);
        
        // Simple equality check - can be extended for more complex conditions
        return Objects.equals(expectedValue, actualValue);
    }
    
    /**
     * Get user roles with hierarchy resolution
     * 
     * @param username User identifier
     * @return Set of roles including inherited roles
     */
    public Set<String> getUserRolesWithHierarchy(String username) {
        Set<String> directRoles = userRoles.getOrDefault(username, new HashSet<>());
        Set<String> allRoles = new HashSet<>(directRoles);
        
        // Add inherited roles from hierarchy
        for (String role : directRoles) {
            allRoles.addAll(getInheritedRoles(role));
        }
        
        return allRoles;
    }
    
    /**
     * Get inherited roles from role hierarchy
     * 
     * @param role Role to check for inheritance
     * @return Set of inherited roles
     */
    private Set<String> getInheritedRoles(String role) {
        Set<String> inherited = new HashSet<>();
        Set<String> parentRoles = roleHierarchy.get(role);
        
        if (parentRoles != null) {
            inherited.addAll(parentRoles);
            
            // Recursively get inherited roles
            for (String parentRole : parentRoles) {
                inherited.addAll(getInheritedRoles(parentRole));
            }
        }
        
        return inherited;
    }
    
    /**
     * Get all permissions for a set of roles
     * 
     * @param roles Set of roles
     * @return Set of permissions
     */
    private Set<String> getUserPermissions(Set<String> roles) {
        return roles.stream()
                .flatMap(role -> rolePermissions.getOrDefault(role, new HashSet<>()).stream())
                .collect(Collectors.toSet());
    }
    
    /**
     * Find which roles granted a specific permission
     * 
     * @param userRoles User's roles
     * @param permission Permission to check
     * @return Set of roles that grant the permission
     */
    private Set<String> findGrantingRoles(Set<String> userRoles, String permission) {
        return userRoles.stream()
                .filter(role -> rolePermissions.getOrDefault(role, new HashSet<>()).contains(permission))
                .collect(Collectors.toSet());
    }
    
    /**
     * Assign role to user
     * 
     * @param username User identifier
     * @param role Role to assign
     * @param assignedBy Who assigned the role
     */
    public void assignRole(String username, String role, String assignedBy) {
        userRoles.computeIfAbsent(username, k -> new HashSet<>()).add(role);
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("username", username);
        metadata.put("role", role);
        metadata.put("assignedBy", assignedBy);
        
        logAuthorizationEvent("ROLE_ASSIGNED", "Role assigned to user", metadata);
        logger.info("Role '{}' assigned to user '{}' by '{}'", role, username, assignedBy);
    }
    
    /**
     * Revoke role from user
     * 
     * @param username User identifier
     * @param role Role to revoke
     * @param revokedBy Who revoked the role
     */
    public void revokeRole(String username, String role, String revokedBy) {
        Set<String> roles = userRoles.get(username);
        if (roles != null) {
            roles.remove(role);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("username", username);
            metadata.put("role", role);
            metadata.put("revokedBy", revokedBy);
            
            logAuthorizationEvent("ROLE_REVOKED", "Role revoked from user", metadata);
            logger.info("Role '{}' revoked from user '{}' by '{}'", role, username, revokedBy);
        }
    }
    
    /**
     * Define permissions for a role
     * 
     * @param role Role identifier
     * @param permissions Set of permissions
     */
    public void defineRolePermissions(String role, Set<String> permissions) {
        rolePermissions.put(role, new HashSet<>(permissions));
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("role", role);
        metadata.put("permissionCount", permissions.size());
        
        logAuthorizationEvent("ROLE_PERMISSIONS_DEFINED", "Permissions defined for role", metadata);
        logger.info("Defined {} permissions for role '{}'", permissions.size(), role);
    }
    
    /**
     * Define role hierarchy
     * 
     * @param childRole Child role
     * @param parentRoles Parent roles that child inherits from
     */
    public void defineRoleHierarchy(String childRole, Set<String> parentRoles) {
        roleHierarchy.put(childRole, new HashSet<>(parentRoles));
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("childRole", childRole);
        metadata.put("parentRoles", parentRoles);
        
        logAuthorizationEvent("ROLE_HIERARCHY_DEFINED", "Role hierarchy defined", metadata);
        logger.info("Defined hierarchy for role '{}' with parents: {}", childRole, parentRoles);
    }
    
    /**
     * Define resource access policy
     * 
     * @param resourceType Resource type
     * @param policy Access policy
     */
    public void defineResourcePolicy(String resourceType, ResourceAccessPolicy policy) {
        resourcePolicies.put(resourceType, policy);
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("resourceType", resourceType);
        metadata.put("requiredRoles", policy.getRequiredRoles());
        metadata.put("requiredPermissions", policy.getRequiredPermissions());
        
        logAuthorizationEvent("RESOURCE_POLICY_DEFINED", "Resource access policy defined", metadata);
        logger.info("Defined access policy for resource type '{}'", resourceType);
    }
    
    /**
     * Record access attempt for monitoring
     * 
     * @param username User identifier
     * @param resource Resource accessed
     * @param action Action performed
     * @param granted Whether access was granted
     * @param reason Reason for decision
     * @param context Access context
     */
    private void recordAccessAttempt(String username, String resource, String action, 
                                   boolean granted, String reason, Map<String, Object> context) {
        AccessAttempt attempt = new AccessAttempt(username, resource, action, granted, reason, context);
        
        accessHistory.computeIfAbsent(username, k -> new ArrayList<>()).add(attempt);
        
        // Keep only recent attempts (last 100 per user)
        List<AccessAttempt> userAttempts = accessHistory.get(username);
        if (userAttempts.size() > 100) {
            userAttempts.subList(0, userAttempts.size() - 100).clear();
        }
    }
    
    /**
     * Get access history for a user
     * 
     * @param username User identifier
     * @return List of access attempts
     */
    public List<AccessAttempt> getAccessHistory(String username) {
        return new ArrayList<>(accessHistory.getOrDefault(username, new ArrayList<>()));
    }
    
    /**
     * Get access statistics for monitoring
     * 
     * @param username User identifier
     * @return Access statistics
     */
    public Map<String, Object> getAccessStatistics(String username) {
        List<AccessAttempt> attempts = accessHistory.getOrDefault(username, new ArrayList<>());
        
        long totalAttempts = attempts.size();
        long grantedAttempts = attempts.stream().mapToLong(a -> a.isGranted() ? 1 : 0).sum();
        long deniedAttempts = totalAttempts - grantedAttempts;
        
        Map<String, Long> resourceAccess = attempts.stream()
                .collect(Collectors.groupingBy(AccessAttempt::getResource, Collectors.counting()));
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalAttempts", totalAttempts);
        statistics.put("grantedAttempts", grantedAttempts);
        statistics.put("deniedAttempts", deniedAttempts);
        statistics.put("successRate", totalAttempts > 0 ? (double) grantedAttempts / totalAttempts : 0.0);
        statistics.put("resourceAccess", resourceAccess);
        
        return statistics;
    }
    
    /**
     * Initialize default roles and permissions
     */
    private void initializeDefaultRoles() {
        // Define default roles
        defineRolePermissions("ADMIN", Set.of("*:*"));
        defineRolePermissions("MANAGER", Set.of(
                "USER:VIEW", "USER:CREATE", "USER:UPDATE",
                "TASK:*", "PROCESS:*", "REPORT:*"
        ));
        defineRolePermissions("USER", Set.of(
                "TASK:VIEW", "TASK:COMPLETE", "TASK:CLAIM",
                "PROCESS:VIEW", "PROCESS:START"
        ));
        defineRolePermissions("GUEST", Set.of(
                "TASK:VIEW", "PROCESS:VIEW"
        ));
        
        // Define role hierarchy
        defineRoleHierarchy("MANAGER", Set.of("USER"));
        defineRoleHierarchy("ADMIN", Set.of("MANAGER", "USER"));
        
        // Define resource policies
        defineResourcePolicy("SENSITIVE_DATA", new ResourceAccessPolicy(
                "SENSITIVE_DATA",
                Set.of("DATA:READ_SENSITIVE"),
                Set.of("ADMIN", "MANAGER"),
                Map.of("department", "SECURITY"),
                false
        ));
        
        logger.info("Default authorization roles and policies initialized");
    }
    
    /**
     * Log authorization event
     * 
     * @param eventType Type of authorization event
     * @param description Event description
     * @param metadata Event metadata
     */
    private void logAuthorizationEvent(String eventType, String description, Map<String, Object> metadata) {
        Map<String, String> auditMetadata = new HashMap<>();
        
        // Convert metadata to string map for audit logger
        metadata.forEach((key, value) -> 
            auditMetadata.put(key, value != null ? value.toString() : "null"));
        
        auditLogger.logSecurityEvent(eventType, description, auditMetadata);
    }
}