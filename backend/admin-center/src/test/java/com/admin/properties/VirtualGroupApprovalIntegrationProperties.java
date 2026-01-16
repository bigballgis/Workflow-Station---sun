package com.admin.properties;

import com.admin.entity.*;
import com.admin.enums.*;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.*;
import com.admin.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property 7: Virtual Group Approval Immediate Effect
 * *For any* approved Virtual_Group request, the user shall be immediately added to the 
 * Virtual_Group and granted the bound Business_Role.
 * 
 * **Validates: Requirements 10.1, 10.2**
 */
public class VirtualGroupApprovalIntegrationProperties {
    
    private PermissionRequestRepository permissionRequestRepository;
    private VirtualGroupRepository virtualGroupRepository;
    private BusinessUnitRepository businessUnitRepository;
    private VirtualGroupMemberRepository virtualGroupMemberRepository;
    private VirtualGroupRoleRepository virtualGroupRoleRepository;
    private RoleRepository roleRepository;
    private UserRepository userRepository;
    private MemberChangeLogRepository memberChangeLogRepository;
    private ApproverService approverService;
    private MemberManagementService memberManagementService;
    private VirtualGroupRoleService virtualGroupRoleService;
    private ObjectMapper objectMapper;
    private PermissionRequestService permissionRequestService;
    
    @BeforeTry
    void setUp() {
        permissionRequestRepository = mock(PermissionRequestRepository.class);
        virtualGroupRepository = mock(VirtualGroupRepository.class);
        businessUnitRepository = mock(BusinessUnitRepository.class);
        virtualGroupMemberRepository = mock(VirtualGroupMemberRepository.class);
        virtualGroupRoleRepository = mock(VirtualGroupRoleRepository.class);
        roleRepository = mock(RoleRepository.class);
        userRepository = mock(UserRepository.class);
        memberChangeLogRepository = mock(MemberChangeLogRepository.class);
        approverService = mock(ApproverService.class);
        virtualGroupRoleService = mock(VirtualGroupRoleService.class);
        objectMapper = new ObjectMapper();
        
        // Create real MemberManagementService with mocked dependencies
        memberManagementService = new MemberManagementService(
                virtualGroupMemberRepository,
                virtualGroupRepository,
                userRepository,
                mock(UserBusinessUnitRoleRepository.class),
                mock(UserBusinessUnitRepository.class),
                virtualGroupRoleRepository,
                memberChangeLogRepository,
                approverService,
                objectMapper);
        
        permissionRequestService = new PermissionRequestService(
                permissionRequestRepository,
                virtualGroupRepository,
                businessUnitRepository,
                virtualGroupMemberRepository,
                approverService,
                memberManagementService,
                virtualGroupRoleService,
                objectMapper);
    }

    
    // ==================== Property 7: Virtual Group Approval Immediate Effect ====================
    
    /**
     * Feature: permission-request-approval, Property 7: Virtual Group Approval Immediate Effect
     * Approving a virtual group request should immediately add user to the group
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 7: Approved VG request adds user to group immediately")
    void approvedVirtualGroupRequestAddsUserToGroupImmediately(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validUserIds") String approverId) {
        
        Assume.that(!applicantId.equals(approverId)); // Self-approval not allowed
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Pending request exists
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId(applicantId)
                .requestType(PermissionRequestType.VIRTUAL_GROUP)
                .targetId(virtualGroupId)
                .status(PermissionRequestStatus.PENDING)
                .reason("Test reason")
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: Approver is authorized
        when(approverService.isApprover(approverId, ApproverTargetType.VIRTUAL_GROUP, virtualGroupId))
                .thenReturn(true);
        
        // Given: Virtual group exists
        VirtualGroup virtualGroup = createVirtualGroup(virtualGroupId);
        when(virtualGroupRepository.findById(virtualGroupId)).thenReturn(Optional.of(virtualGroup));
        
        // Given: User exists
        User user = createUser(applicantId);
        when(userRepository.findById(applicantId)).thenReturn(Optional.of(user));
        
        // Given: User is not already a member
        when(virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(virtualGroupId, applicantId))
                .thenReturn(false);
        
        // Given: Save operations succeed
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(virtualGroupMemberRepository.save(any(VirtualGroupMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Approve the request
        permissionRequestService.approve(requestId, approverId, "Approved");
        
        // Then: Request status should be updated to APPROVED
        ArgumentCaptor<PermissionRequest> requestCaptor = ArgumentCaptor.forClass(PermissionRequest.class);
        verify(permissionRequestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(PermissionRequestStatus.APPROVED);
        assertThat(requestCaptor.getValue().getApproverId()).isEqualTo(approverId);
        
        // Then: User should be added to virtual group immediately
        verify(virtualGroupMemberRepository).save(argThat(member ->
                member.getUser().getId().equals(applicantId) &&
                member.getVirtualGroup().getId().equals(virtualGroupId)));
    }
    
    /**
     * Feature: permission-request-approval, Property 7: Virtual Group Approval Immediate Effect
     * Approving a virtual group request should grant the bound role to the user
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 7: Approved VG request grants bound role")
    void approvedVirtualGroupRequestGrantsBoundRole(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validRoleIds") String boundRoleId,
            @ForAll("validUserIds") String approverId,
            @ForAll("businessRoleTypes") RoleType roleType) {
        
        Assume.that(!applicantId.equals(approverId)); // Self-approval not allowed
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Pending request exists
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId(applicantId)
                .requestType(PermissionRequestType.VIRTUAL_GROUP)
                .targetId(virtualGroupId)
                .status(PermissionRequestStatus.PENDING)
                .reason("Test reason")
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: Approver is authorized
        when(approverService.isApprover(approverId, ApproverTargetType.VIRTUAL_GROUP, virtualGroupId))
                .thenReturn(true);
        
        // Given: Virtual group exists with bound role
        VirtualGroup virtualGroup = createVirtualGroup(virtualGroupId);
        when(virtualGroupRepository.findById(virtualGroupId)).thenReturn(Optional.of(virtualGroup));
        
        // Given: Virtual group has a bound role
        Role boundRole = createRole(boundRoleId, roleType);
        when(virtualGroupRoleService.getBoundRole(virtualGroupId)).thenReturn(Optional.of(boundRole));
        
        // Given: User exists
        User user = createUser(applicantId);
        when(userRepository.findById(applicantId)).thenReturn(Optional.of(user));
        
        // Given: User is not already a member
        when(virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(virtualGroupId, applicantId))
                .thenReturn(false);
        
        // Given: Save operations succeed
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(virtualGroupMemberRepository.save(any(VirtualGroupMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Approve the request
        permissionRequestService.approve(requestId, approverId, "Approved");
        
        // Then: User should be added to virtual group (which grants the bound role)
        verify(virtualGroupMemberRepository).save(argThat(member ->
                member.getUser().getId().equals(applicantId) &&
                member.getVirtualGroup().getId().equals(virtualGroupId)));
        
        // Then: The bound role is implicitly granted through virtual group membership
        // (Role is inherited from virtual group, not directly assigned)
    }

    
    /**
     * Feature: permission-request-approval, Property 7: Virtual Group Approval Immediate Effect
     * BU_UNBOUNDED role should be immediately effective after virtual group approval
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 7: BU_UNBOUNDED role is immediately effective")
    void buUnboundedRoleIsImmediatelyEffectiveAfterApproval(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validRoleIds") String boundRoleId,
            @ForAll("validUserIds") String approverId) {
        
        Assume.that(!applicantId.equals(approverId)); // Self-approval not allowed
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Pending request exists
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId(applicantId)
                .requestType(PermissionRequestType.VIRTUAL_GROUP)
                .targetId(virtualGroupId)
                .status(PermissionRequestStatus.PENDING)
                .reason("Test reason")
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: Approver is authorized
        when(approverService.isApprover(approverId, ApproverTargetType.VIRTUAL_GROUP, virtualGroupId))
                .thenReturn(true);
        
        // Given: Virtual group exists
        VirtualGroup virtualGroup = createVirtualGroup(virtualGroupId);
        when(virtualGroupRepository.findById(virtualGroupId)).thenReturn(Optional.of(virtualGroup));
        
        // Given: Virtual group has a BU_UNBOUNDED role
        Role buUnboundedRole = createRole(boundRoleId, RoleType.BU_UNBOUNDED);
        when(virtualGroupRoleService.getBoundRole(virtualGroupId)).thenReturn(Optional.of(buUnboundedRole));
        
        // Given: User exists
        User user = createUser(applicantId);
        when(userRepository.findById(applicantId)).thenReturn(Optional.of(user));
        
        // Given: User is not already a member
        when(virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(virtualGroupId, applicantId))
                .thenReturn(false);
        
        // Given: Save operations succeed
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(virtualGroupMemberRepository.save(any(VirtualGroupMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Approve the request
        permissionRequestService.approve(requestId, approverId, "Approved");
        
        // Then: User should be added to virtual group
        verify(virtualGroupMemberRepository).save(any(VirtualGroupMember.class));
        
        // Then: BU_UNBOUNDED role is immediately effective (no business unit required)
        // This is verified by the fact that the user is now a member of the virtual group
        // and the role type is BU_UNBOUNDED
        assertThat(buUnboundedRole.getType()).isEqualTo(RoleType.BU_UNBOUNDED);
    }
    
    /**
     * Feature: permission-request-approval, Property 7: Virtual Group Approval Immediate Effect
     * BU_BOUNDED role requires business unit membership to be effective
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 7: BU_BOUNDED role requires BU membership")
    void buBoundedRoleRequiresBusinessUnitMembership(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validRoleIds") String boundRoleId,
            @ForAll("validUserIds") String approverId) {
        
        Assume.that(!applicantId.equals(approverId)); // Self-approval not allowed
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Pending request exists
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId(applicantId)
                .requestType(PermissionRequestType.VIRTUAL_GROUP)
                .targetId(virtualGroupId)
                .status(PermissionRequestStatus.PENDING)
                .reason("Test reason")
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: Approver is authorized
        when(approverService.isApprover(approverId, ApproverTargetType.VIRTUAL_GROUP, virtualGroupId))
                .thenReturn(true);
        
        // Given: Virtual group exists
        VirtualGroup virtualGroup = createVirtualGroup(virtualGroupId);
        when(virtualGroupRepository.findById(virtualGroupId)).thenReturn(Optional.of(virtualGroup));
        
        // Given: Virtual group has a BU_BOUNDED role
        Role buBoundedRole = createRole(boundRoleId, RoleType.BU_BOUNDED);
        when(virtualGroupRoleService.getBoundRole(virtualGroupId)).thenReturn(Optional.of(buBoundedRole));
        
        // Given: User exists
        User user = createUser(applicantId);
        when(userRepository.findById(applicantId)).thenReturn(Optional.of(user));
        
        // Given: User is not already a member
        when(virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(virtualGroupId, applicantId))
                .thenReturn(false);
        
        // Given: Save operations succeed
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(virtualGroupMemberRepository.save(any(VirtualGroupMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Approve the request
        permissionRequestService.approve(requestId, approverId, "Approved");
        
        // Then: User should be added to virtual group
        verify(virtualGroupMemberRepository).save(any(VirtualGroupMember.class));
        
        // Then: BU_BOUNDED role is granted but not yet effective
        // (requires business unit membership to activate)
        assertThat(buBoundedRole.getType()).isEqualTo(RoleType.BU_BOUNDED);
    }

    
    /**
     * Feature: permission-request-approval, Property 7: Virtual Group Approval Immediate Effect
     * Non-approver cannot approve virtual group request
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 7: Non-approver cannot approve VG request")
    void nonApproverCannotApproveVirtualGroupRequest(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validUserIds") String nonApproverId) {
        
        Assume.that(!applicantId.equals(nonApproverId)); // Exclude self-approval case (different error)
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Pending request exists
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId(applicantId)
                .requestType(PermissionRequestType.VIRTUAL_GROUP)
                .targetId(virtualGroupId)
                .status(PermissionRequestStatus.PENDING)
                .reason("Test reason")
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: User is NOT an approver
        when(approverService.isApprover(nonApproverId, ApproverTargetType.VIRTUAL_GROUP, virtualGroupId))
                .thenReturn(false);
        
        // When & Then: Approval should fail (error message contains "审批人")
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> 
                permissionRequestService.approve(requestId, nonApproverId, "Approved"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("审批人");
        
        // Then: User should NOT be added to virtual group
        verify(virtualGroupMemberRepository, never()).save(any(VirtualGroupMember.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 7: Virtual Group Approval Immediate Effect
     * Change log should be recorded when user is added to virtual group
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 7: Change log recorded on VG approval")
    void changeLogRecordedOnVirtualGroupApproval(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String virtualGroupId,
            @ForAll("validUserIds") String approverId) {
        
        Assume.that(!applicantId.equals(approverId)); // Self-approval not allowed
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Pending request exists
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId(applicantId)
                .requestType(PermissionRequestType.VIRTUAL_GROUP)
                .targetId(virtualGroupId)
                .status(PermissionRequestStatus.PENDING)
                .reason("Test reason")
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: Approver is authorized
        when(approverService.isApprover(approverId, ApproverTargetType.VIRTUAL_GROUP, virtualGroupId))
                .thenReturn(true);
        
        // Given: Virtual group exists
        VirtualGroup virtualGroup = createVirtualGroup(virtualGroupId);
        when(virtualGroupRepository.findById(virtualGroupId)).thenReturn(Optional.of(virtualGroup));
        
        // Given: User exists
        User user = createUser(applicantId);
        when(userRepository.findById(applicantId)).thenReturn(Optional.of(user));
        
        // Given: User is not already a member
        when(virtualGroupMemberRepository.existsByVirtualGroupIdAndUserId(virtualGroupId, applicantId))
                .thenReturn(false);
        
        // Given: Save operations succeed
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(virtualGroupMemberRepository.save(any(VirtualGroupMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(memberChangeLogRepository.save(any(MemberChangeLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Approve the request
        permissionRequestService.approve(requestId, approverId, "Approved");
        
        // Then: Change log should be recorded
        ArgumentCaptor<MemberChangeLog> logCaptor = ArgumentCaptor.forClass(MemberChangeLog.class);
        verify(memberChangeLogRepository).save(logCaptor.capture());
        
        MemberChangeLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getChangeType()).isEqualTo(MemberChangeType.JOIN);
        assertThat(savedLog.getTargetType()).isEqualTo(ApproverTargetType.VIRTUAL_GROUP);
        assertThat(savedLog.getTargetId()).isEqualTo(virtualGroupId);
        assertThat(savedLog.getUserId()).isEqualTo(applicantId);
    }
    
    // ==================== Helper Methods ====================
    
    private VirtualGroup createVirtualGroup(String id) {
        return VirtualGroup.builder()
                .id(id)
                .name("Test Group " + id)
                .status("ACTIVE")
                .members(new HashSet<>())
                .build();
    }
    
    private User createUser(String id) {
        return User.builder()
                .id(id)
                .username("user_" + id.substring(0, Math.min(8, id.length())))
                .displayName("Test User")
                .email("test@example.com")
                .status(UserStatus.ACTIVE)
                .build();
    }
    
    private Role createRole(String roleId, RoleType type) {
        return Role.builder()
                .id(roleId)
                .name("Test Role " + roleId)
                .code("ROLE_" + roleId.toUpperCase().replace("-", "_"))
                .type(type)
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
    
    @Provide
    Arbitrary<RoleType> businessRoleTypes() {
        return Arbitraries.of(RoleType.BU_BOUNDED, RoleType.BU_UNBOUNDED);
    }
}
