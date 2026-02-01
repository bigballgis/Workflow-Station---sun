package com.platform.common.security;

import com.platform.common.config.SecurityConfig;
import com.platform.common.config.security.ConfigurationAuditLogger;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-Based Tests for Authorization Security
 * 
 * **Feature: technical-debt-remediation, Property 17: Authorization Enforcement**
 * 
 * **Validates: Requirements 7.4**
 * 
 * Tests universal properties of authorization enforcement including:
 * - Proper authorization checks for protected resources
 * - Role-based access control (RBAC) enforcement
 * - Permission inheritance and delegation
 * - Resource-level access control policies
 * - Authorization audit logging
 * 
 * @author Platform Team
 * @version 1.0
 */
class AuthorizationSecurityPropertyTest {
    
    private SecurityConfig securityConfig;
    private AuthorizationSecurityManager authorizationManager;
    
    @BeforeProperty
    void setUp() {
        // Configure security settings
        securityConfig = new SecurityConfig();
        
        // Create a simple audit logger implementation for testing
        ConfigurationAuditLogger auditLogger = new TestAuditLogger();
        
        authorizationManager = new AuthorizationSecurityManager(securityConfig, auditLogger);
    }
    
    /**
     * Simple test implementation of ConfigurationAuditLogger
     */
    private static class TestAuditLogger extends ConfigurationAuditLogger {
        public TestAuditLogger() {
            super(null); // Pass null for encryption service in test
        }
        
        @Override
        public void logSecurityEvent(String eventType, String description, Map<String, String> securityContext) {
            // Simple test implementation - just log to console or do nothing
            System.out.println("Authorization Event: " + eventType + " - " + description);
        }
    }
    
    /**
     * Property: Users with proper permissions should always be authorized
     * **Validates: Requirements 7.4**
     */
    @Property(tries = 100)
    @Label("Users with proper permissions are always authorized")
    void usersWithProperPermissionsAreAlwaysAuthorized(@ForAll("validUsernames") String username,
                                                       @ForAll("validResources") String resource,
                                                       @ForAll("validActions") String action) {
        // Assign a role with the required permission
        String role = "TEST_ROLE";
        String permission = resource + ":" + action;
        
        authorizationManager.defineRolePermissions(role, Set.of(permission));
        authorizationManager.assignRole(username, role, "system");
        
        // Check authorization
        AuthorizationSecurityManager.AuthorizationResult result = 
                authorizationManager.checkAuthorization(username, resource, action, null);
        
        assertThat(result.isAuthorized())
                .as("User with proper permission should be authorized")
                .isTrue();
        
        assertThat(result.getGrantedBy())
                .as("Authorization should indicate granting role")
                .contains(role);
        
        assertThat(result.getMatchedPermissions())
                .as("Authorization should indicate matched permission")
                .contains(permission);
    }
    
    /**
     * Property: Users without proper permissions should always be denied
     * **Validates: Requirements 7.4**
     */
    @Property(tries = 100)
    @Label("Users without proper permissions are always denied")
    void usersWithoutProperPermissionsAreAlwaysDenied(@ForAll("validUsernames") String username,
                                                      @ForAll("validResources") String resource,
                                                      @ForAll("validActions") String action,
                                                      @ForAll("validResources") String differentResource) {
        Assume.that(!resource.equals(differentResource));
        
        // Assign a role with permission for a different resource
        String role = "LIMITED_ROLE";
        String wrongPermission = differentResource + ":" + action;
        
        authorizationManager.defineRolePermissions(role, Set.of(wrongPermission));
        authorizationManager.assignRole(username, role, "system");
        
        // Check authorization for the original resource
        AuthorizationSecurityManager.AuthorizationResult result = 
                authorizationManager.checkAuthorization(username, resource, action, null);
        
        assertThat(result.isAuthorized())
                .as("User without proper permission should be denied")
                .isFalse();
        
        assertThat(result.getMessage())
                .as("Denial should provide appropriate message")
                .contains("permissions");
    }
    
    /**
     * Property: Wildcard permissions should grant access to all matching resources
     * **Validates: Requirements 7.4**
     */
    @Property(tries = 100)
    @Label("Wildcard permissions grant access to all matching resources")
    void wildcardPermissionsGrantAccessToAllMatchingResources(@ForAll("validUsernames") String username,
                                                             @ForAll("validResources") String resource,
                                                             @ForAll("validActions") String action) {
        // Assign a role with wildcard permission for the resource
        String role = "WILDCARD_ROLE";
        String wildcardPermission = resource + ":*";
        
        authorizationManager.defineRolePermissions(role, Set.of(wildcardPermission));
        authorizationManager.assignRole(username, role, "system");
        
        // Check authorization for any action on the resource
        AuthorizationSecurityManager.AuthorizationResult result = 
                authorizationManager.checkAuthorization(username, resource, action, null);
        
        assertThat(result.isAuthorized())
                .as("User with wildcard permission should be authorized for any action")
                .isTrue();
        
        assertThat(result.getMatchedPermissions())
                .as("Authorization should match wildcard permission")
                .contains(wildcardPermission);
    }
    
    /**
     * Property: Global admin permissions should grant access to all resources
     * **Validates: Requirements 7.4**
     */
    @Property(tries = 100)
    @Label("Global admin permissions grant access to all resources")
    void globalAdminPermissionsGrantAccessToAllResources(@ForAll("validUsernames") String username,
                                                        @ForAll("validResources") String resource,
                                                        @ForAll("validActions") String action) {
        // Assign admin role with global permissions
        String adminRole = "GLOBAL_ADMIN";
        authorizationManager.defineRolePermissions(adminRole, Set.of("*:*"));
        authorizationManager.assignRole(username, adminRole, "system");
        
        // Check authorization for any resource and action
        AuthorizationSecurityManager.AuthorizationResult result = 
                authorizationManager.checkAuthorization(username, resource, action, null);
        
        assertThat(result.isAuthorized())
                .as("User with global admin permission should be authorized for any resource")
                .isTrue();
        
        assertThat(result.getMatchedPermissions())
                .as("Authorization should match global admin permission")
                .contains("*:*");
    }
    
    /**
     * Property: Role hierarchy should grant inherited permissions
     * **Validates: Requirements 7.4**
     */
    @Property(tries = 100)
    @Label("Role hierarchy grants inherited permissions")
    void roleHierarchyGrantsInheritedPermissions(@ForAll("validUsernames") String username,
                                               @ForAll("validResources") String resource,
                                               @ForAll("validActions") String action) {
        // Define parent and child roles
        String parentRole = "PARENT_ROLE";
        String childRole = "CHILD_ROLE";
        String permission = resource + ":" + action;
        
        // Parent role has the permission
        authorizationManager.defineRolePermissions(parentRole, Set.of(permission));
        
        // Child role inherits from parent
        authorizationManager.defineRoleHierarchy(childRole, Set.of(parentRole));
        
        // Assign only child role to user
        authorizationManager.assignRole(username, childRole, "system");
        
        // Check authorization - should succeed due to inheritance
        AuthorizationSecurityManager.AuthorizationResult result = 
                authorizationManager.checkAuthorization(username, resource, action, null);
        
        assertThat(result.isAuthorized())
                .as("User with child role should inherit parent permissions")
                .isTrue();
        
        // Verify user has both roles (direct and inherited)
        Set<String> userRoles = authorizationManager.getUserRolesWithHierarchy(username);
        assertThat(userRoles)
                .as("User should have both child and inherited parent roles")
                .contains(childRole, parentRole);
    }
    
    /**
     * Property: Users with no roles should always be denied access
     * **Validates: Requirements 7.4**
     */
    @Property(tries = 100)
    @Label("Users with no roles are always denied access")
    void usersWithNoRolesAreAlwaysDeniedAccess(@ForAll("validUsernames") String username,
                                             @ForAll("validResources") String resource,
                                             @ForAll("validActions") String action) {
        // Don't assign any roles to the user
        
        // Check authorization
        AuthorizationSecurityManager.AuthorizationResult result = 
                authorizationManager.checkAuthorization(username, resource, action, null);
        
        assertThat(result.isAuthorized())
                .as("User with no roles should be denied access")
                .isFalse();
        
        assertThat(result.getMessage())
                .as("Denial should indicate no roles assigned")
                .contains("roles");
    }
    
    /**
     * Property: Resource access policies should be enforced
     * **Validates: Requirements 7.4**
     */
    @Property(tries = 50)
    @Label("Resource access policies are enforced")
    void resourceAccessPoliciesAreEnforced(@ForAll("validUsernames") String username,
                                         @ForAll("validResources") String resource,
                                         @ForAll("validActions") String action) {
        // Define a resource policy that requires a specific role
        String requiredRole = "POLICY_REQUIRED_ROLE";
        String otherRole = "OTHER_ROLE";
        
        // First define the role with appropriate permissions to avoid fallback to permission checking
        authorizationManager.defineRolePermissions(requiredRole, Set.of(resource + ":" + action));
        authorizationManager.defineRolePermissions(otherRole, Set.of("OTHER_RESOURCE:OTHER_ACTION"));
        
        AuthorizationSecurityManager.ResourceAccessPolicy policy = 
                new AuthorizationSecurityManager.ResourceAccessPolicy(
                        resource,
                        Set.of(), // No specific permissions required
                        Set.of(requiredRole), // Specific role required
                        Map.of(), // No conditions
                        true // Inheritance allowed
                );
        
        authorizationManager.defineResourcePolicy(resource, policy);
        
        // Test 1: User with required role should be granted access
        String allowedUser = username + "_allowed";
        authorizationManager.assignRole(allowedUser, requiredRole, "system");
        
        AuthorizationSecurityManager.AuthorizationResult allowedResult = 
                authorizationManager.checkAuthorization(allowedUser, resource, action, null);
        
        assertThat(allowedResult.isAuthorized())
                .as("User with required role should pass resource policy")
                .isTrue();
        
        // Test 2: User with different role should be denied access
        String deniedUser = username + "_denied";
        authorizationManager.assignRole(deniedUser, otherRole, "system");
        
        AuthorizationSecurityManager.AuthorizationResult deniedResult = 
                authorizationManager.checkAuthorization(deniedUser, resource, action, null);
        
        assertThat(deniedResult.isAuthorized())
                .as("User without required role should fail resource policy")
                .isFalse();
        
        assertThat(deniedResult.getMessage())
                .as("Policy denial should indicate missing required roles")
                .contains("required roles");
    }
    
    /**
     * Property: Access conditions should be evaluated correctly
     * **Validates: Requirements 7.4**
     */
    @Property(tries = 50)
    @Label("Access conditions are evaluated correctly")
    void accessConditionsAreEvaluatedCorrectly(@ForAll("validUsernames") String username,
                                              @ForAll("validResources") String resource,
                                              @ForAll("validActions") String action) {
        // Define a resource policy with access conditions
        String role = "CONDITIONAL_ROLE";
        String conditionKey = "department";
        String requiredValue = "SECURITY";
        
        // First define the role with appropriate permissions
        authorizationManager.defineRolePermissions(role, Set.of(resource + ":" + action));
        
        AuthorizationSecurityManager.ResourceAccessPolicy policy = 
                new AuthorizationSecurityManager.ResourceAccessPolicy(
                        resource,
                        Set.of(), // No specific permissions required
                        Set.of(role), // Role required
                        Map.of(conditionKey, requiredValue), // Access condition
                        true // Inheritance allowed
                );
        
        authorizationManager.defineResourcePolicy(resource, policy);
        
        // Create unique usernames for this test to avoid conflicts
        String testUser = username + "_conditional_" + System.nanoTime();
        authorizationManager.assignRole(testUser, role, "system");
        
        // Test 1: Context with correct condition should be granted
        Map<String, Object> correctContext = Map.of(conditionKey, requiredValue);
        
        AuthorizationSecurityManager.AuthorizationResult allowedResult = 
                authorizationManager.checkAuthorization(testUser, resource, action, correctContext);
        
        assertThat(allowedResult.isAuthorized())
                .as("User with correct access condition should be authorized")
                .isTrue();
        
        // Test 2: Context with incorrect condition should be denied
        Map<String, Object> incorrectContext = Map.of(conditionKey, "WRONG_VALUE");
        
        AuthorizationSecurityManager.AuthorizationResult deniedResult = 
                authorizationManager.checkAuthorization(testUser, resource, action, incorrectContext);
        
        assertThat(deniedResult.isAuthorized())
                .as("User with incorrect access condition should be denied")
                .isFalse();
        
        assertThat(deniedResult.getMessage())
                .as("Condition denial should indicate access condition not met")
                .contains("condition not met");
    }
    
    /**
     * Property: Role assignment and revocation should work correctly
     * **Validates: Requirements 7.4**
     */
    @Property(tries = 100)
    @Label("Role assignment and revocation work correctly")
    void roleAssignmentAndRevocationWorkCorrectly(@ForAll("validUsernames") String username,
                                                @ForAll("validRoles") String role,
                                                @ForAll("validResources") String resource,
                                                @ForAll("validActions") String action) {
        String permission = resource + ":" + action;
        
        // Define role with permission
        authorizationManager.defineRolePermissions(role, Set.of(permission));
        
        // Initially user should not have access
        AuthorizationSecurityManager.AuthorizationResult initialResult = 
                authorizationManager.checkAuthorization(username, resource, action, null);
        
        assertThat(initialResult.isAuthorized())
                .as("User should not have access before role assignment")
                .isFalse();
        
        // Assign role
        authorizationManager.assignRole(username, role, "system");
        
        // Now user should have access
        AuthorizationSecurityManager.AuthorizationResult assignedResult = 
                authorizationManager.checkAuthorization(username, resource, action, null);
        
        assertThat(assignedResult.isAuthorized())
                .as("User should have access after role assignment")
                .isTrue();
        
        // Revoke role
        authorizationManager.revokeRole(username, role, "system");
        
        // User should lose access
        AuthorizationSecurityManager.AuthorizationResult revokedResult = 
                authorizationManager.checkAuthorization(username, resource, action, null);
        
        assertThat(revokedResult.isAuthorized())
                .as("User should lose access after role revocation")
                .isFalse();
    }
    
    // ==================== Arbitraries for Test Data Generation ====================
    
    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> s + Arbitraries.integers().between(1, 999).sample());
    }
    
    @Provide
    Arbitrary<String> validResources() {
        return Arbitraries.oneOf(
                Arbitraries.just("USER"),
                Arbitraries.just("TASK"),
                Arbitraries.just("PROCESS"),
                Arbitraries.just("REPORT"),
                Arbitraries.just("ADMIN"),
                Arbitraries.just("DATA"),
                Arbitraries.just("CONFIG"),
                Arbitraries.just("AUDIT")
        );
    }
    
    @Provide
    Arbitrary<String> validActions() {
        return Arbitraries.oneOf(
                Arbitraries.just("VIEW"),
                Arbitraries.just("CREATE"),
                Arbitraries.just("UPDATE"),
                Arbitraries.just("DELETE"),
                Arbitraries.just("EXECUTE"),
                Arbitraries.just("APPROVE"),
                Arbitraries.just("ASSIGN"),
                Arbitraries.just("REVOKE")
        );
    }
    
    @Provide
    Arbitrary<String> validRoles() {
        return Arbitraries.oneOf(
                Arbitraries.just("ADMIN"),
                Arbitraries.just("MANAGER"),
                Arbitraries.just("USER"),
                Arbitraries.just("GUEST"),
                Arbitraries.just("AUDITOR"),
                Arbitraries.just("OPERATOR"),
                Arbitraries.just("VIEWER")
        );
    }
}