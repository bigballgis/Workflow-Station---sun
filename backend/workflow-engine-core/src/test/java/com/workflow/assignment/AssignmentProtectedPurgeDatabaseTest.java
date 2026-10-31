package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentFailure;
import com.workflow.component.MultiInstanceCanceller;
import com.workflow.component.ProcessInstanceQueryManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real runtime/history and the existing purge component, not a mocked delete/status result. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentProtectedPurgeDatabaseTest extends AssignmentPostgresFixture {
    ProcessInstanceQueryManager purge;
    MultiInstanceCanceller canceller;
    String definition;
    @BeforeAll void configure() {
        openEngine(); canceller=mock(MultiInstanceCanceller.class); purge=new ProcessInstanceQueryManager();
        ReflectionTestUtils.setField(purge,"runtimeService",engine.getRuntimeService());
        ReflectionTestUtils.setField(purge,"historyService",engine.getHistoryService());
        ReflectionTestUtils.setField(purge,"multiInstanceCanceller",canceller);
        var cases=new AssignmentCaseRepository(jdbc,new ObjectMapper());
        var definitions=new AssignmentDefinitionPolicy(engine.getRepositoryService());
        var legacy=new AssignmentLegacyTaskCheck(engine.getTaskService(),engine.getRuntimeService(),cases,definitions);
        ReflectionTestUtils.setField(purge,"assignmentCleanup",new AssignmentLegacyHistoryCheck(engine.getHistoryService(),engine.getRuntimeService(),cases,definitions,legacy));
        var deployment=engine.getRepositoryService().createDeployment().addString("purge.bpmn20.xml","""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:synthetic">
                <process id="purge_probe" isExecutable="true"><startEvent id="start"/><userTask id="work"/><endEvent id="end"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="work"/><sequenceFlow id="b" sourceRef="work" targetRef="end"/>
                </process></definitions>
                """).deploy();
        definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
    }
    @BeforeEach void resetCanceller() { clearInvocations(canceller); }

    @Test void protectedRunningPurgeIsRejectedBeforeCancellingTasksOrDeletingEitherStore() {
        var id=start(true,false);
        assertAll(()->assertThrows(AssignmentFailure.class,()->purge.purgeProcessInstanceAndHistory(id)),
                ()->verifyNoInteractions(canceller),
                ()->assertNotNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(id).singleResult()),
                ()->assertEquals(1,engine.getTaskService().createTaskQuery().processInstanceId(id).count()),()->assertHistory(id));
    }
    @Test void protectedEndedPurgeCannotRemoveTheFrozenRoundVariablesRequiredForReopen() {
        var id=start(true,true);
        assertAll(()->assertThrows(AssignmentFailure.class,()->purge.purgeProcessInstanceAndHistory(id)),
                ()->verifyNoInteractions(canceller),()->assertHistory(id),
                ()->assertEquals(1,engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(id).finished().count()));
    }
    @Test void ordinaryRunningAndEndedInstancesKeepTheExistingPurgeBehavior() {
        var running=start(false,false); var ended=start(false,true);
        purge.purgeProcessInstanceAndHistory(running); purge.purgeProcessInstanceAndHistory(ended);
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(running).singleResult());
        assertEquals(0,engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(running).count());
        assertEquals(0,engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(ended).count());
        assertEquals(0,engine.getHistoryService().createHistoricVariableInstanceQuery().processInstanceId(ended).count());
        verify(canceller).cancelMultiInstanceTasks(running);
    }
    @Test void endedCalledInstanceStillInheritsProtectionWhenItsOwnRegistrationIsMissing() {
        var child=endedChild(true);
        assertThrows(AssignmentFailure.class,()->purge.purgeProcessInstanceAndHistory(child));
        verifyNoInteractions(canceller);
        assertNotNull(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(child).singleResult());
        assertEquals(1,engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(child).finished().count());
    }
    @Test void endedLegacyChildOfAnOrdinaryRunningCallerRemainsPurgeable() {
        var child=endedChild(false);
        purge.purgeProcessInstanceAndHistory(child);
        assertEquals(0,engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(child).count());
        assertEquals(0,engine.getHistoryService().createHistoricTaskInstanceQuery().processInstanceId(child).count());
        verifyNoInteractions(canceller);
    }
    private String endedChild(boolean parentTracked) {
        String key="caller_"+UUID.randomUUID().toString().replace("-","");
        String xml="""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="urn:synthetic">
                <process id="%s" isExecutable="true"><startEvent id="start"/>
                <callActivity id="call" calledElement="%s" flowable:calledElementType="id"/><userTask id="after"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="call"/><sequenceFlow id="b" sourceRef="call" targetRef="after"/>
                </process></definitions>
                """.formatted(key,definition);
        var deployment=engine.getRepositoryService().createDeployment().addString(key+".bpmn20.xml",xml).deploy();
        String callerDefinition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
        var caller=engine.getRuntimeService().startProcessInstanceById(callerDefinition);
        var child=engine.getRuntimeService().createProcessInstanceQuery().superProcessInstanceId(caller.getId()).singleResult();
        if (parentTracked) {
            var cases=new AssignmentCaseRepository(jdbc,new ObjectMapper()); var caseId=UUID.randomUUID();
            cases.createCase(caseId,caller.getId(),"original","BU");
            cases.insert(new AssignmentCaseRegistry.Registration(caller.getId(),caseId,UUID.randomUUID(),callerDefinition,List.of()),null,null,key);
            assertTrue(cases.find(child.getId()).isEmpty(),"Exercise real historical ancestry, without a child registration shortcut");
        }
        engine.getTaskService().complete(engine.getTaskService().createTaskQuery().processInstanceId(child.getId()).singleResult().getId());
        return child.getId();
    }
    private void assertHistory(String id) {
        assertNotNull(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(id).singleResult());
        var variable=engine.getHistoryService().createHistoricVariableInstanceQuery().processInstanceId(id).variableName("retained_value").singleResult();
        assertNotNull(variable); assertEquals("synthetic",variable.getValue());
    }
    private String start(boolean tracked,boolean ended) {
        var instance=engine.getRuntimeService().startProcessInstanceById(definition,Map.of("retained_value","synthetic"));
        if (tracked) {
            var cases=new AssignmentCaseRepository(jdbc,new ObjectMapper()); var caseId=UUID.randomUUID();
            cases.createCase(caseId,instance.getId(),"original","BU");
            cases.insert(new AssignmentCaseRegistry.Registration(instance.getId(),caseId,UUID.randomUUID(),definition,List.of()),null,null,"purge_probe");
        }
        if (ended) engine.getTaskService().complete(engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult().getId());
        return instance.getId();
    }
}
