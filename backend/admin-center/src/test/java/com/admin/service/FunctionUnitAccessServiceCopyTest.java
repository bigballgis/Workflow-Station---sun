package com.admin.service;

import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitAccess;
import com.admin.repository.FunctionUnitAccessRepository;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FunctionUnitAccessServiceCopyTest {

    @Mock
    private FunctionUnitAccessRepository accessRepository;
    @Mock
    private FunctionUnitRepository functionUnitRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;

    private FunctionUnitAccessService service;

    @BeforeEach
    void setUp() {
        service = new FunctionUnitAccessService(
                accessRepository, functionUnitRepository, roleRepository, userRoleRepository);
    }

    @Test
    void copyAccessFromSiblingVersions_copiesFromHighestVersionWithAccess() {
        FunctionUnit oldUnit = FunctionUnit.builder().id("old-id").code("fu-code").version("1.0.1").build();
        FunctionUnit newUnit = FunctionUnit.builder().id("new-id").code("fu-code").version("1.0.2").build();
        FunctionUnitAccess existingAccess = FunctionUnitAccess.builder()
                .accessType("USER")
                .targetType("ROLE")
                .targetId("role-manager")
                .build();

        when(functionUnitRepository.findByCodeOrderByVersionDesc("fu-code"))
                .thenReturn(List.of(newUnit, oldUnit));
        when(accessRepository.findByFunctionUnitId("old-id")).thenReturn(List.of(existingAccess));
        when(functionUnitRepository.findById("new-id")).thenReturn(Optional.of(newUnit));
        when(accessRepository.existsByFunctionUnitIdAndRoleId("new-id", "role-manager")).thenReturn(false);
        when(accessRepository.save(any(FunctionUnitAccess.class))).thenAnswer(inv -> inv.getArgument(0));

        int copied = service.copyAccessFromSiblingVersions("fu-code", "new-id");

        assertThat(copied).isEqualTo(1);
        ArgumentCaptor<FunctionUnitAccess> captor = ArgumentCaptor.forClass(FunctionUnitAccess.class);
        verify(accessRepository).save(captor.capture());
        assertThat(captor.getValue().getFunctionUnit()).isEqualTo(newUnit);
        assertThat(captor.getValue().getTargetId()).isEqualTo("role-manager");
    }

    @Test
    void copyAccessFromSiblingVersions_skipsWhenNoSiblingHasAccess() {
        FunctionUnit newUnit = FunctionUnit.builder().id("new-id").code("fu-code").version("1.0.2").build();

        when(functionUnitRepository.findByCodeOrderByVersionDesc("fu-code")).thenReturn(List.of(newUnit));

        int copied = service.copyAccessFromSiblingVersions("fu-code", "new-id");

        assertThat(copied).isZero();
        verify(accessRepository, never()).save(any());
        verify(functionUnitRepository, never()).findById(eq("new-id"));
    }
}
