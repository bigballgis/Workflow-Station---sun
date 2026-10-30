package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.workflow.assignment.impl.AssignmentCaseRegistryImpl;
import com.workflow.component.*;
import com.workflow.dto.request.ProcessInstanceControlRequest;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring transaction proxy, Flowable cancellation and PostgreSQL case fence; not a Portal HTTP acceptance test. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentProcessControlDatabaseTest extends AssignmentPostgresFixture {
    final MultiInstanceCanceller canceller=mock(MultiInstanceCanceller.class);
    final ProcessCallCascade cascade=mock(ProcessCallCascade.class);
    AssignmentCaseRegistry registry;
    ProcessEngineComponent control;
    String definition;
    @BeforeAll void configure() {
        openEngine(); var json=new ObjectMapper(); var cases=new AssignmentCaseRepository(jdbc,json);
        var definitions=new AssignmentDefinitionPolicy(engine.getRepositoryService());
        registry=new AssignmentCaseRegistryImpl(jdbc,cases,engine.getRuntimeService(),definitions,mock(CatalogSnapshotReader.class));
        var states=new AssignmentTaskStateRepository(jdbc,json);
        var mutation=new AssignmentMutationLifecycle(states,new AssignmentResponsibilityRepository(jdbc,json),registry);
        engine.getRuntimeService().addEventListener(mutation,FlowableEngineEventType.PROCESS_CANCELLED);
        var target=new ProcessEngineComponent();
        ReflectionTestUtils.setField(target,"runtimeService",engine.getRuntimeService());
        ReflectionTestUtils.setField(target,"multiInstanceCanceller",canceller);
        ReflectionTestUtils.setField(target,"processCallCascade",cascade);
        ReflectionTestUtils.setField(target,"assignmentProcessWrites",new AssignmentProcessWriteBoundary(cases,
                new AssignmentLegacyTaskCheck(engine.getTaskService(),engine.getRuntimeService(),cases,definitions)));
        var proxy=new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transaction.getTransactionManager(),new AnnotationTransactionAttributeSource()));
        control=(ProcessEngineComponent)proxy.getProxy();
        var deployment=engine.getRepositoryService().createDeployment().addString("control.bpmn20.xml","""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="synthetic">
                <process id="control_probe" isExecutable="true"><startEvent id="start"/><userTask id="work"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="work"/></process></definitions>
                """).deploy();
        definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
    }
    @BeforeEach void clearCollaborators() { reset(canceller,cascade); }
    @Test void pendingCaseIsRejectedBeforeAnyChildOrExecutionMutation() {
        var root=start();
        jdbc.update("UPDATE we_assignment_cases SET accepted_operation_seq=1 WHERE case_id=?",root.caseId());
        var failure=assertThrows(AssignmentFailure.class,()->control.controlProcessInstance(request(root.instance())));
        assertEquals("ASSIGNMENT_SYNC_PENDING",failure.getDetails().get("reason"));
        verifyNoInteractions(canceller,cascade);
        assertNotNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(root.instance()).singleResult());
        assertEquals("ACTIVE",state(root.instance()));
    }
    @Test void healthyCaseIsFencedBeforeTheFirstCancellationAndCommitsItsLifecycleState() {
        var root=start();
        doAnswer(call->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE pid=pg_backend_pid() AND locktype='advisory' AND granted)",Boolean.class));
            return null;
        }).when(canceller).cancelMultiInstanceTasks(root.instance());
        assertTrue(control.controlProcessInstance(request(root.instance())).isSuccess());
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(root.instance()).singleResult());
        assertEquals("CANCELLED",state(root.instance()));
    }
    @Test void anotherCasePendingSyncDoesNotPreventIndependentCancellation() {
        var blocked=start(); var independent=start();
        jdbc.update("UPDATE we_assignment_cases SET accepted_operation_seq=1 WHERE case_id=?",blocked.caseId());
        assertTrue(control.controlProcessInstance(request(independent.instance())).isSuccess());
        assertEquals("CANCELLED",state(independent.instance())); assertEquals("ACTIVE",state(blocked.instance()));
    }
    @Test void aLegacyRequestKeepsItsOriginalControlPath() {
        var legacy=engine.getRuntimeService().startProcessInstanceById(definition);
        assertTrue(control.controlProcessInstance(request(legacy.getId())).isSuccess());
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(legacy.getId()).singleResult());
    }
    private AssignmentCaseRegistry.Registration start() {
        return transaction.execute(status->{
            var root=registry.newRoot(definition,"original","BU");
            engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(definition).predefineProcessInstanceId(root.instance()).start();
            return root;
        });
    }
    private String state(String instance) { return jdbc.queryForObject("SELECT state FROM we_assignment_instances WHERE process_instance_id=?",String.class,instance); }
    private ProcessInstanceControlRequest request(String instance) {
        var request=new ProcessInstanceControlRequest(); request.setProcessInstanceId(instance); request.setAction("terminate");
        request.setUserId("synthetic actor"); request.setReason("synthetic cancellation"); return request;
    }
}
