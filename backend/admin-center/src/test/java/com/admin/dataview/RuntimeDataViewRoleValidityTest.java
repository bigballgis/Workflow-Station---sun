package com.admin.dataview;

import com.admin.repository.RoleRepository;
import com.admin.repository.UserRoleRepository;
import com.admin.service.UserBusinessUnitService;
import com.platform.security.entity.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Exercises the production JPA role query and View policy against real authorization dates. */
@Testcontainers(disabledWithoutDocker = true)
class RuntimeDataViewRoleValidityTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static SessionFactory factory;
    private static JdbcTemplate jdbc;
    private EntityManager entityManager;
    private RuntimeDataViewAccess policy;
    private final Map<String, Role> roles = new HashMap<>();

    @BeforeAll static void database() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("""
                CREATE TABLE sys_roles(id TEXT PRIMARY KEY);
                CREATE TABLE sys_user_roles(user_id TEXT,role_id TEXT,valid_from TIMESTAMP,valid_to TIMESTAMP);
                CREATE TABLE sys_virtual_group_roles(virtual_group_id TEXT,role_id TEXT);
                CREATE TABLE sys_virtual_group_members(group_id TEXT,user_id TEXT);
                CREATE TABLE sys_user_business_unit_roles(user_id TEXT,role_id TEXT);
                """);
        factory = new Configuration().addAnnotatedClass(Role.class).addAnnotatedClass(UserRole.class)
                .addAnnotatedClass(VirtualGroupRole.class).addAnnotatedClass(VirtualGroupMember.class)
                .addAnnotatedClass(UserBusinessUnitRole.class)
                .setProperty("hibernate.connection.url", POSTGRES.getJdbcUrl())
                .setProperty("hibernate.connection.username", POSTGRES.getUsername())
                .setProperty("hibernate.connection.password", POSTGRES.getPassword())
                .setProperty("hibernate.hbm2ddl.auto", "none").buildSessionFactory();
    }

    @AfterAll static void closeDatabase() { if (factory != null) factory.close(); }

    @BeforeEach void policy() {
        jdbc.execute("TRUNCATE sys_roles,sys_user_roles,sys_virtual_group_roles,sys_virtual_group_members,"
                + "sys_user_business_unit_roles");
        entityManager = factory.createEntityManager();
        var repository = new JpaRepositoryFactory(entityManager).getRepository(UserRoleRepository.class);
        var roleRepository = mock(RoleRepository.class);
        when(roleRepository.findAllById(any())).thenAnswer(call -> {
            List<Role> result = new ArrayList<>();
            for (String id : call.<Iterable<String>>getArgument(0)) result.add(roles.get(id));
            return result;
        });
        var units = mock(UserBusinessUnitService.class);
        when(units.getUserBusinessUnitIds("reader")).thenReturn(List.of("bu"));
        policy = new RuntimeDataViewAccess(repository, roleRepository, units);
    }

    @AfterEach void closeEntityManager() { if (entityManager != null) entityManager.close(); }

    @ParameterizedTest
    @CsvSource({"ADMIN,expired,false", "ADMIN,future,false", "ADMIN,current,true", "ADMIN,unlimited,true",
            "BU_BOUNDED,expired,false", "BU_BOUNDED,future,false", "BU_BOUNDED,current,true", "BU_BOUNDED,unlimited,true"})
    void onlyCurrentlyValidDirectAssignmentsGrantViews(String type, String period, boolean expected) {
        addRole(type);
        String from = "future".equals(period) ? "now()+interval '1 day'" : "now()-interval '2 days'";
        String to = "expired".equals(period) ? "now()-interval '1 day'" : "now()+interval '2 days'";
        if ("unlimited".equals(period)) { from = "NULL"; to = "NULL"; }
        jdbc.execute("INSERT INTO sys_user_roles VALUES('reader','role'," + from + "," + to + ")");
        assertGrant(type, expected);
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,group", "ADMIN,workspace", "BU_BOUNDED,group", "BU_BOUNDED,workspace"})
    void independentGrantSurvivesAnExpiredDirectAssignment(String type, String source) {
        addRole(type);
        jdbc.execute("INSERT INTO sys_user_roles VALUES('reader','role',NULL,now()-interval '1 day')");
        if ("group".equals(source)) {
            jdbc.execute("INSERT INTO sys_virtual_group_members VALUES('team','reader');"
                    + "INSERT INTO sys_virtual_group_roles VALUES('team','role')");
        } else {
            jdbc.execute("INSERT INTO sys_user_business_unit_roles VALUES('reader','role')");
        }
        assertGrant(type, true);
    }

    @Test void inactiveRoleDefinitionCannotGrantAdministratorBypass() {
        addRole("ADMIN");
        roles.get("role").setStatus("INACTIVE");
        jdbc.execute("INSERT INTO sys_user_roles VALUES('reader','role',NULL,NULL)");
        assertGrant("ADMIN", false);
    }

    private void addRole(String type) {
        jdbc.update("INSERT INTO sys_roles VALUES('role')");
        roles.put("role", Role.builder().id("role").code("ADMIN".equals(type) ? "SYS_ADMIN" : "READER")
                .type(type).status("ACTIVE").build());
    }

    private void assertGrant(String type, boolean expected) {
        var principal = policy.principal("reader");
        assertThat(principal.administrator()).isEqualTo(expected && "ADMIN".equals(type));
        var definition = "ADMIN".equals(type) ? Map.<String, Object>of("accessRules", List.of())
                : Map.<String, Object>of("accessRules", List.of(
                        Map.of("targetType", "BUSINESS_UNIT", "targetId", "bu"),
                        Map.of("targetType", "ROLE", "targetId", "role")));
        assertThat(policy.allows(principal, definition)).isEqualTo(expected);
    }
}
