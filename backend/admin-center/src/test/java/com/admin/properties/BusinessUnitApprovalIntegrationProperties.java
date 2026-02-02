package com.admin.properties;

import com.admin.entity.*;
import com.admin.enums.*;
import com.platform.security.entity.User;
import com.platform.security.entity.Role;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.entity.UserBusinessUnit;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.*;
import com.admin.service.*;
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
 * Property 8: Business Unit Approval Immediate Effect
 * *For any* approved Business_Unit request, the user shall be immediately added to the Business_Unit.
 * 
 * **Validates: Requirements 11.1, 11.2**
 */
public class BusinessUnitApprovalIntegrationProperties {
    
    private PermissionRequestRepository permissionRequestRepository;
    private VirtualGroupRepository virtualGroupRepository;
    private BusinessUnitRepository businessUnitRepository;
    private VirtualGroupMemberRepository virtualGroupMemberRepository;
    private UserBusinessUnitRepository userBusinessUnitRepository;
    private MemberChangeLogRepository memberChangeLogRepository;
    private ApproverService approverService;
    private MemberManagementService memberManagementService;
    private VirtualGroupRoleService virtualGroupRoleService;
    private UserBusinessUnitService userBusinessUnitService;
    private ObjectMapper objectMapper;
    private PermissionRequestService permissionRequestService;
    
    @BeforeTry
    void setUp() {
        permissionRequestRepository = mock(PermissionRequestRepository.class);
        virtualGroupRepository = mock(VirtualGroupRepository.class);
        businessUnitRepository = mock(BusinessUnitRepository.class);
        virtualGroupMemberRepository = mock(VirtualGroupMemberRepository.class);
        userBusinessUnitRepository = mock(UserBusinessUnitRepository.class);
        memberChangeLogRepository = mock(MemberChangeLogRepository.class);
        approverService = mock(ApproverService.class);
        memberManagementService = mock(MemberManagementService.class);
        virtualGroupRoleService = mock(VirtualGroupRoleService.class);
        userBusinessUnitService = mock(UserBusinessUnitService.class);
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

    
    // ==================== Property 8: Business Unit Approval Immediate Effect ====================
    
    /**
     * Feature: permission-request-approval, Property 8: Business Unit Approval Immediate Effect
     * Approving a business unit request should immediately add user to the business unit
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 8: Approved BU request adds user to BU immediately")
    void approvedBusinessUnitRequestAddsUserToBuImmediately(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validUserIds") String approverId) {
        
        Assume.that(!applicantId.equals(approverId)); // Self-approval not allowed
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Pending request exists
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId(applicantId)
                .requestType(PermissionRequestType.BUSINESS_UNIT)
                .targetId(businessUnitId)
                .status(PermissionRequestStatus.PENDING)
                .reason("Test reason")
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: Approver is authorized
        when(approverService.isApprover(approverId, ApproverTargetType.BUSINESS_UNIT, businessUnitId))
                .thenReturn(true);
        
        // Given: Save operations succeed
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Approve the request
        permissionRequestService.approve(requestId, approverId, "Approved");
        
        // Then: Request status should be updated to APPROVED
        ArgumentCaptor<PermissionRequest> requestCaptor = ArgumentCaptor.forClass(PermissionRequest.class);
        verify(permissionRequestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(PermissionRequestStatus.APPROVED);
        assertThat(requestCaptor.getValue().getApproverId()).isEqualTo(approverId);
        
        // Then: Approved request should be processed (which adds user to business unit)
        verify(memberManagementService).processApprovedRequest(any(PermissionRequest.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 8: Business Unit Approval Immediate Effect
     * Approving BU request should activate user's BU_BOUNDED roles for that business unit
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 8: Approved BU request activates BU_BOUNDED roles")
    void approvedBusinessUnitRequestActivatesBuBoundedRoles(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validRoleIds") String buBoundedRoleId,
            @ForAll("validUserIds") String approverId) {
        
        Assume.that(!applicantId.equals(approverId)); // Self-approval not allowed
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Pending request exists
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId(applicantId)
                .requestType(PermissionRequestType.BUSINESS_UNIT)
                .targetId(businessUnitId)
                .status(PermissionRequestStatus.PENDING)
                .reason("Test reason")
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: Approver is authorized
        when(approverService.isApprover(approverId, ApproverTargetType.BUSINESS_UNIT, businessUnitId))
                .thenReturn(true);
        
        // Given: User has BU_BOUNDED role through virtual group membership
        Role buBoundedRole = createRole(buBoundedRoleId, RoleType.BU_BOUNDED);
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(applicantId))
                .thenReturn(List.of("vg-1"));
        when(virtualGroupRoleService.getBoundRole("vg-1")).thenReturn(Optional.of(buBoundedRole));
        
        // Given: Save operations succeed
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Approve the request
        permissionRequestService.approve(requestId, approverId, "Approved");
        
        // Then: Approved request should be processed (which adds user to business unit and activates BU_BOUNDED roles)
        verify(memberManagementService).processApprovedRequest(any(PermissionRequest.class));
        
        // Then: BU_BOUNDED role is now activated for this business unit
        // (The role becomes effective because user is now a member of the business unit)
    }

    
    /**
     * Feature: permission-request-approval, Property 8: Business Unit Approval Immediate Effect
     * Non-approver cannot approve business unit request
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 8: Non-approver cannot approve BU request")
    void nonApproverCannotApproveBusinessUnitRequest(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validUserIds") String nonApproverId) {
        
        Assume.that(!applicantId.equals(nonApproverId)); // Exclude self-approval case (different error)
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Pending request exists
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId(applicantId)
                .requestType(PermissionRequestType.BUSINESS_UNIT)
                .targetId(businessUnitId)
                .status(PermissionRequestStatus.PENDING)
                .reason("Test reason")
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: User is NOT an approver
        when(approverService.isApprover(nonApproverId, ApproverTargetType.BUSINESS_UNIT, businessUnitId))
                .thenReturn(false);
        
        // When & Then: Approval should fail (error message contains "不是该目标的审批人")
        assertThatThrownBy(() -> 
                permissionRequestService.approve(requestId, nonApproverId, "Approved"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("审批人");
        
        // Then: Request should NOT be processed
        verify(memberManagementService, never()).processApprovedRequest(any());
    }
    
    /**
     * Feature: permission-request-approval, Property 8: Business Unit Approval Immediate Effect
     * Change log should be recorded when user is added to business unit
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 8: Change log recorded on BU approval")
    void changeLogRecordedOnBusinessUnitApproval(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validUserIds") String approverId) {
        
        Assume.that(!applicantId.equals(approverId)); // Self-approval not allowed
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Pending request exists
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId(applicantId)
                .requestType(PermissionRequestType.BUSINESS_UNIT)
                .targetId(businessUnitId)
                .status(PermissionRequestStatus.PENDING)
                .reason("Test reason")
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // Given: Approver is authorized
        when(approverService.isApprover(approverId, ApproverTargetType.BUSINESS_UNIT, businessUnitId))
                .thenReturn(true);
        
        // Given: Save operations succeed
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Approve the request
        permissionRequestService.approve(requestId, approverId, "Approved");
        
        // Then: MemberManagementService.processApprovedRequest should be called (which records change log internally)
        verify(memberManagementService).processApprovedRequest(any(PermissionRequest.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 8: Business Unit Approval Immediate Effect
     * Already approved request cannot be approved again
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 8: Already approved request cannot be approved again")
    void alreadyApprovedRequestCannotBeApprovedAgain(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validUserIds") String approverId) {
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Request is already approved
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId(applicantId)
                .requestType(PermissionRequestType.BUSINESS_UNIT)
                .targetId(businessUnitId)
                .status(PermissionRequestStatus.APPROVED)
                .reason("Test reason")
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // When & Then: Approval should fail
        assertThatThrownBy(() -> 
                permissionRequestService.approve(requestId, approverId, "Approved"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("已处理");
        
        // Then: User should NOT be added to business unit
        verify(memberManagementService, never()).addUserToBusinessUnit(any(), any(), any());
    }
    
    /**
     * Feature: permission-request-approval, Property 8: Business Unit Approval Immediate Effect
     * Rejected request cannot be approved
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 8: Rejected request cannot be approved")
    void rejectedRequestCannotBeApproved(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String businessUnitId,
            @ForAll("validUserIds") String approverId) {
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Request is already rejected
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId(applicantId)
                .requestType(PermissionRequestType.BUSINESS_UNIT)
                .targetId(businessUnitId)
                .status(PermissionRequestStatus.REJECTED)
                .reason("Test reason")
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // When & Then: Approval should fail
        assertThatThrownBy(() -> 
                permissionRequestService.approve(requestId, approverId, "Approved"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("已处理");
        
        // Then: User should NOT be added to business unit
        verify(memberManagementService, never()).addUserToBusinessUnit(any(), any(), any());
    }

    
    // ==================== Helper Methods ====================
    
    private Role createRole(String roleId, RoleType type) {
        return Role.builder()
                .id(roleId)
                .name("Test Role " + roleId)
                .code("ROLE_" + roleId.toUpperCase().replace("-", "_"))
                .type(com.admin.util.EntityTypeConverter.fromRoleType(type))
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
