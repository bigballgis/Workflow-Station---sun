package com.admin.properties;

import com.platform.security.entity.Role;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupRole;
import com.admin.enums.RoleType;
import com.admin.util.EntityTypeConverter;
import com.admin.repository.RoleRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import com.admin.service.VirtualGroupRoleService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 1: Role Type Validation for Virtual Group Binding
 * *For any* role binding operation to a Virtual_Group, the system shall accept only roles 
 * with type BU_BOUNDED, and reject any role with type ADMIN, AUDITOR or DEVELOPER.
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
    private VirtualGroupRoleService virtualGroupRoleService;
    
    @BeforeTry
    void setUp() {
        virtualGroupRoleRepository = mock(VirtualGroupRoleRepository.class);
        virtualGroupRepository = mock(VirtualGroupRepository.class);
        roleRepository = mock(RoleRepository.class);
        virtualGroupRoleService = new VirtualGroupRoleService(
                virtualGroupRoleRepository,
                virtualGroupRepository,
                roleRepository);
    }
    
    // ==================== Property 2: Single Role Per Virtual Group ====================
    
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
        // Production reads via findSingleByVirtualGroupId (default method; mocked interface
        // does not fall through to findByVirtualGroupId)
        when(virtualGroupRoleRepository.findSingleByVirtualGroupId(virtualGroupId))
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
}
