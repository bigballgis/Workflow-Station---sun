package com.admin.component;

import com.admin.enums.RoleType;
import com.admin.exception.AdminBusinessException;
import com.admin.helper.RoleHelper;
import com.admin.repository.RoleRepository;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Role rules of the platform access model: only business roles are created (D-14), a role still in use
 * is not deleted (§4.3), and platform roles are changed by nobody (AC-13).
 * {@link RoleReferenceFinder} runs against real SQL (H2 in PostgreSQL mode).
 */
class RoleRulesTest {

    private JdbcTemplate jdbc;
    private RoleReferenceFinder finder;
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final I18nService i18n = mock(I18nService.class);
    private RolePermissionManagerComponent roles;

    private final Role manager = Role.builder().id("role-manager").code("MANAGER").name("Department Manager")
            .type("BU_BOUNDED").isSystem(false).build();

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE sys_user_business_unit_roles (id VARCHAR(64), role_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_business_unit_roles (id VARCHAR(64), role_id VARCHAR(64))");
        for (String table : List.of("sys_function_unit_access", "sys_function_unit_audit_access",
                "rt_table_access", "bi_dashboard_assignment")) {
            jdbc.execute("CREATE TABLE " + table + " (id VARCHAR(64), target_type VARCHAR(20), target_id VARCHAR(64), "
                    + "function_unit_id VARCHAR(64))");
        }
        jdbc.execute("CREATE TABLE bi_rbac_mapping (id VARCHAR(64), sys_role_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_function_units (id VARCHAR(64), name VARCHAR(100), code VARCHAR(64), "
                + "imported_at TIMESTAMP, created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_function_unit_contents (id VARCHAR(64), function_unit_id VARCHAR(64), "
                + "content_type VARCHAR(20), content_data TEXT)");
        when(i18n.getMessage(anyString(), any(Object[].class))).thenAnswer(inv -> {
            Object[] args = inv.getArguments();
            StringBuilder text = new StringBuilder(String.valueOf(args[0]));
            for (int i = 1; i < args.length; i++) {
                text.append(" ").append(args[i]);
            }
            return text.toString();
        });
        when(i18n.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        finder = new RoleReferenceFinder(jdbc, i18n, new com.fasterxml.jackson.databind.ObjectMapper());
        roles = new RolePermissionManagerComponent(roleRepository, new RoleHelper(roleRepository), finder, i18n);
        when(roleRepository.findById("role-manager")).thenReturn(Optional.of(manager));
    }

    @Test
    void aRoleNobodyUsesHasNoReferences() {
        assertThat(finder.find(manager)).isEmpty();
    }

    @Test
    void everyKindOfReferenceIsListed() {
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES ('u1', 'role-manager'), ('u2', 'role-manager')");
        jdbc.update("INSERT INTO sys_business_unit_roles VALUES ('b1', 'role-manager')");
        jdbc.update("INSERT INTO sys_function_unit_access VALUES ('a1', 'ROLE', 'MANAGER', 'fu1')");
        jdbc.update("INSERT INTO rt_table_access VALUES ('r1', 'ROLE', 'role-manager', NULL)");
        jdbc.update("INSERT INTO bi_rbac_mapping VALUES ('m1', 'role-manager')");
        jdbc.update("INSERT INTO sys_function_units (id, name, code) VALUES ('fu1', 'Expense Claim', 'FU1'), ('fu2', 'Legacy Flow', 'FU2')");
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('c1', 'fu1', 'PROCESS', "
                + "'<custom:property name=\"roleId\" value=\"MANAGER\" />')");
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('c2', 'fu2', 'PROCESS', "
                + "'<custom:property name=\"roleId\" value=\"role-manager\"/>')");

        assertThat(finder.find(manager)).containsExactly(
                "admin.role.ref_ubr 2",
                "admin.role.ref_bu_binding 1",
                "admin.role.ref_fu_access 1",
                "admin.role.ref_rt_access 1",
                "admin.role.ref_bi_rbac 1",
                "admin.role.ref_processes Expense Claim, Legacy Flow");
    }

    @Test
    void anotherRolesCodeInsideAProcessIsNotAReference() {
        jdbc.update("INSERT INTO sys_function_units (id, name, code) VALUES ('fu1', 'Expense Claim', 'FU1')");
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('c1', 'fu1', 'PROCESS', "
                + "'<custom:property name=\"roleId\" value=\"MANAGER_DEPUTY\" />')");

        assertThat(finder.find(manager)).isEmpty();
    }

    @Test
    void aRoleLaterInATasksRoleListIsAReference() {
        jdbc.update("INSERT INTO sys_function_units (id, name, code) VALUES ('fu1', 'Expense Claim', 'FU1')");
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('c1', 'fu1', 'PROCESS', "
                + "'<custom:property name=\"roleId\" value=\"HMDC_ASSIGN\"/>"
                + "<custom:property name=\"roleIds\" value=\"HMDC_ASSIGN, MANAGER\"/>')");

        assertThat(finder.find(manager)).containsExactly("admin.role.ref_processes Expense Claim");
    }

    @Test
    void aBase64EncodedProcessIsSearchedToo() {
        jdbc.update("INSERT INTO sys_function_units (id, name, code) VALUES ('fu1', 'Legacy Flow', 'FU1')");
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('c1', 'fu1', 'PROCESS', 'PGN1c3RvbTpwcm9wZXJ0eSBuYW1lPSJyb2xlSWRzIiB2YWx1ZT0iTUFOQUdFUiIvPg==')");

        assertThat(finder.find(manager)).containsExactly("admin.role.ref_processes Legacy Flow");
    }

    @Test
    void aViewAccessRuleOnTheRoleIsAReference() {
        jdbc.update("INSERT INTO sys_function_units (id, name, code) VALUES ('fu1', 'Expense Claim', 'FU1'), ('fu2', 'Leave', 'FU2')");
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('c1', 'fu1', 'MAIN_TABLE_VIEW', "
                + "'[{\"viewName\":\"All\",\"accessRules\":[{\"targetType\":\"BUSINESS_UNIT\",\"targetId\":\"bu-1\"},"
                + "{\"targetType\":\"ROLE\",\"targetId\":\"role-manager\"}]}]')");
        // Naming the role elsewhere in a view (e.g. a BU with the same id text) is not a reference.
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('c2', 'fu2', 'MAIN_TABLE_VIEW', "
                + "'[{\"viewName\":\"MANAGER\",\"accessRules\":[{\"targetType\":\"ROLE\",\"targetId\":\"role-other\"}]}]')");

        assertThat(finder.find(manager)).containsExactly("admin.role.ref_views Expense Claim");
    }

    @Test
    void theNoticeListsTheUsingUnitsByCodeWithTheirCurrentName() {
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES ('u1', 'role-manager')");
        jdbc.update("INSERT INTO sys_function_units (id, name, code, imported_at) VALUES "
                + "('fu1-v1', 'Expense Claim (old)', 'EXPENSE', TIMESTAMP '2026-10-01 10:00:00'), "
                + "('fu1-v2', 'Expense Claim', 'EXPENSE', TIMESTAMP '2026-11-01 10:00:00'), "
                + "('fu2', 'Leave', 'LEAVE', NULL), ('fu3', 'Audit Only', 'AUDIT_ONLY', NULL), "
                + "('fu4', 'Unrelated', 'UNRELATED', NULL)");
        jdbc.update("INSERT INTO sys_function_unit_access VALUES ('a1', 'ROLE', 'role-manager', 'fu1-v1')");
        jdbc.update("INSERT INTO sys_function_unit_audit_access VALUES ('a2', 'ROLE', 'MANAGER', 'fu3')");
        jdbc.update("INSERT INTO sys_function_unit_access VALUES ('a3', 'ROLE', 'OTHER', 'fu4')");
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('c1', 'fu2', 'PROCESS', "
                + "'<custom:property name=\"roleIds\" value=\"MANAGER\"/>')");

        var notice = finder.findForNotice(manager);

        assertThat(notice.functionUnits()).extracting(fu -> fu.code() + "=" + fu.name())
                .containsExactly("AUDIT_ONLY=Audit Only", "EXPENSE=Expense Claim", "LEAVE=Leave");
        // FU access is shown as units, so only the other kinds remain as lines.
        assertThat(notice.others()).containsExactly("admin.role.ref_ubr 1");
    }

    @Test
    void aSystemRoleIsChangedByNobodyWith403() {
        Role auditor = Role.builder().id("role-auditor").code("AUDITOR").name("Auditor").type("AUDITOR")
                .isSystem(true).build();
        when(roleRepository.findById("role-auditor")).thenReturn(Optional.of(auditor));

        assertThatThrownBy(() -> roles.updateRole("role-auditor", "Renamed", null, null))
                .isInstanceOf(com.admin.exception.AdminForbiddenException.class);
        assertThatThrownBy(() -> roles.deleteRole("role-auditor"))
                .isInstanceOf(com.admin.exception.AdminForbiddenException.class);
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void aRoleInUseIsNotDeleted() {
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES ('u1', 'role-manager')");

        assertThatThrownBy(() -> roles.deleteRole("role-manager"))
                .isInstanceOf(AdminBusinessException.class)
                .hasMessageContaining("admin.role.ref_ubr 1");
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void anUnusedBusinessRoleIsDeleted() {
        roles.deleteRole("role-manager");

        verify(roleRepository).delete(manager);
    }

    @Test
    void onlyBusinessRolesCanBeCreated() {
        for (RoleType type : List.of(RoleType.ADMIN, RoleType.AUDITOR, RoleType.DEVELOPER)) {
            assertThatThrownBy(() -> roles.createRole("X", "X_" + type, type, null))
                    .isInstanceOf(AdminBusinessException.class)
                    .hasMessage("admin.role.only_bu_bounded_creatable");
        }
        assertThatThrownBy(() -> roles.createRole("X", "X_NULL", null, null))
                .isInstanceOf(AdminBusinessException.class);

        Role created = roles.createRole("Approver", "APPROVER", RoleType.BU_BOUNDED, null);

        assertThat(created.getType()).isEqualTo("BU_BOUNDED");
    }

}
