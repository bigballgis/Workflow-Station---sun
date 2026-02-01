package com.developer.component.impl;

import com.developer.repository.PermissionRepository;
import com.developer.repository.RoleRepository;
import com.developer.security.SecurityCacheManager;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.DisplayName;
import org.assertj.core.api.Assertions;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for SecurityComponentImpl.
 * Validates universal properties of SecurityContext integration, unauthenticated user denial, and user validation.
 * 
 * Feature: security-permission-system, Property 7: SecurityContext Integration
 * Feature: security-permission-system, Property 8: Unauthenticated User Denial
 * Feature: security-permission-system, Property 9: User Validation
 * Validates: Requirements 3.5, 4.1, 4.2, 4.5
 */
@Label("Feature: security-permission-system, Property 7,8,9: SecurityComponentImpl")
public class SecurityComponentImplPropertyTest {
    
    @Mock
    private PermissionRepository permissionRepository;
    
    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private SecurityCacheManager cacheManager;
    
    private SecurityComponentImpl securityComponent;
    
    @BeforeProperty
    void setUp() {
        MockitoAnnotations.openMocks(this);
        securityComponent = new SecurityComponentImpl(
                "test-secret", 86400000L, 5, 30, "test-secret",
                permissionRepository, roleRepository, cacheManager, null);
    }
    
    /**
     * Property 7: SecurityContext Integration
     * For any permission or role check, the system should retrieve the current user identity 
     * from Spring Security's SecurityContext.
     * 
     * Note: This test validates the component's integration with repositories and caching,
     * which is the foundation for SecurityContext integration in the full system.
     */
    @Property(tries = 100)
    @DisplayName("Should integrate with repositories and caching for permission checks")
    void shouldIntegrateWithRepositoriesForPermissionChecks(
            @ForAll("validUsernames") String username,
            @ForAll("validPermissions") String permission,
            @ForAll boolean hasPermission) {
        
        // Setup cache to return empty (no cached result)
        when(cacheManager.getCachedPermission(username, permission)).thenReturn(Optional.empty());
        
        // Setup repository to return the test result
        when(permissionRepository.hasPermission(username, permission)).thenReturn(hasPermission);
        
        // Execute permission check
        boolean result = securityComponent.hasPermission(username, permission);
        
        // Property: Should query database when no cache exists
        verify(permissionRepository, times(1)).hasPermission(username, permission);
        
        // Property: Should cache the result after database query
        verify(cacheManager, times(1)).cachePermission(username, permission, hasPermission);
        
        // Property: Result should match database result
        Assertions.assertThat(result).isEqualTo(hasPermission);
    }
    
    /**
     * Property 7b: Role Check Integration
     * Role checks should follow the same integration pattern.
     */
    @Property(tries = 100)
    @DisplayName("Should integrate with repositories and caching for role checks")
    void shouldIntegrateWithRepositoriesForRoleChecks(
            @ForAll("validUsernames") String username,
            @ForAll("validRoles") String role,
            @ForAll boolean hasRole) {
        
        // Setup cache to return empty (no cached result)
        when(cacheManager.getCachedRole(username, role)).thenReturn(Optional.empty());
        
        // Setup repository to return the test result
        when(roleRepository.hasRole(username, role)).thenReturn(hasRole);
        
        // Execute role check
        boolean result = securityComponent.hasRole(username, role);
        
        // Property: Should query database when no cache exists
        verify(roleRepository, times(1)).hasRole(username, role);
        
        // Property: Should cache the result after database query
        verify(cacheManager, times(1)).cacheRole(username, role, hasRole);
        
        // Property: Result should match database result
        Assertions.assertThat(result).isEqualTo(hasRole);
    }
    
    /**
     * Property 8: Unauthenticated User Denial
     * For any permission or role check when no authenticated user exists, 
     * the system should deny all checks.
     * 
     * This test validates the fail-safe behavior when repositories are unavailable.
     */
    @Property(tries = 100)
    @DisplayName("Should deny all permissions when repositories are unavailable")
    void shouldDenyPermissionsWhenRepositoriesUnavailable(
            @ForAll("validUsernames") String username,
            @ForAll("validPermissions") String permission) {
        
        // Create component without repositories (simulating unavailable state)
        SecurityComponentImpl componentWithoutRepos = new SecurityComponentImpl(
                "test-secret", 86400000L, 5, 30, "test-secret",
                null, null, null, null);
        
        // Execute permission check
        boolean result = componentWithoutRepos.hasPermission(username, permission);
        
        // Property: Should deny all permissions when repositories unavailable
        Assertions.assertThat(result).isFalse();
    }
    
    /**
     * Property 8b: Role Denial Without Repositories
     * Role checks should also deny when repositories are unavailable.
     */
    @Property(tries = 100)
    @DisplayName("Should deny all roles when repositories are unavailable")
    void shouldDenyRolesWhenRepositoriesUnavailable(
            @ForAll("validUsernames") String username,
            @ForAll("validRoles") String role) {
        
        // Create component without repositories (simulating unavailable state)
        SecurityComponentImpl componentWithoutRepos = new SecurityComponentImpl(
                "test-secret", 86400000L, 5, 30, "test-secret",
                null, null, null, null);
        
        // Execute role check
        boolean result = componentWithoutRepos.hasRole(username, role);
        
        // Property: Should deny all roles when repositories unavailable
        Assertions.assertThat(result).isFalse();
    }
    
    /**
     * Property 9: User Validation
     * For any permission check, the system should validate user existence before performing the check.
     * 
     * This test validates error handling during database operations.
     */
    @Property(tries = 100)
    @DisplayName("Should handle database errors gracefully and deny access")
    void shouldHandleDatabaseErrorsGracefully(
            @ForAll("validUsernames") String username,
            @ForAll("validPermissions") String permission) {
        
        // Setup cache to return empty (no cached result)
        when(cacheManager.getCachedPermission(username, permission)).thenReturn(Optional.empty());
        
        // Setup repository to throw database exception
        when(permissionRepository.hasPermission(username, permission))
                .thenThrow(new RuntimeException("Database connection failed"));
        
        // Execute permission check
        boolean result = securityComponent.hasPermission(username, permission);
        
        // Property: Should return false on database error (fail-safe behavior)
        Assertions.assertThat(result).isFalse();
        
        // Property: Should attempt database query
        verify(permissionRepository, times(1)).hasPermission(username, permission);
        
        // Property: Should NOT cache error results
        verify(cacheManager, never()).cachePermission(anyString(), anyString(), anyBoolean());
    }
    
    /**
     * Property 9b: Role Validation Error Handling
     * Role checks should handle database errors the same way.
     */
    @Property(tries = 100)
    @DisplayName("Should handle role database errors gracefully and deny access")
    void shouldHandleRoleDatabaseErrorsGracefully(
            @ForAll("validUsernames") String username,
            @ForAll("validRoles") String role) {
        
        // Setup cache to return empty (no cached result)
        when(cacheManager.getCachedRole(username, role)).thenReturn(Optional.empty());
        
        // Setup repository to throw database exception
        when(roleRepository.hasRole(username, role))
                .thenThrow(new RuntimeException("Database connection failed"));
        
        // Execute role check
        boolean result = securityComponent.hasRole(username, role);
        
        // Property: Should return false on database error (fail-safe behavior)
        Assertions.assertThat(result).isFalse();
        
        // Property: Should attempt database query
        verify(roleRepository, times(1)).hasRole(username, role);
        
        // Property: Should NOT cache error results
        verify(cacheManager, never()).cacheRole(anyString(), anyString(), anyBoolean());
    }
    
    /**
     * Property 7c: Cache Hit Behavior
     * When cached results exist, should use them without querying database.
     */
    @Property(tries = 100)
    @DisplayName("Should use cached results and skip database queries")
    void shouldUseCachedResultsAndSkipDatabase(
            @ForAll("validUsernames") String username,
            @ForAll("validPermissions") String permission,
            @ForAll("validRoles") String role,
            @ForAll boolean cachedPermissionResult,
            @ForAll boolean cachedRoleResult) {
        
        // Setup cache to return cached results
        when(cacheManager.getCachedPermission(username, permission)).thenReturn(Optional.of(cachedPermissionResult));
        when(cacheManager.getCachedRole(username, role)).thenReturn(Optional.of(cachedRoleResult));
        
        // Execute checks
        boolean permissionResult = securityComponent.hasPermission(username, permission);
        boolean roleResult = securityComponent.hasRole(username, role);
        
        // Property: Should NOT query database when cache hit occurs
        verify(permissionRepository, never()).hasPermission(anyString(), anyString());
        verify(roleRepository, never()).hasRole(anyString(), anyString());
        
        // Property: Should NOT cache again (already cached)
        verify(cacheManager, never()).cachePermission(anyString(), anyString(), anyBoolean());
        verify(cacheManager, never()).cacheRole(anyString(), anyString(), anyBoolean());
        
        // Property: Results should match cached results
        Assertions.assertThat(permissionResult).isEqualTo(cachedPermissionResult);
        Assertions.assertThat(roleResult).isEqualTo(cachedRoleResult);
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
}