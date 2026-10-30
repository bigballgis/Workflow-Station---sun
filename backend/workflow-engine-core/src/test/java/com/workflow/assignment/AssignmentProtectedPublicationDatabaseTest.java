package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentFailure;
import com.workflow.component.ProcessDeploymentManager;
import com.workflow.dto.request.ProcessDefinitionRequest;
import com.workflow.exception.WorkflowValidationException;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Public deployment component, actual compiler/parse guard and PostgreSQL; readiness=1 is fixture-owned only. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentProtectedPublicationDatabaseTest extends AssignmentPostgresFixture {
    @BeforeAll void configure() { openEngine(); }
    @Override void configureEngine(SpringProcessEngineConfiguration configuration) {
        configuration.setPreBpmnParseHandlers(List.of(new AssignmentDeploymentParseGuard(new AssignmentDefinitionRetention(jdbc))));
    }
    @Test void aReadyProtectedPublicationReachesTheCompilerBeforeFlowableParseAndStoresItsFrozenContract() {
        var request=request(true); long before=engine.getRepositoryService().createDeploymentQuery().count();
        var result=publisher(true).deployProcess(request);
        assertTrue(result.isSuccess(),result.getMessage());
        assertEquals(before+1,engine.getRepositoryService().createDeploymentQuery().count());
        var metadata=AssignmentCompiledMetadata.read(engine.getRepositoryService().getBpmnModel(result.getEngineProcessDefinitionId()));
        assertFalse(metadata.legacy()); assertTrue(metadata.tracked());
        assertTrue(metadata.policies().get("review").enabled());
    }
    @Test void anUnreadyRuntimeRejectsBeforeTheLegacyTemporaryValidationPath() {
        long before=engine.getRepositoryService().createDeploymentQuery().count();
        var failure=assertThrows(AssignmentFailure.class,()->publisher(false).deployProcess(request(true)));
        assertEquals("ASSIGNMENT_ENGINE_UNSUPPORTED",failure.getDetails().get("reason"));
        assertEquals(before,engine.getRepositoryService().createDeploymentQuery().count());
    }
    @Test void ordinaryLegacyPublicationKeepsItsIdentityAndLegacyContract() {
        var request=request(false); var result=publisher(false).deployProcess(request);
        assertTrue(result.isSuccess(),result.getMessage());
        var definition=engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionId(result.getEngineProcessDefinitionId()).singleResult();
        assertEquals(request.getKey(),definition.getKey());
        assertTrue(AssignmentCompiledMetadata.read(engine.getRepositoryService().getBpmnModel(definition.getId())).legacy());
    }
    @Test void actualFlowableValidationStillRejectsAnInvalidServiceTaskWithoutLeavingADeployment() {
        var request=request(false);
        request.setBpmnXml(request.getBpmnXml().replace("<userTask id=\"review\">","<serviceTask id=\"review\">")
                .replace("</userTask>","</serviceTask>"));
        long before=engine.getRepositoryService().createDeploymentQuery().count();
        assertThrows(WorkflowValidationException.class,()->publisher(false).deployProcess(request));
        assertEquals(before,engine.getRepositoryService().createDeploymentQuery().count());
    }
    private ProcessDeploymentManager publisher(boolean ready) {
        var component=new ProcessDeploymentManager();
        ReflectionTestUtils.setField(component,"repositoryService",engine.getRepositoryService());
        var compiler=new com.workflow.assignment.impl.AssignmentDeploymentCompilerImpl(engine.getRepositoryService(),mock(AssignmentIdentityClient.class),
                new ObjectMapper(),mock(com.workflow.client.AdminCenterClient.class));
        var support=ready?new AssignmentRuntimeSupport() {
            @Override public Capability capability() { return new Capability(1,true); }
        }:new AssignmentUnavailableRuntime();
        ReflectionTestUtils.setField(component,"assignmentDeploymentPreparation",new AssignmentDeploymentPreparation(compiler,support));
        return component;
    }
    private ProcessDefinitionRequest request(boolean protectedPolicy) {
        String key="publish_"+UUID.randomUUID().toString().replace("-","");
        String policy=protectedPolicy?"""
                <extensionElements><flowable:properties><flowable:property name="assignmentExclusion"
                value="{&quot;schemaVersion&quot;:1,&quot;excludeInitiator&quot;:true,&quot;excludePreviousHandlers&quot;:false,
                &quot;excludeSelectedNodeHandlers&quot;:false,&quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}"/>
                </flowable:properties></extensionElements>
                """:"";
        var request=new ProcessDefinitionRequest(); request.setKey(key); request.setName("Synthetic publication");
        request.setBpmnXml("""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="urn:synthetic">
                <process id="%s" isExecutable="true"><startEvent id="start"/><userTask id="review">%s</userTask><endEvent id="end"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="review"/><sequenceFlow id="b" sourceRef="review" targetRef="end"/>
                </process></definitions>
                """.formatted(key,policy));
        return request;
    }
}
