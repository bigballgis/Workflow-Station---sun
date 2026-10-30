package com.portal.component;

import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import com.portal.component.impl.AssignmentPortalSyncImpl;
import com.portal.repository.*;
import com.portal.util.ActionEmailRuleConditionEvaluator;
import com.portal.client.AssignmentEngineTransport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentPostActionDatabaseTest extends AssignmentPortalDatabaseFixture {
    AssignmentPostActionRepository mail;
    AssignmentPortalSyncImpl sync;
    AssignmentOperationReceipt receipt;
    UUID operation,caseId;
    String instance;
    @BeforeEach void prepare() {
        jdbc.update("UPDATE up_assignment_operation_sync SET post_action_dispatched=true");
        operation=UUID.randomUUID(); caseId=UUID.randomUUID(); instance=operation.toString();
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version,accepted_operation_seq) VALUES(?,'actor',1,1)",caseId);
        jdbc.update("INSERT INTO up_process_instance(id,variables,lock_version) VALUES(?,'{\"verdict\":1,\"recipient\":\"original-person\"}',0)",instance);
        mail=new AssignmentPostActionRepository(jdbc,json); sync=component(mail);
        var before=json.createObjectNode().put("verdict",1); var after=before.deepCopy().put("verdict",2);
        var plan=new AssignmentPostAction.Plan("original-action","Approve","APPROVE","original comment",List.of(Map.of("enabled",true,"emailTo","${recipient}")));
        var effect=new AssignmentCompletionRequest.Effect(instance,AssignmentVariablePatch.between(before,after),"review","a".repeat(64),
                List.of(),Map.of(),List.of(),JsonUtils.getObjectMapper().convertValue(plan,new com.fasterxml.jackson.core.type.TypeReference<>() { }));
        Map<String,Object> payload=JsonUtils.getObjectMapper().convertValue(effect,new com.fasterxml.jackson.core.type.TypeReference<>() { });
        receipt=new AssignmentOperationReceipt(operation,caseId,"task","actor",null,"APPROVE","a".repeat(64),1,0,payload,"PENDING_SYNC");
    }
    @Test void mailConditionsAndVariablesUseTheOriginalCommittedSnapshotAfterLaterEdits() {
        transaction.executeWithoutResult(status->sync.apply(receipt));
        var frozen=mail.context(operation).orElseThrow();
        jdbc.update("UPDATE up_process_instance SET variables='{\"verdict\":9,\"recipient\":\"later-person\"}' WHERE id=?",instance);
        var values=mail.variables(snapshot("actor",0));
        assertEquals("original-person",values.get("recipient")); assertEquals(2,values.get("verdict"));
        var filter=Map.<String,Object>of("logic","and","conditions",List.of(Map.of("fieldName","recipient","operator","eq","value","original-person")),"groups",List.of());
        var evaluator=new ActionEmailRuleConditionEvaluator(jdbc);
        assertArrayEquals(new boolean[]{true},evaluator.evaluateSnapshot(frozen.path("row").toString(),List.of(filter)));
        assertArrayEquals(new boolean[]{false},evaluator.evaluate(instance,List.of(filter)));
        transaction.executeWithoutResult(status->sync.apply(receipt));
        assertEquals(frozen,mail.context(operation).orElseThrow());
    }
    @Test void snapshotAccessMustMatchTheOriginalActorProcessRuleAndEvent() {
        transaction.executeWithoutResult(status->sync.apply(receipt));
        assertThrows(AssignmentFailure.class,()->mail.variables(snapshot("other",0)));
        assertThrows(AssignmentFailure.class,()->mail.variables(snapshot("actor",1)));
        assertThrows(AssignmentFailure.class,()->mail.variables(new AssignmentPostAction.SnapshotRequest(operation,0,"forged",instance,"actor")));
        assertThrows(AssignmentFailure.class,()->mail.variables(new AssignmentPostAction.SnapshotRequest(operation,0,AssignmentPostAction.eventId(operation,0),"other","actor")));
    }
    @Test void failedSnapshotRecordingRollsBackBusinessValuesLedgerAndWatermarkTogether() {
        var failing=spy(mail);
        doAnswer(call->{call.callRealMethod(); throw new IllegalStateException("synthetic post-action snapshot failure");}).when(failing).record(any(),any());
        assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status->component(failing).apply(receipt)));
        assertTrue(mail.context(operation).isEmpty());
        assertEquals(1,jdbc.queryForObject("SELECT (variables->>'verdict')::int FROM up_process_instance WHERE id=?",Integer.class,instance));
        assertEquals(0,jdbc.queryForObject("SELECT portal_applied_operation_seq FROM we_assignment_cases WHERE case_id=?",Long.class,caseId));
    }
    @Test void anUnacknowledgedEnqueueRetriesTheSameOperationAndRuleWithoutRecompleting() {
        transaction.executeWithoutResult(status->sync.apply(receipt));
        var engine=mock(AssignmentEngineTransport.class);
        when(engine.post(eq(List.of("post-actions")),any(),any(),eq(false)))
                .thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE)).thenReturn(true);
        var dispatcher=new AssignmentPostActionDispatcher(mail,new ActionEmailRuleConditionEvaluator(jdbc),engine);
        dispatcher.dispatch();
        assertFalse(jdbc.queryForObject("SELECT post_action_dispatched FROM up_assignment_operation_sync WHERE operation_id=?",Boolean.class,operation));
        jdbc.update("UPDATE up_assignment_operation_sync SET post_action_next_attempt_at=clock_timestamp() WHERE operation_id=?",operation);
        dispatcher.dispatch();
        assertTrue(jdbc.queryForObject("SELECT post_action_dispatched FROM up_assignment_operation_sync WHERE operation_id=?",Boolean.class,operation));
        verify(engine,times(2)).post(eq(List.of("post-actions")),eq(new AssignmentPostAction.Enqueue(operation,0)),any(),eq(false));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM up_assignment_operation_sync WHERE operation_id=?",Integer.class,operation));
    }
    private AssignmentPostAction.SnapshotRequest snapshot(String actor,int index) {
        return new AssignmentPostAction.SnapshotRequest(operation,index,AssignmentPostAction.eventId(operation,index),instance,actor);
    }
    private AssignmentPortalSyncImpl component(AssignmentPostActionRepository postActions) {
        var component=new AssignmentPortalSyncImpl(new AssignmentPortalSyncRepository(jdbc,json),jdbc,mock(AssignmentAcceptedProjection.class),postActions,
                mock(AssignmentCompletionCacheInvalidation.class));
        ReflectionTestUtils.setField(component,"entities",mock(jakarta.persistence.EntityManager.class));
        com.portal.testsupport.AssignmentCoordinationTestSupport.stubVerified(component); return component;
    }
}
