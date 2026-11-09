package com.portal.controller;

import com.platform.common.dto.UserPrincipal;
import com.platform.common.i18n.I18nService;
import com.portal.client.AdminCenterClient;
import com.portal.component.BusinessUnitMemberQuery;
import com.portal.component.VirtualGroupAccessComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Member management: the BU member list and BU-role removal (a trusted service call to Admin Center) are limited to
 * an approver of the business unit (from the authenticated session); virtual groups, and removing a member from a
 * whole business unit, are not available in the portal at all (platform access model §5.5).
 */
class MemberControllerApproverGuardTest {

    private final AdminCenterClient adminCenterClient = mock(AdminCenterClient.class);
    private final VirtualGroupAccessComponent access = mock(VirtualGroupAccessComponent.class);
    private final BusinessUnitMemberQuery members = mock(BusinessUnitMemberQuery.class);
    private final MemberController controller =
            new MemberController(adminCenterClient, access, members, mock(I18nService.class));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousCallerIsRefusedBeforeAdminCenterIsCalled() {
        assertThat(controller.removeBusinessUnitRole("bu-1", "victim", "role-1", null).getStatusCode().value())
                .isEqualTo(401);
        assertThat(controller.getBusinessUnitMembers("bu-1").getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(adminCenterClient, members);
    }

    @Test
    void signedInUserWhoIsNotTheApproverIsRefused() {
        signIn("u-1");
        when(access.isApproverForBusinessUnit("u-1", "bu-1")).thenReturn(false);

        assertThat(controller.removeBusinessUnitRole("bu-1", "victim", "role-1", null).getStatusCode().value())
                .isEqualTo(403);
        assertThat(controller.getBusinessUnitMembers("bu-1").getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(adminCenterClient, members);
    }

    /** The list names each member's roles in the unit, the ones the page lets the approver remove. */
    @Test
    void approverOfTheBusinessUnitSeesItsMembersWithTheirRoles() {
        signIn("approver-1");
        when(access.isApproverForBusinessUnit("approver-1", "bu-1")).thenReturn(true);
        List<Map<String, Object>> list = List.of(Map.of("userId", "u-9",
                "roles", List.of(Map.of("id", "role-1", "name", "Clerk"))));
        when(members.members("bu-1")).thenReturn(list);

        var response = controller.getBusinessUnitMembers("bu-1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getData()).isEqualTo(list);
        verifyNoInteractions(adminCenterClient);
    }

    @Test
    void approverOfTheBusinessUnitRemovesARole() {
        signIn("approver-1");
        when(access.isApproverForBusinessUnit("approver-1", "bu-1")).thenReturn(true);
        when(adminCenterClient.removeUserBusinessUnitRole(anyString(), anyString(), anyString())).thenReturn(true);

        assertThat(controller.removeBusinessUnitRole("bu-1", "u-9", "role-1", null).getStatusCode().value())
                .isEqualTo(200);
        verify(adminCenterClient).removeUserBusinessUnitRole("u-9", "bu-1", "role-1");
    }

    @Test
    void groupAndWholeUnitMemberRemovalAreNotAvailableEvenToAnApprover() {
        signIn("approver-1");
        when(access.isApproverForBusinessUnit("approver-1", "bu-1")).thenReturn(true);

        assertThat(controller.removeVirtualGroupMember("vg-finance", "u-9").getStatusCode().value()).isEqualTo(403);
        assertThat(controller.removeBusinessUnitMember("bu-1", "u-9").getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(adminCenterClient);
    }

    private static void signIn(String userId) {
        UserPrincipal principal = UserPrincipal.builder().userId(userId).username(userId).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
