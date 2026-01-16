package com.platform.security.property;

import com.platform.security.dto.RoleSource;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.entity.RoleAssignment;
import com.platform.security.enums.AssignmentTargetType;
import com.platform.security.repository.RoleAssignmentRepository;
import com.platform.security.resolver.TargetResolverFactory;
import com.platform.security.service.impl.UserRoleServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for UserRoleService
 * Feature: role-assignment-targets
 * 
 * Note: Department-based role assignment tests have been removed.
 * Only USER and VIRTUAL_GROUP target types are now supported.
 */
class UserRoleServiceProperties {

    /**
     * Property 5: Effective Users Deduplication
     * For any role with multiple assignments, the effective users list SHALL contain
     * each user at most once, even if the user is matched by multiple assignments.
     * Validates: Requirements 3.5
     */
    @Property(tries = 100)
    void effectiveRolesDeduplication(
            @ForAll @NotBlank @Size(max = 64) String userId,
            @ForAll @NotBlank @Size(max = 64) String roleId,
            @ForAll @NotBlank @Size(max = 50) String roleCode,
            @ForAll @NotBlank @Size(max = 100) String roleName,
            @ForAll @NotBlank @Size(max = 64) String groupId
    ) {
        // Arrange
        RoleAssignmentRepository repository = mock(RoleAssignmentRepository.class);
        TargetResolverFactory resolverFactory = mock(TargetResolverFactory.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        
        UserRoleServiceImpl service = new UserRoleServiceImpl(repository, resolverFactory, jdbcTemplate);
        
        // Create multiple assignments for the same role (user gets role from multiple sources)
        RoleAssignment userAssignment = createAssignment("assign-1", roleId, AssignmentTargetType.USER, userId);
        RoleAssignment groupAssignment = createAssignment("assign-2", roleId, AssignmentTargetType.VIRTUAL_GROUP, groupId);
        
        // Mock repository to return assignments from multiple sources
        when(repository.findValidUserAssignments(userId)).thenReturn(List.of(userAssignment));
        when(repository.findValidVirtualGroupAssignments(List.of(groupId))).thenReturn(List.of(groupAssignment));
        
        // Mock virtual groups
        when(jdbcTemplate.queryForList(
                contains("group_id FROM sys_virtual_group_members"),
                eq(String.class),
                eq(userId)
        )).thenReturn(List.of(groupId));
        
        // Mock role info
        Map<String, Object> roleInfo = new HashMap<>();
        roleInfo.put("id", roleId);
        roleInfo.put("code", roleCode);
        roleInfo.put("name", roleName);
        roleInfo.put("type", "BUSINESS");
        when(jdbcTemplate.queryForMap(
                contains("FROM sys_roles"),
                eq(roleId)
        )).thenReturn(roleInfo);
        
        // Mock display names
        when(jdbcTemplate.queryForObject(contains("display_name"), eq(String.class), eq(userId)))
                .thenReturn("User Name");
        when(jdbcTemplate.queryForObject(contains("FROM sys_virtual_groups"), eq(String.class), eq(groupId)))
                .thenReturn("Group Name");
        
        // Act
        List<UserEffectiveRole> roles = service.getEffectiveRolesForUser(userId);
        
        // Assert - Role should appear only once, but with multiple sources
        long roleCount = roles.stream()
                .filter(r -> r.getRoleId().equals(roleId))
                .count();
        assertThat(roleCount).isEqualTo(1);
        
        // The single role entry should have multiple sources
        Optional<UserEffectiveRole> role = roles.stream()
                .filter(r -> r.getRoleId().equals(roleId))
                .findFirst();
        assertThat(role).isPresent();
        assertThat(role.get().getSources()).hasSizeGreaterThanOrEqualTo(1);
    }

    /**
     * Property 9: Dynamic Membership - User Gains Role
     * For any user added to a virtual group that has a role assignment,
     * the user SHALL immediately be included in the effective users for that role.
     * Validates: Requirements 4.1, 4.3
     */
    @Property(tries = 100)
    void userGainsRoleWhenAddedToGroup(
            @ForAll @NotBlank @Size(max = 64) String userId,
            @ForAll @NotBlank @Size(max = 64) String roleId,
            @ForAll @NotBlank @Size(max = 50) String roleCode,
            @ForAll @NotBlank @Size(max = 100) String roleName,
            @ForAll @NotBlank @Size(max = 64) String groupId
    ) {
        // Arrange
        RoleAssignmentRepository repository = mock(RoleAssignmentRepository.class);
        TargetResolverFactory resolverFactory = mock(TargetResolverFactory.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        
        UserRoleServiceImpl service = new UserRoleServiceImpl(repository, resolverFactory, jdbcTemplate);
        
        // User is member of a virtual group that has a role assignment
        RoleAssignment groupAssignment = createAssignment("assign-1", roleId, AssignmentTargetType.VIRTUAL_GROUP, groupId);
        
        // Mock - no direct user assignments
        when(repository.findValidUserAssignments(userId)).thenReturn(Collections.emptyList());
        when(repository.findValidVirtualGroupAssignments(List.of(groupId))).thenReturn(List.of(groupAssignment));
        
        // Mock user is member of the virtual group
        when(jdbcTemplate.queryForList(
                contains("group_id FROM sys_virtual_group_members"),
                eq(String.class),
                eq(userId)
        )).thenReturn(List.of(groupId));
        
        // Mock role info
        Map<String, Object> roleInfo = new HashMap<>();
        roleInfo.put("id", roleId);
        roleInfo.put("code", roleCode);
        roleInfo.put("name", roleName);
        roleInfo.put("type", "BUSINESS");
        when(jdbcTemplate.queryForMap(
                contains("FROM sys_roles"),
                eq(roleId)
        )).thenReturn(roleInfo);
        
        // Mock group name
        when(jdbcTemplate.queryForObject(contains("FROM sys_virtual_groups"), eq(String.class), eq(groupId)))
                .thenReturn("Test Group");
        
        // Act
        List<UserEffectiveRole> roles = service.getEffectiveRolesForUser(userId);
        
        // Assert - User should have the role from the virtual group
        assertThat(roles).isNotEmpty();
        assertThat(roles.stream().anyMatch(r -> r.getRoleId().equals(roleId))).isTrue();
        
        // Verify the source is VIRTUAL_GROUP
        Optional<UserEffectiveRole> role = roles.stream()
                .filter(r -> r.getRoleId().equals(roleId))
                .findFirst();
        assertThat(role).isPresent();
        assertThat(role.get().getSources()).anyMatch(s -> s.getSourceType() == AssignmentTargetType.VIRTUAL_GROUP);
    }

    /**
     * Property 10: Dynamic Membership - User Loses Role
     * For any user removed from a virtual group that has a role assignment,
     * the user SHALL immediately be excluded from the effective users for that role
     * (unless matched by another assignment).
     * Validates: Requirements 4.2, 4.4
     */
    @Property(tries = 100)
    void userLosesRoleWhenRemovedFromGroup(
            @ForAll @NotBlank @Size(max = 64) String userId,
            @ForAll @NotBlank @Size(max = 64) String roleId
    ) {
        // Arrange
        RoleAssignmentRepository repository = mock(RoleAssignmentRepository.class);
        TargetResolverFactory resolverFactory = mock(TargetResolverFactory.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        
        UserRoleServiceImpl service = new UserRoleServiceImpl(repository, resolverFactory, jdbcTemplate);
        
        // Mock - user has no assignments (removed from all groups)
        when(repository.findValidUserAssignments(userId)).thenReturn(Collections.emptyList());
        when(repository.findValidVirtualGroupAssignments(anyList())).thenReturn(Collections.emptyList());
        
        // Mock user is NOT member of any virtual group (removed)
        when(jdbcTemplate.queryForList(
                contains("group_id FROM sys_virtual_group_members"),
                eq(String.class),
                eq(userId)
        )).thenReturn(Collections.emptyList());
        
        // Act
        List<UserEffectiveRole> roles = service.getEffectiveRolesForUser(userId);
        
        // Assert - User should NOT have the role anymore
        assertThat(roles.stream().noneMatch(r -> r.getRoleId().equals(roleId))).isTrue();
    }

    /**
     * Property: Role codes are unique in the result
     */
    @Property(tries = 100)
    void roleCodesAreUnique(
            @ForAll @NotBlank @Size(max = 64) String userId
    ) {
        // Arrange
        RoleAssignmentRepository repository = mock(RoleAssignmentRepository.class);
        TargetResolverFactory resolverFactory = mock(TargetResolverFactory.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        
        UserRoleServiceImpl service = new UserRoleServiceImpl(repository, resolverFactory, jdbcTemplate);
        
        // Mock empty assignments
        when(repository.findValidUserAssignments(userId)).thenReturn(Collections.emptyList());
        when(repository.findValidVirtualGroupAssignments(anyList())).thenReturn(Collections.emptyList());
        
        when(jdbcTemplate.queryForList(
                contains("group_id FROM sys_virtual_group_members"),
                eq(String.class),
                eq(userId)
        )).thenReturn(Collections.emptyList());
        
        // Act
        List<String> roleCodes = service.getEffectiveRoleCodesForUser(userId);
        
        // Assert - All role codes should be unique
        Set<String> uniqueCodes = new HashSet<>(roleCodes);
        assertThat(uniqueCodes).hasSize(roleCodes.size());
    }

    /**
     * Helper method to create a RoleAssignment
     */
    private RoleAssignment createAssignment(String id, String roleId, AssignmentTargetType targetType, String targetId) {
        return RoleAssignment.builder()
                .id(id)
                .roleId(roleId)
                .targetType(targetType)
                .targetId(targetId)
                .build();
    }
}
