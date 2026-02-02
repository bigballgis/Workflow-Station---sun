package com.admin.properties;

import com.admin.entity.PermissionRequest;
import com.platform.security.entity.Role;
import com.platform.security.entity.VirtualGroupMember;
import com.admin.enums.*;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.*;
import com.admin.service.*;
import com.admin.util.EntityTypeConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property 5: Duplicate Request Prevention
 * *For any* user attempting to create a permission request, the system shall reject the request
 * if a pending request of the same type for the same target already exists.
 * 
 * Property 6: Request Status Transition
 * *For any* permission request, the status shall only transition from PENDING to APPROVED, REJECTED, or CANCELLED.
 * 
 * Property 17: Business Unit Application Restricted to BU-Bounded Role Associations
 * *For any* user attempting to apply for a Business Unit, the system shall only allow the application
 * if the user has at least one BU_BOUNDED role (obtained through Virtual Group membership).
 * 
 * **Validates: Requirements 7.5, 8.1, 8.4, 8.10, 9.4, 9.5, 13.2, 13.3**
 */
public class PermissionRequestProperties {
    
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
    
    // ==================== Property 5: Duplicate Request Prevention ====================
    
    /**
     * Feature: permission-request-approval, Property 5: Duplicate Request Prevention
     * Creating a virtual group request when pending request exists should be rejected
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 5: Duplicate virtual group request should be rejected")
    void duplicateVirtualGroupRequestShouldBeRejected(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String virtualGroupId) {
        
        // Given: Virtual group exists and has approver
        when(virtualGroupRepository.existsById(virtualGroupId)).thenReturn(true);
        when(approverService.hasApprover(ApproverTargetType.VIRTUAL_GROUP, virtualGroupId)).thenReturn(true);
        
        // Given: Pending request already exists
        when(permissionRequestRepository.existsByApplicantIdAndTargetIdAndRequestTypeAndStatus(
                applicantId, virtualGroupId, PermissionRequestType.VIRTUAL_GROUP, PermissionRequestStatus.PENDING))
                .thenReturn(true);
        
        // When & Then: Creating duplicate request should throw exception
        assertThatThrownBy(() -> 
                permissionRequestService.createVirtualGroupRequest(applicantId, virtualGroupId, "Test reason"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("已存在待审批");
        
        // Then: No new request should be saved
        verify(permissionRequestRepository, never()).save(any(PermissionRequest.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 5: Duplicate Request Prevention
     * Creating a business unit request when pending request exists should be rejected
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 5: Duplicate business unit request should be rejected")
    void duplicateBusinessUnitRequestShouldBeRejected(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String businessUnitId) {
        
        // Given: Business unit exists and has approver
        when(businessUnitRepository.existsById(businessUnitId)).thenReturn(true);
        when(approverService.hasApprover(ApproverTargetType.BUSINESS_UNIT, businessUnitId)).thenReturn(true);
        
        // Given: User has BU_BOUNDED role
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(applicantId))
                .thenReturn(List.of("vg-1"));
        Role buBoundedRole = createRole("role-1", RoleType.BU_BOUNDED);
        when(virtualGroupRoleService.getBoundRole("vg-1")).thenReturn(Optional.of(buBoundedRole));
        
        // Given: Pending request already exists
        when(permissionRequestRepository.existsByApplicantIdAndTargetIdAndRequestTypeAndStatus(
                applicantId, businessUnitId, PermissionRequestType.BUSINESS_UNIT, PermissionRequestStatus.PENDING))
                .thenReturn(true);
        
        // When & Then: Creating duplicate request should throw exception
        assertThatThrownBy(() -> 
                permissionRequestService.createBusinessUnitRequest(applicantId, businessUnitId, "Test reason"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("已存在待审批");
        
        // Then: No new request should be saved
        verify(permissionRequestRepository, never()).save(any(PermissionRequest.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 5: Duplicate Request Prevention
     * Creating request when no pending request exists should succeed
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 5: Non-duplicate request should succeed")
    void nonDuplicateRequestShouldSucceed(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String virtualGroupId) {
        
        // Given: Virtual group exists and has approver
        when(virtualGroupRepository.existsById(virtualGroupId)).thenReturn(true);
        when(approverService.hasApprover(ApproverTargetType.VIRTUAL_GROUP, virtualGroupId)).thenReturn(true);
        
        // Given: No pending request exists
        when(permissionRequestRepository.existsByApplicantIdAndTargetIdAndRequestTypeAndStatus(
                applicantId, virtualGroupId, PermissionRequestType.VIRTUAL_GROUP, PermissionRequestStatus.PENDING))
                .thenReturn(false);
        
        // Given: Save succeeds
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Create request
        PermissionRequest result = permissionRequestService.createVirtualGroupRequest(
                applicantId, virtualGroupId, "Test reason");
        
        // Then: Request should be created with PENDING status
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PermissionRequestStatus.PENDING);
        verify(permissionRequestRepository).save(any(PermissionRequest.class));
    }
    
    // ==================== Property 17: BU-Bounded Role Restriction ====================
    
    /**
     * Feature: permission-request-approval, Property 17: BU-Bounded Role Restriction
     * User without BU_BOUNDED role should not be able to apply for business unit
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 17: User without BU_BOUNDED role cannot apply for BU")
    void userWithoutBuBoundedRoleCannotApplyForBusinessUnit(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String businessUnitId) {
        
        // Given: Business unit exists and has approver
        when(businessUnitRepository.existsById(businessUnitId)).thenReturn(true);
        when(approverService.hasApprover(ApproverTargetType.BUSINESS_UNIT, businessUnitId)).thenReturn(true);
        
        // Given: User has no virtual group membership (no roles)
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(applicantId))
                .thenReturn(List.of());
        
        // When & Then: Creating request should throw exception
        assertThatThrownBy(() -> 
                permissionRequestService.createBusinessUnitRequest(applicantId, businessUnitId, "Test reason"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("BU-Bounded");
        
        // Then: No request should be saved
        verify(permissionRequestRepository, never()).save(any(PermissionRequest.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 17: BU-Bounded Role Restriction
     * User with only BU_UNBOUNDED role should not be able to apply for business unit
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 17: User with only BU_UNBOUNDED role cannot apply for BU")
    void userWithOnlyBuUnboundedRoleCannotApplyForBusinessUnit(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String businessUnitId) {
        
        // Given: Business unit exists and has approver
        when(businessUnitRepository.existsById(businessUnitId)).thenReturn(true);
        when(approverService.hasApprover(ApproverTargetType.BUSINESS_UNIT, businessUnitId)).thenReturn(true);
        
        // Given: User has virtual group with BU_UNBOUNDED role only
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(applicantId))
                .thenReturn(List.of("vg-1"));
        Role buUnboundedRole = createRole("role-1", RoleType.BU_UNBOUNDED);
        when(virtualGroupRoleService.getBoundRole("vg-1")).thenReturn(Optional.of(buUnboundedRole));
        
        // When & Then: Creating request should throw exception
        assertThatThrownBy(() -> 
                permissionRequestService.createBusinessUnitRequest(applicantId, businessUnitId, "Test reason"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("BU-Bounded");
        
        // Then: No request should be saved
        verify(permissionRequestRepository, never()).save(any(PermissionRequest.class));
    }
    
    /**
     * Feature: permission-request-approval, Property 17: BU-Bounded Role Restriction
     * User with BU_BOUNDED role should be able to apply for business unit
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 17: User with BU_BOUNDED role can apply for BU")
    void userWithBuBoundedRoleCanApplyForBusinessUnit(
            @ForAll("validUserIds") String applicantId,
            @ForAll("validTargetIds") String businessUnitId) {
        
        // Given: Business unit exists and has approver
        when(businessUnitRepository.existsById(businessUnitId)).thenReturn(true);
        when(approverService.hasApprover(ApproverTargetType.BUSINESS_UNIT, businessUnitId)).thenReturn(true);
        
        // Given: User has virtual group with BU_BOUNDED role
        when(virtualGroupMemberRepository.findVirtualGroupIdsByUserId(applicantId))
                .thenReturn(List.of("vg-1"));
        Role buBoundedRole = createRole("role-1", RoleType.BU_BOUNDED);
        when(virtualGroupRoleService.getBoundRole("vg-1")).thenReturn(Optional.of(buBoundedRole));
        
        // Given: No pending request exists
        when(permissionRequestRepository.existsByApplicantIdAndTargetIdAndRequestTypeAndStatus(
                applicantId, businessUnitId, PermissionRequestType.BUSINESS_UNIT, PermissionRequestStatus.PENDING))
                .thenReturn(false);
        
        // Given: Save succeeds
        when(permissionRequestRepository.save(any(PermissionRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Create request
        PermissionRequest result = permissionRequestService.createBusinessUnitRequest(
                applicantId, businessUnitId, "Test reason");
        
        // Then: Request should be created
        assertThat(result).isNotNull();
        assertThat(result.getRequestType()).isEqualTo(PermissionRequestType.BUSINESS_UNIT);
        verify(permissionRequestRepository).save(any(PermissionRequest.class));
    }
    
    // ==================== Property 6: Request Status Transition ====================
    
    /**
     * Feature: permission-request-approval, Property 6: Request Status Transition
     * Only PENDING requests can be approved
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 6: Only PENDING requests can be approved")
    void onlyPendingRequestsCanBeApproved(
            @ForAll("nonPendingStatuses") PermissionRequestStatus status,
            @ForAll("validUserIds") String approverId) {
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Request exists with non-PENDING status
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId("applicant-1")
                .requestType(PermissionRequestType.VIRTUAL_GROUP)
                .targetId("vg-1")
                .status(status)
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // When & Then: Approving should throw exception
        assertThatThrownBy(() -> permissionRequestService.approve(requestId, approverId, "Approved"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("已处理");
    }
    
    /**
     * Feature: permission-request-approval, Property 6: Request Status Transition
     * Only PENDING requests can be rejected
     */
    @Property(tries = 100)
    @Label("Feature: permission-request-approval, Property 6: Only PENDING requests can be rejected")
    void onlyPendingRequestsCanBeRejected(
            @ForAll("nonPendingStatuses") PermissionRequestStatus status,
            @ForAll("validUserIds") String approverId) {
        
        String requestId = UUID.randomUUID().toString();
        
        // Given: Request exists with non-PENDING status
        PermissionRequest request = PermissionRequest.builder()
                .id(requestId)
                .applicantId("applicant-1")
                .requestType(PermissionRequestType.VIRTUAL_GROUP)
                .targetId("vg-1")
                .status(status)
                .build();
        when(permissionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        
        // When & Then: Rejecting should throw exception
        assertThatThrownBy(() -> permissionRequestService.reject(requestId, approverId, "Rejected"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("已处理");
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
    Arbitrary<PermissionRequestStatus> nonPendingStatuses() {
        return Arbitraries.of(
                PermissionRequestStatus.APPROVED,
                PermissionRequestStatus.REJECTED,
                PermissionRequestStatus.CANCELLED);
    }
}
