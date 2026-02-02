package com.admin.properties;

import com.admin.entity.*;
import com.admin.enums.*;
import com.platform.security.entity.User;
import com.platform.security.entity.Role;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.entity.VirtualGroupRole;
import com.platform.security.entity.UserBusinessUnit;
import com.admin.repository.*;
import com.admin.service.*;
import com.admin.util.EntityTypeConverter;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property 12: BU-Bounded Role Activation
 * *For any* user with a BU_BOUNDED role, the role shall only be effective in Business_Units 
 * where the user is a member.
 * 
 * This test validates the two-stage activation flow:
 * 1. User joins virtual group -> gets BU_BOUNDED role (not yet effective)
 * 2. User joins business unit -> BU_BOUNDED role becomes effective in that business unit
 * 
 * **Validates: Requirements 3.4, 10.4, 11.2**
 */
public class BuBoundedRoleActivationProperties {
    
    private VirtualGroupMemberRepository virtualGroupMemberRepository;
    private VirtualGroupRoleRepository virtualGroupRoleRepository;
    private UserBusinessUnitRepository userBusinessUnitRepository;
    private RoleRepository roleRepository;
    private com.admin.helper.RoleHelper roleHelper;
    private UserPermissionService userPermissionService;
    
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
    
    // ==================== Property 12: BU-Bounded Role Activation ====================
    
    /**
     * Feature: permission-request-approval, Property 12: BU-Bounded Role Activation
     * User with BU_BOUNDED role but no business unit membership should have unactivated role
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 12: BU_BOUNDED role without BU membership is unactivated")
    void buBoundedRoleWithoutBuMembershipIsUnactivated(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: User is member of virtual group
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        // Given: Virtual group has BU_BOUNDED role
        Role buBoundedRole = createRole(roleId, RoleType.BU_BOUNDED);
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buBoundedRole));
        
        // Given: User is NOT member of any business unit
        when(userBusinessUnitRepository.countByUserId(userId)).thenReturn(0L);
        
        // When: Get unactivated BU-Bounded roles
        List<Role> unactivatedRoles = userPermissionService.getUnactivatedBuBoundedRoles(userId);
        
        // Then: The BU_BOUNDED role should be in the unactivated list
        assertThat(unactivatedRoles).hasSize(1);
        assertThat(unactivatedRoles.get(0).getId()).isEqualTo(roleId);
        assertThat(unactivatedRoles.get(0).getType()).isEqualTo(EntityTypeConverter.fromRoleType(RoleType.BU_BOUNDED));
    }
    
    /**
     * Feature: permission-request-approval, Property 12: BU-Bounded Role Activation
     * User with BU_BOUNDED role and business unit membership should have activated role
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 12: BU_BOUNDED role with BU membership is activated")
    void buBoundedRoleWithBuMembershipIsActivated(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: User is member of virtual group
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        // Given: Virtual group has BU_BOUNDED role
        Role buBoundedRole = createRole(roleId, RoleType.BU_BOUNDED);
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buBoundedRole));
        
        // Given: User IS member of a business unit
        when(userBusinessUnitRepository.countByUserId(userId)).thenReturn(1L);
        
        // When: Get unactivated BU-Bounded roles
        List<Role> unactivatedRoles = userPermissionService.getUnactivatedBuBoundedRoles(userId);
        
        // Then: The BU_BOUNDED role should NOT be in the unactivated list (it's activated)
        assertThat(unactivatedRoles).isEmpty();
    }
    
    /**
     * Feature: permission-request-approval, Property 12: BU-Bounded Role Activation
     * hasRoleInBusinessUnit should return true only when user has both role and BU membership
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 12: hasRoleInBusinessUnit requires both role and BU membership")
    void hasRoleInBusinessUnitRequiresBothRoleAndMembership(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: User is member of virtual group
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        // Given: Virtual group has BU_BOUNDED role
        Role buBoundedRole = createRole(roleId, RoleType.BU_BOUNDED);
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buBoundedRole));
        
        // Given: User IS member of the business unit
        when(userBusinessUnitRepository.existsByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(true);
        
        // When: Check if user has role in business unit
        boolean hasRole = userPermissionService.hasRoleInBusinessUnit(userId, roleId, businessUnitId);
        
        // Then: Should return true (user has both role and BU membership)
        assertThat(hasRole).isTrue();
    }
    
    /**
     * Feature: permission-request-approval, Property 12: BU-Bounded Role Activation
     * hasRoleInBusinessUnit should return false when user has role but no BU membership
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 12: hasRoleInBusinessUnit false without BU membership")
    void hasRoleInBusinessUnitFalseWithoutBuMembership(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: User is member of virtual group
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        // Given: Virtual group has BU_BOUNDED role
        Role buBoundedRole = createRole(roleId, RoleType.BU_BOUNDED);
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buBoundedRole));
        
        // Given: User is NOT member of the business unit
        when(userBusinessUnitRepository.existsByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(false);
        
        // When: Check if user has role in business unit
        boolean hasRole = userPermissionService.hasRoleInBusinessUnit(userId, roleId, businessUnitId);
        
        // Then: Should return false (user has role but no BU membership)
        assertThat(hasRole).isFalse();
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
