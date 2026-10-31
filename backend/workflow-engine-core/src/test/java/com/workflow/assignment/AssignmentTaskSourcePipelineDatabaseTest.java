package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.*;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.messaging.support.NotificationDispatchHelper;
import com.workflow.assignment.impl.*;
import com.workflow.client.AdminCenterClient;
import com.workflow.component.BpmnActionParser;
import com.workflow.component.ExceptionHandlerComponent;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.listener.*;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.service.LastUserTaskAssigneeQuery;
import com.workflow.service.TaskAssigneeResolver;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.HistoryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real original listener between the security listeners, real source/qualification/case pipeline and PostgreSQL. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentTaskSourcePipelineDatabaseTest extends AssignmentPostgresFixture {
    final AdminCenterClient adminClient=mock(AdminCenterClient.class);
    final AssignmentIdentityClient identities=mock(AssignmentIdentityClient.class);
    final ExtendedTaskInfoRepository extended=mock(ExtendedTaskInfoRepository.class);
    final NotificationDispatchHelper notifications=mock(NotificationDispatchHelper.class);
    final AssignmentSourceSnapshotClient portal=mock(AssignmentSourceSnapshotClient.class);
    final Map<String,ExtendedTaskInfo> infos=new HashMap<>();
    AssignmentCaseRegistry registry;
    AssignmentTaskStateRepository states;
    AssignmentCaseEvaluator evaluator;
    TaskAssignmentListener original;
    @BeforeAll void configure() {
        openEngine(); var json=new ObjectMapper(); var definitions=new AssignmentDefinitionPolicy(engine.getRepositoryService());
        var cases=new AssignmentCaseRepository(jdbc,json); states=new AssignmentTaskStateRepository(jdbc,json);
        registry=new AssignmentCaseRegistryImpl(jdbc,cases,engine.getRuntimeService(),definitions,mock(CatalogSnapshotReader.class));
        var resolver=new TaskAssigneeResolver(adminClient); var parser=new BpmnActionParser();
        ReflectionTestUtils.setField(parser,"repositoryService",engine.getRepositoryService());
        original=listener(resolver,parser);
        var sources=new AssignmentSourceResolverImpl(resolver,original,new AssignmentSourceValues(engine.getRuntimeService(),definitions,states,portal),definitions);
        var qualification=new AssignmentQualificationComponentImpl(jdbc,identities,sources,definitions,adminClient);
        evaluator=new AssignmentCaseEvaluatorImpl(definitions,new AssignmentResponsibilityRepository(jdbc,json),states,qualification,jdbc);
        var coordinator=new AssignmentCreationCoordinatorImpl(engine.getTaskService(),evaluator,states,extended,notifications);
        ReflectionTestUtils.setField(coordinator,"definitions",definitions);
        var lifecycle=new AssignmentCreationLifecycle(registry,coordinator);
        engine.getRuntimeService().addEventListener(lifecycle.listener(true),FlowableEngineEventType.TASK_CREATED);
        engine.getRuntimeService().addEventListener(original,FlowableEngineEventType.TASK_CREATED);
        engine.getRuntimeService().addEventListener(lifecycle.listener(false),FlowableEngineEventType.TASK_CREATED);
        var mutation=new AssignmentMutationLifecycle(states,new AssignmentResponsibilityRepository(jdbc,json),registry);
        ReflectionTestUtils.setField(mutation,"legacyCheck",new AssignmentLegacyTaskCheck(engine.getTaskService(),engine.getRuntimeService(),cases,definitions));
        engine.getRuntimeService().addEventListener(mutation,FlowableEngineEventType.TASK_ASSIGNED);
    }
    private TaskAssignmentListener listener(TaskAssigneeResolver resolver,BpmnActionParser parser) {
        var listener=new TaskAssignmentListener();
        ReflectionTestUtils.setField(listener,"taskAssigneeResolver",resolver);
        ReflectionTestUtils.setField(listener,"lastUserTaskAssigneeQuery",new LastUserTaskAssigneeQuery(engine.getHistoryService()));
        ReflectionTestUtils.setField(listener,"assignmentStates",states);
        ReflectionTestUtils.setField(listener,"taskService",engine.getTaskService());
        ReflectionTestUtils.setField(listener,"runtimeService",engine.getRuntimeService());
        ReflectionTestUtils.setField(listener,"historyService",engine.getHistoryService());
        ReflectionTestUtils.setField(listener,"repositoryService",engine.getRepositoryService());
        ReflectionTestUtils.setField(listener,"bpmnActionParser",parser);
        ReflectionTestUtils.setField(listener,"extendedTaskInfoRepository",extended);
        ReflectionTestUtils.setField(listener,"notificationDispatchHelper",notifications);
        ReflectionTestUtils.setField(listener,"jdbcTemplate",jdbc);
        var writer=org.springframework.util.ReflectionUtils.findField(TaskAssignmentListener.class,"multiInstanceTaskWriter");
        assertNotNull(writer);
        ReflectionTestUtils.setField(listener,"multiInstanceTaskWriter",mock(writer.getType()));
        ReflectionTestUtils.setField(listener,"adminCenterClient",adminClient);
        ReflectionTestUtils.setField(listener,"exceptionHandlerComponent",mock(ExceptionHandlerComponent.class));
        return listener;
    }
    @BeforeEach void prepareDependencies() {
        reset(adminClient,identities,extended,notifications,portal); infos.clear();
        ReflectionTestUtils.setField(original,"lastUserTaskAssigneeQuery",new LastUserTaskAssigneeQuery(engine.getHistoryService()));
        when(adminClient.getUserInfo(anyString())).thenAnswer(call->Map.of("id",call.getArgument(0),"entityManagerId",call.getArgument(0)+"-manager"));
        when(adminClient.getBusinessUnitCodeById("forged-bu-id")).thenReturn("BU-FORGED");
        when(identities.resolve(anyList())).thenAnswer(call->{
            List<AssignmentIdentitySnapshot.Reference> references=call.getArgument(0); var users=new LinkedHashMap<String,AssignmentIdentitySnapshot.User>();
            for (var reference:references) users.put(reference.value(),new AssignmentIdentitySnapshot.User(reference.value(),reference.value(),true));
            return new AssignmentIdentitySnapshot(0,users,Set.of());
        });
        when(extended.findByTaskIdAndIsDeletedFalse(anyString())).thenAnswer(call->Optional.ofNullable(infos.get(call.getArgument(0))));
        when(extended.save(any())).thenAnswer(call->{ExtendedTaskInfo info=call.getArgument(0); infos.put(info.getTaskId(),info); return info;});
    }
    @Test void nativeInitiatorExpressionCannotTurnAMappedBusinessValueIntoTheSystemInitiator() {
        var root=start("INITIATOR","flowable:assignee=\"${initiator}\"","",true);
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        assertNull(task.getAssignee());
        var state=states.find(task.getId()).orElseThrow();
        assertEquals(AssignmentSourceSpec.Kind.RESOLVER,state.sourceSpec().kind());
        assertEquals("BLOCKED_NO_ELIGIBLE_USER",state.state());
        verify(notifications,never()).publishToUserAfterCommit(eq("forged"),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void untypedNativeInitiatorExpressionUsesTheCanonicalCaseInitiator() {
        var root=start("","flowable:assignee=\"${initiator}\"","",true);
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        assertNull(task.getAssignee());
        assertEquals(AssignmentSourceSpec.Kind.RESOLVER,states.find(task.getId()).orElseThrow().sourceSpec().kind());
    }
    @Test void untypedNativeVariableExpressionRetainsItsDynamicSource() {
        var root=start("","flowable:assignee=\"${renamed_reviewer}\"","",true);
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        var source=states.find(task.getId()).orElseThrow().sourceSpec();
        assertEquals(AssignmentSourceSpec.Kind.VARIABLE,source.kind());
        assertEquals("renamed_reviewer",source.variable());
    }
    @Test void preassignedVariableIsRequalifiedFromItsConfiguredCurrentSource() {
        var root=start("ASSIGNEE_FROM_VARIABLE","flowable:assignee=\"${renamed_reviewer}\"",property("assigneeVariable","renamed_reviewer"),true);
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        var state=states.find(task.getId()).orElseThrow();
        assertEquals(AssignmentSourceSpec.Kind.VARIABLE,state.sourceSpec().kind());
        when(portal.read(any())).thenAnswer(call->{
            AssignmentSourceSnapshot.Request request=call.getArgument(0);
            return new AssignmentSourceSnapshot(1,root.caseId(),root.instance(),states.caseContext(root.caseId()).revision(),states.portalRevision(root.caseId()),
                    AssignmentSourceSnapshot.State.READY,new ObjectMapper().valueToTree(Map.of("renamed_reviewer","updated-reviewer")));
        });
        var decision=evaluator.prepare(task,state.sourceSpec(),List.of());
        assertEquals(List.of("updated-reviewer"),decision.qualification().primary());
        assertFalse(decision.qualification().primary().contains("original-reviewer"));
    }
    @Test void lastHandlerAnchorWithoutAnyPreviousTaskUsesTheRealRootInitiator() {
        var root=start("ENTITY_MANAGER","",property("assigneeAnchor","LAST_TASK_ASSIGNEE"),false);
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        assertEquals("original-manager",task.getAssignee());
        assertEquals("original",states.find(task.getId()).orElseThrow().sourceSpec().anchor());
    }
    @Test void nativePreassignmentCannotOverrideTheConfiguredOrganizationalSource() {
        var root=start("ENTITY_MANAGER","flowable:assignee=\"${initiator}\"","",false);
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        assertEquals("original-manager",task.getAssignee());
        assertEquals(AssignmentSourceSpec.Kind.RESOLVER,states.find(task.getId()).orElseThrow().sourceSpec().kind());
    }
    @Test void aBusinessVariableNamedAssigneeTypeCannotInventAFrozenAssignmentSource() {
        var root=start("","","",true,Map.of("assigneeType","ENTITY_MANAGER"));
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        assertNull(task.getAssignee());
        assertEquals(AssignmentSourceSpec.Kind.FIXED,states.find(task.getId()).orElseThrow().sourceSpec().kind());
        assertEquals("BLOCKED_NO_ELIGIBLE_USER",states.find(task.getId()).orElseThrow().state());
    }
    @Test void aLastHandlerHistoryOutageCannotBecomeAnEmptyOrFallbackAssignment() {
        var unavailable=mock(HistoryService.class);
        when(unavailable.createHistoricTaskInstanceQuery()).thenThrow(new IllegalStateException("synthetic history outage"));
        ReflectionTestUtils.setField(original,"lastUserTaskAssigneeQuery",new LastUserTaskAssigneeQuery(unavailable));
        long cases=jdbc.queryForObject("SELECT count(*) FROM we_assignment_cases",Long.class);
        assertThrows(RuntimeException.class,()->start("ENTITY_MANAGER","",property("assigneeAnchor","LAST_TASK_ASSIGNEE"),false));
        assertEquals(cases,jdbc.queryForObject("SELECT count(*) FROM we_assignment_cases",Long.class));
        assertFalse(AssignmentCreationBuffer.active());
    }
    private AssignmentCaseRegistry.Registration start(String type,String nativeAssignment,String properties,boolean excludeInitiator) {
        return start(type,nativeAssignment,properties,excludeInitiator,Map.of());
    }
    private AssignmentCaseRegistry.Registration start(String type,String nativeAssignment,String properties,boolean excludeInitiator,Map<String,Object> additional) {
        String key="source_pipeline_"+UUID.randomUUID().toString().replace("-","");
        String xml="""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="synthetic">
                <process id="%s" isExecutable="true"><extensionElements><flowable:properties>
                <flowable:property name="assignmentRuntimeContract" value="1"/><flowable:property name="assignmentRuntimeTracking" value="true"/>
                <flowable:property name="assignmentRuntimeUsers" value="{}"/></flowable:properties></extensionElements>
                <startEvent id="start"/><userTask id="work" %s><extensionElements><flowable:properties>%s%s
                <flowable:property name="assignmentExclusion" value="{&quot;schemaVersion&quot;:1,&quot;excludeInitiator&quot;:%s,
                &quot;excludePreviousHandlers&quot;:%s,&quot;excludeSelectedNodeHandlers&quot;:false,&quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}"/>
                </flowable:properties></extensionElements></userTask><sequenceFlow id="a" sourceRef="start" targetRef="work"/></process></definitions>
                """.formatted(key,nativeAssignment,property("assigneeType",type),properties,excludeInitiator,!excludeInitiator);
        // Match the first-party Designer namespace, rather than proving only Flowable's own extension parser.
        xml=xml.replace("xmlns:flowable=\"http://flowable.org/bpmn\"", "xmlns:flowable=\"http://flowable.org/bpmn\" xmlns:custom=\"http://workflow.platform/schema/custom\"")
                .replace("flowable:properties","custom:properties").replace("flowable:property","custom:property");
        var deployment=engine.getRepositoryService().createDeployment().addString(key+".bpmn20.xml",xml).deploy();
        String definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
        return transaction.execute(status->{
            var root=registry.newRoot(definition,"original","BU-ORIGINAL","original-bu-id");
            var variables=new HashMap<String,Object>(Map.of("initiator","forged","activeBusinessUnitId","forged-bu-id","renamed_reviewer","original-reviewer"));
            variables.putAll(additional);
            engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(definition).predefineProcessInstanceId(root.instance())
                    .variables(variables).start();
            return root;
        });
    }
    private String property(String name,String value) {
        return value.isEmpty()?"":"<flowable:property name=\""+name+"\" value=\""+value+"\"/>";
    }
}
