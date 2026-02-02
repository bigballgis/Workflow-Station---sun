package com.admin.helper;

import com.admin.enums.RoleType;
import com.admin.repository.RoleRepository;
import com.platform.security.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RoleHelper service.
 * Tests role-related business operations with mocked repository dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoleHelper Tests")
class RoleHelperTest {
    
    @Mock
    private RoleRepository roleRepository;
    
    @InjectMocks
    private RoleHelper roleHelper;
    
    private Role buBoundedRole;
    private Role buUnboundedRole;
    private Role developerRole;
    private Role adminRole;
    private Role systemRole;
    private Role customRole;
    
    @BeforeEach
    void setUp() {
        // Create test roles
        buBoundedRole = Role.builder()
                .id("role-1")
                .code("BU_MANAGER")
                .name("Business Unit Manager")
                .type("BU_BOUNDED")
                .isSystem(false)
                .build();
        
        buUnboundedRole = Role.builder()
                .id("role-2")
                .code("BU_ADMIN")
                .name("Business Unit Admin")
                .type("BU_UNBOUNDED")
                .isSystem(false)
                .build();
        
        developerRole = Role.builder()
                .id("role-3")
                .code("DEV")
                .name("Developer")
                .type("DEVELOPER")
                .isSystem(true)
                .build();
        
        adminRole = Role.builder()
                .id("role-4")
                .code("ADMIN")
                .name("System Administrator")
                .type("ADMIN")
                .isSystem(true)
                .build();
        
        systemRole = Role.builder()
                .id("role-5")
                .code("SYSTEM_ROLE")
                .name("System Role")
                .type("ADMIN")
                .isSystem(true)
                .build();
        
        customRole = Role.builder()
                .id("role-6")
                .code("CUSTOM_ROLE")
                .name("Custom Role")
                .type("BU_BOUNDED")
                .isSystem(false)
                .build();
    }
    
    // ========== isBusinessRole(String) Tests ==========
    
    @Test
    @DisplayName("isBusinessRole(String): Should return true for BU_BOUNDED")
    void testIsBusinessRoleString_BuBounded() {
        assertTrue(roleHelper.isBusinessRole("BU_BOUNDED"));
    }
    
    @Test
    @DisplayName("isBusinessRole(String): Should return true for BU_UNBOUNDED")
    void testIsBusinessRoleString_BuUnbounded() {
        assertTrue(roleHelper.isBusinessRole("BU_UNBOUNDED"));
    }
    
    @Test
    @DisplayName("isBusinessRole(String): Should return false for DEVELOPER")
    void testIsBusinessRoleString_Developer() {
        assertFalse(roleHelper.isBusinessRole("DEVELOPER"));
    }
    
    @Test
    @DisplayName("isBusinessRole(String): Should return false for ADMIN")
    void testIsBusinessRoleString_Admin() {
        assertFalse(roleHelper.isBusinessRole("ADMIN"));
    }
    
    @Test
    @DisplayName("isBusinessRole(String): Should return false for null")
    void testIsBusinessRoleString_Null() {
        assertFalse(roleHelper.isBusinessRole((String) null));
    }
    
    @Test
    @DisplayName("isBusinessRole(String): Should return false for empty string")
    void testIsBusinessRoleString_EmptyString() {
        assertFalse(roleHelper.isBusinessRole(""));
    }
    
    @Test
    @DisplayName("isBusinessRole(String): Should return false for unknown type")
    void testIsBusinessRoleString_UnknownType() {
        assertFalse(roleHelper.isBusinessRole("UNKNOWN_TYPE"));
    }
    
    // ========== isBusinessRole(Role) Tests ==========
    
    @Test
    @DisplayName("isBusinessRole(Role): Should return true for BU_BOUNDED role")
    void testIsBusinessRoleEntity_BuBounded() {
        assertTrue(roleHelper.isBusinessRole(buBoundedRole));
    }
    
    @Test
    @DisplayName("isBusinessRole(Role): Should return true for BU_UNBOUNDED role")
    void testIsBusinessRoleEntity_BuUnbounded() {
        assertTrue(roleHelper.isBusinessRole(buUnboundedRole));
    }
    
    @Test
    @DisplayName("isBusinessRole(Role): Should return false for DEVELOPER role")
    void testIsBusinessRoleEntity_Developer() {
        assertFalse(roleHelper.isBusinessRole(developerRole));
    }
    
    @Test
    @DisplayName("isBusinessRole(Role): Should return false for ADMIN role")
    void testIsBusinessRoleEntity_Admin() {
        assertFalse(roleHelper.isBusinessRole(adminRole));
    }
    
    @Test
    @DisplayName("isBusinessRole(Role): Should return false for null role")
    void testIsBusinessRoleEntity_Null() {
        assertFalse(roleHelper.isBusinessRole((Role) null));
    }
    
    // ========== isSystemRole Tests ==========
    
    @Test
    @DisplayName("isSystemRole: Should return true for system role")
    void testIsSystemRole_SystemRole() {
        assertTrue(roleHelper.isSystemRole(systemRole));
    }
    
    @Test
    @DisplayName("isSystemRole: Should return true for developer role (system)")
    void testIsSystemRole_DeveloperRole() {
        assertTrue(roleHelper.isSystemRole(developerRole));
    }
    
    @Test
    @DisplayName("isSystemRole: Should return false for custom role")
    void testIsSystemRole_CustomRole() {
        assertFalse(roleHelper.isSystemRole(customRole));
    }
    
    @Test
    @DisplayName("isSystemRole: Should return false for null role")
    void testIsSystemRole_Null() {
        assertFalse(roleHelper.isSystemRole(null));
    }
    
    @Test
    @DisplayName("isSystemRole: Should return false when isSystem is null")
    void testIsSystemRole_IsSystemNull() {
        Role roleWithNullIsSystem = Role.builder()
                .id("role-7")
                .code("TEST")
                .name("Test Role")
                .type("BU_BOUNDED")
                .isSystem(null)
                .build();
        
        assertFalse(roleHelper.isSystemRole(roleWithNullIsSystem));
    }
    
    @Test
    @DisplayName("isSystemRole: Should return false when isSystem is false")
    void testIsSystemRole_IsSystemFalse() {
        assertFalse(roleHelper.isSystemRole(buBoundedRole));
    }
    
    // ========== isDeveloperRole Tests ==========
    
    @Test
    @DisplayName("isDeveloperRole: Should return true for DEVELOPER type")
    void testIsDeveloperRole_Developer() {
        assertTrue(roleHelper.isDeveloperRole("DEVELOPER"));
    }
    
    @Test
    @DisplayName("isDeveloperRole: Should return false for BU_BOUNDED type")
    void testIsDeveloperRole_BuBounded() {
        assertFalse(roleHelper.isDeveloperRole("BU_BOUNDED"));
    }
    
    @Test
    @DisplayName("isDeveloperRole: Should return false for ADMIN type")
    void testIsDeveloperRole_Admin() {
        assertFalse(roleHelper.isDeveloperRole("ADMIN"));
    }
    
    @Test
    @DisplayName("isDeveloperRole: Should return false for null")
    void testIsDeveloperRole_Null() {
        assertFalse(roleHelper.isDeveloperRole(null));
    }
    
    // ========== isAdminRole Tests ==========
    
    @Test
    @DisplayName("isAdminRole: Should return true for ADMIN type")
    void testIsAdminRole_Admin() {
        assertTrue(roleHelper.isAdminRole("ADMIN"));
    }
    
    @Test
    @DisplayName("isAdminRole: Should return false for BU_BOUNDED type")
    void testIsAdminRole_BuBounded() {
        assertFalse(roleHelper.isAdminRole("BU_BOUNDED"));
    }
    
    @Test
    @DisplayName("isAdminRole: Should return false for DEVELOPER type")
    void testIsAdminRole_Developer() {
        assertFalse(roleHelper.isAdminRole("DEVELOPER"));
    }
    
    @Test
    @DisplayName("isAdminRole: Should return false for null")
    void testIsAdminRole_Null() {
        assertFalse(roleHelper.isAdminRole(null));
    }
    
    // ========== getRoleType Tests ==========
    
    @Test
    @DisplayName("getRoleType: Should return BU_BOUNDED for BU_BOUNDED role")
    void testGetRoleType_BuBounded() {
        RoleType result = roleHelper.getRoleType(buBoundedRole);
        assertEquals(RoleType.BU_BOUNDED, result);
    }
    
    @Test
    @DisplayName("getRoleType: Should return BU_UNBOUNDED for BU_UNBOUNDED role")
    void testGetRoleType_BuUnbounded() {
        RoleType result = roleHelper.getRoleType(buUnboundedRole);
        assertEquals(RoleType.BU_UNBOUNDED, result);
    }
    
    @Test
    @DisplayName("getRoleType: Should return DEVELOPER for DEVELOPER role")
    void testGetRoleType_Developer() {
        RoleType result = roleHelper.getRoleType(developerRole);
        assertEquals(RoleType.DEVELOPER, result);
    }
    
    @Test
    @DisplayName("getRoleType: Should return ADMIN for ADMIN role")
    void testGetRoleType_Admin() {
        RoleType result = roleHelper.getRoleType(adminRole);
        assertEquals(RoleType.ADMIN, result);
    }
    
    @Test
    @DisplayName("getRoleType: Should return null for null role")
    void testGetRoleType_Null() {
        RoleType result = roleHelper.getRoleType(null);
        assertNull(result);
    }
    
    @Test
    @DisplayName("getRoleType: Should throw IllegalArgumentException for invalid type")
    void testGetRoleType_InvalidType() {
        Role invalidRole = Role.builder()
                .id("role-8")
                .code("INVALID")
                .name("Invalid Role")
                .type("INVALID_TYPE")
                .build();
        
        assertThrows(IllegalArgumentException.class, () -> roleHelper.getRoleType(invalidRole));
    }
    
    // ========== getBusinessRoles Tests ==========
    
    @Test
    @DisplayName("getBusinessRoles: Should return only business roles")
    void testGetBusinessRoles_ReturnsOnlyBusinessRoles() {
        List<Role> allRoles = Arrays.asList(
                buBoundedRole,
                buUnboundedRole,
                developerRole,
                adminRole,
                customRole
        );
        
        when(roleRepository.findAll()).thenReturn(allRoles);
        
        List<Role> businessRoles = roleHelper.getBusinessRoles();
        
        assertEquals(3, businessRoles.size());
        assertTrue(businessRoles.contains(buBoundedRole));
        assertTrue(businessRoles.contains(buUnboundedRole));
        assertTrue(businessRoles.contains(customRole));
        assertFalse(businessRoles.contains(developerRole));
        assertFalse(businessRoles.contains(adminRole));
        
        verify(roleRepository, times(1)).findAll();
    }
    
    @Test
    @DisplayName("getBusinessRoles: Should return empty list when no business roles exist")
    void testGetBusinessRoles_EmptyList() {
        List<Role> allRoles = Arrays.asList(developerRole, adminRole);
        
        when(roleRepository.findAll()).thenReturn(allRoles);
        
        List<Role> businessRoles = roleHelper.getBusinessRoles();
        
        assertTrue(businessRoles.isEmpty());
        verify(roleRepository, times(1)).findAll();
    }
    
    @Test
    @DisplayName("getBusinessRoles: Should return empty list when repository returns empty")
    void testGetBusinessRoles_RepositoryEmpty() {
        when(roleRepository.findAll()).thenReturn(List.of());
        
        List<Role> businessRoles = roleHelper.getBusinessRoles();
        
        assertTrue(businessRoles.isEmpty());
        verify(roleRepository, times(1)).findAll();
    }
    
    // ========== getSystemRoles Tests ==========
    
    @Test
    @DisplayName("getSystemRoles: Should return only system roles")
    void testGetSystemRoles_ReturnsOnlySystemRoles() {
        List<Role> allRoles = Arrays.asList(
                buBoundedRole,
                developerRole,
                adminRole,
                systemRole,
                customRole
        );
        
        when(roleRepository.findAll()).thenReturn(allRoles);
        
        List<Role> systemRoles = roleHelper.getSystemRoles();
        
        assertEquals(3, systemRoles.size());
        assertTrue(systemRoles.contains(developerRole));
        assertTrue(systemRoles.contains(adminRole));
        assertTrue(systemRoles.contains(systemRole));
        assertFalse(systemRoles.contains(buBoundedRole));
        assertFalse(systemRoles.contains(customRole));
        
        verify(roleRepository, times(1)).findAll();
    }
    
    @Test
    @DisplayName("getSystemRoles: Should return empty list when no system roles exist")
    void testGetSystemRoles_EmptyList() {
        List<Role> allRoles = Arrays.asList(buBoundedRole, customRole);
        
        when(roleRepository.findAll()).thenReturn(allRoles);
        
        List<Role> systemRoles = roleHelper.getSystemRoles();
        
        assertTrue(systemRoles.isEmpty());
        verify(roleRepository, times(1)).findAll();
    }
    
    // ========== isValidRoleType Tests ==========
    
    @Test
    @DisplayName("isValidRoleType: Should return true for BU_BOUNDED")
    void testIsValidRoleType_BuBounded() {
        assertTrue(roleHelper.isValidRoleType("BU_BOUNDED"));
    }
    
    @Test
    @DisplayName("isValidRoleType: Should return true for BU_UNBOUNDED")
    void testIsValidRoleType_BuUnbounded() {
        assertTrue(roleHelper.isValidRoleType("BU_UNBOUNDED"));
    }
    
    @Test
    @DisplayName("isValidRoleType: Should return true for DEVELOPER")
    void testIsValidRoleType_Developer() {
        assertTrue(roleHelper.isValidRoleType("DEVELOPER"));
    }
    
    @Test
    @DisplayName("isValidRoleType: Should return true for ADMIN")
    void testIsValidRoleType_Admin() {
        assertTrue(roleHelper.isValidRoleType("ADMIN"));
    }
    
    @Test
    @DisplayName("isValidRoleType: Should return false for invalid type")
    void testIsValidRoleType_Invalid() {
        assertFalse(roleHelper.isValidRoleType("INVALID_TYPE"));
    }
    
    @Test
    @DisplayName("isValidRoleType: Should return false for null")
    void testIsValidRoleType_Null() {
        assertFalse(roleHelper.isValidRoleType(null));
    }
    
    @Test
    @DisplayName("isValidRoleType: Should return false for empty string")
    void testIsValidRoleType_EmptyString() {
        assertFalse(roleHelper.isValidRoleType(""));
    }
}
