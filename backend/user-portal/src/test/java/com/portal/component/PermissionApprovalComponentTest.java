package com.portal.component;

import com.platform.common.i18n.I18nService;
import com.portal.entity.PermissionRequest;
import com.portal.enums.PermissionRequestStatus;
import com.portal.enums.PermissionRequestType;
import com.portal.repository.PermissionRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionApprovalComponentTest {

    @Mock
    private PermissionRequestRepository permissionRequestRepository;
    @Mock
    private RoleAccessComponent roleAccessComponent;
    @Mock
    private VirtualGroupAccessComponent virtualGroupAccessComponent;
    @Mock
    private I18nService i18nService;
    @Mock
    private PermissionRequestEnrichmentComponent enrichmentComponent;

    private PermissionApprovalComponent component;

    @BeforeEach
    void setUp() {
        component = new PermissionApprovalComponent(
                permissionRequestRepository,
                roleAccessComponent,
                virtualGroupAccessComponent,
                i18nService,
                enrichmentComponent);
    }

    @Test
    void buJoinAddMemberFailureLeavesPendingAndThrows() {
        PermissionRequest request = pendingLeaderJoin();
        when(permissionRequestRepository.findById(2L)).thenReturn(Optional.of(request));
        when(virtualGroupAccessComponent.isApproverForBusinessUnit("approver", "bu-1")).thenReturn(true);
        when(virtualGroupAccessComponent.isUserInBusinessUnit("applicant", "bu-1")).thenReturn(false);
        when(virtualGroupAccessComponent.addUserToBusinessUnit(eq("applicant"), eq("bu-1"), anyString()))
                .thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> component.approveRequest(2L, "approver", "ok"));

        assertEquals("Failed to add user to business unit", ex.getMessage());
        assertEquals(PermissionRequestStatus.PENDING, request.getStatus());
        verify(permissionRequestRepository, never()).save(any());
        verify(virtualGroupAccessComponent, never()).assignUserBusinessUnitRole(
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void buJoinSuccessPersistsApproved() {
        PermissionRequest request = pendingLeaderJoin();
        when(permissionRequestRepository.findById(2L)).thenReturn(Optional.of(request));
        when(virtualGroupAccessComponent.isApproverForBusinessUnit("approver", "bu-1")).thenReturn(true);
        when(virtualGroupAccessComponent.isUserInBusinessUnit("applicant", "bu-1")).thenReturn(false);
        when(virtualGroupAccessComponent.addUserToBusinessUnit(eq("applicant"), eq("bu-1"), anyString()))
                .thenReturn(true);
        when(virtualGroupAccessComponent.assignUserBusinessUnitRole(
                "applicant", "bu-1", "role-1", "LEADER")).thenReturn(true);
        when(virtualGroupAccessComponent.getVirtualGroupIdByBoundRoleId("role-1")).thenReturn(null);
        when(permissionRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PermissionRequest saved = component.approveRequest(2L, "approver", "ok");

        assertEquals(PermissionRequestStatus.APPROVED, saved.getStatus());
        assertEquals("approver", saved.getApproverId());
    }

    private static PermissionRequest pendingLeaderJoin() {
        return PermissionRequest.builder()
                .id(2L)
                .applicantId("applicant")
                .requestType(PermissionRequestType.BUSINESS_UNIT_JOIN)
                .businessUnitId("bu-1")
                .roleId("role-1")
                .membershipType("LEADER")
                .status(PermissionRequestStatus.PENDING)
                .reason("need leader")
                .build();
    }
}
