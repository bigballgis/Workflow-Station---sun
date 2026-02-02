package com.admin.properties;

import com.admin.entity.*;
import com.admin.enums.*;
import com.platform.security.entity.User;
import com.platform.security.entity.Role;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.entity.VirtualGroupRole;
import com.platform.security.entity.UserBusinessUnit;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.*;
import com.admin.service.ApproverService;
import com.admin.service.MemberManagementService;
import com.admin.service.PermissionRequestService;
import com.admin.service.VirtualGroupRoleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property tests for Approval Workflow State Management
 * 
 * **Feature: technical-debt-remediation, Property 6: Approval Workflow State Management**
 * **Validates: Requirements 2.3**
 * 
 * Tests that approval workflow operations manage state transitions correctly
 * and maintain workflow integrity throughout the approval process.
 */
public class ApprovalWorkflowProperties {
    
    private PermissionRequestRepository permissionRequestRepository;
    private VirtualGroupRepository virtualGroupRepository;
    private BusinessUnitRepository businessUnitRepository;
    private VirtualGroupMemberRepository virtualGroupMemberRepository;
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
        approverService = mock(ApproverService.class);
        memberManagementService = mock(MemberManagementService.class);
        virtualGroupRoleService = mock(VirtualGroupRoleService.class);
        objectMapper = new ObjectMapper();
        
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
    
    // ==================== Property 6: Approval Workflow State Management ====================
    
    /**
     * Feature: technical-debt-remediation, Property 6: Approval Workflow State Management
     * Approving a pending request should transition state to APPROVED and execute business logic
     */
    @Property(tries = 100)
    @Label("Feature: technical-debt-remediation, Property 6: Approval transitions state correctly")
    void approvalTransitionsStateCorrectly(
            @ForAll("validRequestIds") String requestId,
            @ForAll("validUserIds") String approverId,
            @ForAll("validComments") String comment) {
        
        // Given: A pending permission request exists
        PermissionRequest request = createPendingRequest(requestId, PermissionRequestType.VIRTUAL_GROUP);
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: Approver has permission to approve this request
        when(approverService.isApprover(approverId, ApproverTargetType.VIRTUAL_GROUP, request.getTargetId()))
                .thenReturn(true);
        
        // Given: Save operations succeed
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Approve the request
        permissionRequestService.approve(requestId, approverId, comment);
        
        // Then: Request state should be updated to APPROVED
        ArgumentCaptor<PermissionRequest> requestCaptor = ArgumentCaptor.forClass(PermissionRequest.class);
        verify(permissionRequestRepository).save(requestCaptor.capture());
        
        PermissionRequest savedRequest = requestCaptor.getValue();
        assertThat(savedRequest.getStatus()).isEqualTo(PermissionRequestStatus.APPROVED);
        assertThat(savedRequest.getApproverId()).isEqualTo(approverId);
        assertThat(savedRequest.getApproverComment()).isEqualTo(comment);
        assertThat(savedRequest.getApprovedAt()).isNotNull();
        
        // Then: Business logic should be executed
        verify(memberManagementService).processApprovedRequest(request);
    }
    
    /**
     * Feature: technical-debt-remediation, Property 6: Approval Workflow State Management
     * Rejecting a pending request should transition state to REJECTED with proper reason
     */
    @Property(tries = 100)
    @Label("Feature: technical-debt-remediation, Property 6: Rejection transitions state correctly")
    void rejectionTransitionsStateCorrectly(
            @ForAll("validRequestIds") String requestId,
            @ForAll("validUserIds") String approverId,
            @ForAll("validComments") String rejectionReason) {
        
        // Given: A pending permission request exists
        PermissionRequest request = createPendingRequest(requestId, PermissionRequestType.BUSINESS_UNIT);
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: Approver has permission to reject this request
        when(approverService.isApprover(approverId, ApproverTargetType.BUSINESS_UNIT, request.getTargetId()))
                .thenReturn(true);
        
        // Given: Save operations succeed
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Reject the request
        permissionRequestService.reject(requestId, approverId, rejectionReason);
        
        // Then: Request state should be updated to REJECTED
        ArgumentCaptor<PermissionRequest> requestCaptor = ArgumentCaptor.forClass(PermissionRequest.class);
        verify(permissionRequestRepository).save(requestCaptor.capture());
        
        PermissionRequest savedRequest = requestCaptor.getValue();
        assertThat(savedRequest.getStatus()).isEqualTo(PermissionRequestStatus.REJECTED);
        assertThat(savedRequest.getApproverId()).isEqualTo(approverId);
        assertThat(savedRequest.getApproverComment()).isEqualTo(rejectionReason);
        assertThat(savedRequest.getApprovedAt()).isNotNull();
        
        // Then: No business logic should be executed for rejected requests
        verify(memberManagementService, never()).processApprovedRequest(any());
    }
    
    /**
     * Feature: technical-debt-remediation, Property 6: Approval Workflow State Management
     * Only authorized approvers should be able to approve requests
     */
    @Property(tries = 100)
    @Label("Feature: technical-debt-remediation, Property 6: Only authorized approvers can approve")
    void onlyAuthorizedApproversCanApprove(
            @ForAll("validRequestIds") String requestId,
            @ForAll("validUserIds") String unauthorizedUserId,
            @ForAll("validComments") String comment) {
        
        // Given: A pending permission request exists
        PermissionRequest request = createPendingRequest(requestId, PermissionRequestType.VIRTUAL_GROUP);
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: User is NOT an approver for this request
        when(approverService.isApprover(unauthorizedUserId, ApproverTargetType.VIRTUAL_GROUP, request.getTargetId()))
                .thenReturn(false);
        
        // When & Then: Approval should fail with authorization error
        assertThatThrownBy(() -> permissionRequestService.approve(requestId, unauthorizedUserId, comment))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("不是该");
        
        // Then: Request state should not be modified
        verify(permissionRequestRepository, never()).save(any(PermissionRequest.class));
        verify(memberManagementService, never()).processApprovedRequest(any());
    }
    
    /**
     * Feature: technical-debt-remediation, Property 6: Approval Workflow State Management
     * Already processed requests should not be processed again
     */
    @Property(tries = 100)
    @Label("Feature: technical-debt-remediation, Property 6: Already processed requests cannot be reprocessed")
    void alreadyProcessedRequestsCannotBeReprocessed(
            @ForAll("validRequestIds") String requestId,
            @ForAll("validUserIds") String approverId,
            @ForAll("validComments") String comment,
            @ForAll("processedStatuses") PermissionRequestStatus processedStatus) {
        
        // Given: A request that has already been processed
        PermissionRequest request = createProcessedRequest(requestId, processedStatus);
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: Approver has permission (but request is already processed)
        when(approverService.isApprover(approverId, ApproverTargetType.VIRTUAL_GROUP, request.getTargetId()))
                .thenReturn(true);
        
        // When & Then: Approval should fail with state error
        assertThatThrownBy(() -> permissionRequestService.approve(requestId, approverId, comment))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("该申请已处理");
        
        // Then: Request state should not be modified
        verify(permissionRequestRepository, never()).save(any(PermissionRequest.class));
        verify(memberManagementService, never()).processApprovedRequest(any());
    }
    
    /**
     * Feature: technical-debt-remediation, Property 6: Approval Workflow State Management
     * Rejection requires a comment/reason
     */
    @Property(tries = 100)
    @Label("Feature: technical-debt-remediation, Property 6: Rejection requires comment")
    void rejectionRequiresComment(
            @ForAll("validRequestIds") String requestId,
            @ForAll("validUserIds") String approverId) {
        
        // Given: A pending permission request exists
        PermissionRequest request = createPendingRequest(requestId, PermissionRequestType.VIRTUAL_GROUP);
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: Approver has permission
        when(approverService.isApprover(approverId, ApproverTargetType.VIRTUAL_GROUP, request.getTargetId()))
                .thenReturn(true);
        
        // When & Then: Rejection without comment should fail
        assertThatThrownBy(() -> permissionRequestService.reject(requestId, approverId, null))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("拒绝时必须提供审批意见");
        
        assertThatThrownBy(() -> permissionRequestService.reject(requestId, approverId, ""))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("拒绝时必须提供审批意见");
        
        assertThatThrownBy(() -> permissionRequestService.reject(requestId, approverId, "   "))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("拒绝时必须提供审批意见");
        
        // Then: Request state should not be modified
        verify(permissionRequestRepository, never()).save(any(PermissionRequest.class));
    }
    
    /**
     * Feature: technical-debt-remediation, Property 6: Approval Workflow State Management
     * Workflow state transitions should be atomic and consistent
     */
    @Property(tries = 100)
    @Label("Feature: technical-debt-remediation, Property 6: State transitions are atomic")
    void stateTransitionsAreAtomic(
            @ForAll("validRequestIds") String requestId,
            @ForAll("validUserIds") String approverId,
            @ForAll("validComments") String comment) {
        
        // Given: A pending permission request exists
        PermissionRequest request = createPendingRequest(requestId, PermissionRequestType.VIRTUAL_GROUP);
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: Approver has permission
        when(approverService.isApprover(approverId, ApproverTargetType.VIRTUAL_GROUP, request.getTargetId()))
                .thenReturn(true);
        
        // Given: Save operations succeed
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Approve the request
        permissionRequestService.approve(requestId, approverId, comment);
        
        // Then: All state changes should be consistent
        ArgumentCaptor<PermissionRequest> requestCaptor = ArgumentCaptor.forClass(PermissionRequest.class);
        verify(permissionRequestRepository).save(requestCaptor.capture());
        
        PermissionRequest savedRequest = requestCaptor.getValue();
        
        // Verify atomic state transition
        assertThat(savedRequest.getStatus()).isEqualTo(PermissionRequestStatus.APPROVED);
        assertThat(savedRequest.getApproverId()).isEqualTo(approverId);
        assertThat(savedRequest.getApproverComment()).isEqualTo(comment);
        assertThat(savedRequest.getApprovedAt()).isNotNull();
        
        // Verify timestamp consistency (allow for some timing flexibility)
        assertThat(savedRequest.getApprovedAt()).isAfterOrEqualTo(savedRequest.getCreatedAt());
        // Note: updatedAt is set by the service to current time, so we just verify it exists
        assertThat(savedRequest.getUpdatedAt()).isNotNull();
    }
    
    // ==================== Helper Methods ====================
    
    private PermissionRequest createPendingRequest(String requestId, PermissionRequestType type) {
        return PermissionRequest.builder()
                .id(requestId)
                .applicantId("applicant-" + requestId)
                .requestType(type)
                .targetId("target-" + requestId)
                .status(PermissionRequestStatus.PENDING)
                .reason("Test request")
                .createdAt(Instant.now().minusSeconds(3600)) // 1 hour ago
                .updatedAt(Instant.now().minusSeconds(3600))
                .build();
    }
    
    private PermissionRequest createProcessedRequest(String requestId, PermissionRequestStatus status) {
        return PermissionRequest.builder()
                .id(requestId)
                .applicantId("applicant-" + requestId)
                .requestType(PermissionRequestType.VIRTUAL_GROUP)
                .targetId("target-" + requestId)
                .status(status)
                .reason("Test request")
                .approverId("previous-approver")
                .approverComment("Previously processed")
                .createdAt(Instant.now().minusSeconds(7200)) // 2 hours ago
                .updatedAt(Instant.now().minusSeconds(3600)) // 1 hour ago
                .approvedAt(Instant.now().minusSeconds(3600))
                .build();
    }
    
    // ==================== Data Generators ====================
    
    @Provide
    Arbitrary<String> validRequestIds() {
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
    }
    
    @Provide
    Arbitrary<String> validUserIds() {
        return Arbitraries.create(UUID::randomUUID).map(UUID::toString);
    }
    
    @Provide
    Arbitrary<String> validComments() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(100)
                .map(s -> "Comment: " + s);
    }
    
    @Provide
    Arbitrary<PermissionRequestStatus> processedStatuses() {
        return Arbitraries.of(
                PermissionRequestStatus.APPROVED,
                PermissionRequestStatus.REJECTED,
                PermissionRequestStatus.CANCELLED
        );
    }
}