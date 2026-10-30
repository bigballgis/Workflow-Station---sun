package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentFailure;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentTaskTrackingDatabaseTest extends AssignmentPostgresFixture {
    AssignmentTaskTrackingQuery query;
    AssignmentCaseRepository cases;
    String legacy,tracked;
    @BeforeAll void prepare() {
        openEngine(); var definitions=new AssignmentDefinitionPolicy(engine.getRepositoryService());
        cases=new AssignmentCaseRepository(jdbc,new ObjectMapper());
        query=new AssignmentTaskTrackingQuery(cases,definitions,new AssignmentLegacyTaskCheck(engine.getTaskService(),engine.getRuntimeService(),cases,definitions),
                engine.getRuntimeService(),engine.getHistoryService());
        legacy=deploy("read_legacy",false); tracked=deploy("read_tracked",true);
    }
    @Test void activeAndHistoricLegacyInstancesHavePositiveLegacyProof() {
        var process=engine.getRuntimeService().startProcessInstanceById(legacy);
        assertFalse(query.trackedInstance(process.getId(),legacy));
        var task=engine.getTaskService().createTaskQuery().processInstanceId(process.getId()).singleResult();
        engine.getTaskService().complete(task.getId());
        assertFalse(query.trackedInstance(process.getId(),legacy));
    }
    @Test void aTrackedDefinitionNeverBecomesLegacyWhenItsRegistryRowIsMissing() {
        var process=engine.getRuntimeService().startProcessInstanceById(tracked);
        assertTrue(query.trackedInstance(process.getId(),tracked));
    }
    @Test void aCaseTrackedLegacyDefinitionIsStillProtectedAndCannotUseAnotherDefinition() {
        var process=engine.getRuntimeService().startProcessInstanceById(legacy); UUID caseId=UUID.randomUUID();
        cases.createCase(caseId,process.getId(),"original","BU");
        cases.insert(new AssignmentCaseRegistry.Registration(process.getId(),caseId,UUID.randomUUID(),legacy,List.of()),null,null,"synthetic");
        assertTrue(query.trackedInstance(process.getId(),legacy));
        assertThrows(AssignmentFailure.class,()->query.trackedInstance(process.getId(),tracked));
    }
    @Test void unknownInstanceIsNotARealLegacyProof() {
        assertThrows(AssignmentFailure.class,()->query.trackedInstance("missing-instance",legacy));
    }
    private String deploy(String key,boolean protectedContract) {
        String metadata=protectedContract?"""
                <extensionElements><flowable:properties><flowable:property name="assignmentRuntimeContract" value="1"/>
                <flowable:property name="assignmentRuntimeTracking" value="true"/><flowable:property name="assignmentRuntimeUsers" value="{}"/>
                </flowable:properties></extensionElements>
                """:"";
        var deployment=engine.getRepositoryService().createDeployment().addString(key+".bpmn20.xml","""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="synthetic">
                <process id="%s" isExecutable="true">%s<startEvent id="start"/><userTask id="work"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="work"/></process></definitions>
                """.formatted(key,metadata)).deploy();
        return engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
    }
}
