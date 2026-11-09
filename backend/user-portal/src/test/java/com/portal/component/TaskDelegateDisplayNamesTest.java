package com.portal.component;

import com.portal.dto.TaskInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.assertj.core.api.Assertions.assertThat;

/** The task page names the single-task delegate instead of showing the stored user id or BU / role codes. */
class TaskDelegateDisplayNamesTest {

    private final EmbeddedDatabase db = new EmbeddedDatabaseBuilder().generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2).build();
    private TaskDelegateDisplayNames names;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE sys_users(id VARCHAR(64), username VARCHAR(100), full_name VARCHAR(100),"
                + " display_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_business_units(id VARCHAR(64), code VARCHAR(64), name VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_roles(id VARCHAR(64), code VARCHAR(64), name VARCHAR(100))");
        jdbc.update("INSERT INTO sys_users VALUES ('user-e2e-wangfang', 'e2e_wangfang', 'Wang Fang', NULL)");
        jdbc.update("INSERT INTO sys_users VALUES ('user-nameless', 'nameless', NULL, NULL)");
        jdbc.update("INSERT INTO sys_business_units VALUES ('bu-1', 'FIN', 'Finance')");
        jdbc.update("INSERT INTO sys_roles VALUES ('r-1', 'CLERK', 'Clerk')");
        names = new TaskDelegateDisplayNames(jdbc);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void aUserDelegateIsNamed() {
        TaskInfo task = TaskInfo.builder().taskId("t1").delegatedTo("user-e2e-wangfang").build();

        names.annotate(task);

        assertThat(task.getDelegatedToName()).isEqualTo("Wang Fang");
    }

    @Test
    void aDelegateWithoutANameShowsTheUsername() {
        TaskInfo task = TaskInfo.builder().taskId("t1").delegatedTo("user-nameless").build();

        names.annotate(task);

        assertThat(task.getDelegatedToName()).isEqualTo("nameless");
    }

    @Test
    void aBuRoleDelegateNamesTheUnitAndTheRole() {
        TaskInfo task = TaskInfo.builder().taskId("t1").delegatedBuCode("FIN").delegatedRoleCode("CLERK").build();

        names.annotate(task);

        assertThat(task.getDelegatedBuName()).isEqualTo("Finance");
        assertThat(task.getDelegatedRoleName()).isEqualTo("Clerk");
    }

    @Test
    void unknownTargetsAndUndelegatedTasksStayUnnamed() {
        TaskInfo unknown = TaskInfo.builder().taskId("t1").delegatedTo("ghost")
                .delegatedBuCode("NONE").delegatedRoleCode("NONE").build();
        TaskInfo plain = TaskInfo.builder().taskId("t2").build();

        names.annotate(unknown);
        names.annotate(plain);

        assertThat(unknown.getDelegatedToName()).isNull();
        assertThat(unknown.getDelegatedBuName()).isNull();
        assertThat(unknown.getDelegatedRoleName()).isNull();
        assertThat(plain.getDelegatedToName()).isNull();
    }
}
