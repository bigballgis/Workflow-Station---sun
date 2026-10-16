package com.workflow.recordnote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.jdbc.SqlIdentifiers;
import com.platform.common.recordnote.RecordNoteTaskTarget;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises production JSONB predicates against PostgreSQL, including nested parallel activations. */
@EnabledIfEnvironmentVariable(named="NOTE_TEST_JDBC_URL",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecordNoteRoundDatabaseTest {
    JdbcTemplate admin,jdbc;
    String schema;
    RecordNoteRoundRepository rounds;
    @BeforeAll void start() throws Exception {
        var source=new DriverManagerDataSource(System.getenv("NOTE_TEST_JDBC_URL"),
                System.getenv("NOTE_TEST_JDBC_USER"),System.getenv("NOTE_TEST_JDBC_PASSWORD"));
        admin=new JdbcTemplate(source);
        schema=SqlIdentifiers.requireIdentifier("note_round_test_"+UUID.randomUUID().toString().replace("-",""));
        admin.execute("CREATE SCHEMA "+schema);
        var properties=new Properties(); properties.setProperty("currentSchema",schema);
        var scoped=new DriverManagerDataSource(source.getUrl(),source.getUsername(),source.getPassword());
        scoped.setConnectionProperties(properties);
        jdbc=new JdbcTemplate(scoped);
        jdbc.execute("CREATE TABLE up_record_note(id bigint,target_type text,target_id text,table_kind text,table_id text,created_at timestamptz)");
        jdbc.execute("CREATE TABLE up_process_instance(id text,function_unit_catalog_id text,variables jsonb)");
        jdbc.execute("CREATE TABLE sys_function_unit_contents(function_unit_id text,content_type text,content_data text)");
        try(var connection=scoped.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(Path.of("../../deploy/init-scripts/00-schema/94-record-note-notifications.sql").toAbsolutePath().normalize()));
        }
        rounds=new RecordNoteRoundRepository(jdbc,new ObjectMapper());
    }
    @AfterAll void stop() { if(admin!=null && schema!=null) admin.execute("DROP SCHEMA "+schema+" CASCADE"); }
    @BeforeEach void clear() { jdbc.execute("TRUNCATE we_record_note_task_rounds"); }
    void store(String task,String row,Map<String,String> context) {
        rounds.created("request","definition","execution-"+task,new RecordNoteTaskTarget(task,"inner-work","inner",
                "arbitrary_lines",Map.of("custom_pk",row),context.entrySet().stream().filter(e -> e.getKey().startsWith("inner:")).map(Map.Entry::getValue).findFirst().orElseThrow(),"PERSON","owner_x",task,
                "COMPLETED",context));
    }
    Set<String> current() { return rounds.current("request").stream().map(RecordNoteTaskTarget::taskId).collect(java.util.stream.Collectors.toSet()); }
    @Test void reenteringOneNestedMiKeepsOtherParentRowsCurrent() {
        store("a","a",Map.of("outer:request","outer-1","inner:parent-a","inner-a-1"));
        store("b","b",Map.of("outer:request","outer-1","inner:parent-b","inner-b-1"));
        rounds.activate("request",Map.of("inner:parent-a","inner-a-2"));
        assertEquals(Set.of("b"),current());
        store("a2","a",Map.of("outer:request","outer-1","inner:parent-a","inner-a-2"));
        assertEquals(Set.of("a2","b"),current());
        rounds.cancelActivation("request","inner-a-2");
        assertEquals(Set.of("b"),current());
    }
    @Test void reenteringOuterMiInvalidatesAllItsCompletedDescendants() {
        store("a","a",Map.of("outer:request","outer-1","inner:parent-a","inner-a-1"));
        store("b","b",Map.of("outer:request","outer-1","inner:parent-b","inner-b-1"));
        rounds.activate("request",Map.of("outer:request","outer-2"));
        assertTrue(current().isEmpty());
    }
    @Test void returningToAnAncestorOnlyInvalidatesItsOwnParentActivation() {
        store("a","a",Map.of("outer:request","outer-1","inner:parent-a","inner-a-1"));
        store("b","b",Map.of("outer:request","outer-1","inner:parent-a","inner-a-1"));
        jdbc.update("UPDATE we_record_note_task_rounds SET activation_context=CAST(? AS jsonb) WHERE task_id='b'",
                "{\"outer:other-request-branch\":\"outer-other\"}");
        rounds.invalidate("request",List.of("inner-work"),null,"outer-1");
        assertEquals(Set.of("b"),current());
    }
    @Test void legacySnapshotsWithoutActivationContextRemainUnresolved() {
        store("a","a",Map.of("inner:parent-a","activation"));
        jdbc.update("UPDATE we_record_note_task_rounds SET activation_context='{}'::jsonb");
        assertEquals("UNRESOLVED",rounds.current("request").get(0).state());
    }
}
