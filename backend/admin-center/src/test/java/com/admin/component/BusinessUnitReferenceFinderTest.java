package com.admin.component;

import com.admin.dto.response.ReferenceNotice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.i18n.I18nService;
import com.platform.security.entity.BusinessUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Where a business unit is used, against real SQL (H2 in PostgreSQL mode): BU + Role nodes in both BPMN forms, view
 * access rules, FU access, BI Audience and relation table access targets, memberships and bound roles (§8.3).
 */
class BusinessUnitReferenceFinderTest {

    private JdbcTemplate jdbc;
    private BusinessUnitReferenceFinder finder;
    private final BusinessUnit finance = BusinessUnit.builder().id("bu-fin").code("FINANCE").name("Finance").build();

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE sys_user_business_unit_roles (id VARCHAR(64), business_unit_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE sys_business_unit_roles (id VARCHAR(64), business_unit_id VARCHAR(64))");
        for (String table : List.of("sys_function_unit_access", "rt_table_access", "bi_dashboard_assignment")) {
            jdbc.execute("CREATE TABLE " + table + " (id VARCHAR(64), target_type VARCHAR(20), target_id VARCHAR(64), "
                    + "function_unit_id VARCHAR(64))");
        }
        jdbc.execute("CREATE TABLE sys_function_units (id VARCHAR(64), name VARCHAR(100), code VARCHAR(64), "
                + "imported_at TIMESTAMP, created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_function_unit_contents (id VARCHAR(64), function_unit_id VARCHAR(64), "
                + "content_type VARCHAR(20), content_data TEXT)");
        I18nService i18n = mock(I18nService.class);
        when(i18n.getMessage(anyString(), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0) + " " + inv.getArgument(1));
        finder = new BusinessUnitReferenceFinder(jdbc, i18n, new ObjectMapper());
    }

    @Test
    void anUnusedUnitHasNoReferences() {
        ReferenceNotice notice = finder.findForNotice(finance);
        assertThat(notice.functionUnits()).isEmpty();
        assertThat(notice.others()).isEmpty();
    }

    @Test
    void bothBpmnFormsOfABuRoleNodeAreReferences() {
        unit("fu-new", "NEW_FORM", "New Form");
        unit("fu-old", "OLD_FORM", "Old Form");
        unit("fu-b64", "LEGACY", "Legacy");
        unit("fu-other", "OTHER", "Other");
        process("fu-new", task("<custom:property name=\"assigneeType\" value=\"BU_ROLE\"/>"
                + "<custom:property name=\"businessUnitId\" value=\"FINANCE\"/>"));
        process("fu-old", task("<custom:property name=\"assigneeType\" value=\"BU_ROLE\"/>"
                + "<custom:property name=\"assigneeValue\" value=\"bu-fin\"/>"));
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES (?, 'fu-b64', 'PROCESS', ?)",
                UUID.randomUUID().toString(), Base64.getEncoder().encodeToString(task(
                        "<custom:property name=\"businessUnitId\" value=\"FINANCE\"/>").getBytes(StandardCharsets.UTF_8)));
        // assigneeValue names a BU only on a BU_ROLE node; a FINANCE text elsewhere is no reference.
        process("fu-other", task("<custom:property name=\"assigneeType\" value=\"VIRTUAL_GROUP\"/>"
                + "<custom:property name=\"assigneeValue\" value=\"FINANCE\"/>")
                + task("<custom:property name=\"businessUnitId\" value=\"FINANCE_EAST\"/>"));

        assertThat(finder.findForNotice(finance).functionUnits())
                .extracting(ReferenceNotice.FunctionUnit::code)
                .containsExactly("LEGACY", "NEW_FORM", "OLD_FORM");
    }

    @Test
    void viewRulesAccessRowsAndOtherTargetsAreReferences() {
        unit("fu-view", "VIEWS", "Views");
        unit("fu-access", "ACCESS", "Access");
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('c-v', 'fu-view', 'MAIN_TABLE_VIEW', "
                + "'[{\"viewName\":\"All\",\"accessRules\":[{\"targetType\":\"BUSINESS_UNIT\",\"targetId\":\"FINANCE\"},"
                + "{\"targetType\":\"ROLE\",\"targetId\":\"MANAGER\"}]}]')");
        jdbc.update("INSERT INTO sys_function_unit_access VALUES ('a1', 'BUSINESS_UNIT', 'bu-fin', 'fu-access')");
        jdbc.update("INSERT INTO sys_function_unit_access VALUES ('a2', 'ROLE', 'FINANCE', 'fu-access')");
        jdbc.update("INSERT INTO bi_dashboard_assignment VALUES ('b1', 'BUSINESS_UNIT', 'FINANCE', NULL),"
                + " ('b2', 'BUSINESS_UNIT', 'bu-fin', NULL), ('b3', 'ROLE', 'bu-fin', NULL)");
        jdbc.update("INSERT INTO rt_table_access VALUES ('r1', 'BUSINESS_UNIT', 'FINANCE', NULL)");
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES ('u1', 'bu-fin'), ('u2', 'bu-fin')");
        jdbc.update("INSERT INTO sys_business_unit_roles VALUES ('br1', 'bu-fin')");

        ReferenceNotice notice = finder.findForNotice(finance);

        assertThat(notice.functionUnits()).extracting(ReferenceNotice.FunctionUnit::code)
                .containsExactly("ACCESS", "VIEWS");
        assertThat(notice.others()).containsExactly("admin.role.ref_ubr 2", "admin.role.ref_bu_binding 1",
                "admin.role.ref_rt_access 1", "admin.role.ref_bi_assignment 2");
    }

    private void unit(String id, String code, String name) {
        jdbc.update("INSERT INTO sys_function_units (id, name, code) VALUES (?, ?, ?)", id, name, code);
    }

    private void process(String functionUnitId, String bpmn) {
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES (?, ?, 'PROCESS', ?)",
                UUID.randomUUID().toString(), functionUnitId, bpmn);
    }

    private static String task(String properties) {
        return "<bpmn:userTask id=\"T" + UUID.randomUUID().toString().substring(0, 4) + "\" name=\"Review\">"
                + "<bpmn:extensionElements><custom:properties>" + properties
                + "</custom:properties></bpmn:extensionElements></bpmn:userTask>";
    }
}
