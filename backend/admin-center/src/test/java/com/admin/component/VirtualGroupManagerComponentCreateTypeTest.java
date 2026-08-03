package com.admin.component;

import com.admin.dto.request.VirtualGroupCreateRequest;
import com.admin.dto.response.VirtualGroupResult;
import com.admin.enums.VirtualGroupType;
import com.admin.exception.AdminBusinessException;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserBusinessUnitRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import com.platform.security.entity.VirtualGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualGroupManagerComponentCreateTypeTest {

    @Mock private VirtualGroupRepository virtualGroupRepository;
    @Mock private VirtualGroupMemberRepository virtualGroupMemberRepository;
    @Mock private VirtualGroupRoleRepository virtualGroupRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private UserBusinessUnitRepository userBusinessUnitRepository;

    private VirtualGroupManagerComponent component;

    @BeforeEach
    void setUp() {
        component = new VirtualGroupManagerComponent(
                virtualGroupRepository,
                virtualGroupMemberRepository,
                virtualGroupRoleRepository,
                roleRepository,
                userRepository,
                jdbcTemplate,
                userBusinessUnitRepository);
    }

    @Test
    @DisplayName("create rejects SYSTEM type")
    void createRejectsSystemType() {
        VirtualGroupCreateRequest request = VirtualGroupCreateRequest.builder()
                .name("Bad System")
                .code("BAD_SYSTEM")
                .type(VirtualGroupType.SYSTEM)
                .build();

        assertThatThrownBy(() -> component.createVirtualGroup(request))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("CUSTOM or DEVELOPER");

        verify(virtualGroupRepository, never()).save(any());
    }

    @Test
    @DisplayName("create accepts DEVELOPER type")
    void createAcceptsDeveloperType() {
        when(virtualGroupRepository.existsByName(anyString())).thenReturn(false);
        when(virtualGroupRepository.existsByCode(anyString())).thenReturn(false);
        when(virtualGroupRepository.save(any(VirtualGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        VirtualGroupCreateRequest request = VirtualGroupCreateRequest.builder()
                .name("Dev Team A")
                .code("DEV_TEAM_A")
                .type(VirtualGroupType.DEVELOPER)
                .build();

        VirtualGroupResult result = component.createVirtualGroup(request);
        assertThat(result.isSuccess()).isTrue();

        ArgumentCaptor<VirtualGroup> captor = ArgumentCaptor.forClass(VirtualGroup.class);
        verify(virtualGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("DEVELOPER");
    }

    @Test
    @DisplayName("update rejects changing a CUSTOM group to SYSTEM")
    void updateRejectsSystemType() {
        VirtualGroup existing = VirtualGroup.builder()
                .id("vg-1")
                .name("Custom Group")
                .code("CUSTOM_G")
                .type("CUSTOM")
                .status("ACTIVE")
                .build();
        when(virtualGroupRepository.findById("vg-1")).thenReturn(Optional.of(existing));

        VirtualGroupCreateRequest request = VirtualGroupCreateRequest.builder()
                .name("Custom Group")
                .type(VirtualGroupType.SYSTEM)
                .build();

        assertThatThrownBy(() -> component.updateVirtualGroup("vg-1", request))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("CUSTOM or DEVELOPER");
    }
}
