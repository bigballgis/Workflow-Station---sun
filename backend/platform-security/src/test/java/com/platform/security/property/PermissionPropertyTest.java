package com.platform.security.property;

import com.platform.cache.service.CacheService;
import com.platform.common.dto.DataFilter;
import com.platform.security.model.*;
import com.platform.security.repository.PermissionRepository;
import com.platform.security.service.PermissionService;
import com.platform.security.service.impl.PermissionServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.Size;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property tests for PermissionService.
 * Validates: Property 5 (Permission Execution Consistency), Property 6 (Data Permission Filtering)
 */
class PermissionPropertyTest {
    
    private PermissionRepository permissionRepository;
    private CacheService cacheService;
    private PermissionService permissionService;
    
    void setup() {
        permissionRepository = Mockito.mock(PermissionRepository.class);
        cacheService = Mockito.mock(CacheService.class);
        when(cacheService.get(anyString(), any())).thenReturn(Optional.empty());
        doNothing().when(cacheService).set(anyString(), any(), any(Duration.class));
        permissionService = new PermissionServiceImpl(permissionRepository, cacheService);
    }
    
    // Property 5: Permission Execution Consistency
    // For any API request, if the user doesn't have the required permission, access should be denied
    
    @Property(tries = 100)
    void userWithoutPermissionShouldBeDenied(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String permission) {
        setup();
        
        // User has no permissions
        when(permissionRepository.findPermissionsByUserId(userId)).thenReturn(Collections.emptySet());
        when(permissionRepository.findRolesByUserId(userId)).thenReturn(Collections.emptySet());
        
        boolean result = permissionService.hasPermission(userId, permission);
        
        assertThat(result).isFalse();
    }
    
    @Property(tries = 100)
    void userWithPermissionShouldBeAllowed(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String permissionCode) {
        setup();
        
        Permission permission = Permission.builder()
                .id(UUID.randomUUID().toString())
                .code(permissionCode)
                .name(permissionCode)
                .enabled(true)
                .build();
        
        when(permissionRepository.findPermissionsByUserId(userId)).thenReturn(Set.of(permission));
        when(permissionRepository.findRolesByUserId(userId)).thenReturn(Collections.emptySet());
        
        boolean result = permissionService.hasPermission(userId, permissionCode);
        
        assertThat(result).isTrue();
    }
    
    @Property(tries = 100)
    void disabledPermissionShouldNotGrantAccess(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String permissionCode) {
        setup();
        
        Permission permission = Permission.builder()
                .id(UUID.randomUUID().toString())
                .code(permissionCode)
                .name(permissionCode)
                .enabled(false)  // Disabled
                .build();
        
        when(permissionRepository.findPermissionsByUserId(userId)).thenReturn(Set.of(permission));
        when(permissionRepository.findRolesByUserId(userId)).thenReturn(Collections.emptySet());
        
        boolean result = permissionService.hasPermission(userId, permissionCode);
        
        assertThat(result).isFalse();
    }
    
    @Property(tries = 100)
    void rolePermissionsShouldBeInherited(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String roleCode,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String permissionCode) {
        setup();
        
        Role role = Role.builder()
                .id(UUID.randomUUID().toString())
                .code(roleCode)
                .name(roleCode)
                .permissionCodes(Set.of(permissionCode))
                .enabled(true)
                .build();
        
        when(permissionRepository.findPermissionsByUserId(userId)).thenReturn(Collections.emptySet());
        when(permissionRepository.findRolesByUserId(userId)).thenReturn(Set.of(role));
        
        boolean result = permissionService.hasPermission(userId, permissionCode);
        
        assertThat(result).isTrue();
    }

    
    @Property(tries = 100)
    void hasAnyPermissionShouldReturnTrueIfAnyMatch(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll @Size(min = 1, max = 5) Set<@AlphaChars @Size(min = 1, max = 10) String> requestedPerms) {
        setup();
        
        // User has only the first permission
        String firstPerm = requestedPerms.iterator().next();
        Permission permission = Permission.builder()
                .id(UUID.randomUUID().toString())
                .code(firstPerm)
                .name(firstPerm)
                .enabled(true)
                .build();
        
        when(permissionRepository.findPermissionsByUserId(userId)).thenReturn(Set.of(permission));
        when(permissionRepository.findRolesByUserId(userId)).thenReturn(Collections.emptySet());
        
        boolean result = permissionService.hasAnyPermission(userId, requestedPerms);
        
        assertThat(result).isTrue();
    }
    
    @Property(tries = 100)
    void hasAllPermissionsShouldRequireAllMatches(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll @Size(min = 2, max = 5) Set<@AlphaChars @Size(min = 1, max = 10) String> requestedPerms) {
        setup();
        
        // User has only the first permission, not all
        String firstPerm = requestedPerms.iterator().next();
        Permission permission = Permission.builder()
                .id(UUID.randomUUID().toString())
                .code(firstPerm)
                .name(firstPerm)
                .enabled(true)
                .build();
        
        when(permissionRepository.findPermissionsByUserId(userId)).thenReturn(Set.of(permission));
        when(permissionRepository.findRolesByUserId(userId)).thenReturn(Collections.emptySet());
        
        // Should return false because user doesn't have ALL permissions
        boolean result = permissionService.hasAllPermissions(userId, requestedPerms);
        
        if (requestedPerms.size() > 1) {
            assertThat(result).isFalse();
        }
    }
    
    // Property 6: Data Permission Filtering Correctness
    // For any data query, returned data should only include rows and columns the user can access
    
    @Property(tries = 100)
    void dataFilterShouldBeAppliedWhenConfigured(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String resourceType,
            @ForAll @AlphaChars @Size(min = 1, max = 50) String filterExpr) {
        setup();
        
        Map<String, Object> params = Map.of("deptId", "dept123");
        DataPermission dataPerm = DataPermission.builder()
                .id(UUID.randomUUID().toString())
                .resourceType(resourceType)
                .filterExpression(filterExpr)
                .filterParameters(params)
                .enabled(true)
                .build();
        
        when(permissionRepository.findDataPermission(userId, resourceType))
                .thenReturn(Optional.of(dataPerm));
        
        DataFilter result = permissionService.getDataFilter(userId, resourceType);
        
        assertThat(result.hasRestrictions()).isTrue();
        assertThat(result.getFilterExpression()).isEqualTo(filterExpr);
        assertThat(result.getParameters()).isEqualTo(params);
    }
    
    @Property(tries = 100)
    void noDataFilterWhenNotConfigured(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String resourceType) {
        setup();
        
        when(permissionRepository.findDataPermission(userId, resourceType))
                .thenReturn(Optional.empty());
        
        DataFilter result = permissionService.getDataFilter(userId, resourceType);
        
        assertThat(result.hasRestrictions()).isFalse();
    }
    
    @Property(tries = 100)
    void disabledDataPermissionShouldNotFilter(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String resourceType) {
        setup();
        
        DataPermission dataPerm = DataPermission.builder()
                .id(UUID.randomUUID().toString())
                .resourceType(resourceType)
                .filterExpression("department_id = :deptId")
                .enabled(false)  // Disabled
                .build();
        
        when(permissionRepository.findDataPermission(userId, resourceType))
                .thenReturn(Optional.of(dataPerm));
        
        DataFilter result = permissionService.getDataFilter(userId, resourceType);
        
        assertThat(result.hasRestrictions()).isFalse();
    }
    
    @Property(tries = 100)
    void accessibleColumnsShouldBeReturned(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String resourceType,
            @ForAll @Size(min = 1, max = 10) List<@AlphaChars @Size(min = 1, max = 20) String> columns) {
        setup();
        
        DataPermission dataPerm = DataPermission.builder()
                .id(UUID.randomUUID().toString())
                .resourceType(resourceType)
                .accessibleColumns(columns)
                .enabled(true)
                .build();
        
        when(permissionRepository.findDataPermission(userId, resourceType))
                .thenReturn(Optional.of(dataPerm));
        
        List<String> result = permissionService.getAccessibleColumns(userId, resourceType);
        
        assertThat(result).containsExactlyElementsOf(columns);
    }
    
    // API Permission Tests
    
    @Property(tries = 100)
    void apiPermissionShouldCheckRequiredPermissions(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId,
            @ForAll @AlphaChars @Size(min = 1, max = 20) String requiredPerm) {
        setup();
        
        String apiPath = "/api/test";
        String method = "GET";
        
        ApiPermission apiPerm = ApiPermission.builder()
                .id(UUID.randomUUID().toString())
                .apiPath(apiPath)
                .method(method)
                .requiredPermissions(Set.of(requiredPerm))
                .requireAll(false)
                .enabled(true)
                .build();
        
        when(permissionRepository.findApiPermission(apiPath, method))
                .thenReturn(Optional.of(apiPerm));
        when(permissionRepository.findPermissionsByUserId(userId)).thenReturn(Collections.emptySet());
        when(permissionRepository.findRolesByUserId(userId)).thenReturn(Collections.emptySet());
        
        boolean result = permissionService.hasApiPermission(userId, apiPath, method);
        
        // User has no permissions, should be denied
        assertThat(result).isFalse();
    }
    
    @Property(tries = 100)
    void unconfiguredApiShouldAllowAccess(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId) {
        setup();
        
        String apiPath = "/api/unconfigured";
        String method = "GET";
        
        when(permissionRepository.findApiPermission(apiPath, method))
                .thenReturn(Optional.empty());
        
        boolean result = permissionService.hasApiPermission(userId, apiPath, method);
        
        // No permission configured, should allow by default
        assertThat(result).isTrue();
    }
    
    // Null safety tests
    
    @Property(tries = 50)
    void nullUserIdShouldReturnFalse(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String permission) {
        setup();
        
        assertThat(permissionService.hasPermission(null, permission)).isFalse();
        assertThat(permissionService.hasApiPermission(null, "/api/test", "GET")).isFalse();
        assertThat(permissionService.hasRole(null, "admin")).isFalse();
    }
    
    @Property(tries = 50)
    void nullPermissionShouldReturnFalse(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId) {
        setup();
        
        assertThat(permissionService.hasPermission(userId, null)).isFalse();
    }
    
    // Cache invalidation test
    
    @Property(tries = 50)
    void cacheInvalidationShouldDeleteCacheEntries(
            @ForAll @AlphaChars @Size(min = 1, max = 20) String userId) {
        setup();
        
        permissionService.invalidateUserPermissionCache(userId);
        
        verify(cacheService).delete("permission:user:" + userId);
        verify(cacheService).delete("role:user:" + userId);
    }
}
