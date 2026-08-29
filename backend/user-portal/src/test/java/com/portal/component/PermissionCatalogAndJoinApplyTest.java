package com.portal.component;

import com.portal.entity.PermissionRequest;
import com.portal.enums.PermissionRequestStatus;
import com.portal.repository.PermissionRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionCatalogAndJoinApplyTest {

    @Mock
    private RoleAccessComponent roleAccessComponent;
    @Mock
    private VirtualGroupAccessComponent virtualGroupAccessComponent;
    @Mock
    private FunctionUnitAccessComponent functionUnitAccessComponent;
    @Mock
    private PermissionRequestRepository permissionRequestRepository;

    private PermissionCatalogComponent catalog;
    private PermissionRequestSubmissionComponent submission;

    @BeforeEach
    void setUp() {
        catalog = new PermissionCatalogComponent(
                roleAccessComponent, virtualGroupAccessComponent, functionUnitAccessComponent);
        submission = new PermissionRequestSubmissionComponent(
                permissionRequestRepository, roleAccessComponent, virtualGroupAccessComponent);
    }

    @Test
    void availableBusinessUnitsKeepAlreadyJoinedChildUnits() {
        when(virtualGroupAccessComponent.getBusinessUnits()).thenReturn(List.of(
                Map.of("id", "hase", "name", "HASE", "parentId", "root"),
                Map.of("id", "hmdc", "name", "hase-hmdc", "parentId", "hase")
        ));

        List<Map<String, Object>> out = catalog.getAvailableBusinessUnits("user-12345");

        assertThat(out).extracting(m -> m.get("id")).containsExactly("hase", "hmdc");
        verify(virtualGroupAccessComponent, never()).getUserBusinessUnits(any());
    }

    @Test
    void alreadyInBuWithoutThatRoleCanApplyForLeader() {
        stubJoinLookup("hmdc", "role-1", true, List.of());
        when(permissionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PermissionRequest saved = submission.requestBusinessUnitJoinWithRole(
                "user-12345", null, "hmdc", "role-1", "need leader", "LEADER");

        assertThat(saved.getStatus()).isEqualTo(PermissionRequestStatus.PENDING);
        assertThat(saved.getMembershipType()).isEqualTo("LEADER");
        assertThat(saved.getBusinessUnitId()).isEqualTo("hmdc");
    }

    @Test
    void memberCanApplyToUpgradeToLeader() {
        stubJoinLookup("hmdc", "role-1", true, List.of(
                Map.of("roleId", "role-1", "membershipType", "MEMBER")));
        when(permissionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PermissionRequest saved = submission.requestBusinessUnitJoinWithRole(
                "user-12345", null, "hmdc", "role-1", "upgrade", "LEADER");

        assertThat(saved.getMembershipType()).isEqualTo("LEADER");
    }

    @Test
    void sameTierUbrIsRejected() {
        stubJoinLookup("hmdc", "role-1", true, List.of(
                Map.of("roleId", "role-1", "membershipType", "LEADER")));

        assertThatThrownBy(() -> submission.requestBusinessUnitJoinWithRole(
                "user-12345", null, "hmdc", "role-1", "again", "LEADER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already has this role");
        verify(permissionRequestRepository, never()).save(any());
    }

    private void stubJoinLookup(String buId, String roleId, boolean inBu, List<Map<String, Object>> existingUbrs) {
        when(roleAccessComponent.isActivePortalUser("user-12345")).thenReturn(true);
        when(virtualGroupAccessComponent.getBusinessUnitById(buId))
                .thenReturn(Map.of("id", buId, "name", "hase-hmdc"));
        when(virtualGroupAccessComponent.getBusinessUnitBoundRoles(buId))
                .thenReturn(List.of(Map.of("id", roleId, "name", "Maker")));
        when(virtualGroupAccessComponent.isUserInBusinessUnit("user-12345", buId)).thenReturn(inBu);
        when(virtualGroupAccessComponent.listUserBusinessUnitRolesInBusinessUnit("user-12345", buId))
                .thenReturn(existingUbrs);
    }
}
