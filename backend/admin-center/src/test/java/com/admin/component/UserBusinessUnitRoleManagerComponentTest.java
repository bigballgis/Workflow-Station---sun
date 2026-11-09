package com.admin.component;

import com.admin.repository.BusinessUnitRoleRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserBusinessUnitRepository;
import com.admin.repository.UserBusinessUnitRoleRepository;
import com.admin.exception.AdminBusinessException;
import com.admin.service.ClaimPoolResyncService;
import com.platform.security.entity.Role;
import com.platform.security.entity.UserBusinessUnit;
import com.platform.security.entity.UserBusinessUnitRole;
import com.platform.security.ubr.UbrMembershipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBusinessUnitRoleManagerComponentTest {

    @Mock
    private UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    @Mock
    private UserBusinessUnitRepository userBusinessUnitRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private BusinessUnitRoleRepository businessUnitRoleRepository;
    @Mock
    private ClaimPoolResyncService claimPoolResyncService;

    private UserBusinessUnitRoleManagerComponent component;

    @BeforeEach
    void setUp() {
        component = new UserBusinessUnitRoleManagerComponent(
                userBusinessUnitRoleRepository, userBusinessUnitRepository,
                roleRepository, businessUnitRoleRepository, claimPoolResyncService);
    }

    @Test
    void assignNewUbrReportsThePoolForEngineResync() {
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId("u1", "bu-1", "role-1"))
                .thenReturn(Optional.empty());
        when(userBusinessUnitRepository.existsByUserIdAndBusinessUnitId("u1", "bu-1")).thenReturn(true);
        when(roleRepository.findById("role-1")).thenReturn(Optional.of(
                Role.builder().id("role-1").code("MANAGER").type("BU_BOUNDED").build()));
        when(businessUnitRoleRepository.existsByBusinessUnitIdAndRoleId("bu-1", "role-1")).thenReturn(true);

        component.assign("u1", "bu-1", "role-1", "admin");

        verify(userBusinessUnitRoleRepository).save(any(UserBusinessUnitRole.class));
        verify(claimPoolResyncService).ubrChanged("bu-1", List.of("role-1"));
    }

    @Test
    void tierChangeKeepsThePoolAsItIs() {
        UserBusinessUnitRole ubr = UserBusinessUnitRole.builder().id("ubr-1").userId("u1")
                .businessUnitId("bu-1").roleId("role-1").membershipType(UbrMembershipType.MEMBER).build();
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId("u1", "bu-1", "role-1"))
                .thenReturn(Optional.of(ubr));

        component.assign("u1", "bu-1", "role-1", "admin", UbrMembershipType.LEADER);

        verify(userBusinessUnitRoleRepository).save(ubr);
        verifyNoInteractions(claimPoolResyncService);
    }

    @Test
    void rejectedAssignReportsNothing() {
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId("u1", "bu-1", "role-1"))
                .thenReturn(Optional.empty());
        when(userBusinessUnitRepository.existsByUserIdAndBusinessUnitId("u1", "bu-1")).thenReturn(false);

        assertThatThrownBy(() -> component.assign("u1", "bu-1", "role-1", "admin"))
                .isInstanceOf(AdminBusinessException.class);

        verifyNoInteractions(claimPoolResyncService);
    }

    @Test
    void removeLastUbrAlsoLeavesBusinessUnit() {
        UserBusinessUnitRole ubr = UserBusinessUnitRole.builder()
                .id("ubr-1").userId("u1").businessUnitId("bu-1").roleId("role-1").build();
        UserBusinessUnit membership = UserBusinessUnit.builder()
                .id("ubu-1").userId("u1").businessUnitId("bu-1").build();
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId("u1", "bu-1", "role-1"))
                .thenReturn(Optional.of(ubr));
        when(userBusinessUnitRoleRepository.existsByUserIdAndBusinessUnitId("u1", "bu-1"))
                .thenReturn(false);
        when(userBusinessUnitRepository.findByUserIdAndBusinessUnitId("u1", "bu-1"))
                .thenReturn(Optional.of(membership));

        component.remove("u1", "bu-1", "role-1", "admin");

        verify(userBusinessUnitRoleRepository).delete(ubr);
        verify(userBusinessUnitRepository).delete(membership);
        verify(claimPoolResyncService).ubrChanged("bu-1", List.of("role-1"));
    }

    @Test
    void removeKeepsBusinessUnitWhenOtherRolesRemain() {
        UserBusinessUnitRole ubr = UserBusinessUnitRole.builder()
                .id("ubr-1").userId("u1").businessUnitId("bu-1").roleId("role-1").build();
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId("u1", "bu-1", "role-1"))
                .thenReturn(Optional.of(ubr));
        when(userBusinessUnitRoleRepository.existsByUserIdAndBusinessUnitId("u1", "bu-1"))
                .thenReturn(true);

        component.remove("u1", "bu-1", "role-1", "admin");

        verify(userBusinessUnitRoleRepository).delete(ubr);
        verify(userBusinessUnitRepository, never()).delete(any());
        verify(userBusinessUnitRepository, never()).findByUserIdAndBusinessUnitId("u1", "bu-1");
    }

    @Test
    void removeIsNoOpWhenAssignmentMissing() {
        when(userBusinessUnitRoleRepository.findByUserIdAndBusinessUnitIdAndRoleId("u1", "bu-1", "role-1"))
                .thenReturn(Optional.empty());

        component.remove("u1", "bu-1", "role-1", "admin");

        verify(userBusinessUnitRoleRepository, never()).delete(any());
        verifyNoInteractions(userBusinessUnitRepository);
        verifyNoInteractions(claimPoolResyncService);
    }
}
