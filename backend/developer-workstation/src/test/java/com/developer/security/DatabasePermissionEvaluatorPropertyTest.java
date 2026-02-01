package com.developer.security;

import com.developer.repository.PermissionRepository;
import com.developer.repository.RoleRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.DisplayName;
import org.assertj.core.api.Assertions;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for DatabasePermissionEvaluator.
 * Validates universal properties of database query execution, error handling, and Spring Security integration.
 * 
 * Feature: security-permission-system, Property 3: Database Query Execution
 * Feature: security-permission-system, Property 4: Database Error Handling
 * Feature: security-permission-system, Property 6: Spring Security Integration
 * Validates: Requirements 1.1, 1.4, 2.1, 2.4, 3.2, 3.4, 5.4
 */
@Label("Feature: security-permission-system, Property 3,4,6: DatabasePermissionEvaluator")
public class DatabasePermissionEvaluatorPropertyTest {
    
    @Mock
    private PermissionRepository permissionRepository;
    
    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private SecurityCacheManager cacheManager;
    
    @Mock
    private Authentication authentication;
    
    private DatabasePermissionEvaluator permissionEvaluator;
    
    @BeforeProperty
    void setUp() {
        MockitoAnnotations.openMocks(this);
        permissionEvaluator = new DatabasePermissionEvaluator(permissionRepository, roleRepository, cacheManager, null);
    }
    
    /**
     * Property 3: Database Query Execution
     * For any permission or role check request, the system should query the database 
     * for the user's actual permissions/roles (unless cached).
     */
    @Property(tries = 100)
    @DisplayName("Should query database when no cached result exists")
    void shouldQueryDatabaseWhenNoCachedResult(
            @ForAll("validUsernames") String username,
            @ForAll("validPermissions") String permission,
            @ForAll boolean hasPermission) {
        
        // Setup authentication
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(username);
        
        // Setup cache to return empty (no cached result)
        when(cacheManager.getCachedPermission(username, permission)).thenReturn(java.util.Optional.empty());
        
        // Setup repository to return the test result
        when(permissionRepository.hasPermission(username, permission)).thenReturn(hasPermission);
        
        // Execute permission check
        boolean result = permissionEvaluator.hasPermission(authentication, null, permission);
        
        // Property: Should query database when no cache exists
        verify(permissionRepository, times(1)).hasPermission(username, permission);
        
        // Property: Should cache the result after database query
        verify(cacheManager, times(1)).cachePermission(username, permission, hasPermission);
        
        // Property: Result should match database result
        Assertions.assertThat(result).isEqualTo(hasPermission);
    }
    
    /**
     * Property 3b: Cache Hit Behavior
     * For any permission check, if cached result exists, should not query database.
     */
    @Property(tries = 100)
    @DisplayName("Should use cached result and skip database query")
    void shouldUseCachedResultAndSkipDatabase(
            @ForAll("validUsernames") String username,
            @ForAll("validPermissions") String permission,
            @ForAll boolean cachedResult) {
        
        // Setup authentication
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(username);
        
        // Setup cache to return cached result
        when(cacheManager.getCachedPermission(username, permission)).thenReturn(java.util.Optional.of(cachedResult));
        
        // Execute permission check
        boolean result = permissionEvaluator.hasPermission(authentication, null, permission);
        
        // Property: Should NOT query database when cache hit occurs
        verify(permissionRepository, never()).hasPermission(anyString(), anyString());
        
        // Property: Should NOT cache again (already cached)
        verify(cacheManager, never()).cachePermission(anyString(), anyString(), anyBoolean());
        
        // Property: Result should match cached result
        Assertions.assertThat(result).isEqualTo(cachedResult);
    }
    
    /**
     * Property 4: Database Error Handling
     * For any database connectivity failure during permission or role checks, 
     * the system should return false and log the error.
     */
    @Property(tries = 100)
    @DisplayName("Should return false and handle database errors gracefully")
    void shouldReturnFalseOnDatabaseError(
            @ForAll("validUsernames") String username,
            @ForAll("validPermissions") String permission) {
        
        // Setup authentication
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(username);
        
        // Setup cache to return empty (no cached result)
        when(cacheManager.getCachedPermission(username, permission)).thenReturn(java.util.Optional.empty());
        
        // Setup repository to throw database exception
        when(permissionRepository.hasPermission(username, permission))
                .thenThrow(new RuntimeException("Database connection failed"));
        
        // Execute permission check
        boolean result = permissionEvaluator.hasPermission(authentication, null, permission);
        
        // Property: Should return false on database error (fail-safe behavior)
        Assertions.assertThat(result).isFalse();
        
        // Property: Should attempt database query
        verify(permissionRepository, times(1)).hasPermission(username, permission);
        
        // Property: Should NOT cache error results
        verify(cacheManager, never()).cachePermission(anyString(), anyString(), anyBoolean());
    }
    
    /**
     * Property 6: Spring Security Integration
     * For any method annotated with Spring Security annotations (@PreAuthorize, @Secured), 
     * the permission system should be automatically invoked for validation.
     */
    @Property(tries = 100)
    @DisplayName("Should handle Spring Security PermissionEvaluator interface correctly")
    void shouldHandleSpringSecurityInterface(
            @ForAll("validUsernames") String username,
            @ForAll("validPermissions") String permission,
            @ForAll("targetObjects") Object targetObject,
            @ForAll boolean hasPermission) {
        
        // Setup authentication
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(username);
        
        // Setup cache and repository
        when(cacheManager.getCachedPermission(username, permission)).thenReturn(java.util.Optional.empty());
        when(permissionRepository.hasPermission(username, permission)).thenReturn(hasPermission);
        
        // Test both PermissionEvaluator interface methods
        boolean result1 = permissionEvaluator.hasPermission(authentication, targetObject, permission);
        boolean result2 = permissionEvaluator.hasPermission(authentication, "targetId", "targetType", permission);
        
        // Property: Both interface methods should work consistently
        Assertions.assertThat(result1).isEqualTo(hasPermission);
        Assertions.assertThat(result2).isEqualTo(hasPermission);
        
        // Property: Should query database for both calls (since no cache)
        verify(permissionRepository, times(2)).hasPermission(username, permission);
    }
    
    /**
     * Property 4b: Unauthenticated User Handling
     * For any permission check when no authenticated user exists, should deny all checks.
     */
    @Property(tries = 100)
    @DisplayName("Should deny all permissions for unauthenticated users")
    void shouldDenyPermissionsForUnauthenticatedUsers(
            @ForAll("validPermissions") String permission,
            @ForAll("targetObjects") Object targetObject) {
        
        // Test with null authentication
        boolean result1 = permissionEvaluator.hasPermission(null, targetObject, permission);
        Assertions.assertThat(result1).isFalse();
        
        // Test with unauthenticated user
        when(authentication.isAuthenticated()).thenReturn(false);
        boolean result2 = permissionEvaluator.hasPermission(authentication, targetObject, permission);
        Assertions.assertThat(result2).isFalse();
        
        // Property: Should never query database for unauthenticated users
        verify(permissionRepository, never()).hasPermission(anyString(), anyString());
        verify(cacheManager, never()).getCachedPermission(anyString(), anyString());
    }
    
    /**
     * Property 3c: Role Checking Integration
     * The hasRole method should follow the same caching and database query patterns.
     */
    @Property(tries = 100)
    @DisplayName("Role checking should follow same patterns as permission checking")
    void roleCheckingShouldFollowSamePatterns(
            @ForAll("validUsernames") String username,
            @ForAll("validRoles") String role,
            @ForAll boolean hasRole) {
        
        // Setup cache to return empty (no cached result)
        when(cacheManager.getCachedRole(username, role)).thenReturn(java.util.Optional.empty());
        
        // Setup repository to return the test result
        when(roleRepository.hasRole(username, role)).thenReturn(hasRole);
        
        // Execute role check
        boolean result = permissionEvaluator.hasRole(username, role);
        
        // Property: Should query database when no cache exists
        verify(roleRepository, times(1)).hasRole(username, role);
        
        // Property: Should cache the result after database query
        verify(cacheManager, times(1)).cacheRole(username, role, hasRole);
        
        // Property: Result should match database result
        Assertions.assertThat(result).isEqualTo(hasRole);
    }
    
    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.of("admin", "user1", "user2", "manager", "developer");
    }
    
    @Provide
    Arbitrary<String> validPermissions() {
        return Arbitraries.of(
                "ADMIN:USER:READ", "ADMIN:USER:WRITE", "ADMIN:ROLE:READ", "ADMIN:ROLE:WRITE",
                "DEVELOPER:FUNCTION:READ", "DEVELOPER:FUNCTION:WRITE", "USER:TASK:READ"
        );
    }
    
    @Provide
    Arbitrary<String> validRoles() {
        return Arbitraries.of("ADMIN", "USER", "DEVELOPER", "MANAGER", "GUEST");
    }
    
    @Provide
    Arbitrary<Object> targetObjects() {
        return Arbitraries.of("targetObject", 123, null, new Object());
    }
}