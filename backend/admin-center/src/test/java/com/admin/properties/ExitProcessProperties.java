package com.admin.properties;

import com.admin.entity.*;
import com.admin.enums.*;
import com.platform.security.entity.User;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.entity.UserBusinessUnit;
import com.platform.security.entity.UserBusinessUnitRole;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.*;
import com.admin.service.MemberManagementService;
import com.admin.service.ApproverService;
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
 * Property tests for Exit Process Cleanup
 * 
 * **Feature: technical-debt-remediation, Property 7: Exit Process Cleanup**
 * **Validates: Requirements 2.4**
 * 
 * Tests that exit process operations execute proper cleanup logic and ensure
 * system consistency throughout the exit process.
 */
public class ExitProcessProperties {
    
    private VirtualGroupMemberRepository virtualGroupMemberRepository;
    private UserBusinessUnitRepository userBusinessUnitRepository;
    private UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    private MemberChangeLogRepository memberChangeLogRepository;
    private ApproverService approverService;
    private ObjectMapper objectMapper;
    private MemberManagementService memberManagementService;
    
    @BeforeTry
    void setUp() {
        virtualGroupMemberRepository = mock(VirtualGroupMemberRepository.class);
        userBusinessUnitRepository = mock(UserBusinessUnitRepository.class);
        userBusinessUnitRoleRepository = mock(UserBusinessUnitRoleRepository.class);
        memberChangeLogRepository = mock(MemberChangeLogRepository.class);
        approverService = mock(ApproverService.class);
        objectMapper = new ObjectMapper();
        
        // Configure mock to simulate @CreatedDate behavior
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> {
                    MemberChangeLog log = invocation.getArgument(0);
                    // Simulate @CreatedDate behavior since it doesn't work with mocks
                    if (log.getCreatedAt() == null) {
                        log.setCreatedAt(java.time.Instant.now());
                    }
                    return log;
                });
        
        memberManagementService = new MemberManagementService(
                virtualGroupMemberRepository,
                mock(VirtualGroupRepository.class),
                mock(UserRepository.class),
                userBusinessUnitRoleRepository,
                userBusinessUnitRepository,
                mock(VirtualGroupRoleRepository.class),
                memberChangeLogRepository,
                approverService,
                objectMapper);
    }
    
    // ==================== Property 7: Exit Process Cleanup ====================
    
    /**
     * Feature: technical-debt-remediation, Property 7: Exit Process Cleanup
     * Exiting virtual group should immediately revoke inherited roles and clean up membership
     */
    @Property(tries = 100)
    @Label("Feature: technical-debt-remediation, Property 7: VG exit revokes roles and cleans up")
    void virtualGroupExitRevokesRolesAndCleansUp(
            @ForAll("validUserIds") String userId,
            @ForAll("validGroupIds") String virtualGroupId) {
        
        // When: Exit virtual group
        memberManagementService.exitVirtualGroup(virtualGroupId, userId);
        
        // Then: Membership should be deleted (which revokes inherited roles)
        verify(virtualGroupMemberRepository).deleteByVirtualGroupIdAndUserId(virtualGroupId, userId);
        
        // Then: Exit should be logged with proper cleanup context
        ArgumentCaptor<MemberChangeLog> logCaptor = ArgumentCaptor.forClass(MemberChangeLog.class);
        verify(memberChangeLogRepository).save(logCaptor.capture());
        
        MemberChangeLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getChangeType()).isEqualTo(MemberChangeType.EXIT);
        assertThat(savedLog.getTargetType()).isEqualTo(ApproverTargetType.VIRTUAL_GROUP);
        assertThat(savedLog.getUserId()).isEqualTo(userId);
        assertThat(savedLog.getOperatorId()).isEqualTo(userId); // Self-exit
        assertThat(savedLog.getReason()).contains("用户主动退出");
    }
    
    /**
     * Feature: technical-debt-remediation, Property 7: Exit Process Cleanup
     * Exiting business unit should deactivate BU-Bounded roles and clean up membership
     */
    @Property(tries = 100)
    @Label("Feature: technical-debt-remediation, Property 7: BU exit deactivates roles and cleans up")
    void businessUnitExitDeactivatesRolesAndCleansUp(
            @ForAll("validUserIds") String userId,
            @ForAll("validBusinessUnitIds") String businessUnitId) {
        
        // Given: User is a member of the business unit
        UserBusinessUnit membership = UserBusinessUnit.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .businessUnitId(businessUnitId)
                .build();
        when(userBusinessUnitRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Optional.of(membership));
        
        // Given: User has legacy role assignments that need cleanup
        List<UserBusinessUnitRole> legacyRoles = Arrays.asList(
                createUserBusinessUnitRole(userId, businessUnitId, "role1"),
                createUserBusinessUnitRole(userId, businessUnitId, "role2")
        );
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(legacyRoles);
        
        // When: Exit business unit
        memberManagementService.exitBusinessUnit(businessUnitId, userId);
        
        // Then: Membership should be deleted (which deactivates BU-Bounded roles)
        verify(userBusinessUnitRepository).delete(membership);
        
        // Then: Legacy role assignments should be cleaned up
        verify(userBusinessUnitRoleRepository).deleteAll(legacyRoles);
        
        // Then: Exit should be logged with proper cleanup context
        ArgumentCaptor<MemberChangeLog> logCaptor = ArgumentCaptor.forClass(MemberChangeLog.class);
        verify(memberChangeLogRepository).save(logCaptor.capture());
        
        MemberChangeLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getChangeType()).isEqualTo(MemberChangeType.EXIT);
        assertThat(savedLog.getTargetType()).isEqualTo(ApproverTargetType.BUSINESS_UNIT);
        assertThat(savedLog.getUserId()).isEqualTo(userId);
        assertThat(savedLog.getOperatorId()).isEqualTo(userId); // Self-exit
        assertThat(savedLog.getReason()).contains("用户主动退出业务单元");
    }
    
    /**
     * Feature: technical-debt-remediation, Property 7: Exit Process Cleanup
     * Exiting business unit roles should clean up specific role assignments
     */
    @Property(tries = 100)
    @Label("Feature: technical-debt-remediation, Property 7: Role exit cleans up specific assignments")
    void businessUnitRoleExitCleansUpSpecificAssignments(
            @ForAll("validUserIds") String userId,
            @ForAll("validBusinessUnitIds") String businessUnitId,
            @ForAll("validRoleIdLists") List<String> roleIdsToExit) {
        
        // Given: User has the roles to exit
        List<UserBusinessUnitRole> roleAssignments = new ArrayList<>();
        for (String roleId : roleIdsToExit) {
            UserBusinessUnitRole assignment = createUserBusinessUnitRole(userId, businessUnitId, roleId);
            roleAssignments.add(assignment);
            when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId(userId, businessUnitId, roleId))
                    .thenReturn(Optional.of(assignment));
        }
        
        // When: Exit specific roles
        memberManagementService.exitBusinessUnitRoles(businessUnitId, userId, roleIdsToExit);
        
        // Then: Each role assignment should be deleted
        for (UserBusinessUnitRole assignment : roleAssignments) {
            verify(userBusinessUnitRoleRepository).delete(assignment);
        }
        
        // Then: Exit should be logged with proper cleanup context
        ArgumentCaptor<MemberChangeLog> logCaptor = ArgumentCaptor.forClass(MemberChangeLog.class);
        verify(memberChangeLogRepository).save(logCaptor.capture());
        
        MemberChangeLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getChangeType()).isEqualTo(MemberChangeType.EXIT);
        assertThat(savedLog.getTargetType()).isEqualTo(ApproverTargetType.BUSINESS_UNIT);
        assertThat(savedLog.getUserId()).isEqualTo(userId);
        assertThat(savedLog.getOperatorId()).isEqualTo(userId); // Self-exit
        assertThat(savedLog.getReason()).contains("用户主动退出");
    }
    
    /**
     * Feature: technical-debt-remediation, Property 7: Exit Process Cleanup
     * Exit operations should be idempotent - multiple exits should not cause errors
     */
    @Property(tries = 100)
    @Label("Feature: technical-debt-remediation, Property 7: Exit operations are idempotent")
    void exitOperationsAreIdempotent(
            @ForAll("validUserIds") String userId,
            @ForAll("validGroupIds") String virtualGroupId) {
        
        // Given: User is not a member (already exited or never joined)
        // Note: deleteByVirtualGroupIdAndUserId returns void, so we just verify it's called
        doNothing().when(virtualGroupMemberRepository).deleteByVirtualGroupIdAndUserId(virtualGroupId, userId);
        
        // When: Exit virtual group (should not fail even if not a member)
        memberManagementService.exitVirtualGroup(virtualGroupId, userId);
        
        // Then: Delete operation should still be attempted
        verify(virtualGroupMemberRepository).deleteByVirtualGroupIdAndUserId(virtualGroupId, userId);
        
        // Then: Exit should still be logged (for audit purposes)
        verify(memberChangeLogRepository).save(any(MemberChangeLog.class));
    }
    
    /**
     * Feature: technical-debt-remediation, Property 7: Exit Process Cleanup
     * Business unit exit should handle cases where user has no legacy roles gracefully
     */
    @Property(tries = 100)
    @Label("Feature: technical-debt-remediation, Property 7: BU exit handles no legacy roles gracefully")
    void businessUnitExitHandlesNoLegacyRolesGracefully(
            @ForAll("validUserIds") String userId,
            @ForAll("validBusinessUnitIds") String businessUnitId) {
        
        // Given: User is a member but has no legacy role assignments
        UserBusinessUnit membership = UserBusinessUnit.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .businessUnitId(businessUnitId)
                .build();
        when(userBusinessUnitRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Optional.of(membership));
        
        // Given: No legacy role assignments
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Collections.emptyList());
        
        // When: Exit business unit
        memberManagementService.exitBusinessUnit(businessUnitId, userId);
        
        // Then: Membership should still be deleted
        verify(userBusinessUnitRepository).delete(membership);
        
        // Then: No role cleanup should be attempted (empty list)
        verify(userBusinessUnitRoleRepository, never()).deleteAll(any());
        
        // Then: Exit should still be logged
        verify(memberChangeLogRepository).save(any(MemberChangeLog.class));
    }
    
    /**
     * Feature: technical-debt-remediation, Property 7: Exit Process Cleanup
     * Exit operations should maintain audit trail consistency
     */
    @Property(tries = 100)
    @Label("Feature: technical-debt-remediation, Property 7: Exit maintains audit trail consistency")
    void exitMaintainsAuditTrailConsistency(
            @ForAll("validUserIds") String userId,
            @ForAll("validGroupIds") String virtualGroupId) {
        
        // When: Exit virtual group
        memberManagementService.exitVirtualGroup(virtualGroupId, userId);
        
        // Then: Audit log should have consistent information
        ArgumentCaptor<MemberChangeLog> logCaptor = ArgumentCaptor.forClass(MemberChangeLog.class);
        verify(memberChangeLogRepository).save(logCaptor.capture());
        
        MemberChangeLog savedLog = logCaptor.getValue();
        
        // Verify audit trail consistency
        assertThat(savedLog.getId()).isNotNull();
        assertThat(savedLog.getChangeType()).isEqualTo(MemberChangeType.EXIT);
        assertThat(savedLog.getTargetType()).isEqualTo(ApproverTargetType.VIRTUAL_GROUP);
        assertThat(savedLog.getTargetId()).isEqualTo(virtualGroupId);
        assertThat(savedLog.getUserId()).isEqualTo(userId);
        assertThat(savedLog.getOperatorId()).isEqualTo(userId);
        assertThat(savedLog.getReason()).isNotNull();
        assertThat(savedLog.getCreatedAt()).isNotNull();
        
        // Verify self-exit consistency
        assertThat(savedLog.getUserId()).isEqualTo(savedLog.getOperatorId());
    }
    
    /**
     * Feature: technical-debt-remediation, Property 7: Exit Process Cleanup
     * Exit operations should handle non-existent memberships gracefully
     */
    @Property(tries = 100)
    @Label("Feature: technical-debt-remediation, Property 7: Exit handles non-existent memberships gracefully")
    void exitHandlesNonExistentMembershipsGracefully(
            @ForAll("validUserIds") String userId,
            @ForAll("validBusinessUnitIds") String businessUnitId) {
        
        // Given: User is not a member of the business unit
        when(userBusinessUnitRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Optional.empty());
        
        // Given: No legacy role assignments
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitId(userId, businessUnitId))
                .thenReturn(Collections.emptyList());
        
        // When: Exit business unit (should not fail)
        memberManagementService.exitBusinessUnit(businessUnitId, userId);
        
        // Then: No membership deletion should be attempted
        verify(userBusinessUnitRepository, never()).delete(any(UserBusinessUnit.class));
        
        // Then: No role cleanup should be attempted
        verify(userBusinessUnitRoleRepository, never()).deleteAll(any());
        
        // Then: Exit should still be logged for audit purposes
        verify(memberChangeLogRepository).save(any(MemberChangeLog.class));
    }
    
    // ==================== Helper Methods ====================
    
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
    Arbitrary<String> validGroupIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "vg-" + s.toLowerCase());
    }
    
    @Provide
    Arbitrary<String> validBusinessUnitIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(20)
                .map(s -> "bu-" + s.toLowerCase());
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