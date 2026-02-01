package com.developer.security;

import com.developer.repository.PermissionRepository;
import com.developer.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Spring Security annotations with database-backed permission checking.
 * Tests @PreAuthorize and @Secured annotation integration.
 * 
 * Requirements: 3.2, 3.4
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration-test")
public class SpringSecurityAnnotationIntegrationTest {
    
    @MockBean
    private PermissionRepository permissionRepository;
    
    @MockBean
    private RoleRepository roleRepository;
    
    @MockBean
    private SecurityCacheManager cacheManager;
    
    @MockBean
    private SecurityAuditLogger auditLogger;
    
    private TestSecuredService testService;
    
    @BeforeEach
    void setUp() {
        testService = new TestSecuredService();
    }
    
    /**
     * Test @PreAuthorize annotation integration with hasPermission() method
     */
    @Test
    @WithMockUser(username = "testuser")
    void testPreAuthorizeWithPermissionIntegration() {
        // Mock permission repository to return true
        when(permissionRepository.hasPermission("testuser", "READ_DATA")).thenReturn(true);
        when(cacheManager.getCachedPermission("testuser", "READ_DATA")).thenReturn(java.util.Optional.empty());
        
        // Should succeed when user has permission
        String result = testService.readData();
        assertEquals("Data read successfully", result);
        
        // Verify database was queried
        verify(permissionRepository).hasPermission("testuser", "READ_DATA");
        verify(cacheManager).cachePermission("testuser", "READ_DATA", true);
    }
    
    /**
     * Test @PreAuthorize annotation denies access when user lacks permission
     */
    @Test
    @WithMockUser(username = "testuser")
    void testPreAuthorizeDenieAccessWithoutPermission() {
        // Mock permission repository to return false
        when(permissionRepository.hasPermission("testuser", "WRITE_DATA")).thenReturn(false);
        when(cacheManager.getCachedPermission("testuser", "WRITE_DATA")).thenReturn(java.util.Optional.empty());
        
        // Should throw AccessDeniedException when user lacks permission
        assertThrows(org.springframework.security.access.AccessDeniedException.class, 
                () -> testService.writeData("test"));
        
        // Verify database was queried
        verify(permissionRepository).hasPermission("testuser", "WRITE_DATA");
        verify(cacheManager).cachePermission("testuser", "WRITE_DATA", false);
    }
    
    /**
     * Test @PreAuthorize with role checking
     */
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testPreAuthorizeWithRoleIntegration() {
        // Mock role repository to return true
        when(roleRepository.hasRole("admin", "ADMIN")).thenReturn(true);
        when(cacheManager.getCachedRole("admin", "ADMIN")).thenReturn(java.util.Optional.empty());
        
        // Should succeed when user has role
        String result = testService.adminOnlyOperation();
        assertEquals("Admin operation completed", result);
        
        // Verify database was queried
        verify(roleRepository).hasRole("admin", "ADMIN");
        verify(cacheManager).cacheRole("admin", "ADMIN", true);
    }
    
    /**
     * Test @PreAuthorize denies access when user lacks role
     */
    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void testPreAuthorizeDenieAccessWithoutRole() {
        // Mock role repository to return false
        when(roleRepository.hasRole("user", "ADMIN")).thenReturn(false);
        when(cacheManager.getCachedRole("user", "ADMIN")).thenReturn(java.util.Optional.empty());
        
        // Should throw AccessDeniedException when user lacks role
        assertThrows(org.springframework.security.access.AccessDeniedException.class, 
                () -> testService.adminOnlyOperation());
        
        // Verify database was queried
        verify(roleRepository).hasRole("user", "ADMIN");
        verify(cacheManager).cacheRole("user", "ADMIN", false);
    }
    
    /**
     * Test caching integration with @PreAuthorize
     */
    @Test
    @WithMockUser(username = "testuser")
    void testPreAuthorizeWithCaching() {
        // Mock cached permission result
        when(cacheManager.getCachedPermission("testuser", "READ_DATA"))
                .thenReturn(java.util.Optional.of(true));
        
        // Should succeed using cached result
        String result = testService.readData();
        assertEquals("Data read successfully", result);
        
        // Verify cache was used, database was not queried
        verify(cacheManager).getCachedPermission("testuser", "READ_DATA");
        verify(permissionRepository, never()).hasPermission(anyString(), anyString());
        verify(auditLogger).logSuccessfulAccess("testuser", "permission_evaluation", "READ_DATA", true);
    }
    
    /**
     * Test complex @PreAuthorize expression with multiple conditions
     */
    @Test
    @WithMockUser(username = "manager")
    void testComplexPreAuthorizeExpression() {
        // Mock both permission and role checks
        when(permissionRepository.hasPermission("manager", "MANAGE_USERS")).thenReturn(true);
        when(roleRepository.hasRole("manager", "MANAGER")).thenReturn(true);
        when(cacheManager.getCachedPermission("manager", "MANAGE_USERS")).thenReturn(java.util.Optional.empty());
        when(cacheManager.getCachedRole("manager", "MANAGER")).thenReturn(java.util.Optional.empty());
        
        // Should succeed when user has both permission and role
        String result = testService.manageUsers();
        assertEquals("Users managed successfully", result);
        
        // Verify both checks were performed
        verify(permissionRepository).hasPermission("manager", "MANAGE_USERS");
        verify(roleRepository).hasRole("manager", "MANAGER");
    }
    
    /**
     * Test @PreAuthorize with unauthenticated user
     */
    @Test
    void testPreAuthorizeWithUnauthenticatedUser() {
        // Clear security context
        SecurityContextHolder.clearContext();
        
        // Should throw AccessDeniedException for unauthenticated user
        assertThrows(org.springframework.security.access.AccessDeniedException.class, 
                () -> testService.readData());
        
        // Verify no database queries were made
        verify(permissionRepository, never()).hasPermission(anyString(), anyString());
        verify(auditLogger).logAuthenticationIssue("permission_evaluation", "no_authenticated_user");
    }
    
    /**
     * Test database error handling in @PreAuthorize
     */
    @Test
    @WithMockUser(username = "testuser")
    void testPreAuthorizeWithDatabaseError() {
        // Mock database error
        RuntimeException dbError = new RuntimeException("Database connection failed");
        when(permissionRepository.hasPermission("testuser", "READ_DATA")).thenThrow(dbError);
        when(cacheManager.getCachedPermission("testuser", "READ_DATA")).thenReturn(java.util.Optional.empty());
        
        // Should deny access on database error (fail-safe behavior)
        assertThrows(org.springframework.security.access.AccessDeniedException.class, 
                () -> testService.readData());
        
        // Verify error was logged
        verify(auditLogger).logDatabaseError("testuser", "permission_evaluation", "READ_DATA", dbError);
    }
    
    /**
     * Test method-level security with method parameters
     */
    @Test
    @WithMockUser(username = "owner")
    void testMethodSecurityWithParameters() {
        // Mock permission check for specific resource
        when(permissionRepository.hasPermission("owner", "DELETE_RESOURCE")).thenReturn(true);
        when(cacheManager.getCachedPermission("owner", "DELETE_RESOURCE")).thenReturn(java.util.Optional.empty());
        
        // Should succeed when user has permission for the resource
        String result = testService.deleteResource("resource123");
        assertEquals("Resource resource123 deleted", result);
        
        // Verify permission was checked
        verify(permissionRepository).hasPermission("owner", "DELETE_RESOURCE");
    }
    
    /**
     * Test service class with secured methods for integration testing
     */
    @Service
    public static class TestSecuredService {
        
        @PreAuthorize("hasPermission(null, 'READ_DATA')")
        public String readData() {
            return "Data read successfully";
        }
        
        @PreAuthorize("hasPermission(null, 'WRITE_DATA')")
        public String writeData(String data) {
            return "Data written: " + data;
        }
        
        @PreAuthorize("hasRole('ADMIN')")
        public String adminOnlyOperation() {
            return "Admin operation completed";
        }
        
        @PreAuthorize("hasPermission(null, 'MANAGE_USERS') and hasRole('MANAGER')")
        public String manageUsers() {
            return "Users managed successfully";
        }
        
        @PreAuthorize("hasPermission(null, 'DELETE_RESOURCE')")
        public String deleteResource(String resourceId) {
            return "Resource " + resourceId + " deleted";
        }
    }
}