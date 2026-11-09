package com.portal.component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The Member Management list: the unit's members with the roles they hold there, from one real SQL query. */
class BusinessUnitMemberQueryTest {

    private final EmbeddedDatabase db = new EmbeddedDatabaseBuilder().generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2).build();
    private BusinessUnitMemberQuery query;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE sys_users(id VARCHAR(64), username VARCHAR(100), full_name VARCHAR(100),"
                + " display_name VARCHAR(100), deleted BOOLEAN)");
        jdbc.execute("CREATE TABLE sys_roles(id VARCHAR(64), code VARCHAR(64), name VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_user_business_units(user_id VARCHAR(64), business_unit_id VARCHAR(64),"
                + " created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_user_business_unit_roles(user_id VARCHAR(64), business_unit_id VARCHAR(64),"
                + " role_id VARCHAR(64), created_at TIMESTAMP)");
        jdbc.update("INSERT INTO sys_users VALUES ('u-ann', 'ann', 'Ann Lee', 'Ann', false)");
        jdbc.update("INSERT INTO sys_users VALUES ('u-bob', 'bob', NULL, 'Bobby', false)");
        jdbc.update("INSERT INTO sys_users VALUES ('u-cat', 'cat', NULL, NULL, false)");
        jdbc.update("INSERT INTO sys_users VALUES ('u-gone', 'gone', 'Gone', NULL, true)");
        jdbc.update("INSERT INTO sys_roles VALUES ('r-clerk', 'CLERK', 'Clerk')");
        jdbc.update("INSERT INTO sys_roles VALUES ('r-appr', 'APPROVER', 'Approver')");
        jdbc.update("INSERT INTO sys_user_business_units VALUES ('u-ann', 'bu-1', TIMESTAMP '2026-10-01 09:00:00')");
        jdbc.update("INSERT INTO sys_user_business_units VALUES ('u-cat', 'bu-1', TIMESTAMP '2026-10-03 09:00:00')");
        jdbc.update("INSERT INTO sys_user_business_units VALUES ('u-gone', 'bu-1', TIMESTAMP '2026-10-01 09:00:00')");
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES ('u-ann', 'bu-1', 'r-clerk', TIMESTAMP '2026-10-02 09:00:00')");
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES ('u-ann', 'bu-1', 'r-appr', TIMESTAMP '2026-10-02 10:00:00')");
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES ('u-ann', 'bu-2', 'r-clerk', TIMESTAMP '2026-10-02 09:00:00')");
        // Holds a role without a membership row: still a member.
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES ('u-bob', 'bu-1', 'r-clerk', TIMESTAMP '2026-10-04 09:00:00')");
        jdbc.update("INSERT INTO sys_user_business_unit_roles VALUES ('u-gone', 'bu-1', 'r-clerk', TIMESTAMP '2026-10-01 09:00:00')");
        query = new BusinessUnitMemberQuery(jdbc);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void eachMemberComesOnceWithTheRolesHeldInThisUnit() {
        List<Map<String, Object>> members = query.members("bu-1");

        assertThat(members).extracting(m -> m.get("userId")).containsExactly("u-ann", "u-bob", "u-cat");
        Map<String, Object> ann = members.get(0);
        assertThat(ann.get("username")).isEqualTo("ann");
        assertThat(ann.get("fullName")).isEqualTo("Ann Lee");
        assertThat(ann.get("joinedAt")).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 0));
        assertThat(roles(ann)).containsExactly(Map.of("id", "r-appr", "code", "APPROVER", "name", "Approver"),
                Map.of("id", "r-clerk", "code", "CLERK", "name", "Clerk"));
    }

    @Test
    void aMemberWithoutARoleOrANameIsStillListed() {
        List<Map<String, Object>> members = query.members("bu-1");

        assertThat(members.get(1).get("fullName")).isEqualTo("Bobby");
        assertThat(roles(members.get(1))).extracting(r -> r.get("id")).containsExactly("r-clerk");
        assertThat(members.get(2).get("fullName")).isNull();
        assertThat(roles(members.get(2))).isEmpty();
    }

    @Test
    void anotherUnitOrAnUnknownUnitListsOnlyItsOwn() {
        assertThat(query.members("bu-2")).extracting(m -> m.get("userId")).containsExactly("u-ann");
        assertThat(query.members("bu-none")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> roles(Map<String, Object> member) {
        return (List<Map<String, Object>>) member.get("roles");
    }
}
