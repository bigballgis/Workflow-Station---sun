package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.*;
import com.platform.common.assignment.AssignmentTaskReadSnapshot.*;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.messaging.support.NotificationDispatchHelper;
import com.workflow.assignment.impl.*;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentTaskReadSnapshotDatabaseTest extends AssignmentPostgresFixture {
    final AssignmentQualificationComponent qualification=mock(AssignmentQualificationComponent.class);
    final AssignmentIdentityClient identities=mock(AssignmentIdentityClient.class);
    final ExtendedTaskInfoRepository extended=mock(ExtendedTaskInfoRepository.class);
    final Map<String,ExtendedTaskInfo> infos=new HashMap<>();
    AssignmentCaseRegistry registry;
    AssignmentTaskReadComponent reader;
    AssignmentTaskReadContextsDatabaseTest.CountingJdbc reads;
    String protectedDefinition,legacyDefinition,groupDefinition;
    @BeforeAll void prepare() {
        openEngine(); var json=new ObjectMapper(); var definitions=new AssignmentDefinitionPolicy(engine.getRepositoryService());
        var cases=new AssignmentCaseRepository(jdbc,json); var states=new AssignmentTaskStateRepository(jdbc,json);
        registry=new AssignmentCaseRegistryImpl(jdbc,cases,engine.getRuntimeService(),definitions,mock(CatalogSnapshotReader.class));
        var evaluator=new AssignmentCaseEvaluatorImpl(definitions,new AssignmentResponsibilityRepository(jdbc,json),states,qualification,jdbc);
        var coordinator=new AssignmentCreationCoordinatorImpl(engine.getTaskService(),evaluator,states,extended,mock(NotificationDispatchHelper.class));
        ReflectionTestUtils.setField(coordinator,"definitions",definitions);
        var lifecycle=new AssignmentCreationLifecycle(registry,coordinator);
        engine.getRuntimeService().addEventListener(lifecycle.listener(true),FlowableEngineEventType.TASK_CREATED);
        engine.getRuntimeService().addEventListener(lifecycle.listener(false),FlowableEngineEventType.TASK_CREATED);
        reads=new AssignmentTaskReadContextsDatabaseTest.CountingJdbc(source);
        reader=new AssignmentTaskReadComponent(new AssignmentTaskReadContexts(reads,engine.getManagementService()),new AssignmentReadContextClassifier(definitions),
                new AssignmentTaskReadProjectionRepository(reads,engine.getManagementService()),identities,definitions);
        protectedDefinition=deploy("claim_read_protected",true,false); legacyDefinition=deploy("claim_read_legacy",false,false);
        groupDefinition=deploy("claim_read_group",true,true);
    }
    @BeforeEach void resetDependencies() {
        reset(qualification,identities,extended); infos.clear();
        jdbc.update("UPDATE ac_assignment_authority_fence SET revision=0 WHERE scope='platform'");
        when(qualification.prepare(any())).thenAnswer(call->{
            AssignmentQualificationComponent.Request request=call.getArgument(0);
            var users=request.policy().enabled()?List.of("actor"):List.<String>of();
            return new AssignmentQualificationComponent.Prepared(0,Instant.now().plusSeconds(60),users,List.of(),true,actor(0,true).users());
        });
        when(identities.resolve(anyList())).thenAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return actor(0,true);
        });
        when(extended.findByTaskIdAndIsDeletedFalse(anyString())).thenAnswer(call->Optional.ofNullable(infos.get(call.getArgument(0))));
        when(extended.save(any())).thenAnswer(call->{ExtendedTaskInfo info=call.getArgument(0); infos.put(info.getTaskId(),info); return info;});
    }
    @Test void aHundredProtectedTasksUseTwoBatchStatementsAndOneActorSnapshot() {
        var ids=new ArrayList<String>(); for (int i=0;i<100;i++) ids.add(start(protectedDefinition));
        reads.statements=0;
        var snapshot=reader.read(new Request(ids,"actor"));
        assertEquals(100,snapshot.tasks().size());
        assertTrue(snapshot.tasks().stream().allMatch(task->task.state()==State.CURRENT && task.claim().exclusionAllowsClaim()));
        assertEquals(2,reads.statements); verify(identities,times(1)).resolve(anyList());
    }
    @Test void legacyTasksDoNotIntroducePersonnelHttpLookupsOrChangeExistingPermissions() {
        var process=engine.getRuntimeService().startProcessInstanceById(legacyDefinition);
        String task=engine.getTaskService().createTaskQuery().processInstanceId(process.getId()).singleResult().getId();
        var view=read(task); assertEquals(State.LEGACY,view.state()); assertEquals(Boolean.FALSE,view.tracked());
        assertTrue(view.claim().exclusionAllowsClaim()); verifyNoInteractions(identities);
    }
    @Test void anExpiredProjectionNeverAdvertisesOldLinksAsClaimable() {
        String task=start(protectedDefinition); jdbc.update("UPDATE we_assignment_task_states SET next_evaluation_at=clock_timestamp()-interval '1 second' WHERE task_id=?",task);
        assertDenied(task,State.RECONCILING);
    }
    @Test void aCaseChangedDuringTheActorHttpReadIsDetectedByTheFinalStatement() {
        String task=start(protectedDefinition);
        when(identities.resolve(anyList())).thenAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.update("UPDATE we_assignment_cases SET revision=revision+1 WHERE case_id=(SELECT case_id FROM we_assignment_task_states WHERE task_id=?)",task);
            return actor(0,true);
        });
        assertDenied(task,State.RECONCILING);
    }
    @Test void aNewAuthorityEpochCannotBeCombinedWithAnOlderActorOrCandidateSnapshot() {
        String task=start(protectedDefinition); jdbc.update("UPDATE ac_assignment_authority_fence SET revision=1 WHERE scope='platform'");
        assertDenied(task,State.RECONCILING);
        when(identities.resolve(anyList())).thenReturn(actor(1,true)); assertDenied(task,State.RECONCILING);
    }
    @Test void inactiveActorsAndPendingSynchronizationAreReadOnly() {
        String task=start(protectedDefinition); jdbc.update("UPDATE ac_assignment_authority_fence SET revision=1 WHERE scope='platform'");
        when(identities.resolve(anyList())).thenReturn(actor(1,false)); assertDenied(task,State.ACTOR_INACTIVE);
        jdbc.update("UPDATE we_assignment_cases SET accepted_operation_seq=1 WHERE case_id=(SELECT case_id FROM we_assignment_task_states WHERE task_id=?)",task);
        assertDenied(task,State.SYNC_PENDING);
    }
    @Test void aMissingProjectionIsContextUnavailableInsteadOfLegacy() {
        String task=start(protectedDefinition); jdbc.update("DELETE FROM we_assignment_task_states WHERE task_id=?",task);
        assertDenied(task,State.CONTEXT_UNAVAILABLE);
    }
    @Test void anUnavailableActorServiceDoesNotRestoreTheOldCandidates() {
        String task=start(protectedDefinition); when(identities.resolve(anyList())).thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE));
        assertDenied(task,State.DEPENDENCY_UNAVAILABLE);
        assertEquals(1,engine.getTaskService().getIdentityLinksForTask(task).stream().filter(link->"actor".equals(link.getUserId())).count());
    }
    @Test void heldAndClosedTasksCannotBeAdvertisedAsClaimable() {
        String task=start(protectedDefinition); engine.getTaskService().claim(task,"actor");
        var held=read(task); assertEquals(State.CURRENT,held.state()); assertFalse(held.claim().exclusionAllowsClaim());
        assertEquals("actor",held.claim().holding().userId());
        engine.getTaskService().complete(task); assertDenied(task,State.NOT_ACTIVE);
    }
    @Test void holderChangesDuringActorLookupAreObservedByTheFinalStatement() {
        String task=start(protectedDefinition);
        when(identities.resolve(anyList())).thenAnswer(call->{
            engine.getTaskService().claim(task,"someone-else");
            return actor(0,true);
        });
        var view=read(task);
        assertEquals(State.CURRENT,view.state()); assertFalse(view.claim().exclusionAllowsClaim());
        assertEquals("someone-else",view.claim().holding().userId());
    }
    @Test void aRuleDisabledNativeGroupKeepsTheOriginalGroupPermissionCheck() {
        String task=start(groupDefinition); var view=read(task);
        assertEquals(State.CURRENT,view.state()); assertTrue(view.claim().exclusionAllowsClaim(),"Only the extra veto passes; the original group check is still required");
        assertTrue(engine.getTaskService().getIdentityLinksForTask(task).stream().anyMatch(link->"native-group".equals(link.getGroupId())));
    }
    @Test void thisReaderCannotBeNestedInsideABusinessTransaction() {
        assertThrows(AssignmentFailure.class,()->transaction.execute(status->reader.read(new Request(List.of("task"),"actor"))));
        verifyNoInteractions(identities);
    }
    private AssignmentIdentitySnapshot actor(long epoch,boolean active) {
        return new AssignmentIdentitySnapshot(epoch,Map.of("actor",new AssignmentIdentitySnapshot.User("actor","synthetic",active)),Set.of());
    }
    private Entry read(String task) { return reader.read(new Request(List.of(task),"actor")).tasks().get(0); }
    private void assertDenied(String task,State state) { var view=read(task); assertEquals(state,view.state()); assertFalse(view.claim().exclusionAllowsClaim()); }
    private String start(String definition) {
        var root=transaction.execute(status->{
            var registered=registry.newRoot(definition,"original","BU");
            engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(definition).predefineProcessInstanceId(registered.instance()).start(); return registered;
        });
        return engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult().getId();
    }
    private String deploy(String key,boolean tracked,boolean group) {
        String metadata=tracked?"""
                <extensionElements><flowable:properties><flowable:property name="assignmentRuntimeContract" value="1"/>
                <flowable:property name="assignmentRuntimeTracking" value="true"/><flowable:property name="assignmentRuntimeUsers" value="{&quot;actor&quot;:&quot;actor&quot;}"/>
                </flowable:properties></extensionElements>
                """:"";
        String policy=tracked && !group?"""
                <extensionElements><flowable:properties><flowable:property name="assignmentExclusion" value="{&quot;schemaVersion&quot;:1,
                &quot;excludeInitiator&quot;:false,&quot;excludePreviousHandlers&quot;:true,&quot;excludeSelectedNodeHandlers&quot;:false,
                &quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}"/></flowable:properties></extensionElements>
                """:"";
        var deployment=engine.getRepositoryService().createDeployment().addString(key+".bpmn20.xml","""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="synthetic">
                <process id="%s" isExecutable="true">%s<startEvent id="start"/><userTask id="work" %s>%s</userTask>
                <sequenceFlow id="a" sourceRef="start" targetRef="work"/></process></definitions>
                """.formatted(key,metadata,group?"flowable:candidateGroups=\"native-group\"":"flowable:candidateUsers=\"actor\"",policy)).deploy();
        return engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
    }
}
