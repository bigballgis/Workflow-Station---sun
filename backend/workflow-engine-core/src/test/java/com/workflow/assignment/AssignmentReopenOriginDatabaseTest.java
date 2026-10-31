package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentReopenReceipt;
import com.workflow.assignment.impl.AssignmentReopenComponentImpl;
import org.flowable.common.engine.impl.identity.Authentication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentReopenOriginDatabaseTest extends AssignmentPostgresFixture {
    @BeforeAll void deploy() {
        openEngine();
        engine.getRepositoryService().createDeployment().addString("origin.bpmn20.xml","""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="origin">
                <process id="origin" isExecutable="true"><startEvent id="start"/><userTask id="submit" flowable:assignee="${initiator}"/>
                <endEvent id="end"/><sequenceFlow id="a" sourceRef="start" targetRef="submit"/><sequenceFlow id="b" sourceRef="submit" targetRef="end"/>
                </process></definitions>
                """).deploy();
    }
    @Test void administratorIsRecordedAsReopenerWhileOriginalInitiatorAndBuRemainAuthoritative() {
        var fixture=fixture(Map.of("initiator","original","activeBusinessUnitId","original-bu"));
        Authentication.setAuthenticatedUserId("outer-context");
        try {
            var result=fixture.component().reopen(fixture.source(),fixture.request());
            assertEquals("administrator",result.actor());
            var process=engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(result.processInstanceId()).singleResult();
            assertEquals("original",process.getStartUserId());
            assertEquals("original",engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(process.getId()).singleResult().getStartUserId());
            assertEquals("original",engine.getTaskService().createTaskQuery().processInstanceId(process.getId()).singleResult().getAssignee());
            assertEquals("original-bu",result.variables().get("activeBusinessUnitId"));
            assertEquals("original",fixture.cases().origin(result.caseId()).initiator());
            assertEquals("outer-context",Authentication.getAuthenticatedUserId());
            assertEquals(result,fixture.component().reopen(fixture.source(),fixture.request()));
        } finally { Authentication.setAuthenticatedUserId(null); }
    }
    @Test void failedStartRestoresCallerAuthenticationAndRollsBackNewRound() {
        var fixture=fixture(Map.of("initiator","original","notSerializable",new Object()));
        Authentication.setAuthenticatedUserId("outer-context");
        try {
            var failure=assertThrows(org.flowable.common.engine.api.FlowableException.class,
                    ()->fixture.component().reopen(fixture.source(),fixture.request()));
            assertTrue(failure.getMessage().contains("variable type"),failure.getMessage());
            assertEquals("outer-context",Authentication.getAuthenticatedUserId());
            assertTrue(fixture.cases().reopened(fixture.request().operationId()).isEmpty());
            assertTrue(fixture.cases().find(fixture.next()).isEmpty());
        } finally { Authentication.setAuthenticatedUserId(null); }
    }
    private Fixture fixture(Map<String,Object> variables) {
        var cases=new AssignmentCaseRepository(jdbc,new ObjectMapper());var caseId=UUID.randomUUID();String sourceId=UUID.randomUUID().toString();
        var definition=engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionKey("origin").singleResult().getId();
        var source=new AssignmentCaseRegistry.Registration(sourceId,caseId,UUID.randomUUID(),definition,List.of());
        cases.createCase(caseId,sourceId,"original","BU","original-bu");
        cases.insert(source,null,null,"FU");
        jdbc.update("UPDATE we_assignment_instances SET state='COMPLETED' WHERE process_instance_id=?",sourceId);
        var next=new AssignmentCaseRegistry.Registration(UUID.randomUUID().toString(),caseId,UUID.randomUUID(),definition,List.of());
        var registry=mock(AssignmentCaseRegistry.class);
        when(registry.reopen(sourceId)).thenAnswer(call->{cases.insert(next,null,sourceId,"FU");return next;});
        var preparation=mock(AssignmentReopenPreparation.class);
        var request=new AssignmentReopenReceipt.Request(UUID.randomUUID(),"administrator");
        when(preparation.prepare(sourceId,request)).thenReturn(new AssignmentReopenPreparation.Prepared(source,
                new AssignmentRootQualification.Prepared("administrator","admin-bu","ADMIN",0,Instant.now().plusSeconds(5)),variables,"business","Original"));
        when(preparation.snapshot(anyString())).thenAnswer(call->engine.getRuntimeService().getVariables(call.getArgument(0,String.class)));
        var component=new AssignmentReopenComponentImpl(registry,cases,preparation,engine.getRuntimeService(),transaction.getTransactionManager());
        ReflectionTestUtils.setField(component,"runtimeSupport",mock(AssignmentRuntimeSupport.class));
        return new Fixture(component,cases,request,next.instance(),sourceId);
    }
    private record Fixture(AssignmentReopenComponentImpl component,AssignmentCaseRepository cases,AssignmentReopenReceipt.Request request,String next,String source) { }
}
