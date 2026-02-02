package com.admin.properties;

import com.platform.security.entity.Role;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupRole;
import com.admin.enums.RoleType;
import com.admin.util.EntityTypeConverter;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.RoleRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import com.admin.service.VirtualGroupRoleService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 1: Role Type Validation for Virtual Group Binding
 * *For any* role binding operation to a Virtual_Group, the system shall accept only roles 
 * with type BU_BOUNDED or BU_UNBOUNDED, and reject any role with type ADMIN or DEVELOPER.
 * 
 * Property 2: Single Role Per Virtual Group
 * *For any* Virtual_Group, the system shall ensure that at most one role is bound at any time.
 * Binding a new role shall replace the existing binding.
 * 
 * **Validates: Requirements 2.1, 2.6, 2.7**
 */
public class VirtualGroupRoleBindingProperties {
    
    private VirtualGroupRoleRepository virtualGroupRoleRepository;
    private VirtualGroupRepository virtualGroupRepository;
    private RoleRepository roleRepository;
    private com.admin.helper.RoleHelper roleHelper;
    private VirtualGroupRoleService virtualGroupRoleService;
    
    @BeforeTry
    void setUp() {
        virtualGroupRoleRepository = mock(VirtualGroupRoleRepository.class);
        virtualGroupRepository = mock(VirtualGroupRepository.class);
        roleRepository = mock(RoleRepository.class);
        roleHelper = mock(com.admin.helper.RoleHelper.class);
        virtualGroupRoleService = new VirtualGroupRoleService(
                virtualGroupRoleRepository,
                virtualGroupRepository,
                roleRepository,
                roleHelper);
    }
    
    // ==================== Property 1: Role Type Validation ====================
    
    /**
     * Feature: permission-request-approval, Property 1: Role Type Validation for Binding
     * BU_BOUNDED type roles should be accepted for binding
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 1: BU_BOUNDED roles should be accepted for binding")
    void buBoundedRolesShouldBeAcceptedForBinding(
            @ForAll("validVirtualGroupIds") String virtualGroupId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: Virtual group exists
        when(virtualGroupRepository.existsById(virtualGroupId)).thenReturn(true);
        VirtualGroup virtualGroup = VirtualGroup.builder()
                .id(virtualGroupId)
                .name("Test Group")
                .type("CUSTOM")
                .build();
        when(virtualGroupRepository.findById(virtualGroupId)).thenReturn(Optional.of(virtualGroup));
        
        // Given: Role exists and is BU_BOUNDED type
        Role buBoundedRole = createRole(roleId, RoleType.BU_BOUNDED);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(buBoundedRole));
        
        // Given: RoleHelper recognizes it as a business role
        when(roleHelper.isBusinessRole(buBoundedRole)).thenReturn(true);
        
        // Given: No existing binding
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.empty());
        
        // Given: Save succeeds
        when(virtualGroupRoleRepository.save(any(VirtualGroupRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Bind role
        virtualGroupRoleService.bindRole(virtualGroupId, roleId);
        
        // Then: Role should be saved
        verify(virtualGroupRoleRepository).save(any(VirtualGroupRole.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 1: Role Type Validation for Binding
     * BU_UNBOUNDED type roles should be accepted for binding
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 1: BU_UNBOUNDED roles should be accepted for binding")
    void buUnboundedRolesShouldBeAcceptedForBinding(
            @ForAll("validVirtualGroupIds") String virtualGroupId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: Virtual group exists
        when(virtualGroupRepository.existsById(virtualGroupId)).thenReturn(true);
        VirtualGroup virtualGroup = VirtualGroup.builder()
                .id(virtualGroupId)
                .name("Test Group")
                .type("CUSTOM")
                .build();
        when(virtualGroupRepository.findById(virtualGroupId)).thenReturn(Optional.of(virtualGroup));
        
        // Given: Role exists and is BU_UNBOUNDED type
        Role buUnboundedRole = createRole(roleId, RoleType.BU_UNBOUNDED);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(buUnboundedRole));
        
        // Given: RoleHelper recognizes it as a business role
        when(roleHelper.isBusinessRole(buUnboundedRole)).thenReturn(true);
        
        // Given: No existing binding
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.empty());
        
        // Given: Save succeeds
        when(virtualGroupRoleRepository.save(any(VirtualGroupRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Bind role
        virtualGroupRoleService.bindRole(virtualGroupId, roleId);
        
        // Then: Role should be saved
        verify(virtualGroupRoleRepository).save(any(VirtualGroupRole.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 1: Role Type Validation for Binding
     * ADMIN type roles should be rejected for binding
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 1: ADMIN roles should be rejected for binding")
    void adminRolesShouldBeRejectedForBinding(
            @ForAll("validVirtualGroupIds") String virtualGroupId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: Virtual group exists
        when(virtualGroupRepository.existsById(virtualGroupId)).thenReturn(true);
        VirtualGroup virtualGroup = VirtualGroup.builder()
                .id(virtualGroupId)
                .name("Test Group")
                .type("CUSTOM")
                .build();
        when(virtualGroupRepository.findById(virtualGroupId)).thenReturn(Optional.of(virtualGroup));
        
        // Given: Role exists and is ADMIN type
        Role adminRole = createRole(roleId, RoleType.ADMIN);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(adminRole));
        
        // Given: RoleHelper recognizes it as NOT a business role
        when(roleHelper.isBusinessRole(adminRole)).thenReturn(false);
        
        // When & Then: Binding should throw exception
        assertThatThrownBy(() -> virtualGroupRoleService.bindRole(virtualGroupId, roleId))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("只能绑定业务角色");
        
        // Then: Role should not be saved
        verify(virtualGroupRoleRepository, never()).save(any(VirtualGroupRole.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 1: Role Type Validation for Binding
     * DEVELOPER type roles should be rejected for binding
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 1: DEVELOPER roles should be rejected for binding")
    void developerRolesShouldBeRejectedForBinding(
            @ForAll("validVirtualGroupIds") String virtualGroupId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: Virtual group exists
        when(virtualGroupRepository.existsById(virtualGroupId)).thenReturn(true);
        VirtualGroup virtualGroup = VirtualGroup.builder()
                .id(virtualGroupId)
                .name("Test Group")
                .type("CUSTOM")
                .build();
        when(virtualGroupRepository.findById(virtualGroupId)).thenReturn(Optional.of(virtualGroup));
        
        // Given: Role exists and is DEVELOPER type
        Role developerRole = createRole(roleId, RoleType.DEVELOPER);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(developerRole));
        
        // Given: RoleHelper recognizes it as NOT a business role
        when(roleHelper.isBusinessRole(developerRole)).thenReturn(false);
        
        // When & Then: Binding should throw exception
        assertThatThrownBy(() -> virtualGroupRoleService.bindRole(virtualGroupId, roleId))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("只能绑定业务角色");
        
        // Then: Role should not be saved
        verify(virtualGroupRoleRepository, never()).save(any(VirtualGroupRole.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 1: Role Type Validation for Binding
     * For any role type, only BU_BOUNDED and BU_UNBOUNDED should pass validation
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 1: Only business role types pass validation")
    void onlyBusinessTypePassesValidation(
            @ForAll("allRoleTypes") RoleType roleType,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: Create role with the given type
        Role role = createRole(roleId, roleType);
        
        // Given: Mock roleHelper behavior based on role type
        when(roleHelper.isBusinessRole(role)).thenReturn(roleType.isBusinessRole());
        
        // When: Validate role type
        boolean shouldPass = roleType.isBusinessRole();
        
        // Then: Validation result should match expectation
        if (shouldPass) {
            // Should not throw
            virtualGroupRoleService.validateBusinessRole(role);
        } else {
            // Should throw
            assertThatThrownBy(() -> virtualGroupRoleService.validateBusinessRole(role))
                    .isInstanceOf(AdminBusinessException.class);
        }
    }
    
    // ==================== Property 2: Single Role Per Virtual Group ====================
    
    /**
     * Feature: permission-request-approval, Property 2: Single Role Per Virtual Group
     * Binding a new role to a virtual group with existing binding should replace the old binding
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 2: Binding new role replaces existing binding")
    void bindingNewRoleReplacesExistingBinding(
            @ForAll("validVirtualGroupIds") String virtualGroupId,
            @ForAll("validRoleIds") String oldRoleId,
            @ForAll("validRoleIds") String newRoleId) {
        
        // Given: Virtual group exists
        when(virtualGroupRepository.existsById(virtualGroupId)).thenReturn(true);
        VirtualGroup virtualGroup = VirtualGroup.builder()
                .id(virtualGroupId)
                .name("Test Group")
                .type("CUSTOM")
                .build();
        when(virtualGroupRepository.findById(virtualGroupId)).thenReturn(Optional.of(virtualGroup));
        
        // Given: Old role binding exists
        VirtualGroupRole existingBinding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(oldRoleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(existingBinding));
        
        // Given: New role exists and is BU_BOUNDED type
        Role newRole = createRole(newRoleId, RoleType.BU_BOUNDED);
        when(roleRepository.findById(newRoleId)).thenReturn(Optional.of(newRole));
        
        // Given: RoleHelper recognizes it as a business role
        when(roleHelper.isBusinessRole(newRole)).thenReturn(true);
        
        // Given: Save succeeds
        when(virtualGroupRoleRepository.save(any(VirtualGroupRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Bind new role
        virtualGroupRoleService.bindRole(virtualGroupId, newRoleId);
        
        // Then: Old binding should be deleted
        verify(virtualGroupRoleRepository).delete(existingBinding);
        
        // Then: New binding should be saved
        verify(virtualGroupRoleRepository).save(argThat(binding -> 
                binding.getVirtualGroupId().equals(virtualGroupId) && 
                binding.getRoleId().equals(newRoleId)));
    }
    
    /**
     * Feature: permission-request-approval, Property 2: Single Role Per Virtual Group
     * After binding, virtual group should have exactly one role
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 2: Virtual group has at most one role after binding")
    void virtualGroupHasAtMostOneRoleAfterBinding(
            @ForAll("validVirtualGroupIds") String virtualGroupId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: Virtual group exists
        when(virtualGroupRepository.existsById(virtualGroupId)).thenReturn(true);
        VirtualGroup virtualGroup = VirtualGroup.builder()
                .id(virtualGroupId)
                .name("Test Group")
                .type("CUSTOM")
                .build();
        when(virtualGroupRepository.findById(virtualGroupId)).thenReturn(Optional.of(virtualGroup));
        
        // Given: Role exists and is BU_BOUNDED type
        Role role = createRole(roleId, RoleType.BU_BOUNDED);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        
        // Given: RoleHelper recognizes it as a business role
        when(roleHelper.isBusinessRole(role)).thenReturn(true);
        
        // Given: No existing binding
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.empty());
        
        // Given: Save succeeds and returns the saved entity
        when(virtualGroupRoleRepository.save(any(VirtualGroupRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Bind role
        virtualGroupRoleService.bindRole(virtualGroupId, roleId);
        
        // Then: Exactly one save should occur (not multiple)
        verify(virtualGroupRoleRepository, times(1)).save(any(VirtualGroupRole.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 2: Single Role Per Virtual Group
     * getBoundRole should return single role, not a list
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 2: getBoundRole returns single role")
    void getBoundRoleReturnsSingleRole(
            @ForAll("validVirtualGroupIds") String virtualGroupId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: Virtual group exists
        when(virtualGroupRepository.existsById(virtualGroupId)).thenReturn(true);
        
        // Given: Binding exists
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        
        // Given: Role exists
        Role role = createRole(roleId, RoleType.BU_BOUNDED);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        
        // When: Get bound role
        Optional<Role> boundRole = virtualGroupRoleService.getBoundRole(virtualGroupId);
        
        // Then: Should return exactly one role
        assertThat(boundRole).isPresent();
        assertThat(boundRole.get().getId()).isEqualTo(roleId);
    }
    
    // ==================== Helper Methods ====================
    
    private Role createRole(String roleId, RoleType type) {
        return Role.builder()
                .id(roleId)
                .name("Test Role " + roleId)
                .code("ROLE_" + roleId.toUpperCase().replace("-", "_"))
                .type(EntityTypeConverter.fromRoleType(type))
                .status("ACTIVE")
                .build();
    }
    
    // ==================== Data Generators ====================
    
    @Provide
    Arbitrary<String> validVirtualGroupIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "vg-" + s.toLowerCase());
    }
    
    @Provide
    Arbitrary<String> validRoleIds() {
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
    }
    
    @Provide
    Arbitrary<RoleType> allRoleTypes() {
        return Arbitraries.of(RoleType.values());
    }
}
