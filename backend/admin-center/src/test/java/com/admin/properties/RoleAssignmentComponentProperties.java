package com.admin.properties;

import com.admin.component.RoleAssignmentComponent;
import com.admin.dto.request.CreateAssignmentRequest;
import com.admin.dto.response.EffectiveUserResponse;
import com.admin.exception.AdminBusinessException;
import com.admin.exception.RoleNotFoundException;
import com.admin.repository.RoleRepository;
import com.platform.security.dto.ResolvedUser;
import com.platform.security.entity.RoleAssignment;
import com.platform.security.enums.AssignmentTargetType;
import com.platform.security.repository.RoleAssignmentRepository;
import com.platform.security.resolver.TargetResolver;
import com.platform.security.resolver.TargetResolverFactory;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.reset;

/**
 * Property tests for RoleAssignmentComponent
 * 
 * Tests the following properties:
 * - Property 6: Assignment Uniqueness
 * - Property 7: Target Validation
 * - Property 8: Assignment Deletion Removes Role Access
 * 
 * Validates: Requirements 2.2, 2.3, 2.6
 */
public class RoleAssignmentComponentProperties {
    
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;
    
    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private TargetResolverFactory targetResolverFactory;
    
    @Mock
    private TargetResolver targetResolver;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    private RoleAssignmentComponent component;
    
    @BeforeProperty
    void setUp() {
        MockitoAnnotations.openMocks(this);
        component = new RoleAssignmentComponent(
                roleAssignmentRepository,
                roleRepository,
                targetResolverFactory,
                jdbcTemplate
        );
    }
    
    // ==================== Property 6: Assignment Uniqueness ====================
    
    /**
     * Feature: role-assignment-targets, Property 6: Assignment Uniqueness
     * For any attempt to create an assignment with the same (role_id, target_type, target_id)
     * as an existing assignment, the system SHALL reject the request with a duplicate error.
     * 
     * Validates: Requirements 2.3
     */
    @Property(tries = 100)
    @Label("Feature: role-assignment-targets, Property 6: Assignment Uniqueness")
    void duplicateAssignmentShouldBeRejected(
            @ForAll("roleIds") String roleId,
            @ForAll("targetTypes") AssignmentTargetType targetType,
            @ForAll("targetIds") String targetId) {
        
        // Given: Role exists, target exists, but assignment already exists
        when(roleRepository.existsById(roleId)).thenReturn(true);
        when(targetResolverFactory.getResolver(targetType)).thenReturn(targetResolver);
        when(targetResolver.targetExists(targetId)).thenReturn(true);
        when(roleAssignmentRepository.existsByRoleIdAndTargetTypeAndTargetId(roleId, targetType, targetId))
                .thenReturn(true);
        
        CreateAssignmentRequest request = CreateAssignmentRequest.builder()
                .roleId(roleId)
                .targetType(targetType)
                .targetId(targetId)
                .build();
        
        // When & Then: Should throw duplicate error
        assertThatThrownBy(() -> component.createAssignment(request, "operator-001"))
                .isInstanceOf(AdminBusinessException.class)
                .satisfies(ex -> {
                    AdminBusinessException abe = (AdminBusinessException) ex;
                    assertThat(abe.getErrorCode()).isEqualTo("DUPLICATE_ASSIGNMENT");
                });
        
        // Verify no save was attempted
        verify(roleAssignmentRepository, never()).save(any());
    }
    
    /**
     * Feature: role-assignment-targets, Property 6: Assignment Uniqueness (positive case)
     * For any new assignment with unique (role_id, target_type, target_id),
     * the system SHALL successfully create the assignment.
     * 
     * Validates: Requirements 2.3
     */
    @Property(tries = 100)
    @Label("Feature: role-assignment-targets, Property 6: Unique assignment should be created")
    void uniqueAssignmentShouldBeCreated(
            @ForAll("roleIds") String roleId,
            @ForAll("targetTypes") AssignmentTargetType targetType,
            @ForAll("targetIds") String targetId) {
        
        // Reset mocks to avoid counting calls across property iterations
        reset(roleAssignmentRepository, roleRepository, targetResolverFactory, targetResolver);
        
        // Given: Role exists, target exists, no existing assignment
        when(roleRepository.existsById(roleId)).thenReturn(true);
        when(targetResolverFactory.getResolver(targetType)).thenReturn(targetResolver);
        when(targetResolver.targetExists(targetId)).thenReturn(true);
        when(roleAssignmentRepository.existsByRoleIdAndTargetTypeAndTargetId(roleId, targetType, targetId))
                .thenReturn(false);
        when(roleAssignmentRepository.save(any(RoleAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        CreateAssignmentRequest request = CreateAssignmentRequest.builder()
                .roleId(roleId)
                .targetType(targetType)
                .targetId(targetId)
                .build();
        
        // When
        RoleAssignment result = component.createAssignment(request, "operator-001");
        
        // Then: Assignment should be created with correct values
        assertThat(result).isNotNull();
        assertThat(result.getRoleId()).isEqualTo(roleId);
        assertThat(result.getTargetType()).isEqualTo(targetType);
        assertThat(result.getTargetId()).isEqualTo(targetId);
        assertThat(result.getId()).isNotNull();
        
        verify(roleAssignmentRepository).save(any(RoleAssignment.class));
    }
    
    // ==================== Property 7: Target Validation ====================
    
    /**
     * Feature: role-assignment-targets, Property 7: Target Validation
     * For any assignment creation request, if the target_id does not correspond to
     * an existing entity of the specified target_type, the system SHALL reject
     * the request with a not-found error.
     * 
     * Validates: Requirements 2.2
     */
    @Property(tries = 100)
    @Label("Feature: role-assignment-targets, Property 7: Non-existent target should be rejected")
    void nonExistentTargetShouldBeRejected(
            @ForAll("roleIds") String roleId,
            @ForAll("targetTypes") AssignmentTargetType targetType,
            @ForAll("targetIds") String targetId) {
        
        // Given: Role exists, but target does not exist
        when(roleRepository.existsById(roleId)).thenReturn(true);
        when(targetResolverFactory.getResolver(targetType)).thenReturn(targetResolver);
        when(targetResolver.targetExists(targetId)).thenReturn(false);
        
        CreateAssignmentRequest request = CreateAssignmentRequest.builder()
                .roleId(roleId)
                .targetType(targetType)
                .targetId(targetId)
                .build();
        
        // When & Then: Should throw target not found error
        assertThatThrownBy(() -> component.createAssignment(request, "operator-001"))
                .isInstanceOf(AdminBusinessException.class)
                .satisfies(ex -> {
                    AdminBusinessException abe = (AdminBusinessException) ex;
                    assertThat(abe.getErrorCode()).isEqualTo("TARGET_NOT_FOUND");
                });
        
        // Verify no duplicate check or save was attempted
        verify(roleAssignmentRepository, never()).existsByRoleIdAndTargetTypeAndTargetId(any(), any(), any());
        verify(roleAssignmentRepository, never()).save(any());
    }
    
    /**
     * Feature: role-assignment-targets, Property 7: Role Validation
     * For any assignment creation request, if the role_id does not correspond to
     * an existing role, the system SHALL reject the request with a not-found error.
     * 
     * Validates: Requirements 2.2
     */
    @Property(tries = 100)
    @Label("Feature: role-assignment-targets, Property 7: Non-existent role should be rejected")
    void nonExistentRoleShouldBeRejected(
            @ForAll("roleIds") String roleId,
            @ForAll("targetTypes") AssignmentTargetType targetType,
            @ForAll("targetIds") String targetId) {
        
        // Given: Role does not exist
        when(roleRepository.existsById(roleId)).thenReturn(false);
        
        CreateAssignmentRequest request = CreateAssignmentRequest.builder()
                .roleId(roleId)
                .targetType(targetType)
                .targetId(targetId)
                .build();
        
        // When & Then: Should throw role not found error
        assertThatThrownBy(() -> component.createAssignment(request, "operator-001"))
                .isInstanceOf(RoleNotFoundException.class);
        
        // Verify no target check, duplicate check, or save was attempted
        verify(targetResolverFactory, never()).getResolver(any());
        verify(roleAssignmentRepository, never()).existsByRoleIdAndTargetTypeAndTargetId(any(), any(), any());
        verify(roleAssignmentRepository, never()).save(any());
    }

    
    // ==================== Property 8: Assignment Deletion Removes Role Access ====================
    
    /**
     * Feature: role-assignment-targets, Property 8: Assignment Deletion Removes Role Access
     * For any assignment that is deleted, users who only had the role through that assignment
     * SHALL no longer have the role (unless granted through another assignment).
     * 
     * Validates: Requirements 2.6
     */
    @Property(tries = 100)
    @Label("Feature: role-assignment-targets, Property 8: Deleted assignment removes role access")
    void deletedAssignmentRemovesRoleAccess(
            @ForAll("roleIds") String roleId,
            @ForAll("targetTypes") AssignmentTargetType targetType,
            @ForAll("targetIds") String targetId,
            @ForAll("userIds") String userId) {
        
        String assignmentId = UUID.randomUUID().toString();
        
        // Given: An assignment exists with one user
        RoleAssignment assignment = RoleAssignment.builder()
                .id(assignmentId)
                .roleId(roleId)
                .targetType(targetType)
                .targetId(targetId)
                .assignedAt(LocalDateTime.now())
                .assignedBy("operator-001")
                .build();
        
        ResolvedUser resolvedUser = ResolvedUser.builder()
                .userId(userId)
                .username("user-" + userId)
                .displayName("User " + userId)
                .build();
        
        // Setup mocks for initial state (assignment exists)
        when(roleAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(roleAssignmentRepository.findByRoleId(roleId)).thenReturn(List.of(assignment));
        when(targetResolverFactory.getResolver(targetType)).thenReturn(targetResolver);
        when(targetResolver.resolveUsers(targetId)).thenReturn(List.of(resolvedUser));
        when(targetResolver.getTargetDisplayName(targetId)).thenReturn("Target " + targetId);
        
        // Verify user has role before deletion
        List<EffectiveUserResponse> usersBefore = component.getEffectiveUsers(roleId);
        assertThat(usersBefore).hasSize(1);
        assertThat(usersBefore.get(0).getUserId()).isEqualTo(userId);
        
        // When: Delete the assignment
        doAnswer(invocation -> {
            // After deletion, findByRoleId returns empty list
            when(roleAssignmentRepository.findByRoleId(roleId)).thenReturn(Collections.emptyList());
            return null;
        }).when(roleAssignmentRepository).delete(assignment);
        
        component.deleteAssignment(assignmentId, "operator-001");
        
        // Then: User should no longer have the role
        List<EffectiveUserResponse> usersAfter = component.getEffectiveUsers(roleId);
        assertThat(usersAfter).isEmpty();
    }
    
    /**
     * Feature: role-assignment-targets, Property 8: Multiple assignments preserve access
     * For any user with role access through multiple assignments, deleting one assignment
     * SHALL NOT remove the role if the user still has access through another assignment.
     * 
     * Validates: Requirements 2.6
     */
    @Property(tries = 100)
    @Label("Feature: role-assignment-targets, Property 8: Multiple assignments preserve access")
    void multipleAssignmentsPreserveAccess(
            @ForAll("roleIds") String roleId,
            @ForAll("targetIds") String targetId1,
            @ForAll("targetIds") String targetId2,
            @ForAll("userIds") String userId) {
        
        // Skip if targets are the same
        Assume.that(!targetId1.equals(targetId2));
        
        String assignmentId1 = UUID.randomUUID().toString();
        String assignmentId2 = UUID.randomUUID().toString();
        
        // Given: Two assignments exist, both granting role to the same user
        RoleAssignment assignment1 = RoleAssignment.builder()
                .id(assignmentId1)
                .roleId(roleId)
                .targetType(AssignmentTargetType.USER)
                .targetId(targetId1)
                .assignedAt(LocalDateTime.now())
                .assignedBy("operator-001")
                .build();
        
        RoleAssignment assignment2 = RoleAssignment.builder()
                .id(assignmentId2)
                .roleId(roleId)
                .targetType(AssignmentTargetType.USER)
                .targetId(targetId2)
                .assignedAt(LocalDateTime.now())
                .assignedBy("operator-001")
                .build();
        
        ResolvedUser resolvedUser = ResolvedUser.builder()
                .userId(userId)
                .username("user-" + userId)
                .displayName("User " + userId)
                .build();
        
        // Setup mocks - both assignments resolve to the same user
        when(roleAssignmentRepository.findById(assignmentId1)).thenReturn(Optional.of(assignment1));
        when(roleAssignmentRepository.findByRoleId(roleId)).thenReturn(List.of(assignment1, assignment2));
        when(targetResolverFactory.getResolver(AssignmentTargetType.USER)).thenReturn(targetResolver);
        when(targetResolver.resolveUsers(targetId1)).thenReturn(List.of(resolvedUser));
        when(targetResolver.resolveUsers(targetId2)).thenReturn(List.of(resolvedUser));
        when(targetResolver.getTargetDisplayName(any())).thenReturn("Target");
        
        // Verify user has role with 2 sources before deletion
        List<EffectiveUserResponse> usersBefore = component.getEffectiveUsers(roleId);
        assertThat(usersBefore).hasSize(1);
        assertThat(usersBefore.get(0).getSources()).hasSize(2);
        
        // When: Delete one assignment
        doAnswer(invocation -> {
            // After deletion, findByRoleId returns only the second assignment
            when(roleAssignmentRepository.findByRoleId(roleId)).thenReturn(List.of(assignment2));
            return null;
        }).when(roleAssignmentRepository).delete(assignment1);
        
        component.deleteAssignment(assignmentId1, "operator-001");
        
        // Then: User should still have the role (through the other assignment)
        List<EffectiveUserResponse> usersAfter = component.getEffectiveUsers(roleId);
        assertThat(usersAfter).hasSize(1);
        assertThat(usersAfter.get(0).getUserId()).isEqualTo(userId);
        assertThat(usersAfter.get(0).getSources()).hasSize(1);
    }
    
    /**
     * Feature: role-assignment-targets, Property 8: Delete non-existent assignment fails
     * For any attempt to delete an assignment that does not exist,
     * the system SHALL reject the request with a not-found error.
     * 
     * Validates: Requirements 2.6
     */
    @Property(tries = 100)
    @Label("Feature: role-assignment-targets, Property 8: Delete non-existent assignment fails")
    void deleteNonExistentAssignmentShouldFail(
            @ForAll("assignmentIds") String assignmentId) {
        
        // Given: Assignment does not exist
        when(roleAssignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());
        
        // When & Then: Should throw not found error
        assertThatThrownBy(() -> component.deleteAssignment(assignmentId, "operator-001"))
                .isInstanceOf(AdminBusinessException.class)
                .satisfies(ex -> {
                    AdminBusinessException abe = (AdminBusinessException) ex;
                    assertThat(abe.getErrorCode()).isEqualTo("ASSIGNMENT_NOT_FOUND");
                });
        
        // Verify no delete was attempted
        verify(roleAssignmentRepository, never()).delete(any());
    }
    
    // ==================== Arbitrary Providers ====================
    
    @Provide
    Arbitrary<String> roleIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "ROLE_" + s.toUpperCase());
    }
    
    @Provide
    Arbitrary<String> targetIds() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }
    
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.create(() -> "user-" + UUID.randomUUID().toString().substring(0, 8));
    }
    
    @Provide
    Arbitrary<String> assignmentIds() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }
    
    @Provide
    Arbitrary<AssignmentTargetType> targetTypes() {
        return Arbitraries.of(AssignmentTargetType.values());
    }
}
