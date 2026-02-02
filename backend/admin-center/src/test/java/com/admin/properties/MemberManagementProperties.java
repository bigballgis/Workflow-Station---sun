package com.admin.properties;

import com.admin.entity.*;
import com.admin.enums.ApproverTargetType;
import com.admin.enums.MemberChangeType;
import com.admin.enums.PermissionRequestType;
import com.platform.security.entity.User;
import com.platform.security.entity.Role;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.entity.VirtualGroupRole;
import com.platform.security.entity.UserBusinessUnit;
import com.platform.security.entity.UserBusinessUnitRole;
import com.platform.security.model.UserStatus;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.*;
import com.admin.service.ApproverService;
import com.admin.service.MemberManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property tests for MemberManagementService
 * 
 * Properties tested:
 * - Property 6: Approval Immediate Effect
 * - Property 9: Member Removal Immediate Effect (旧版本)
 * - Property 10: Exit All Roles Removes User from Business Unit (旧版本)
 * - Property 11: Member Removal Immediate Effect (新版本)
 * - Property 15: Virtual Group Exit Revokes Role
 * - Property 16: Business Unit Exit Deactivates BU-Bounded Roles
 * 
 * **Validates: Requirements 10.1, 10.2, 10.3, 14.3, 14.4, 15.3, 15.4, 15.6, 15.7, 16.2, 16.3**
 */
public class MemberManagementProperties {
    
    private VirtualGroupMemberRepository virtualGroupMemberRepository;
    private VirtualGroupRepository virtualGroupRepository;
    private UserRepository userRepository;
    private UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    private UserBusinessUnitRepository userBusinessUnitRepository;
    private VirtualGroupRoleRepository virtualGroupRoleRepository;
    private MemberChangeLogRepository memberChangeLogRepository;
    private ApproverService approverService;
    private ObjectMapper objectMapper;
    private MemberManagementService memberManagementService;
    
    @BeforeTry
    void setUp() {
        virtualGroupMemberRepository = mock(VirtualGroupMemberRepository.class);
        virtualGroupRepository = mock(VirtualGroupRepository.class);
        userRepository = mock(UserRepository.class);
        userBusinessUnitRoleRepository = mock(UserBusinessUnitRoleRepository.class);
        userBusinessUnitRepository = mock(UserBusinessUnitRepository.class);
        virtualGroupRoleRepository = mock(VirtualGroupRoleRepository.class);
        memberChangeLogRepository = mock(MemberChangeLogRepository.class);
        approverService = mock(ApproverService.class);
        objectMapper = new ObjectMapper();
        
        memberManagementService = new MemberManagementService(
                virtualGroupMemberRepository,
                virtualGroupRepository,
                userRepository,
                userBusinessUnitRoleRepository,
                userBusinessUnitRepository,
                virtualGroupRoleRepository,
                memberChangeLogRepository,
                approverService,
                objectMapper);
    }
    
    // ==================== Property 6: Approval Immediate Effect ====================
    
    /**
     * Feature: permission-request-approval, Property 6: Approval Immediate Effect
     * Approved virtual group request should immediately add user to group
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 6: Approved VG request adds user immediately")
    void approvedVirtualGroupRequestAddsUserImmediately(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validUserIds") String operatorId) {
        
        // Given: User is not already a member
        when(virtualGroupMemberRepository.existsByGroupIdAndUserId(virtualGroupId, userId))
                .thenReturn(false);
        
        // Given: Virtual group exists
        VirtualGroup virtualGroup = createVirtualGroup(virtualGroupId);
        when(virtualGroupRepository.findById(virtualGroupId)).thenReturn(Optional.of(virtualGroup));
        
        // Given: User exists
        User user = createUser(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        // Given: Save succeeds
        when(virtualGroupMemberRepository.save(any(VirtualGroupMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Add user to virtual group
        memberManagementService.addUserToVirtualGroup(userId, virtualGroupId, operatorId);
        
        // Then: User should be added immediately
        verify(virtualGroupMemberRepository).save(argThat(member -> 
                member.getUserId().equals(userId) &&
                member.getGroupId().equals(virtualGroupId)));
        
        // Then: Change log should be recorded
        verify(memberChangeLogRepository).save(argThat(log ->
                log.getChangeType() == MemberChangeType.JOIN &&
                log.getUserId().equals(userId)));
    }
    
    /**
     * Feature: permission-request-approval, Property 6: Approval Immediate Effect
     * Approved business unit role request should immediately assign roles
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 6: Approved BU role request assigns roles immediately")
    void approvedBusinessUnitRoleRequestAssignsRolesImmediately(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validRoleIdLists") List<String> roleIds,
            @ForAll("validUserIds") String operatorId) {
        
        // Given: User doesn't have these roles yet
        for (String roleId : roleIds) {
            when(userBusinessUnitRoleRepository.existsByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleId))
                    .thenReturn(false);
        }
        
        // Given: Save succeeds
        when(userBusinessUnitRoleRepository.save(any(UserBusinessUnitRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Add user to business unit with roles
        memberManagementService.addUserToBusinessUnitWithRoles(userId, businessUnitId, roleIds, operatorId);
        
        // Then: All roles should be assigned
        verify(userBusinessUnitRoleRepository, times(roleIds.size())).save(any(UserBusinessUnitRole.class));
        
        // Then: Change log should be recorded
        verify(memberChangeLogRepository).save(argThat(log ->
                log.getChangeType() == MemberChangeType.JOIN &&
                log.getUserId().equals(userId)));
    }
    
    /**
     * Feature: permission-request-approval, Property 6: Approval Immediate Effect
     * Already existing membership should not create duplicate
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 6: Existing membership should not create duplicate")
    void existingMembershipShouldNotCreateDuplicate(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validUserIds") String operatorId) {
        
        // Given: User is already a member
        when(virtualGroupMemberRepository.existsByGroupIdAndUserId(virtualGroupId, userId))
                .thenReturn(true);
        
        // When: Try to add user again
        memberManagementService.addUserToVirtualGroup(userId, virtualGroupId, operatorId);
        
        // Then: Should not save new member
        verify(virtualGroupMemberRepository, never()).save(any(VirtualGroupMember.class));
    }
    
    // ==================== Property 9: Member Removal Immediate Effect ====================
    
    /**
     * Feature: permission-request-approval, Property 9: Member Removal Immediate Effect
     * Approver removing virtual group member should take effect immediately
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 9: VG member removal takes effect immediately")
    void virtualGroupMemberRemovalTakesEffectImmediately(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validUserIds") String approverId) {
        
        // Given: Approver is configured
        when(approverService.isApprover(approverId, ApproverTargetType.VIRTUAL_GROUP, virtualGroupId))
                .thenReturn(true);
        
        // Given: Save succeeds
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Remove member
        memberManagementService.removeVirtualGroupMember(virtualGroupId, userId, approverId);
        
        // Then: Member should be deleted immediately
        verify(virtualGroupMemberRepository).deleteByGroupIdAndUserId(virtualGroupId, userId);
        
        // Then: Change log should be recorded
        verify(memberChangeLogRepository).save(argThat(log ->
                log.getChangeType() == MemberChangeType.REMOVED &&
                log.getUserId().equals(userId) &&
                log.getOperatorId().equals(approverId)));
    }
    
    /**
     * Feature: permission-request-approval, Property 9: Member Removal Immediate Effect
     * Non-approver cannot remove virtual group member
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 9: Non-approver cannot remove VG member")
    void nonApproverCannotRemoveVirtualGroupMember(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validUserIds") String nonApproverId) {
        
        // Given: User is not an approver
        when(approverService.isApprover(nonApproverId, ApproverTargetType.VIRTUAL_GROUP, virtualGroupId))
                .thenReturn(false);
        
        // When & Then: Removal should throw exception
        assertThatThrownBy(() -> memberManagementService.removeVirtualGroupMember(virtualGroupId, userId, nonApproverId))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("不是该虚拟组的审批人");
        
        // Then: Member should not be deleted
        verify(virtualGroupMemberRepository, never()).deleteByGroupIdAndUserId(any(), any());
    }
    
    /**
     * Feature: permission-request-approval, Property 9: Member Removal Immediate Effect
     * Approver removing business unit role should take effect immediately
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 9: BU role removal takes effect immediately")
    void businessUnitRoleRemovalTakesEffectImmediately(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validRoleIds") String roleId,
            @ForAll("validUserIds") String approverId) {
        
        // Given: Approver is configured
        when(approverService.isApprover(approverId, ApproverTargetType.BUSINESS_UNIT, businessUnitId))
                .thenReturn(true);
        
        // Given: Role assignment exists
        UserBusinessUnitRole assignment = createUserBusinessUnitRole(userId, businessUnitId, roleId);
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleId))
                .thenReturn(Optional.of(assignment));
        
        // Given: Save succeeds
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Remove role
        memberManagementService.removeBusinessUnitRole(businessUnitId, userId, roleId, approverId);
        
        // Then: Role should be deleted immediately
        verify(userBusinessUnitRoleRepository).delete(assignment);
        
        // Then: Change log should be recorded
        verify(memberChangeLogRepository).save(argThat(log ->
                log.getChangeType() == MemberChangeType.REMOVED &&
                log.getUserId().equals(userId)));
    }
    
    // ==================== Property 10: Exit All Roles Removes User from Business Unit ====================
    
    /**
     * Feature: permission-request-approval, Property 10: Exit All Roles Removes User from Business Unit
     * Exiting all roles should leave user with no roles in business unit
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 10: Exiting all roles leaves no roles")
    void exitingAllRolesLeavesNoRoles(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validRoleIdLists") List<String> roleIds) {
        
        // Given: User has these roles
        for (String roleId : roleIds) {
            UserBusinessUnitRole assignment = createUserBusinessUnitRole(userId, businessUnitId, roleId);
            when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleId))
                    .thenReturn(Optional.of(assignment));
        }
        
        // Given: After exit, no roles remain
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Collections.emptyList());
        
        // Given: Save succeeds
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Exit all roles
        memberManagementService.exitBusinessUnitRoles(businessUnitId, userId, roleIds);
        
        // Then: All roles should be deleted
        verify(userBusinessUnitRoleRepository, times(roleIds.size())).delete(any(UserBusinessUnitRole.class));
        
        // Then: Change log should be recorded
        verify(memberChangeLogRepository).save(argThat(log ->
                log.getChangeType() == MemberChangeType.EXIT &&
                log.getUserId().equals(userId)));
    }
    
    /**
     * Feature: permission-request-approval, Property 10: Exit All Roles Removes User from Business Unit
     * Exiting some roles should leave remaining roles
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 10: Exiting some roles leaves remaining roles")
    void exitingSomeRolesLeavesRemainingRoles(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validRoleIds") String roleToExit,
            @ForAll("validRoleIds") String roleToKeep) {
        
        Assume.that(!roleToExit.equals(roleToKeep));
        
        // Given: User has the role to exit
        UserBusinessUnitRole assignmentToExit = createUserBusinessUnitRole(userId, businessUnitId, roleToExit);
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleToExit))
                .thenReturn(Optional.of(assignmentToExit));
        
        // Given: After exit, one role remains
        UserBusinessUnitRole remainingAssignment = createUserBusinessUnitRole(userId, businessUnitId, roleToKeep);
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(List.of(remainingAssignment));
        
        // Given: Save succeeds
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Exit one role
        memberManagementService.exitBusinessUnitRoles(businessUnitId, userId, List.of(roleToExit));
        
        // Then: Only the exited role should be deleted
        verify(userBusinessUnitRoleRepository, times(1)).delete(assignmentToExit);
    }
    
    // ==================== Property 11: Virtual Group Role Inheritance ====================
    
    /**
     * Feature: permission-request-approval, Property 11: Virtual Group Role Inheritance
     * User exiting virtual group should trigger exit log
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 11: VG exit should record change log")
    void virtualGroupExitShouldRecordChangeLog(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String virtualGroupId) {
        
        // Given: Save succeeds
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Exit virtual group
        memberManagementService.exitVirtualGroup(virtualGroupId, userId);
        
        // Then: Member should be deleted
        verify(virtualGroupMemberRepository).deleteByGroupIdAndUserId(virtualGroupId, userId);
        
        // Then: Change log should be recorded with EXIT type
        ArgumentCaptor<MemberChangeLog> logCaptor = ArgumentCaptor.forClass(MemberChangeLog.class);
        verify(memberChangeLogRepository).save(logCaptor.capture());
        
        MemberChangeLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getChangeType()).isEqualTo(MemberChangeType.EXIT);
        assertThat(savedLog.getTargetType()).isEqualTo(ApproverTargetType.VIRTUAL_GROUP);
        assertThat(savedLog.getUserId()).isEqualTo(userId);
        assertThat(savedLog.getOperatorId()).isEqualTo(userId); // Self-exit
    }
    
    /**
     * Feature: permission-request-approval, Property 11: Virtual Group Role Inheritance
     * Getting virtual group role IDs should return bound role (single role binding)
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 11: Get VG role IDs returns bound role")
    void getVirtualGroupRoleIdsReturnsBoundRole(
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validRoleIds") String expectedRoleId) {
        
        // Given: Virtual group has one bound role (single role binding)
        VirtualGroupRole binding = VirtualGroupRole.builder()
                .id(UUID.randomUUID().toString())
                .virtualGroupId(virtualGroupId)
                .roleId(expectedRoleId)
                .build();
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId)).thenReturn(Optional.of(binding));
        
        // When: Get role IDs
        List<String> actualRoleIds = memberManagementService.getVirtualGroupRoleIds(virtualGroupId);
        
        // Then: Should return the single bound role ID
        assertThat(actualRoleIds).containsExactly(expectedRoleId);
    }
    
    /**
     * Feature: permission-request-approval, Property 11: Virtual Group Role Inheritance
     * Getting virtual group role IDs should return empty list when no role bound
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 11: Get VG role IDs returns empty when no role bound")
    void getVirtualGroupRoleIdsReturnsEmptyWhenNoRoleBound(
            @ForAll("validTargetIds") String virtualGroupId) {
        
        // Given: Virtual group has no bound role
        when(virtualGroupRoleRepository.findByVirtualGroupId(virtualGroupId)).thenReturn(Optional.empty());
        
        // When: Get role IDs
        List<String> actualRoleIds = memberManagementService.getVirtualGroupRoleIds(virtualGroupId);
        
        // Then: Should return empty list
        assertThat(actualRoleIds).isEmpty();
    }
    
    // ==================== Property 11 (New): Member Removal Immediate Effect ====================
    
    /**
     * Feature: permission-request-approval, Property 11: Member Removal Immediate Effect
     * Approver removing business unit member should take effect immediately
     * **Validates: Requirements 15.3, 15.4**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 11: BU member removal takes effect immediately")
    void businessUnitMemberRemovalTakesEffectImmediately(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validUserIds") String approverId) {
        
        // Given: Approver is configured
        when(approverService.isApprover(approverId, ApproverTargetType.BUSINESS_UNIT, businessUnitId))
                .thenReturn(true);
        
        // Given: User is a member of the business unit
        UserBusinessUnit membership = UserBusinessUnit.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .businessUnitId(businessUnitId)
                .build();
        when(userBusinessUnitRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Optional.of(membership));
        
        // Given: User has no role assignments (new model)
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Collections.emptyList());
        
        // Given: Save succeeds
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Remove member
        memberManagementService.removeBusinessUnitMember(businessUnitId, userId, approverId);
        
        // Then: Membership should be deleted immediately
        verify(userBusinessUnitRepository).delete(membership);
        
        // Then: Change log should be recorded
        verify(memberChangeLogRepository).save(argThat(log ->
                log.getChangeType() == MemberChangeType.REMOVED &&
                log.getUserId().equals(userId) &&
                log.getOperatorId().equals(approverId)));
    }
    
    /**
     * Feature: permission-request-approval, Property 11: Member Removal Immediate Effect
     * Non-approver cannot remove business unit member
     * **Validates: Requirements 15.3, 15.4**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 11: Non-approver cannot remove BU member")
    void nonApproverCannotRemoveBusinessUnitMember(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validUserIds") String nonApproverId) {
        
        // Given: User is not an approver
        when(approverService.isApprover(nonApproverId, ApproverTargetType.BUSINESS_UNIT, businessUnitId))
                .thenReturn(false);
        
        // When & Then: Removal should throw exception
        assertThatThrownBy(() -> memberManagementService.removeBusinessUnitMember(businessUnitId, userId, nonApproverId))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("不是该业务单元的审批人");
        
        // Then: Membership should not be deleted
        verify(userBusinessUnitRepository, never()).delete(any(UserBusinessUnit.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 11: Member Removal Immediate Effect
     * Removing business unit member should also remove legacy role assignments
     * **Validates: Requirements 15.3, 15.4**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 11: BU member removal also removes legacy role assignments")
    void businessUnitMemberRemovalAlsoRemovesLegacyRoleAssignments(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validRoleIdLists") List<String> roleIds,
            @ForAll("validUserIds") String approverId) {
        
        // Given: Approver is configured
        when(approverService.isApprover(approverId, ApproverTargetType.BUSINESS_UNIT, businessUnitId))
                .thenReturn(true);
        
        // Given: User is a member of the business unit
        UserBusinessUnit membership = UserBusinessUnit.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .businessUnitId(businessUnitId)
                .build();
        when(userBusinessUnitRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Optional.of(membership));
        
        // Given: User has legacy role assignments
        List<UserBusinessUnitRole> roleAssignments = roleIds.stream()
                .map(roleId -> createUserBusinessUnitRole(userId, businessUnitId, roleId))
                .toList();
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(roleAssignments);
        
        // Given: Save succeeds
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Remove member
        memberManagementService.removeBusinessUnitMember(businessUnitId, userId, approverId);
        
        // Then: Membership should be deleted
        verify(userBusinessUnitRepository).delete(membership);
        
        // Then: All legacy role assignments should be deleted
        verify(userBusinessUnitRoleRepository).deleteAll(roleAssignments);
    }
    
    // ==================== Property 15: Virtual Group Exit Revokes Role ====================
    
    /**
     * Feature: permission-request-approval, Property 15: Virtual Group Exit Revokes Role
     * User exiting virtual group should immediately revoke the inherited role
     * **Validates: Requirements 15.3, 16.2**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 15: VG exit revokes inherited role")
    void virtualGroupExitRevokesInheritedRole(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String virtualGroupId) {
        
        // Given: Save succeeds
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Exit virtual group
        memberManagementService.exitVirtualGroup(virtualGroupId, userId);
        
        // Then: Member should be deleted immediately (which revokes the inherited role)
        verify(virtualGroupMemberRepository).deleteByGroupIdAndUserId(virtualGroupId, userId);
        
        // Then: Change log should be recorded with EXIT type
        ArgumentCaptor<MemberChangeLog> logCaptor = ArgumentCaptor.forClass(MemberChangeLog.class);
        verify(memberChangeLogRepository).save(logCaptor.capture());
        
        MemberChangeLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getChangeType()).isEqualTo(MemberChangeType.EXIT);
        assertThat(savedLog.getTargetType()).isEqualTo(ApproverTargetType.VIRTUAL_GROUP);
        assertThat(savedLog.getUserId()).isEqualTo(userId);
        assertThat(savedLog.getReason()).contains("用户主动退出");
    }
    
    // ==================== Property 16: Business Unit Exit Deactivates BU-Bounded Roles ====================
    
    /**
     * Feature: permission-request-approval, Property 16: Business Unit Exit Deactivates BU-Bounded Roles
     * User exiting business unit should immediately deactivate BU-Bounded roles for that business unit
     * **Validates: Requirements 15.4, 16.3**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 16: BU exit deactivates BU-Bounded roles")
    void businessUnitExitDeactivatesBuBoundedRoles(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String businessUnitId) {
        
        // Given: User is a member of the business unit
        UserBusinessUnit membership = UserBusinessUnit.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .businessUnitId(businessUnitId)
                .build();
        when(userBusinessUnitRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Optional.of(membership));
        
        // Given: User has no legacy role assignments
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Collections.emptyList());
        
        // Given: Save succeeds
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Exit business unit
        memberManagementService.exitBusinessUnit(businessUnitId, userId);
        
        // Then: Membership should be deleted immediately (which deactivates BU-Bounded roles)
        verify(userBusinessUnitRepository).delete(membership);
        
        // Then: Change log should be recorded with EXIT type
        ArgumentCaptor<MemberChangeLog> logCaptor = ArgumentCaptor.forClass(MemberChangeLog.class);
        verify(memberChangeLogRepository).save(logCaptor.capture());
        
        MemberChangeLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getChangeType()).isEqualTo(MemberChangeType.EXIT);
        assertThat(savedLog.getTargetType()).isEqualTo(ApproverTargetType.BUSINESS_UNIT);
        assertThat(savedLog.getUserId()).isEqualTo(userId);
        assertThat(savedLog.getReason()).contains("用户主动退出业务单元");
    }
    
    /**
     * Feature: permission-request-approval, Property 16: Business Unit Exit Deactivates BU-Bounded Roles
     * Exiting business unit should also remove legacy role assignments
     * **Validates: Requirements 15.4, 16.3**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 16: BU exit also removes legacy role assignments")
    void businessUnitExitAlsoRemovesLegacyRoleAssignments(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validRoleIdLists") List<String> roleIds) {
        
        // Given: User is a member of the business unit
        UserBusinessUnit membership = UserBusinessUnit.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .businessUnitId(businessUnitId)
                .build();
        when(userBusinessUnitRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Optional.of(membership));
        
        // Given: User has legacy role assignments
        List<UserBusinessUnitRole> roleAssignments = roleIds.stream()
                .map(roleId -> createUserBusinessUnitRole(userId, businessUnitId, roleId))
                .toList();
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(roleAssignments);
        
        // Given: Save succeeds
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Exit business unit
        memberManagementService.exitBusinessUnit(businessUnitId, userId);
        
        // Then: Membership should be deleted
        verify(userBusinessUnitRepository).delete(membership);
        
        // Then: All legacy role assignments should be deleted
        verify(userBusinessUnitRoleRepository).deleteAll(roleAssignments);
    }
    
    /**
     * Feature: permission-request-approval, Property 16: Business Unit Exit Deactivates BU-Bounded Roles
     * Exiting business unit when not a member should not throw error
     * **Validates: Requirements 15.4, 16.3**
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 16: BU exit when not member should not throw")
    void businessUnitExitWhenNotMemberShouldNotThrow(
            @ForAll("validUserIds") String userId,
            @ForAll("validTargetIds") String businessUnitId) {
        
        // Given: User is not a member of the business unit
        when(userBusinessUnitRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Optional.empty());
        
        // Given: User has no legacy role assignments
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Collections.emptyList());
        
        // Given: Save succeeds
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Exit business unit - should not throw
        memberManagementService.exitBusinessUnit(businessUnitId, userId);
        
        // Then: No membership deletion should occur
        verify(userBusinessUnitRepository, never()).delete(any(UserBusinessUnit.class));
        
        // Then: Change log should still be recorded
        verify(memberChangeLogRepository).save(any(MemberChangeLog.class));
    }
    
    // ==================== Helper Methods ====================
    
    private VirtualGroup createVirtualGroup(String id) {
        return VirtualGroup.builder()
                .id(id)
                .name("Test Group " + id)
                .status("ACTIVE")
                .build();
    }
    
    private User createUser(String id) {
        return User.builder()
                .id(id)
                .username("user_" + id.substring(0, 8))
                .displayName("Test User")
                .email("test@example.com")
                .status(UserStatus.ACTIVE)
                .build();
    }
    
    private UserBusinessUnitRole createUserBusinessUnitRole(String userId, String businessUnitId, String roleId) {
        return UserBusinessUnitRole.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .businessUnitId(businessUnitId)
                .roleId(roleId)
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
                .ofMaxSize(5);
    }
}
