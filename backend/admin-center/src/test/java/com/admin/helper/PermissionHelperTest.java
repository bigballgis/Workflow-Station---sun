package com.admin.helper;

import com.admin.repository.PermissionRepository;
import com.platform.security.entity.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PermissionHelper service.
 * Tests permission operations with mocked repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionHelper Tests")
class PermissionHelperTest {
    
    @Mock
    private PermissionRepository permissionRepository;
    
    @InjectMocks
    private PermissionHelper permissionHelper;
    
    private Permission permissionWithFields;
    private Permission permissionWithCode;
    private Permission permissionWithBoth;
    private Permission wildcardResourcePermission;
    private Permission wildcardActionPermission;
    private Permission wildcardBothPermission;
    private Permission prefixWildcardPermission;
    
    @BeforeEach
    void setUp() {
        // Permission with resource and action fields
        permissionWithFields = Permission.builder()
                .id("perm-1")
                .code("USER_READ")
                .name("Read User")
                .resource("user")
                .action("read")
                .build();
        
        // Permission with only code field (format: resource:action)
        permissionWithCode = Permission.builder()
                .id("perm-2")
                .code("role:write")
                .name("Write Role")
                .resource(null)
                .action(null)
                .build();
        
        // Permission with both fields and code
        permissionWithBoth = Permission.builder()
                .id("perm-3")
                .code("permission:delete")
                .name("Delete Permission")
                .resource("permission")
                .action("delete")
                .build();
        
        // Wildcard resource permission
        wildcardResourcePermission = Permission.builder()
                .id("perm-4")
                .code("ALL_READ")
                .name("Read All")
                .resource("*")
                .action("read")
                .build();
        
        // Wildcard action permission
        wildcardActionPermission = Permission.builder()
                .id("perm-5")
                .code("USER_ALL")
                .name("All User Actions")
                .resource("user")
                .action("*")
                .build();
        
        // Wildcard both permission
        wildcardBothPermission = Permission.builder()
                .id("perm-6")
                .code("ALL_ALL")
                .name("All Permissions")
                .resource("*")
                .action("*")
                .build();
        
        // Prefix wildcard permission
        prefixWildcardPermission = Permission.builder()
                .id("perm-7")
                .code("ADMIN_ALL")
                .name("All Admin Actions")
                .resource("admin.*")
                .action("*")
                .build();
    }
    
    // ========== getResource Tests ==========
    
    @Test
    @DisplayName("getResource: Should return resource when field is set")
    void testGetResource_WithResourceTypeField() {
        String result = permissionHelper.getResource(permissionWithFields);
        assertEquals("user", result);
    }
    
    @Test
    @DisplayName("getResource: Should parse from code when resource is null")
    void testGetResource_ParseFromCode() {
        String result = permissionHelper.getResource(permissionWithCode);
        assertEquals("role", result);
    }
    
    @Test
    @DisplayName("getResource: Should prefer resource field over code")
    void testGetResource_PreferField() {
        String result = permissionHelper.getResource(permissionWithBoth);
        assertEquals("permission", result);
    }
    
    @Test
    @DisplayName("getResource: Should return null for null permission")
    void testGetResource_NullPermission() {
        String result = permissionHelper.getResource(null);
        assertNull(result);
    }
    
    @Test
    @DisplayName("getResource: Should return null when both resource and code are null")
    void testGetResource_BothNull() {
        Permission emptyPermission = Permission.builder()
                .id("perm-8")
                .code(null)
                .name("Empty Permission")
                .resource(null)
                .action("read")
                .build();
        
        String result = permissionHelper.getResource(emptyPermission);
        assertNull(result);
    }
    
    @Test
    @DisplayName("getResource: Should return null when resource is empty and code has no colon")
    void testGetResource_EmptyResourceTypeNoColon() {
        Permission permission = Permission.builder()
                .id("perm-9")
                .code("SIMPLE_CODE")
                .name("Simple Code Permission")
                .resource("")
                .action("read")
                .build();
        
        String result = permissionHelper.getResource(permission);
        assertNull(result);
    }
    
    @Test
    @DisplayName("getResource: Should return wildcard resource")
    void testGetResource_Wildcard() {
        String result = permissionHelper.getResource(wildcardResourcePermission);
        assertEquals("*", result);
    }
    
    @Test
    @DisplayName("getResource: Should return prefix wildcard resource")
    void testGetResource_PrefixWildcard() {
        String result = permissionHelper.getResource(prefixWildcardPermission);
        assertEquals("admin.*", result);
    }
    
    // ========== getAction Tests ==========
    
    @Test
    @DisplayName("getAction: Should return action when field is set")
    void testGetAction_WithActionField() {
        String result = permissionHelper.getAction(permissionWithFields);
        assertEquals("read", result);
    }
    
    @Test
    @DisplayName("getAction: Should parse from code when action is null")
    void testGetAction_ParseFromCode() {
        String result = permissionHelper.getAction(permissionWithCode);
        assertEquals("write", result);
    }
    
    @Test
    @DisplayName("getAction: Should prefer action field over code")
    void testGetAction_PreferField() {
        String result = permissionHelper.getAction(permissionWithBoth);
        assertEquals("delete", result);
    }
    
    @Test
    @DisplayName("getAction: Should return null for null permission")
    void testGetAction_NullPermission() {
        String result = permissionHelper.getAction(null);
        assertNull(result);
    }
    
    @Test
    @DisplayName("getAction: Should return null when both action and code are null")
    void testGetAction_BothNull() {
        Permission emptyPermission = Permission.builder()
                .id("perm-10")
                .code(null)
                .name("Empty Permission")
                .resource("user")
                .action(null)
                .build();
        
        String result = permissionHelper.getAction(emptyPermission);
        assertNull(result);
    }
    
    @Test
    @DisplayName("getAction: Should return null when action is empty and code has no colon")
    void testGetAction_EmptyActionNoColon() {
        Permission permission = Permission.builder()
                .id("perm-11")
                .code("SIMPLE_CODE")
                .name("Simple Code Permission")
                .resource("user")
                .action("")
                .build();
        
        String result = permissionHelper.getAction(permission);
        assertNull(result);
    }
    
    @Test
    @DisplayName("getAction: Should return wildcard action")
    void testGetAction_Wildcard() {
        String result = permissionHelper.getAction(wildcardActionPermission);
        assertEquals("*", result);
    }
    
    // ========== matches Tests ==========
    
    @Test
    @DisplayName("matches: Should return true for exact match")
    void testMatches_ExactMatch() {
        assertTrue(permissionHelper.matches(permissionWithFields, "user", "read"));
    }
    
    @Test
    @DisplayName("matches: Should return false for resource mismatch")
    void testMatches_ResourceMismatch() {
        assertFalse(permissionHelper.matches(permissionWithFields, "role", "read"));
    }
    
    @Test
    @DisplayName("matches: Should return false for action mismatch")
    void testMatches_ActionMismatch() {
        assertFalse(permissionHelper.matches(permissionWithFields, "user", "write"));
    }
    
    @Test
    @DisplayName("matches: Should return true for wildcard resource")
    void testMatches_WildcardResource() {
        assertTrue(permissionHelper.matches(wildcardResourcePermission, "user", "read"));
        assertTrue(permissionHelper.matches(wildcardResourcePermission, "role", "read"));
        assertTrue(permissionHelper.matches(wildcardResourcePermission, "anything", "read"));
    }
    
    @Test
    @DisplayName("matches: Should return true for wildcard action")
    void testMatches_WildcardAction() {
        assertTrue(permissionHelper.matches(wildcardActionPermission, "user", "read"));
        assertTrue(permissionHelper.matches(wildcardActionPermission, "user", "write"));
        assertTrue(permissionHelper.matches(wildcardActionPermission, "user", "delete"));
    }
    
    @Test
    @DisplayName("matches: Should return true for wildcard both")
    void testMatches_WildcardBoth() {
        assertTrue(permissionHelper.matches(wildcardBothPermission, "user", "read"));
        assertTrue(permissionHelper.matches(wildcardBothPermission, "role", "write"));
        assertTrue(permissionHelper.matches(wildcardBothPermission, "anything", "anything"));
    }
    
    @Test
    @DisplayName("matches: Should return true for prefix wildcard match")
    void testMatches_PrefixWildcard() {
        assertTrue(permissionHelper.matches(prefixWildcardPermission, "admin.user", "read"));
        assertTrue(permissionHelper.matches(prefixWildcardPermission, "admin.role", "write"));
        assertTrue(permissionHelper.matches(prefixWildcardPermission, "admin.anything", "delete"));
    }
    
    @Test
    @DisplayName("matches: Should return false for prefix wildcard non-match")
    void testMatches_PrefixWildcardNonMatch() {
        assertFalse(permissionHelper.matches(prefixWildcardPermission, "user", "read"));
        assertFalse(permissionHelper.matches(prefixWildcardPermission, "role", "write"));
    }
    
    @Test
    @DisplayName("matches: Should return false for null permission")
    void testMatches_NullPermission() {
        assertFalse(permissionHelper.matches(null, "user", "read"));
    }
    
    @Test
    @DisplayName("matches: Should return false for null resource")
    void testMatches_NullResource() {
        assertFalse(permissionHelper.matches(permissionWithFields, null, "read"));
    }
    
    @Test
    @DisplayName("matches: Should return false for null action")
    void testMatches_NullAction() {
        assertFalse(permissionHelper.matches(permissionWithFields, "user", null));
    }
    
    @Test
    @DisplayName("matches: Should return false when permission has null resource")
    void testMatches_PermissionNullResource() {
        Permission permission = Permission.builder()
                .id("perm-12")
                .code("TEST")
                .name("Test Permission")
                .resource(null)
                .action("read")
                .build();
        
        assertFalse(permissionHelper.matches(permission, "user", "read"));
    }
    
    @Test
    @DisplayName("matches: Should return false when permission has null action")
    void testMatches_PermissionNullAction() {
        Permission permission = Permission.builder()
                .id("perm-13")
                .code("TEST")
                .name("Test Permission")
                .resource("user")
                .action(null)
                .build();
        
        assertFalse(permissionHelper.matches(permission, "user", "read"));
    }
    
    // ========== isWildcard Tests ==========
    
    @Test
    @DisplayName("isWildcard: Should return false for exact permission")
    void testIsWildcard_ExactPermission() {
        assertFalse(permissionHelper.isWildcard(permissionWithFields));
    }
    
    @Test
    @DisplayName("isWildcard: Should return true for wildcard resource")
    void testIsWildcard_WildcardResource() {
        assertTrue(permissionHelper.isWildcard(wildcardResourcePermission));
    }
    
    @Test
    @DisplayName("isWildcard: Should return true for wildcard action")
    void testIsWildcard_WildcardAction() {
        assertTrue(permissionHelper.isWildcard(wildcardActionPermission));
    }
    
    @Test
    @DisplayName("isWildcard: Should return true for wildcard both")
    void testIsWildcard_WildcardBoth() {
        assertTrue(permissionHelper.isWildcard(wildcardBothPermission));
    }
    
    @Test
    @DisplayName("isWildcard: Should return true for prefix wildcard")
    void testIsWildcard_PrefixWildcard() {
        assertTrue(permissionHelper.isWildcard(prefixWildcardPermission));
    }
    
    @Test
    @DisplayName("isWildcard: Should return false for null permission")
    void testIsWildcard_NullPermission() {
        assertFalse(permissionHelper.isWildcard(null));
    }
    
    @Test
    @DisplayName("isWildcard: Should return false when both resource and action are null")
    void testIsWildcard_BothNull() {
        Permission permission = Permission.builder()
                .id("perm-14")
                .code("TEST")
                .name("Test Permission")
                .resource(null)
                .action(null)
                .build();
        
        assertFalse(permissionHelper.isWildcard(permission));
    }
    
    @Test
    @DisplayName("isWildcard: Should return false when resource is empty and action is not wildcard")
    void testIsWildcard_EmptyResourceNonWildcardAction() {
        Permission permission = Permission.builder()
                .id("perm-15")
                .code("TEST")
                .name("Test Permission")
                .resource("")
                .action("read")
                .build();
        
        assertFalse(permissionHelper.isWildcard(permission));
    }
    
    @Test
    @DisplayName("isWildcard: Should return true when only resource is wildcard")
    void testIsWildcard_OnlyResourceWildcard() {
        Permission permission = Permission.builder()
                .id("perm-16")
                .code("TEST")
                .name("Test Permission")
                .resource("*")
                .action("read")
                .build();
        
        assertTrue(permissionHelper.isWildcard(permission));
    }
    
    @Test
    @DisplayName("isWildcard: Should return true when only action is wildcard")
    void testIsWildcard_OnlyActionWildcard() {
        Permission permission = Permission.builder()
                .id("perm-17")
                .code("TEST")
                .name("Test Permission")
                .resource("user")
                .action("*")
                .build();
        
        assertTrue(permissionHelper.isWildcard(permission));
    }
}
