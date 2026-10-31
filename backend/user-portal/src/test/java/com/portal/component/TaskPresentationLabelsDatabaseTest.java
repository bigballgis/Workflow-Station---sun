package com.portal.component;

import com.portal.client.AssignmentEngineTransport;
import com.portal.service.UserDisplayNameResolver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class TaskPresentationLabelsDatabaseTest extends AssignmentPortalDatabaseFixture {
    final AssignmentEngineTransport engine=mock(AssignmentEngineTransport.class);
    final UserDisplayNameResolver users=mock(UserDisplayNameResolver.class);
    TaskPresentationLabels labels;
    @BeforeAll void directory() {
        jdbc.execute("CREATE TABLE sys_business_units(id text,code text,name text)");
        jdbc.execute("CREATE TABLE sys_roles(id text,code text,name text)");
        jdbc.execute("CREATE TABLE sys_users(id text,username text,employee_id text)");
        jdbc.update("INSERT INTO sys_business_units VALUES('bu-id','BU_A','Finance')");
        jdbc.update("INSERT INTO sys_roles VALUES('role-id','MANAGER','Department Manager'),('audit-id','AUDITOR','Auditor')");
        jdbc.update("INSERT INTO sys_users VALUES('person-a','alice-login','employee-a'),('person-b','bob-login','employee-b')");
        labels=new TaskPresentationLabels(engine,users,jdbc);
    }
    @BeforeEach void names() {
        reset(engine,users);
        when(users.resolveBatch(anyCollection())).thenReturn(Map.of("person-a","Alice","person-b","Bob"));
    }
    @Test void unheldRolePoolShowsAllResolvedRolesAndPreservesActualUserAliases() {
        reply(null,Map.of("businessUnit","BU_A","roles",List.of("MANAGER","AUDITOR")));
        var display=labels.read(List.of("process"),List.of()).get(0);
        assertThat(display.assignee()).isEqualTo("Finance + Department Manager / Auditor");
        assertThat(display.stage()).isEqualTo("multi: sub form1");
        assertThat(display.userIds()).contains("person-a","alice-login","employee-a","person-b");
    }
    @Test void nativeHolderWinsOverAnyStalePoolLabel() {
        reply("person-a",Map.of("businessUnit","BU_A","roles",List.of("MANAGER")));
        var display=labels.read(List.of("process"),List.of()).get(0);
        assertThat(display.assignee()).isEqualTo("Alice");
        assertThat(display.userIds()).contains("person-a").doesNotContain("person-b");
    }
    @Test void ordinaryUnheldTaskShowsAllCandidateNames() {
        reply(null,null);
        assertThat(labels.read(List.of("process"),List.of()).get(0).assignee()).isEqualTo("Alice, Bob");
    }
    @Test void directoryMissKeepsConfiguredCodesWithoutGuessingAnotherBu() {
        reply(null,Map.of("businessUnit","REMOVED_BU","roles",List.of("REMOVED_ROLE")));
        assertThat(labels.read(List.of("process"),List.of()).get(0).assignee()).isEqualTo("REMOVED_BU + REMOVED_ROLE");
    }
    @Test void incompleteScopeIsAnErrorInsteadOfAHiddenFallback() {
        reply(null,Map.of("businessUnit","BU_A","roles",List.of()));
        assertThatThrownBy(()->labels.read(List.of("process"),List.of())).isInstanceOf(IllegalStateException.class);
    }
    private void reply(String holder,Object scope) {
        var task=new LinkedHashMap<String,Object>();
        task.put("taskId","task"); task.put("processInstanceId","process"); task.put("processDefinitionId","frozen");
        task.put("taskName","sub form1"); task.put("currentStepName","multi: sub form1");
        task.put("currentAssignee",holder); task.put("candidateUserIds",List.of("person-a","person-b"));
        var entry=new LinkedHashMap<String,Object>(); entry.put("task",task); entry.put("scope",scope);
        when(engine.post(eq(List.of("task-presentations")),any(),any(),eq(false))).thenReturn(List.of(entry));
    }
}
