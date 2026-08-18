package com.admin.service;

import com.admin.enums.DeveloperPermission;
import com.admin.repository.DeveloperRolePermissionRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.UserRoleRepository;
import com.platform.security.entity.Role;
import com.platform.security.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperPermissionServiceTest {

    @Mock private DeveloperRolePermissionRepository permissionRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;

    private DeveloperPermissionService service;

    @BeforeEach
    void setUp() {
        service = new DeveloperPermissionService(
                permissionRepository, userRoleRepository, roleRepository, userRepository);
    }

    @Test
    void auditorDefaultsToFunctionUnitViewOnly() {
        stubUser("u-auditor");
        Role auditor = role("role-auditor", "AUDITOR", "AUDITOR");
        when(userRoleRepository.findAllRoleIdsByUserId("u-auditor")).thenReturn(List.of("role-auditor"));
        when(roleRepository.findById("role-auditor")).thenReturn(Optional.of(auditor));
        when(permissionRepository.findPermissionsByRoleIds(List.of("role-auditor")))
                .thenReturn(Set.of());

        assertEquals(EnumSet.of(DeveloperPermission.FUNCTION_UNIT_VIEW),
                service.getUserPermissions("u-auditor"));
    }

    @Test
    void auditorMisconfiguredWritePermissionsAreClamped() {
        stubUser("u-auditor");
        Role auditor = role("role-auditor", "AUDITOR", "AUDITOR");
        when(userRoleRepository.findAllRoleIdsByUserId("u-auditor")).thenReturn(List.of("role-auditor"));
        when(roleRepository.findById("role-auditor")).thenReturn(Optional.of(auditor));
        when(permissionRepository.findPermissionsByRoleIds(List.of("role-auditor")))
                .thenReturn(EnumSet.of(
                        DeveloperPermission.FUNCTION_UNIT_VIEW,
                        DeveloperPermission.FUNCTION_UNIT_UPDATE,
                        DeveloperPermission.FUNCTION_UNIT_DELETE));

        assertEquals(EnumSet.of(DeveloperPermission.FUNCTION_UNIT_VIEW),
                service.getUserPermissions("u-auditor"));
    }

    @Test
    void auditorPlusDeveloperKeepsDeveloperPermissions() {
        stubUser("u-both");
        Role auditor = role("role-auditor", "AUDITOR", "AUDITOR");
        Role developer = role("role-developer", "DEVELOPER", "DEVELOPER");
        when(userRoleRepository.findAllRoleIdsByUserId("u-both"))
                .thenReturn(List.of("role-auditor", "role-developer"));
        when(roleRepository.findById("role-auditor")).thenReturn(Optional.of(auditor));
        when(roleRepository.findById("role-developer")).thenReturn(Optional.of(developer));
        when(permissionRepository.findPermissionsByRoleIds(List.of("role-auditor", "role-developer")))
                .thenReturn(EnumSet.of(
                        DeveloperPermission.FUNCTION_UNIT_VIEW,
                        DeveloperPermission.FUNCTION_UNIT_UPDATE));

        Set<DeveloperPermission> perms = service.getUserPermissions("u-both");
        assertTrue(perms.contains(DeveloperPermission.FUNCTION_UNIT_VIEW));
        assertTrue(perms.contains(DeveloperPermission.FUNCTION_UNIT_UPDATE));
    }

    @Test
    void sysAdminGetsAllPermissionsEvenIfTypeIsAdmin() {
        stubUser("u-admin");
        Role sysAdmin = role("role-sys-admin", "SYS_ADMIN", "ADMIN");
        when(userRoleRepository.findAllRoleIdsByUserId("u-admin")).thenReturn(List.of("role-sys-admin"));
        when(roleRepository.findById("role-sys-admin")).thenReturn(Optional.of(sysAdmin));

        assertEquals(EnumSet.allOf(DeveloperPermission.class), service.getUserPermissions("u-admin"));
    }

    private void stubUser(String userId) {
        when(userRepository.existsById(userId)).thenReturn(true);
    }

    private static Role role(String id, String code, String type) {
        return Role.builder().id(id).code(code).type(type).status("ACTIVE").build();
    }
}
