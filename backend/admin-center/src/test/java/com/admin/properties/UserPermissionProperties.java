package com.admin.properties;

import com.admin.entity.*;
import com.admin.enums.RoleType;
import com.platform.security.entity.User;
import com.platform.security.entity.Role;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.entity.VirtualGroupRole;
import com.platform.security.entity.UserBusinessUnit;
import com.admin.repository.*;
import com.admin.service.UserPermissionService;
import com.admin.util.EntityTypeConverter;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property tests for UserPermissionService
 * 
 * Properties tested:
 * - Property 12: BU-Bounded Role Activation
 * - Property 13: BU-Unbounded Role Immediate Effect
 * - Property 18: Unactivated BU-Bounded Role Reminder
 * 
 * **Validates: Requirements 3.4, 3.5, 10.3, 10.4, 11.2, 18.1, 18.2, 18.6, 18.7, 18.9**
 */
public class UserPermissionProperties {
    
    private VirtualGroupMemberRepository virtualGroupMemberRepository;
    private VirtualGroupRoleRepository virtualGroupRoleRepository;
    private UserBusinessUnitRepository userBusinessUnitRepository;
    private RoleRepository roleRepository;
    private UserPreferenceRepository userPreferenceRepository;
    private BusinessUnitRepository businessUnitRepository;
    private com.admin.helper.RoleHelper roleHelper;
    private UserPermissionService userPermissionService;
    
    @BeforeTry
    void setUp() {
        virtualGroupMemberRepository = mock(VirtualGroupMemberRepository.class);
        virtualGroupRoleRepository = mock(VirtualGroupRoleRepository.class);
        userBusinessUnitRepository = mock(UserBusinessUnitRepository.class);
        roleRepository = mock(RoleRepository.class);
        userPreferenceRepository = mock(UserPreferenceRepository.class);
        businessUnitRepository = mock(BusinessUnitRepository.class);
        roleHelper = mock(com.admin.helper.RoleHelper.class);
        
        userPermissionService = new UserPermissionService(
                virtualGroupMemberRepository,
                virtualGroupRoleRepository,
                userBusinessUnitRepository,
                roleRepository,
                userPreferenceRepository,
                businessUnitRepository,
                roleHelper);
    }
    
    // ==================== Property 12: BU-Bounded Role Activation ====================
    
    /**
     * Feature: permission-request-approval, Property 12: BU-Bounded Role Activation
     * BU-Bounded role should only be effective in business units where user is a member
     * **Validates: Requirements 3.4, 10.4, 11.2**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 12: BU-Bounded role effective only in member BUs")
    void buBoundedRoleEffectiveOnlyInMemberBusinessUnits(
            @ForAll("validUserIds") String userId,
            @ForAll("validRoleIds") String roleId,
            @ForAll("validTargetIds") String memberBusinessUnitId,
            @ForAll("validTargetIds") String nonMemberBusinessUnitId) {
        
        Assume.that(!memberBusinessUnitId.equals(nonMemberBusinessUnitId));
        
        // Given: User has a BU-Bounded role through virtual group
        String virtualGroupId = "vg-" + UUID.randomUUID();
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        
        Role buBoundedRole = createRole(roleId, RoleType.BU_BOUNDED);
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buBoundedRole));
        
        // Given: User is member of one business unit but not the other
        when(userBusinessUnitRepository.existsByUserIdAndBusinessUnitId(userId, memberBusinessUnitId))
                .thenReturn(true);
        when(userBusinessUnitRepository.existsByUserIdAndBusinessUnitId(userId, nonMemberBusinessUnitId))
                .thenReturn(false);
        
        // When & Then: Role should be effective in member business unit
        boolean hasRoleInMemberBU = userPermissionService.hasRoleInBusinessUnit(userId, roleId, memberBusinessUnitId);
        assertThat(hasRoleInMemberBU).isTrue();
        
        // When & Then: Role should NOT be effective in non-member business unit
        boolean hasRoleInNonMemberBU = userPermissionService.hasRoleInBusinessUnit(userId, roleId, nonMemberBusinessUnitId);
        assertThat(hasRoleInNonMemberBU).isFalse();
    }

    
    /**
     * Feature: permission-request-approval, Property 12: BU-Bounded Role Activation
     * User with BU-Bounded role and no business unit membership should have no activated roles
     * **Validates: Requirements 3.4, 10.4, 11.2**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 12: BU-Bounded role not activated without BU membership")
    void buBoundedRoleNotActivatedWithoutBusinessUnitMembership(
            @ForAll("validUserIds") String userId,
            @ForAll("validRoleIds") String roleId,
            @ForAll("validTargetIds") String businessUnitId) {
        
        // Given: User has a BU-Bounded role through virtual group
        String virtualGroupId = "vg-" + UUID.randomUUID();
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        
        Role buBoundedRole = createRole(roleId, RoleType.BU_BOUNDED);
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buBoundedRole));
        
        // Given: User is NOT a member of any business unit
        when(userBusinessUnitRepository.existsByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(false);
        
        // When: Check if user has role in business unit
        boolean hasRole = userPermissionService.hasRoleInBusinessUnit(userId, roleId, businessUnitId);
        
        // Then: Role should NOT be effective
        assertThat(hasRole).isFalse();
    }
    
    // ==================== Property 13: BU-Unbounded Role Immediate Effect ====================
    
    /**
     * Feature: permission-request-approval, Property 13: BU-Unbounded Role Immediate Effect
     * BU-Unbounded role should be immediately effective regardless of business unit membership
     * **Validates: Requirements 3.5, 10.3**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 13: BU-Unbounded role immediately effective")
    void buUnboundedRoleImmediatelyEffective(
            @ForAll("validUserIds") String userId,
            @ForAll("validRoleIds") String roleId,
            @ForAll("validTargetIds") String anyBusinessUnitId) {
        
        // Given: User has a BU-Unbounded role through virtual group
        String virtualGroupId = "vg-" + UUID.randomUUID();
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        
        Role buUnboundedRole = createRole(roleId, RoleType.BU_UNBOUNDED);
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buUnboundedRole));
        
        // Given: User is NOT a member of any business unit (doesn't matter for BU-Unbounded)
        when(userBusinessUnitRepository.existsByUserIdAndBusinessUnitId(userId, anyBusinessUnitId))
                .thenReturn(false);
        
        // When: Check if user has role
        boolean hasRole = userPermissionService.hasRoleInBusinessUnit(userId, roleId, anyBusinessUnitId);
        
        // Then: Role should be effective immediately (regardless of BU membership)
        assertThat(hasRole).isTrue();
    }
    
    /**
     * Feature: permission-request-approval, Property 13: BU-Unbounded Role Immediate Effect
     * Getting BU-Unbounded roles should return all such roles user has
     * **Validates: Requirements 3.5, 10.3**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 13: Get BU-Unbounded roles returns all such roles")
    void getBuUnboundedRolesReturnsAllSuchRoles(
            @ForAll("validUserIds") String userId,
            @ForAll("validRoleIdLists") List<String> unboundedRoleIds,
            @ForAll("validRoleIdLists") List<String> boundedRoleIds) {
        
        // Ensure no overlap between role ID lists
        Set<String> unboundedSet = new HashSet<>(unboundedRoleIds);
        boundedRoleIds = boundedRoleIds.stream()
                .filter(id -> !unboundedSet.contains(id))
                .toList();
        
        // Given: User has multiple virtual groups with different role types
        List<String> virtualGroupIds = new ArrayList<>();
        List<Role> allRoles = new ArrayList<>();
        
        // Setup BU-Unbounded roles
        for (int i = 0; i < unboundedRoleIds.size(); i++) {
            String vgId = "vg-unbounded-" + i;
            virtualGroupIds.add(vgId);
            
            VirtualGroupRole binding = VirtualGroupRole.builder()
                    .id(UUID.randomUUID().toString())
                    .virtualGroupId(vgId)
                    .roleId(unboundedRoleIds.get(i))
                    .build();
            when(virtualGroupRoleRepository.findByVirtualGroupId(vgId))
                    .thenReturn(Optional.of(binding));
            
            allRoles.add(createRole(unboundedRoleIds.get(i), RoleType.BU_UNBOUNDED));
        }
        
        // Setup BU-Bounded roles
        for (int i = 0; i < boundedRoleIds.size(); i++) {
            String vgId = "vg-bounded-" + i;
            virtualGroupIds.add(vgId);
            
            VirtualGroupRole binding = VirtualGroupRole.builder()
                    .id(UUID.randomUUID().toString())
                    .virtualGroupId(vgId)
                    .roleId(boundedRoleIds.get(i))
                    .build();
            when(virtualGroupRoleRepository.findByVirtualGroupId(vgId))
                    .thenReturn(Optional.of(binding));
            
            allRoles.add(createRole(boundedRoleIds.get(i), RoleType.BU_BOUNDED));
        }
        
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(virtualGroupIds);
        
        Set<String> allRoleIds = new HashSet<>();
        allRoleIds.addAll(unboundedRoleIds);
        allRoleIds.addAll(boundedRoleIds);
        when(roleRepository.findAllById(allRoleIds)).thenReturn(allRoles);
        
        // When: Get BU-Unbounded roles
        List<Role> result = userPermissionService.getUserBuUnboundedRoles(userId);
        
        // Then: Should return only BU-Unbounded roles
        assertThat(result).hasSize(unboundedRoleIds.size());
        assertThat(result).allMatch(role -> EntityTypeConverter.fromRoleType(RoleType.BU_UNBOUNDED).equals(role.getType()));
    }

    
    // ==================== Property 18: Unactivated BU-Bounded Role Reminder ====================
    
    /**
     * Feature: permission-request-approval, Property 18: Unactivated BU-Bounded Role Reminder
     * User with BU-Bounded role but no BU membership should see reminder
     * **Validates: Requirements 18.1, 18.2, 18.6**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 18: Show reminder for unactivated BU-Bounded roles")
    void showReminderForUnactivatedBuBoundedRoles(
            @ForAll("validUserIds") String userId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: User has a BU-Bounded role through virtual group
        String virtualGroupId = "vg-" + UUID.randomUUID();
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        
        Role buBoundedRole = createRole(roleId, RoleType.BU_BOUNDED);
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buBoundedRole));
        
        // Given: User is NOT a member of any business unit
        when(userBusinessUnitRepository.countByUserId(userId)).thenReturn(0L);
        
        // Given: User has NOT set "don't remind" preference
        when(userPreferenceRepository.findByUserIdAndPreferenceKey(userId, UserPreference.KEY_DONT_REMIND_BU_APPLICATION))
                .thenReturn(Optional.empty());
        
        // When: Check if should show reminder
        boolean shouldShow = userPermissionService.shouldShowBuApplicationReminder(userId);
        
        // Then: Should show reminder
        assertThat(shouldShow).isTrue();
    }
    
    /**
     * Feature: permission-request-approval, Property 18: Unactivated BU-Bounded Role Reminder
     * User with "don't remind" preference should not see reminder
     * **Validates: Requirements 18.7**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 18: Don't show reminder when preference set")
    void dontShowReminderWhenPreferenceSet(
            @ForAll("validUserIds") String userId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: User has a BU-Bounded role through virtual group
        String virtualGroupId = "vg-" + UUID.randomUUID();
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        
        Role buBoundedRole = createRole(roleId, RoleType.BU_BOUNDED);
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buBoundedRole));
        
        // Given: User is NOT a member of any business unit
        when(userBusinessUnitRepository.countByUserId(userId)).thenReturn(0L);
        
        // Given: User HAS set "don't remind" preference
        UserPreference preference = UserPreference.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .preferenceKey(UserPreference.KEY_DONT_REMIND_BU_APPLICATION)
                .preferenceValue("true")
                .build();
        when(userPreferenceRepository.findByUserIdAndPreferenceKey(userId, UserPreference.KEY_DONT_REMIND_BU_APPLICATION))
                .thenReturn(Optional.of(preference));
        
        // When: Check if should show reminder
        boolean shouldShow = userPermissionService.shouldShowBuApplicationReminder(userId);
        
        // Then: Should NOT show reminder
        assertThat(shouldShow).isFalse();
    }
    
    /**
     * Feature: permission-request-approval, Property 18: Unactivated BU-Bounded Role Reminder
     * User with BU membership should not see reminder (roles are activated)
     * **Validates: Requirements 18.9**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 18: No reminder when BU-Bounded roles are activated")
    void noReminderWhenBuBoundedRolesActivated(
            @ForAll("validUserIds") String userId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: User has a BU-Bounded role through virtual group
        String virtualGroupId = "vg-" + UUID.randomUUID();
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        
        Role buBoundedRole = createRole(roleId, RoleType.BU_BOUNDED);
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buBoundedRole));
        
        // Given: User IS a member of at least one business unit (role is activated)
        when(userBusinessUnitRepository.countByUserId(userId)).thenReturn(1L);
        
        // When: Check if should show reminder
        boolean shouldShow = userPermissionService.shouldShowBuApplicationReminder(userId);
        
        // Then: Should NOT show reminder (role is activated)
        assertThat(shouldShow).isFalse();
    }
    
    /**
     * Feature: permission-request-approval, Property 18: Unactivated BU-Bounded Role Reminder
     * User with only BU-Unbounded roles should not see reminder
     * **Validates: Requirements 18.1, 18.2**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 18: No reminder for BU-Unbounded only users")
    void noReminderForBuUnboundedOnlyUsers(
            @ForAll("validUserIds") String userId,
            @ForAll("validRoleIds") String roleId) {
        
        // Given: User has only BU-Unbounded role through virtual group
        String virtualGroupId = "vg-" + UUID.randomUUID();
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(userId))
                .thenReturn(List.of(virtualGroupId));
        
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(roleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId))
                .thenReturn(Optional.of(binding));
        
        Role buUnboundedRole = createRole(roleId, RoleType.BU_UNBOUNDED);
        when(roleRepository.findAllById(Set.of(roleId))).thenReturn(List.of(buUnboundedRole));
        
        // Given: User is NOT a member of any business unit (doesn't matter for BU-Unbounded)
        when(userBusinessUnitRepository.countByUserId(userId)).thenReturn(0L);
        
        // When: Check if should show reminder
        boolean shouldShow = userPermissionService.shouldShowBuApplicationReminder(userId);
        
        // Then: Should NOT show reminder (no BU-Bounded roles)
        assertThat(shouldShow).isFalse();
    }
    
    /**
     * Feature: permission-request-approval, Property 18: Unactivated BU-Bounded Role Reminder
     * Setting "don't remind" preference should persist
     * **Validates: Requirements 18.7**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 18: Setting dont remind preference persists")
    void settingDontRemindPreferencePersists(
            @ForAll("validUserIds") String userId) {
        
        // Given: No existing preference
        when(userPreferenceRepository.findByUserIdAndPreferenceKey(userId, UserPreference.KEY_DONT_REMIND_BU_APPLICATION))
                .thenReturn(Optional.empty());
        when(userPreferenceRepository.save(any(UserPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Set "don't remind" preference
        userPermissionService.setDontRemindPreference(userId, true);
        
        // Then: Preference should be saved
        verify(userPreferenceRepository).save(argThat(pref ->
                pref.getUserId().equals(userId) &&
                pref.getPreferenceKey().equals(UserPreference.KEY_DONT_REMIND_BU_APPLICATION) &&
                "true".equals(pref.getPreferenceValue())));
    }
    
    /**
     * Feature: permission-request-approval, Property 18: Unactivated BU-Bounded Role Reminder
     * Updating existing "don't remind" preference should work
     * **Validates: Requirements 18.7**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 18: Updating dont remind preference works")
    void updatingDontRemindPreferenceWorks(
            @ForAll("validUserIds") String userId) {
        
        // Given: Existing preference set to true
        UserPreference existingPreference = UserPreference.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .preferenceKey(UserPreference.KEY_DONT_REMIND_BU_APPLICATION)
                .preferenceValue("true")
                .createdAt(Instant.now())
                .build();
        when(userPreferenceRepository.findByUserIdAndPreferenceKey(userId, UserPreference.KEY_DONT_REMIND_BU_APPLICATION))
                .thenReturn(Optional.of(existingPreference));
        when(userPreferenceRepository.save(any(UserPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Update preference to false
        userPermissionService.setDontRemindPreference(userId, false);
        
        // Then: Preference should be updated
        verify(userPreferenceRepository).save(argThat(pref ->
                pref.getUserId().equals(userId) &&
                "false".equals(pref.getPreferenceValue())));
    }
    
    // ==================== Helper Methods ====================
    
    private Role createRole(String id, RoleType type) {
        return Role.builder()
                .id(id)
                .name("Role " + id.substring(0, 8))
                .code("ROLE_" + id.substring(0, 8).toUpperCase())
                .type(EntityTypeConverter.fromRoleType(type))
                .status("ACTIVE")
                .build();
    }
    
    // ==================== Data Generators ====================
    
    @Provide
    Arbitrary<String> validUserIds() {
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
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
    
    @Provide
    Arbitrary<List<String>> validRoleIdLists() {
        return Arbitraries.create(UUID::randomUUID)
                .map(UUID::toString)
                .list()
                .ofMinSize(1)
                .ofMaxSize(3);
    }
}
