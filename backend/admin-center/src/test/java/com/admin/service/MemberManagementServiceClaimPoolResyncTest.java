package com.admin.service;

import com.admin.repository.MemberChangeLogRepository;
import com.admin.repository.UserBusinessUnitRepository;
import com.admin.repository.UserBusinessUnitRoleRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.UserBusinessUnitRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Every UBR grant or revoke in {@link MemberManagementService} reports exactly the pools it changed. */
class MemberManagementServiceClaimPoolResyncTest {

    private final UserBusinessUnitRoleRepository ubrRepository = mock(UserBusinessUnitRoleRepository.class);
    private final ApproverService approverService = mock(ApproverService.class);
    private final ClaimPoolResyncService claimPoolResyncService = mock(ClaimPoolResyncService.class);
    private MemberManagementService service;

    @BeforeEach
    void setUp() {
        service = new MemberManagementService(
                mock(VirtualGroupMemberRepository.class), mock(VirtualGroupRepository.class),
                mock(UserRepository.class), ubrRepository, mock(UserBusinessUnitRepository.class),
                mock(VirtualGroupRoleRepository.class), mock(MemberChangeLogRepository.class),
                approverService, new ObjectMapper(), mock(I18nService.class), claimPoolResyncService);
        when(approverService.isApprover(anyString(), any(), anyString())).thenReturn(true);
    }

    @Test
    void approvalReportsOnlyNewlyGrantedRoles() {
        when(ubrRepository.existsByUserIdAndBusinessUnitIdAndRoleId("u1", "bu-1", "role-1")).thenReturn(true);

        service.addUserToBusinessUnitWithRoles("u1", "bu-1", List.of("role-1", "role-2"), "approver");

        verify(claimPoolResyncService).ubrChanged("bu-1", List.of("role-2"));
    }

    @Test
    void exitBusinessUnitReportsEveryRemovedRole() {
        when(ubrRepository.findByUserIdAndBusinessUnitId("u1", "bu-1")).thenReturn(List.of(ubr("role-1"), ubr("role-2")));

        service.exitBusinessUnit("bu-1", "u1");

        verify(claimPoolResyncService).ubrChanged("bu-1", List.of("role-1", "role-2"));
    }

    @Test
    void removeBusinessUnitMemberReportsEveryRemovedRole() {
        when(ubrRepository.findByUserIdAndBusinessUnitId("u1", "bu-1")).thenReturn(List.of(ubr("role-1")));

        service.removeBusinessUnitMember("bu-1", "u1", "approver");

        verify(claimPoolResyncService).ubrChanged("bu-1", List.of("role-1"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void exitBusinessUnitRolesReportsOnlyRolesActuallyHeld() {
        when(ubrRepository.findByUserIdAndBusinessUnitIdAndRoleId("u1", "bu-1", "role-1"))
                .thenReturn(Optional.of(ubr("role-1")));
        when(ubrRepository.findByUserIdAndBusinessUnitIdAndRoleId("u1", "bu-1", "role-2"))
                .thenReturn(Optional.empty());

        service.exitBusinessUnitRoles("bu-1", "u1", List.of("role-1", "role-2"));

        verify(claimPoolResyncService).ubrChanged("bu-1", List.of("role-1"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void removeBusinessUnitRoleReportsTheRole() {
        when(ubrRepository.findByUserIdAndBusinessUnitIdAndRoleId("u1", "bu-1", "role-1"))
                .thenReturn(Optional.of(ubr("role-1")));

        service.removeBusinessUnitRole("bu-1", "u1", "role-1", "approver");

        verify(claimPoolResyncService).ubrChanged("bu-1", List.of("role-1"));
    }

    private static UserBusinessUnitRole ubr(String roleId) {
        return UserBusinessUnitRole.builder().id("ubr-" + roleId).userId("u1").businessUnitId("bu-1").roleId(roleId).build();
    }
}
