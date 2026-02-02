package com.admin.properties;

import com.admin.entity.*;
import com.admin.enums.*;
import com.admin.util.EntityTypeConverter;
import com.platform.security.entity.User;
import com.platform.security.entity.Role;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.entity.VirtualGroupRole;
import com.platform.security.entity.UserBusinessUnit;
import com.admin.repository.*;
import com.admin.service.*;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property 13: BU-Unbounded Role Immediate Effect
 * *For any* user who receives a BU_UNBOUNDED role, the role shall be immediately effective.
 * 
 * This test validates that BU_UNBOUNDED roles are immediately effective upon joining
 * a virtual group, without requiring business unit membership.
 * 
 * **Validates: Requirements 3.5, 10.3**
 */
public class BuUnboundedRoleImmediateEffectProperties {
    
    private VirtualGroupMemberRepository virtualGroupMemberRepository;
    private VirtualGroupRoleRepository virtualGroupRoleRepository;
    private UserBusinessUnitRepository userBusinessUnitRepository;
    private RoleRepository roleRepository;
    private UserPermissionService userPermissionService;
    private com.admin.helper.RoleHelper roleHelper;
    
    @BeforeTry
    void setUp() {
        virtualGroupMemberRepository = mock(VirtualGroupMemberRepository.class);
        virtualGroupRoleRepository = mock(VirtualGroupRoleRepository.class);
        userBusinessUnitRepository = mock(UserBusinessUnitRepository.class);
        roleRepository = mock(RoleRepository.class);
        roleHelper = mock(com.admin.helper.RoleHelper.class);
        
        userPermissionService = new UserPermissionService(
                virtualGroupMemberRepository,
                virtualGroupRoleRepository,
                userBusinessUnitRepository,
                roleRepository,
                mock(UserPreferenceRepository.class),
                mock(BusinessUnitRepository.class),
                roleHelper);
    }
    
    // ==================== Property 13: BU-Unbounded Role Immediate Effect ====================
    
    /**
     * Feature: permission-request-approval, Property 13: BU-Unbounded Role Immediate Effect
     * BU_UNBOUNDED role should be immediately effective without business unit membership
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 13: BU_UNBOUNDED role is immediately effective")
    void buUnboundedRoleIsImmediatelyEffective(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: User is member of virtual group
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        // Given: Virtual group has BU_UNBOUNDED role
        Role buUnboundedRole = createRole(roleId, RoleType.BU_UNBOUNDED);
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buUnboundedRole));
        
        // Given: User is NOT member of any business unit
        when(userBusinessUnitRepository.countByUserId(userId)).thenReturn(0L);
        
        // When: Get user's BU-Unbounded roles
        List<Role> buUnboundedRoles = userPermissionService.getUserBuUnboundedRoles(userId);
        
        // Then: The BU_UNBOUNDED role should be in the list (immediately effective)
        assertThat(buUnboundedRoles).hasSize(1);
        assertThat(buUnboundedRoles.get(0).getId()).isEqualTo(roleId);
        assertThat(buUnboundedRoles.get(0).getType()).isEqualTo(EntityTypeConverter.fromRoleType(RoleType.BU_UNBOUNDED));
    }
    
    /**
     * Feature: permission-request-approval, Property 13: BU-Unbounded Role Immediate Effect
     * BU_UNBOUNDED role should NOT appear in unactivated roles list
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 13: BU_UNBOUNDED role not in unactivated list")
    void buUnboundedRoleNotInUnactivatedList(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: User is member of virtual group
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        // Given: Virtual group has BU_UNBOUNDED role
        Role buUnboundedRole = createRole(roleId, RoleType.BU_UNBOUNDED);
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buUnboundedRole));
        
        // Given: User is NOT member of any business unit
        when(userBusinessUnitRepository.countByUserId(userId)).thenReturn(0L);
        
        // When: Get unactivated BU-Bounded roles
        List<Role> unactivatedRoles = userPermissionService.getUnactivatedBuBoundedRoles(userId);
        
        // Then: The BU_UNBOUNDED role should NOT be in the unactivated list
        // (BU_UNBOUNDED roles are always effective, so they're never "unactivated")
        assertThat(unactivatedRoles).isEmpty();
    }
    
    /**
     * Feature: permission-request-approval, Property 13: BU-Unbounded Role Immediate Effect
     * BU_UNBOUNDED role should be effective regardless of business unit membership
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 13: BU_UNBOUNDED role effective regardless of BU membership")
    void buUnboundedRoleEffectiveRegardlessOfBuMembership(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validRoleIds") String roleId,
            @ForAll boolean hasBuMembership) {
        
        // Given: User is member of virtual group
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        // Given: Virtual group has BU_UNBOUNDED role
        Role buUnboundedRole = createRole(roleId, RoleType.BU_UNBOUNDED);
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buUnboundedRole));
        
        // Given: User may or may not be member of business unit
        when(userBusinessUnitRepository.countByUserId(userId)).thenReturn(hasBuMembership ? 1L : 0L);
        
        // When: Get user's BU-Unbounded roles
        List<Role> buUnboundedRoles = userPermissionService.getUserBuUnboundedRoles(userId);
        
        // Then: The BU_UNBOUNDED role should always be in the list (regardless of BU membership)
        assertThat(buUnboundedRoles).hasSize(1);
        assertThat(buUnboundedRoles.get(0).getId()).isEqualTo(roleId);
    }
    
    /**
     * Feature: permission-request-approval, Property 13: BU-Unbounded Role Immediate Effect
     * User with no virtual group membership should have no BU_UNBOUNDED roles
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 13: No VG membership means no BU_UNBOUNDED roles")
    void noVgMembershipMeansNoBuUnboundedRoles(
            @ForAll("validUserIds") String userId) {
        
        // Given: User is NOT member of any virtual group
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(Collections.emptyList());
        
        // When: Get user's BU-Unbounded roles
        List<Role> buUnboundedRoles = userPermissionService.getUserBuUnboundedRoles(userId);
        
        // Then: Should be empty
        assertThat(buUnboundedRoles).isEmpty();
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
    Arbitrary<String> validUserIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "user-" + s.toLowerCase());
    }
    
    @Provide
    Arbitrary<String> validTargetIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "target-" + s.toLowerCase());
    }
    
    @Provide
    Arbitrary<String> validRoleIds() {
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
    }
}
