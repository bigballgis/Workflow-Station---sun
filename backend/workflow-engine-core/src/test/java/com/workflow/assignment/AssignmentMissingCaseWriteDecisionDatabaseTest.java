package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.*;
import com.workflow.assignment.impl.AssignmentWriteDecisionComponentImpl;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentMissingCaseWriteDecisionDatabaseTest extends AssignmentPostgresFixture {
    @Test void missingRegistrationCannotTurnATrackedDefinitionIntoALegacyPortalPermissionPacket() {
        openEngine();
        var deployment=engine.getRepositoryService().createDeployment().addString("tracked.bpmn20.xml","""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="synthetic">
                <process id="missing_case_probe" isExecutable="true"><extensionElements><flowable:properties>
                <flowable:property name="assignmentRuntimeContract" value="1"/><flowable:property name="assignmentRuntimeTracking" value="true"/>
                <flowable:property name="assignmentRuntimeUsers" value="{}"/></flowable:properties></extensionElements>
                <startEvent id="start"/><userTask id="work"/><sequenceFlow id="a" sourceRef="start" targetRef="work"/>
                </process></definitions>
                """).deploy();
        var definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
        var instance=engine.getRuntimeService().startProcessInstanceById(definition.getId());
        var task=engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        var json=new ObjectMapper();
        try (var context=new AnnotationConfigApplicationContext()) {
            context.registerBean(TaskService.class,engine::getTaskService);
            context.registerBean(AssignmentCaseRegistry.class,()->mock(AssignmentCaseRegistry.class));
            context.registerBean(AssignmentWriteGuard.class,()->mock(AssignmentWriteGuard.class));
            context.registerBean(PlatformTransactionManager.class,transaction::getTransactionManager);
            context.registerBean(AssignmentLegacyTaskCheck.class,()->new AssignmentLegacyTaskCheck(engine.getTaskService(),engine.getRuntimeService(),
                    new AssignmentCaseRepository(jdbc,json),new AssignmentDefinitionPolicy(engine.getRepositoryService())));
            context.register(AssignmentWriteDecisionComponentImpl.class); context.refresh();
            var request=new AssignmentWriteDecision.Request(task.getId(),"actor",null,"SAVE","a".repeat(64),instance.getId());
            assertThrows(AssignmentFailure.class,()->context.getBean(AssignmentWriteDecisionComponent.class).prepare(request));
        }
    }
}
