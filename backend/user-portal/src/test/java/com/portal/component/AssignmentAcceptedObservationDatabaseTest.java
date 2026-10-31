package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentAcceptedObservationDatabaseTest extends AssignmentPortalDatabaseFixture {
    final WorkflowEngineClient engine=mock(WorkflowEngineClient.class);
    AssignmentAcceptedObservation component;
    AssignmentCompletionOrchestrator.Result completion;
    UUID operation;
    @BeforeAll void historyTable() {
        jdbc.execute("CREATE TABLE up_process_history(id bigserial PRIMARY KEY,process_instance_id varchar(64) NOT NULL,task_id varchar(64),"
                +"operator_id varchar(64) NOT NULL,operation_type varchar(50) NOT NULL,activity_type varchar(50),comment text,operation_time timestamp)");
        component=new AssignmentAcceptedObservation(jdbc,engine,transaction.getTransactionManager());
    }
    @BeforeEach void prepare() {
        reset(engine); operation=UUID.randomUUID();
        jdbc.update("INSERT INTO up_assignment_operation_sync(operation_id,case_id,operation_seq,request_digest,effect_digest) VALUES(?,?,1,?,?)",
                operation,UUID.randomUUID(),"a".repeat(64),"b".repeat(64));
        var expected=new SubTableBatchInitialMiOutcome.Expected("node",true,"renamed_items",List.of(Map.of("key",1)),List.of(),List.of());
        var plan=new SubTableBatchInitialMiOutcome.Plan("instance","frozen",List.of(expected));
        completion=new AssignmentCompletionOrchestrator.Result(operation,"READY",plan);
        when(engine.getProcessInstanceTasks("instance")).thenReturn(Optional.empty());
    }
    @Test void repeatedResponsesReuseOneStoredObservationAndOneHistoryEntry() {
        var first=component.observe(completion,"actor","task");
        assertTrue(first.historyRecorded()); assertEquals("UNCONFIRMED",first.status());
        assertEquals(first,component.observe(completion,"actor","task"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM up_process_history WHERE task_id='task'",Integer.class));
        verify(engine,times(1)).getProcessInstanceTasks("instance");
    }
    @Test void historyFailureCannotTurnAnAcceptedCompletionIntoAFailedOrRepeatedAction() {
        var result=component.observe(completion,null,"failed-history-task");
        assertFalse(result.historyRecorded()); assertEquals("UNCONFIRMED",result.status());
        assertNull(jdbc.queryForObject("SELECT completion_observation::text FROM up_assignment_operation_sync WHERE operation_id=?",String.class,operation));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM up_process_history WHERE task_id='failed-history-task'",Integer.class));
    }
    @Test void pendingSynchronizationDoesNotRunPostCommitObservationOrWriteHistory() {
        var pending=new AssignmentCompletionOrchestrator.Result(operation,"PENDING_SYNC",completion.observation());
        assertNull(component.observe(pending,"actor","pending-task")); verifyNoInteractions(engine);
    }
}
