package com.admin.component;

import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserBusinessUnitRoleRepository;
import com.admin.repository.UserRepository;
import com.admin.service.ApproverService;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.Role;
import com.platform.security.entity.UserBusinessUnitRole;
import com.platform.security.ubr.UbrMembershipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForceUnclaimAuthorizationComponentTest {

    @Mock
    private ApproverService approverService;
    @Mock
    private UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    @Mock
    private BusinessUnitRepository businessUnitRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRepository userRepository;

    private ForceUnclaimAuthorizationComponent component;

    @BeforeEach
    void setUp() {
        component = new ForceUnclaimAuthorizationComponent(
                approverService, userBusinessUnitRoleRepository, businessUnitRepository, roleRepository, userRepository);
    }

    @Test
    void memberCannotForceUnclaim() {
        when(approverService.getApproverBusinessUnitIds("u1")).thenReturn(List.of());
        when(userBusinessUnitRoleRepository.findByUserIdAndMembershipType("u1", UbrMembershipType.LEADER))
                .thenReturn(List.of());

        Map<String, Boolean> flags = component.evaluate("u1", List.of(
                new ForceUnclaimAuthorizationComponent.ForceUnclaimItem("t1", "bu-1", List.of("role-1"))));
        assertThat(flags.get("t1")).isFalse();
    }

    @Test
    void leaderOfMatchingUbrCanForceUnclaim() {
        when(approverService.getApproverBusinessUnitIds("leader")).thenReturn(List.of());
        UserBusinessUnitRole row = UserBusinessUnitRole.builder()
                .id("ubr-1")
                .userId("leader")
                .businessUnitId("bu-1")
                .roleId("role-1")
                .membershipType(UbrMembershipType.LEADER)
                .build();
        when(userBusinessUnitRoleRepository.findByUserIdAndMembershipType("leader", UbrMembershipType.LEADER))
                .thenReturn(List.of(row));
        when(businessUnitRepository.findAllById(anyCollection())).thenReturn(List.of(
                BusinessUnit.builder().id("bu-1").code("HMDC").build()));
        when(roleRepository.findAllById(anyCollection())).thenReturn(List.of(
                Role.builder().id("role-1").code("MAKER").build()));

        Map<String, Boolean> flags = component.evaluate("leader", List.of(
                new ForceUnclaimAuthorizationComponent.ForceUnclaimItem("t1", "HMDC", List.of("MAKER"))));
        assertThat(flags.get("t1")).isTrue();
    }

    @Test
    void buApproverCanForceUnclaimWithoutBeingLeader() {
        when(approverService.getApproverBusinessUnitIds("appr")).thenReturn(List.of("bu-1"));
        when(businessUnitRepository.findAllById(anyCollection())).thenReturn(List.of(
                BusinessUnit.builder().id("bu-1").code("HMDC").build()));
        when(userBusinessUnitRoleRepository.findByUserIdAndMembershipType("appr", UbrMembershipType.LEADER))
                .thenReturn(List.of());

        Map<String, Boolean> flags = component.evaluate("appr", List.of(
                new ForceUnclaimAuthorizationComponent.ForceUnclaimItem("t1", "bu-1", List.of("role-x"))));
        assertThat(flags.get("t1")).isTrue();
    }

    @Test
    void leaderOfDifferentRoleCannotForceUnclaim() {
        when(approverService.getApproverBusinessUnitIds("leader")).thenReturn(List.of());
        UserBusinessUnitRole row = UserBusinessUnitRole.builder()
                .id("ubr-1")
                .userId("leader")
                .businessUnitId("bu-1")
                .roleId("role-1")
                .membershipType(UbrMembershipType.LEADER)
                .build();
        when(userBusinessUnitRoleRepository.findByUserIdAndMembershipType("leader", UbrMembershipType.LEADER))
                .thenReturn(List.of(row));
        when(businessUnitRepository.findAllById(anyCollection())).thenReturn(List.of(
                BusinessUnit.builder().id("bu-1").code("HMDC").build()));
        when(roleRepository.findAllById(anyCollection())).thenReturn(List.of(
                Role.builder().id("role-1").code("MAKER").build()));

        Map<String, Boolean> flags = component.evaluate("leader", List.of(
                new ForceUnclaimAuthorizationComponent.ForceUnclaimItem("t1", "HMDC", List.of("CHECKER"))));
        assertThat(flags.get("t1")).isFalse();
    }
}
