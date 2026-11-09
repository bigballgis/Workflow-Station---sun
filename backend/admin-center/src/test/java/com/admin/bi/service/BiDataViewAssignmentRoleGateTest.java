package com.admin.bi.service;

import com.admin.bi.component.DashboardRoleGate;
import com.admin.bi.config.BiProperties;
import com.admin.bi.dto.response.DataViewDashboardResponse;
import com.admin.bi.entity.BiDashboardRegistry;
import com.admin.bi.entity.BiDataViewAssignment;
import com.admin.bi.entity.BiSupersetRole;
import com.admin.bi.enums.DashboardStatus;
import com.admin.bi.enums.SupersetRoleStatus;
import com.admin.bi.repository.BiDashboardRegistryRepository;
import com.admin.bi.repository.BiDataViewAssignmentRepository;
import com.admin.bi.repository.BiDataViewMetadataRepository;
import com.admin.bi.service.impl.BiDataViewAssignmentServiceImpl;
import com.admin.repository.RoleRepository;
import com.admin.service.UserRoleIdResolver;
import com.admin.service.UserBusinessUnitService;
import com.platform.security.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Data -> Views path (dashboards bound to a View) must apply the same RBAC-mapping role gate as the landing
 * page: a dashboard carrying Superset roles is listed and guest-token-eligible only when the user's mapped Superset
 * roles cover it. The View is named by function unit code and View name and resolved in the active release.
 */
class BiDataViewAssignmentRoleGateTest {

    private static final String USER = "user-1";
    private static final String FU = "FU";
    private static final String VIEW = "Open cases";
    private static final List<String> USER_ROLES = List.of("role-analyst");

    private BiDataViewAssignmentRepository assignmentRepository;
    private BiDashboardRegistryRepository dashboardRepository;
    private BiRbacMappingService rbacMappingService;
    private BiDataViewMetadataRepository metadata;
    private UserRoleIdResolver userRoleIdResolver;
    private RoleRepository roleRepository;
    private BiDataViewAssignmentServiceImpl service;
    private BiDashboardRegistry restricted;
    private BiDashboardRegistry open;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        assignmentRepository = mock(BiDataViewAssignmentRepository.class);
        dashboardRepository = mock(BiDashboardRegistryRepository.class);
        rbacMappingService = mock(BiRbacMappingService.class);
        metadata = mock(BiDataViewMetadataRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        userRoleIdResolver = mock(UserRoleIdResolver.class);
        roleRepository = mock(RoleRepository.class);
        UserBusinessUnitService userBusinessUnitService = mock(UserBusinessUnitService.class);

        // The active release publishes the View for BU bu-1 + role role-analyst ...
        when(metadata.publishedView(FU, VIEW)).thenReturn(Optional.of(new BiDataViewMetadataRepository.ReleasedView(
                "catalog-1", FU, "Cases", VIEW, "cases", "Cases", "MAIN", List.of(
                        Map.of("targetType", "BUSINESS_UNIT", "targetId", "bu-1"),
                        Map.of("targetType", "ROLE", "targetId", "role-analyst")))));
        // ... the user holds that role in that BU, and the role is granted on the unit.
        when(userRoleIdResolver.allRoleIds(USER)).thenReturn(USER_ROLES);
        when(roleRepository.findAllById(USER_ROLES)).thenReturn(List.of(
                Role.builder().id("role-analyst").code("ANALYST").type("BU_BOUNDED").status("ACTIVE").build()));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(FU))).thenReturn(List.of("ANALYST"));
        when(userBusinessUnitService.getUserBusinessUnitIds(USER)).thenReturn(List.of("bu-1"));

        when(dashboardRepository.findAllById(any())).thenAnswer(inv -> {
            java.util.List<BiDashboardRegistry> result = new java.util.ArrayList<>();
            for (String id : inv.<Iterable<String>>getArgument(0)) {
                dashboardRepository.findById(id).ifPresent(result::add);
            }
            return result;
        });
        restricted = dashboard("dash-alpha", "3");
        open = dashboard("dash-open", null);
        when(dashboardRepository.findById(restricted.getId())).thenReturn(Optional.of(restricted));
        when(dashboardRepository.findById(open.getId())).thenReturn(Optional.of(open));
        when(assignmentRepository.findByFunctionUnitCodeAndViewNameAndEnabledTrueOrderByCreatedAtAscIdAsc(FU, VIEW))
                .thenReturn(List.of(binding(restricted.getId()), binding(open.getId())));
        when(assignmentRepository.existsByDashboardIdAndFunctionUnitCodeAndViewNameAndEnabledTrue(
                restricted.getId(), FU, VIEW)).thenReturn(true);
        when(assignmentRepository.existsByDashboardIdAndFunctionUnitCodeAndViewNameAndEnabledTrue(
                open.getId(), FU, VIEW)).thenReturn(true);

        service = new BiDataViewAssignmentServiceImpl(assignmentRepository, dashboardRepository, jdbcTemplate,
                userRoleIdResolver, roleRepository, userBusinessUnitService,
                new DashboardRoleGate(rbacMappingService, new BiProperties(), userRoleIdResolver),
                mock(jakarta.persistence.EntityManager.class), metadata);
    }

    @Test
    void withoutMatchingMappingOnlyUnrestrictedDashboardsAreListedOrGrantable() {
        when(rbacMappingService.getEffectiveSupersetRoles(USER_ROLES)).thenReturn(List.of(supersetRole(4, "Gamma")));

        assertThat(service.getDashboardsForView(USER, FU, VIEW))
                .extracting(DataViewDashboardResponse::dashboardId)
                .containsExactly(open.getId());
        assertThat(service.canAccessDashboardForView(USER, restricted.getId(), FU, VIEW)).isFalse();
        assertThat(service.canAccessDashboardForView(USER, open.getId(), FU, VIEW)).isTrue();
    }

    @Test
    void intersectingMappingOpensTheRestrictedDashboard() {
        when(rbacMappingService.getEffectiveSupersetRoles(USER_ROLES)).thenReturn(List.of(supersetRole(3, "Alpha")));

        assertThat(service.getDashboardsForView(USER, FU, VIEW))
                .extracting(DataViewDashboardResponse::dashboardId)
                .containsExactly(restricted.getId(), open.getId());
        assertThat(service.canAccessDashboardForView(USER, restricted.getId(), FU, VIEW)).isTrue();
    }

    @Test
    void supersetAdminMappingBypassesTheGate() {
        when(rbacMappingService.getEffectiveSupersetRoles(USER_ROLES)).thenReturn(List.of(supersetRole(1, "Admin")));

        assertThat(service.canAccessDashboardForView(USER, restricted.getId(), FU, VIEW)).isTrue();
    }

    @Test
    void disabledViewBindingCannotListOrAuthorizeTheDashboard() {
        when(assignmentRepository.findByFunctionUnitCodeAndViewNameAndEnabledTrueOrderByCreatedAtAscIdAsc(FU, VIEW))
                .thenReturn(List.of());
        when(assignmentRepository.existsByDashboardIdAndFunctionUnitCodeAndViewNameAndEnabledTrue(
                open.getId(), FU, VIEW)).thenReturn(false);
        assertThat(service.getDashboardsForView(USER, FU, VIEW)).isEmpty();
        assertThat(service.canAccessDashboardForView(USER, open.getId(), FU, VIEW)).isFalse();
    }

    @Test
    void aViewTheActiveReleaseDoesNotPublishIsNotAccessible() {
        when(metadata.publishedView(FU, "Archived")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDashboardsForView(USER, FU, "Archived"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.canAccessDashboardForView(USER, open.getId(), FU, "Archived"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aSystemAdministratorIsCheckedLikeEveryoneElse() {
        when(userRoleIdResolver.allRoleIds(USER)).thenReturn(List.of("role-sys-admin"));
        when(roleRepository.findAllById(List.of("role-sys-admin"))).thenReturn(List.of(
                Role.builder().id("role-sys-admin").code("SYS_ADMIN").type("ADMIN").status("ACTIVE").build()));

        assertThatThrownBy(() -> service.getDashboardsForView(USER, FU, VIEW))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static BiDashboardRegistry dashboard(String id, String supersetRoleIds) {
        return BiDashboardRegistry.builder()
                .id(id).dashboardTitle(id).embedId(UUID.randomUUID())
                .supersetDashboardUuid(UUID.randomUUID()).supersetDashboardId(id.hashCode())
                .supersetRoleIds(supersetRoleIds).status(DashboardStatus.ACTIVE)
                .lastSyncedAt(LocalDateTime.now()).build();
    }

    private static BiDataViewAssignment binding(String dashboardId) {
        return BiDataViewAssignment.builder()
                .id(UUID.randomUUID().toString()).dashboardId(dashboardId)
                .functionUnitCode(FU).viewName(VIEW).build();
    }

    private static BiSupersetRole supersetRole(int id, String name) {
        return BiSupersetRole.builder().id(id).supersetRoleId(id).name(name)
                .status(SupersetRoleStatus.ACTIVE).lastSyncedAt(LocalDateTime.now()).build();
    }
}
