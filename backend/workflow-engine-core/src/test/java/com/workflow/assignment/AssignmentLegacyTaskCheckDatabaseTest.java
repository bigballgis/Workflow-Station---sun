package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentFailure;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** A callee's legacy definition alone cannot authorize mutation of a protected parent case. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentLegacyTaskCheckDatabaseTest extends AssignmentPostgresFixture {
    AssignmentCaseRepository cases;
    AssignmentLegacyTaskCheck check;
    AssignmentWriteBoundary boundary;
    @BeforeAll void configure() {
        openEngine();
        cases=new AssignmentCaseRepository(jdbc,new ObjectMapper());
        check=new AssignmentLegacyTaskCheck(engine.getTaskService(),engine.getRuntimeService(),cases,
                new AssignmentDefinitionPolicy(engine.getRepositoryService()));
        boundary=new AssignmentWriteBoundary(new AssignmentTaskStateRepository(jdbc,new ObjectMapper()));
        ReflectionTestUtils.setField(boundary,"legacyCheck",check);
    }
    @Test void missingChildRegistrationStillChecksTheRegisteredCaller() {
        String child=deploy("<userTask id=\"work\"/>",false);
        String parent=deploy("<callActivity id=\"work\" calledElement=\""+child+"\" flowable:calledElementType=\"id\"/>",false);
        var root=engine.getRuntimeService().startProcessInstanceById(parent);
        var task=engine.getTaskService().createTaskQuery().processDefinitionId(child).singleResult();
        var caseId=UUID.randomUUID();
        cases.createCase(caseId,root.getId(),"original","BU");
        cases.insert(new AssignmentCaseRegistry.Registration(root.getId(),caseId,UUID.randomUUID(),parent,List.of()),null,null,"synthetic-root");
        assertTrue(cases.find(task.getProcessInstanceId()).isEmpty());
        assertThrows(AssignmentFailure.class,()->check.requireLegacy(task));
        assertThrows(AssignmentFailure.class,()->boundary.requireLegacyCompletion(task.getId()));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
    }
    @Test void unregisteredTrackedDefinitionIsRejectedWithoutInventingCaseContext() {
        String definition=deploy("<userTask id=\"work\"/>",true);
        var root=engine.getRuntimeService().startProcessInstanceById(definition);
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.getId()).singleResult();
        assertTrue(cases.find(root.getId()).isEmpty());
        assertThrows(AssignmentFailure.class,()->boundary.requireLegacyCompletion(task.getId()));
        assertTrue(cases.find(root.getId()).isEmpty());
    }
    @Test void genuinelyLegacyRootAndNestedCallsRemainUsable() {
        String child=deploy("<userTask id=\"work\"/>",false);
        String parent=deploy("<callActivity id=\"work\" calledElement=\""+child+"\" flowable:calledElementType=\"id\"/>",false);
        var root=engine.getRuntimeService().startProcessInstanceById(parent);
        var task=engine.getTaskService().createTaskQuery().processDefinitionId(child).singleResult();
        assertDoesNotThrow(()->check.requireLegacy(task));
        assertDoesNotThrow(()->boundary.requireLegacyCompletion(task.getId()));
        engine.getTaskService().complete(task.getId());
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(root.getId()).singleResult());
    }
    @Test void standaloneTaskIsPositivelyIdentifiedAsLegacy() {
        var task=engine.getTaskService().newTask();
        task.setName("Synthetic standalone task"); engine.getTaskService().saveTask(task);
        assertDoesNotThrow(()->check.requireLegacy(task));
        engine.getTaskService().deleteTask(task.getId(),true);
    }
    private String deploy(String activity,boolean tracked) {
        String key="ancestry_"+UUID.randomUUID().toString().replace("-","");
        String metadata=tracked?"""
                <extensionElements><flowable:properties><flowable:property name="assignmentRuntimeContract" value="1"/>
                <flowable:property name="assignmentRuntimeTracking" value="true"/>
                <flowable:property name="assignmentRuntimeUsers" value="{}"/></flowable:properties></extensionElements>
                """:"";
        String xml="""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="synthetic">
                <process id="%s" isExecutable="true">%s<startEvent id="start"/>%s<endEvent id="end"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="work"/><sequenceFlow id="b" sourceRef="work" targetRef="end"/>
                </process></definitions>
                """.formatted(key,metadata,activity);
        var deployment=engine.getRepositoryService().createDeployment().addString(key+".bpmn20.xml",xml).deploy();
        return engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
    }
}
