package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.workflow.config.FlowableConfig;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentDeploymentBoundaryDatabaseTest extends AssignmentPostgresFixture {
    @BeforeAll void configure() { openEngine(); }
    @Override void configureEngine(SpringProcessEngineConfiguration configuration) {
        var config=new FlowableConfig();
        for (var field:FlowableConfig.class.getDeclaredFields()) ReflectionTestUtils.setField(config,field.getName(),mock(field.getType()));
        var lifecycle=(AssignmentCreationLifecycle)ReflectionTestUtils.getField(config,"assignmentCreationLifecycle");
        when(lifecycle.listener(anyBoolean())).thenReturn(mock(org.flowable.common.engine.api.delegate.event.FlowableEventListener.class));
        config.customProcessEngineConfigurer().configure(configuration);
    }
    @Test void rawDeploymentCannotForgeTheServerCompiledContract() {
        long before=engine.getRepositoryService().createDeploymentQuery().count();
        rejected(()->deploy("forged",true));
        assertEquals(before,engine.getRepositoryService().createDeploymentQuery().count());
    }
    @Test void ordinaryLegacyDeploymentKeepsItsOriginalPath() {
        var result=deploy("legacy",false);
        assertNotNull(engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(result.getId()).singleResult());
    }
    @Test void portableProtectedPoliciesCannotBypassCompilationEither() {
        String portable=xml("portable",false).replace("<userTask id=\"work\"/>","""
                <userTask id="work"><extensionElements><flowable:properties><flowable:property name="assignmentExclusion"
                value="{&quot;schemaVersion&quot;:1,&quot;excludeInitiator&quot;:true,&quot;excludePreviousHandlers&quot;:false,
                &quot;excludeSelectedNodeHandlers&quot;:false,&quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}"/>
                </flowable:properties></extensionElements></userTask>
                """);
        rejected(()->engine.getRepositoryService().createDeployment().addString("portable.bpmn20.xml",portable).deploy());
    }
    @Test void exactCompiledResourceSurvivesARealEngineRestartWithoutPublishingAnotherDefinition() {
        String compiled=xml("restart_probe",true), resource="restart_probe.bpmn20.xml";
        var deployed=AssignmentCompiledDeployment.deploy(resource,compiled,()->engine.getRepositoryService().createDeployment().addString(resource,compiled).deploy());
        String definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployed.getId()).singleResult().getId();
        engine.close(); engine=null; openEngine();
        var instance=engine.getRuntimeService().startProcessInstanceById(definition);
        assertNotNull(engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult());
        assertEquals(1,engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionId(definition).count());
    }
    @Test void aReplacedXmlOrAnExtraResourceCannotBorrowTheCompilerScopeAndFailureDoesNotLeakIt() {
        String compiled=xml("authorized",true), resource="authorized.bpmn20.xml";
        long before=engine.getRepositoryService().createDeploymentQuery().count();
        rejected(()->AssignmentCompiledDeployment.deploy(resource,compiled,()->engine.getRepositoryService().createDeployment()
                .addString(resource,xml("replacement",true)).deploy()));
        rejected(()->AssignmentCompiledDeployment.deploy(resource,compiled,()->engine.getRepositoryService().createDeployment()
                .addString(resource,compiled).addString("extra.bpmn20.xml",xml("extra",true)).deploy()));
        rejected(()->deploy("authorized",true));
        assertEquals(before,engine.getRepositoryService().createDeploymentQuery().count());
    }
    private void rejected(org.junit.jupiter.api.function.Executable action) {
        var failure=assertThrows(RuntimeException.class,action);
        Throwable cause=failure;
        while (!(cause instanceof AssignmentFailure) && cause.getCause()!=null) cause=cause.getCause();
        assertInstanceOf(AssignmentFailure.class,cause,"Must fail at the assignment boundary, not an unrelated Flowable error");
    }
    private org.flowable.engine.repository.Deployment deploy(String key,boolean compiled) {
        return engine.getRepositoryService().createDeployment().addString(key+".bpmn20.xml",xml(key,compiled)).deploy();
    }
    private String xml(String key,boolean compiled) {
        String metadata=compiled?"""
                <extensionElements><flowable:properties><flowable:property name="assignmentRuntimeContract" value="1"/>
                <flowable:property name="assignmentRuntimeTracking" value="true"/><flowable:property name="assignmentRuntimeUsers" value="{}"/>
                </flowable:properties></extensionElements>
                """:"";
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="synthetic">
                <process id="%s" isExecutable="true">%s<startEvent id="start"/><userTask id="work"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="work"/></process></definitions>
                """.formatted(key,metadata);
    }
}
