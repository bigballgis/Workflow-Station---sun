package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.workflow.assignment.impl.AssignmentCaseRegistryImpl;
import com.workflow.config.FlowableConfig;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercise the actual FlowableConfig start-event registration, before any service task can have an external effect. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentInstanceStartDatabaseTest extends AssignmentPostgresFixture {
    AssignmentCaseRegistry registry;
    AssignmentCaseRepository cases;
    final List<Boolean> observed=new ArrayList<>();
    String child,parent;
    @Override Map<Object,Object> engineBeans() {
        var beans=new LinkedHashMap<>(super.engineBeans());
        beans.put("scopeProbe",(JavaDelegate) execution->observed.add(cases.find(execution.getProcessInstanceId()).isPresent()));
        return beans;
    }
    @Override void configureEngine(SpringProcessEngineConfiguration configuration) {
        var json=new ObjectMapper(); cases=new AssignmentCaseRepository(jdbc,json);
        var runtime=mock(org.flowable.engine.RuntimeService.class,invocation->{
            return invocation.getMethod().invoke(engine.getRuntimeService(),invocation.getArguments());
        });
        var repository=mock(org.flowable.engine.RepositoryService.class,invocation->{
            return invocation.getMethod().invoke(engine.getRepositoryService(),invocation.getArguments());
        });
        registry=new AssignmentCaseRegistryImpl(jdbc,cases,runtime,new AssignmentDefinitionPolicy(repository),mock(CatalogSnapshotReader.class));
        var mutation=new AssignmentMutationLifecycle(new AssignmentTaskStateRepository(jdbc,json),new AssignmentResponsibilityRepository(jdbc,json),registry);
        var config=new FlowableConfig();
        for (var field:FlowableConfig.class.getDeclaredFields()) ReflectionTestUtils.setField(config,field.getName(),mock(field.getType()));
        ReflectionTestUtils.setField(config,"assignmentMutationLifecycle",mutation);
        var lifecycle=(AssignmentCreationLifecycle)ReflectionTestUtils.getField(config,"assignmentCreationLifecycle");
        when(lifecycle.listener(anyBoolean())).thenReturn(mock(org.flowable.common.engine.api.delegate.event.FlowableEventListener.class));
        config.customProcessEngineConfigurer().configure(configuration);
    }
    @BeforeAll void definitions() {
        openEngine();
        child=deploy("early_child","<serviceTask id=\"work\" flowable:delegateExpression=\"${scopeProbe}\"/>");
        parent=deploy("early_parent","<callActivity id=\"work\" calledElement=\""+child+"\" flowable:calledElementType=\"id\"/>",child);
    }
    @BeforeEach void resetProbe() { observed.clear(); }
    @Test void aProtectedNativeRootIsRejectedBeforeItsFirstServiceTask() {
        assertThrows(RuntimeException.class,()->engine.getRuntimeService().startProcessInstanceById(child));
        assertTrue(observed.isEmpty(),"Reject before the first possible external effect");
    }
    @Test void aCalledInstanceHasItsCaseBeforeItsFirstServiceTaskAndCanEndWithoutAHumanTask() {
        var root=transaction.execute(status->{
            var registered=registry.newRoot(parent,"original","BU");
            engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(parent).predefineProcessInstanceId(registered.instance()).start();
            return registered;
        });
        assertEquals(List.of(true),observed);
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM we_assignment_instances WHERE case_id=?",Integer.class,root.caseId()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM we_assignment_instances WHERE case_id=? AND state='ACTIVE'",Integer.class,root.caseId()));
    }
    @Test void firstPartyRowCallsAlreadyHaveTheirMappedStableIdentityAtTheStartEvent() {
        String definition=deploy("early_row_parent","""
                <callActivity id="work" calledElement="%s" flowable:calledElementType="id"><extensionElements><flowable:properties>
                <flowable:property name="callRowsTable" value="renamed_rows"/></flowable:properties></extensionElements>
                <multiInstanceLoopCharacteristics isSequential="false"/></callActivity>
                """.formatted(child),child);
        String first=UUID.randomUUID().toString(),second=UUID.randomUUID().toString();
        var root=transaction.execute(status->{
            var registered=registry.newRoot(definition,"original","BU");
            engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(definition).predefineProcessInstanceId(registered.instance())
                    .variables(Map.of("__subTables__",Map.of("dw:renamed_rows",List.of(Map.of("platformRowUuid",first),Map.of("platformRowUuid",second))))).start();
            return registered;
        });
        assertEquals(List.of(true,true),observed);
        var rows=jdbc.queryForList("SELECT call_path->0->'row'->>'platformRowUuid' FROM we_assignment_instances WHERE case_id=? AND parent_instance_id IS NOT NULL",String.class,root.caseId());
        assertEquals(Set.of(first,second),new HashSet<>(rows));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM we_assignment_instances WHERE case_id=? AND state='ACTIVE'",Integer.class,root.caseId()));
    }
    private String deploy(String key,String work,String... dependencies) {
        String resource=key+".bpmn20.xml";
        String xml="""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="synthetic">
                <process id="%s" isExecutable="true"><extensionElements><flowable:properties>
                <flowable:property name="assignmentRuntimeContract" value="1"/><flowable:property name="assignmentRuntimeTracking" value="true"/>
                <flowable:property name="assignmentRuntimeUsers" value="{}"/>
                <flowable:property name="assignmentRuntimeDefinitions" value="%s"/></flowable:properties></extensionElements>
                <startEvent id="start"/>%s<endEvent id="end"/><sequenceFlow id="a" sourceRef="start" targetRef="work"/>
                <sequenceFlow id="b" sourceRef="work" targetRef="end"/></process></definitions>
                """.formatted(key,com.platform.common.util.JsonUtils.toJson(List.of(dependencies)).replace("\"","&quot;"),work);
        String compiled=com.workflow.util.CallActivityDataMappingCompiler.compile(xml);
        var deployment=AssignmentCompiledDeployment.deploy(resource,compiled,()->engine.getRepositoryService().createDeployment().addString(resource,compiled).deploy());
        return engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
    }
}
