package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentReconciliationFairnessDatabaseTest extends AssignmentPostgresFixture {
    @BeforeEach void clear() {
        jdbc.update("DELETE FROM we_assignment_task_states"); jdbc.update("DELETE FROM we_assignment_instances"); jdbc.update("DELETE FROM we_assignment_cases");
    }
    @Test void ongoingCaseWritesCannotStarveAnOlderTaskWhenAllDeadlinesAreEqual() {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version,revision) VALUES(?,'actor',1,2)",id);
        jdbc.update("INSERT INTO we_assignment_instances(process_instance_id,case_id,round_id,process_definition_id,function_unit_code,call_path,state) VALUES('root',?,?,'definition','synthetic','[]','ACTIVE')",id,UUID.randomUUID());
        for (String task:List.of("a","b")) jdbc.update("""
                INSERT INTO we_assignment_task_states(task_id,case_id,process_instance_id,process_definition_id,node_key,slot_key,slot_identity,policy_hash,primary_candidates,state,next_evaluation_at,synced_revision,last_evaluated_at)
                VALUES (?,?,'root','definition','work',?,'{}','hash','[]','ACTIVE','9999-12-31',?,clock_timestamp()-CAST(? AS interval))
                """,task,id,task,"a".equals(task)?1:0,"a".equals(task)?"1 second":"1 minute");
        var states=new AssignmentTaskStateRepository(jdbc,new ObjectMapper());
        assertEquals(List.of("b"),states.dueTasks(0,1));
        jdbc.update("UPDATE we_assignment_task_states SET synced_revision=2,last_evaluated_at=clock_timestamp() WHERE task_id='b'");
        jdbc.update("UPDATE we_assignment_cases SET revision=3 WHERE case_id=?",id);
        assertEquals(List.of("a"),states.dueTasks(0,1));
        jdbc.update("UPDATE we_assignment_task_states SET synced_revision=3,last_evaluated_at=clock_timestamp() WHERE task_id='a'");
        jdbc.update("UPDATE we_assignment_cases SET revision=4 WHERE case_id=?",id);
        assertEquals(List.of("b"),states.dueTasks(0,1));
    }
    @Test void firstTasksExpiringAgainCannotStarveOlderUnprocessedMiTasksInTheSameCase() {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version) VALUES(?,'actor',1)",id);
        jdbc.update("INSERT INTO we_assignment_instances(process_instance_id,case_id,round_id,process_definition_id,function_unit_code,call_path,state) VALUES('root',?,?,'definition','synthetic','[]','ACTIVE')",id,UUID.randomUUID());
        for (String task:List.of("a","b","c","d")) jdbc.update("""
                INSERT INTO we_assignment_task_states(task_id,case_id,process_instance_id,process_definition_id,node_key,slot_key,slot_identity,policy_hash,primary_candidates,state,next_evaluation_at)
                VALUES (?,?,'root','definition','work',?,'{}','hash','[]','ACTIVE',clock_timestamp()-interval '1 minute')
                """,task,id,task);
        var states=new AssignmentTaskStateRepository(jdbc,new ObjectMapper());
        assertEquals(List.of("a","b"),states.dueTasks(0,2));
        // They have already been processed once and expire again before the next bounded tick.
        jdbc.update("UPDATE we_assignment_task_states SET next_evaluation_at=clock_timestamp()-interval '1 second' WHERE task_id IN ('a','b')");
        assertEquals(List.of("c","d"),states.dueTasks(0,2));
        jdbc.update("UPDATE we_assignment_cases SET revision=1 WHERE case_id=?",id);
        jdbc.update("UPDATE we_assignment_task_states SET synced_revision=1 WHERE task_id IN ('c','d')");
        assertEquals(List.of("a","b"),states.dueTasks(0,2));
    }
}
