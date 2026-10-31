package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.messaging.support.NotificationDispatchHelper;
import com.workflow.assignment.impl.*;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "ASSIGNMENT_TEST_JDBC_URL", matches = ".+")
class AssignmentCreationLifecycleDatabaseTest extends AssignmentPostgresFixture {
    final AssignmentQualificationComponent qualification = mock(AssignmentQualificationComponent.class);
    final ExtendedTaskInfoRepository extended = mock(ExtendedTaskInfoRepository.class);
    final NotificationDispatchHelper notifications = mock(NotificationDispatchHelper.class);
    final com.workflow.client.AdminCenterClient adminClient = mock(com.workflow.client.AdminCenterClient.class);
    final AssignmentDelegationSnapshotClient standing = mock(AssignmentDelegationSnapshotClient.class);
    AssignmentCaseRegistry registry;
    AssignmentTaskStateRepository states;
    AssignmentWriteExecutor writes;
    AssignmentWriteGuard actionGuard;
    AssignmentCaseEvaluator evaluator;
    AssignmentProjectionCoordinator projection;
    final Map<String,ExtendedTaskInfo> taskInfos = new java.util.HashMap<>();
    AssignmentQualificationComponent.Prepared prepared;

    @BeforeAll void configure() {
        openEngine();
        var json = new ObjectMapper();
        var definitions = new AssignmentDefinitionPolicy(engine.getRepositoryService());
        registry = new AssignmentCaseRegistryImpl(jdbc, new AssignmentCaseRepository(jdbc, json),
                engine.getRuntimeService(), definitions, mock(CatalogSnapshotReader.class));
        states = new AssignmentTaskStateRepository(jdbc, json);
        evaluator = new AssignmentCaseEvaluatorImpl(definitions, new AssignmentResponsibilityRepository(jdbc, json),
                states, qualification, jdbc);
        projection = new AssignmentProjectionCoordinatorImpl(engine.getTaskService(), evaluator, states, extended, jdbc);
        var coordinator = new AssignmentCreationCoordinatorImpl(engine.getTaskService(), evaluator, states, extended, notifications);
        org.springframework.test.util.ReflectionTestUtils.setField(coordinator,"definitions",definitions);
        var legacyCheck=new AssignmentLegacyTaskCheck(engine.getTaskService(),engine.getRuntimeService(),new AssignmentCaseRepository(jdbc,json),definitions);
        var writeGuard=new AssignmentWriteGuardImpl(engine.getTaskService(),evaluator,states,extended,new AssignmentDelegationResolver(adminClient,standing));
        actionGuard=writeGuard;
        org.springframework.test.util.ReflectionTestUtils.setField(writeGuard,"legacyCheck",legacyCheck);
        writes = new AssignmentWriteExecutorImpl(writeGuard, states, transaction.getTransactionManager());
        org.springframework.test.util.ReflectionTestUtils.setField(writes,"legacyCheck",legacyCheck);
        var lifecycle = new AssignmentCreationLifecycle(registry, coordinator);
        engine.getRuntimeService().addEventListener(lifecycle.listener(true), FlowableEngineEventType.TASK_CREATED);
        engine.getRuntimeService().addEventListener(lifecycle.listener(false), FlowableEngineEventType.TASK_CREATED);
        engine.getRuntimeService().addEventListener(new AssignmentCallRowOutputLifecycle(new AssignmentCaseRepository(jdbc,json),definitions,
                engine.getRuntimeService(),json),FlowableEngineEventType.PROCESS_COMPLETED);
        var mutation=new AssignmentMutationLifecycle(states,new AssignmentResponsibilityRepository(jdbc,json),registry);
        org.springframework.test.util.ReflectionTestUtils.setField(mutation,"legacyCheck",legacyCheck);
        engine.getRuntimeService().addEventListener(mutation, FlowableEngineEventType.TASK_ASSIGNED,
                FlowableEngineEventType.TASK_COMPLETED, FlowableEngineEventType.PROCESS_COMPLETED, FlowableEngineEventType.PROCESS_CANCELLED);
    }

    @BeforeEach void prepareQualification() {
        reset(qualification, extended, notifications, adminClient, standing);
        when(standing.read(any())).thenAnswer(call -> new com.platform.common.assignment.AssignmentDelegationSnapshot(
                1, call.getArgument(0), 0, false, null, Instant.now().plusSeconds(30)));
        taskInfos.clear();
        jdbc.update("UPDATE ac_assignment_authority_fence SET revision=0 WHERE scope='platform'");
        when(extended.findByTaskIdAndIsDeletedFalse(anyString())).thenAnswer(call -> Optional.ofNullable(taskInfos.get(call.getArgument(0))));
        when(extended.save(any())).thenAnswer(call -> { ExtendedTaskInfo info = call.getArgument(0); taskInfos.put(info.getTaskId(), info); return info; });
        prepared = new AssignmentQualificationComponent.Prepared(0, Instant.now().plusSeconds(30),
                List.of("initiator", "eligible"), List.of(), true, identities());
        when(qualification.prepare(any())).thenAnswer(call -> prepared);
    }

    @Test void forbiddenNativeAssigneeIsReplacedBeforeNotificationsAndProjection() {
        var root = start("flowable:assignee=\"initiator\"");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        assertNull(task.getAssignee());
        assertEquals(List.of("eligible"), candidates(task.getId()));
        assertEquals(List.of("eligible"), states.find(task.getId()).orElseThrow().eligible());
        verify(notifications, never()).publishToUserAfterCommit(eq("initiator"), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(notifications).publishToUserAfterCommit(eq("eligible"), eq("TASK"), anyString(), anyString(), eq("/tasks/" + task.getId()), anyString());
        var saved = org.mockito.ArgumentCaptor.forClass(ExtendedTaskInfo.class);
        verify(extended).save(saved.capture());
        assertEquals("eligible", saved.getValue().getAssignmentTarget());
        assertEquals(com.workflow.enums.AssignmentType.CANDIDATE_USERS, saved.getValue().getAssignmentType());
    }

    @Test void allExcludedUsersLeaveAnExplicitBlockedTaskWithoutAutomaticCompletion() {
        prepared = new AssignmentQualificationComponent.Prepared(0, Instant.now().plusSeconds(30),
                List.of("initiator"), List.of(), false, Map.of());
        var root = start("flowable:assignee=\"initiator\"");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        assertNotNull(task);
        assertNull(task.getAssignee());
        assertTrue(candidates(task.getId()).isEmpty());
        assertEquals("BLOCKED_NO_ELIGIBLE_USER", states.find(task.getId()).orElseThrow().state());
        verifyNoInteractions(notifications);
    }

    @Test void ruleOffNativeGroupKeepsItsExistingAssignmentAndRemainsActive() {
        prepared = new AssignmentQualificationComponent.Prepared(0,Instant.now().plusSeconds(30),List.of(),List.of(),true,identities());
        var root = start("flowable:candidateGroups=\"existing-group\"",false);
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        assertTrue(engine.getTaskService().getIdentityLinksForTask(task.getId()).stream()
                .anyMatch(link -> "candidate".equals(link.getType()) && "existing-group".equals(link.getGroupId())));
        assertEquals("ACTIVE",states.find(task.getId()).orElseThrow().state());
        reconcile(task.getId());
        assertTrue(engine.getTaskService().getIdentityLinksForTask(task.getId()).stream().anyMatch(link -> "existing-group".equals(link.getGroupId())));
        assertEquals("ACTIVE",states.find(task.getId()).orElseThrow().state());
    }

    @Test void qualificationFailureRollsBackTheRootTaskAndCreationScope() {
        when(qualification.prepare(any())).thenThrow(new IllegalStateException("synthetic qualification outage"));
        long before = jdbc.queryForObject("SELECT count(*) FROM we_assignment_cases", Long.class);
        assertThrows(RuntimeException.class, () -> start("flowable:candidateUsers=\"initiator,eligible\""));
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM we_assignment_cases", Long.class));
        assertFalse(AssignmentCreationBuffer.active());
        verifyNoInteractions(notifications);
    }

    @Test void rawFlowableCompletionCannotBypassServerAuthorization() {
        var root = start("flowable:candidateUsers=\"initiator,eligible\"");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        assertThrows(RuntimeException.class, () -> engine.getTaskService().complete(task.getId()));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
        assertTrue(new AssignmentResponsibilityRepository(jdbc, new ObjectMapper()).effective(root.caseId()).isEmpty());
    }

    @Test void verifiedDelegatedCompletionRetainsBothResponsibilityIdentities() {
        var root = start("flowable:candidateUsers=\"initiator,eligible\"");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        transaction.executeWithoutResult(status -> {
            new com.platform.common.assignment.AssignmentCaseFence(jdbc).locked(root.caseId());
            AssignmentMutationScope.establish(new AssignmentMutationScope.Authorized(task.getId(),
                    AssignmentMutationScope.Operation.COMPLETE, "delegate", java.util.Set.of("delegate", "eligible"), java.util.Set.of("eligible")));
            engine.getTaskService().complete(task.getId());
        });
        var facts = new AssignmentResponsibilityRepository(jdbc, new ObjectMapper()).effective(root.caseId());
        assertEquals(java.util.Set.of("delegate", "eligible"), facts.get(0).responsibleUserIds());
        assertFalse(states.find(task.getId()).orElseThrow().active());
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT state FROM we_assignment_instances WHERE process_instance_id=?", String.class, root.instance()));
    }

    @Test void rawAssignmentCannotRestoreAnExcludedHolder() {
        var root = start("flowable:candidateUsers=\"initiator,eligible\"");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        assertThrows(RuntimeException.class, () -> engine.getTaskService().setAssignee(task.getId(), "initiator"));
        assertNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
    }

    @Test void missingProjectionCannotBecomeLegacyDuringRawAssignment() {
        var root=start("flowable:candidateUsers=\"initiator,eligible\"");
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        jdbc.update("DELETE FROM we_assignment_task_states WHERE task_id=?",task.getId());
        assertThrows(RuntimeException.class,()->engine.getTaskService().setAssignee(task.getId(),"initiator"));
        assertNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
    }

    @Test void missingProjectionCannotBecomeLegacyDuringRawCompletion() {
        var root=start("flowable:candidateUsers=\"initiator,eligible\"");
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        jdbc.update("DELETE FROM we_assignment_task_states WHERE task_id=?",task.getId());
        assertThrows(RuntimeException.class,()->engine.getTaskService().complete(task.getId()));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
    }

    @Test void missingProjectionCannotAuthorizeAnExecutorBusinessWrite() {
        var root=start("flowable:candidateUsers=\"initiator,eligible\"");
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        jdbc.update("DELETE FROM we_assignment_task_states WHERE task_id=?",task.getId());
        assertThrows(com.platform.common.assignment.AssignmentFailure.class,
                ()->writes.execute(command(task.getId(),AssignmentMutationScope.Operation.WRITE,"initiator"),()->"bypassed"));
    }

    @Test void newlyInsertedTaskCannotBypassProtectionAfterItsCreationCheckInTheSameTransaction() {
        assertThrows(RuntimeException.class,()->transaction.execute(status->{
            var root=start("flowable:candidateUsers=\"initiator,eligible\"");
            var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
            jdbc.update("DELETE FROM we_assignment_task_states WHERE task_id=?",task.getId());
            engine.getTaskService().setAssignee(task.getId(),"initiator");
            return null;
        }));
    }

    @Test void ordinaryLegacyNativeAssigneeStillStartsAndCompletesWithoutProtectedContext() {
        String key="legacy_"+java.util.UUID.randomUUID().toString().replace("-","");
        String xml="""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="legacy">
                <process id="%s" isExecutable="true"><startEvent id="start"/><userTask id="work" flowable:assignee="legacy-user"/>
                <endEvent id="end"/><sequenceFlow id="a" sourceRef="start" targetRef="work"/>
                <sequenceFlow id="b" sourceRef="work" targetRef="end"/></process></definitions>
                """.formatted(key);
        var deployment=engine.getRepositoryService().createDeployment().addString(key+".bpmn20.xml",xml).deploy();
        String definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
        var process=engine.getRuntimeService().startProcessInstanceById(definition);
        var task=engine.getTaskService().createTaskQuery().processInstanceId(process.getId()).singleResult();
        assertEquals("legacy-user",task.getAssignee());
        assertTrue(states.find(task.getId()).isEmpty());
        engine.getTaskService().complete(task.getId());
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(process.getId()).singleResult());
    }
    @Test void ordinaryStandaloneTaskStillWorksWithAllCreationAndMutationListenersInstalled() {
        var task=engine.getTaskService().newTask();
        task.setName("Synthetic independent task"); task.setAssignee("independent-user");
        engine.getTaskService().saveTask(task);
        assertTrue(states.find(task.getId()).isEmpty());
        assertEquals("independent-user",engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
        engine.getTaskService().complete(task.getId());
        assertNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
    }
    @Test void pendingPortalSynchronizationPreventsCancellationUntilItsAcceptedWatermarkIsConfirmed() {
        var root=start("flowable:candidateUsers=\"initiator,eligible\"");
        jdbc.update("UPDATE we_assignment_cases SET accepted_operation_seq=1 WHERE case_id=?",root.caseId());
        assertThrows(RuntimeException.class,()->engine.getRuntimeService().deleteProcessInstance(root.instance(),"synthetic cancellation"));
        assertNotNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(root.instance()).singleResult());
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT state FROM we_assignment_instances WHERE process_instance_id=?",String.class,root.instance()));
        jdbc.update("UPDATE we_assignment_cases SET portal_applied_operation_seq=1,confirmed_operation_seq=1 WHERE case_id=?",root.caseId());
        engine.getRuntimeService().deleteProcessInstance(root.instance(),"synthetic cancellation after synchronization");
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(root.instance()).singleResult());
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT state FROM we_assignment_instances WHERE process_instance_id=?",String.class,root.instance()));
    }

    @Test void anotherCandidateCannotCompleteAnAlreadyClaimedTask() {
        prepared = new AssignmentQualificationComponent.Prepared(0, Instant.now().plusSeconds(30),
                List.of("initiator", "eligible", "delegate"), List.of(), true, identities());
        var root = start("flowable:candidateUsers=\"initiator,eligible,delegate\"");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        writes.execute(command(task.getId(), AssignmentMutationScope.Operation.CLAIM, "eligible"), () -> {
            engine.getTaskService().claim(task.getId(), "eligible"); return null;
        });
        assertThrows(com.platform.common.assignment.AssignmentFailure.class, () ->
                writes.execute(command(task.getId(), AssignmentMutationScope.Operation.COMPLETE, "delegate"), () -> {
                    engine.getTaskService().complete(task.getId()); return null;
                }));
        assertEquals("eligible", engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
        assertTrue(new AssignmentResponsibilityRepository(jdbc, new ObjectMapper()).effective(root.caseId()).isEmpty());
    }

    @Test void guardedDelegateCompletionRecordsActualAndNominalHandlers() {
        var root = start("flowable:candidateUsers=\"initiator,eligible\"");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        claim(task.getId());
        taskInfos.get(task.getId()).delegateTask("delegate", "eligible", "synthetic delegation");
        writes.execute(command(task.getId(), AssignmentMutationScope.Operation.COMPLETE, "delegate"), () -> {
            engine.getTaskService().complete(task.getId()); return null;
        });
        assertEquals(java.util.Set.of("delegate", "eligible"), new AssignmentResponsibilityRepository(jdbc,
                new ObjectMapper()).effective(root.caseId()).get(0).responsibleUserIds());
    }

    @Test void delegationRevokedDuringItsLookupCannotUseAnOlderPreparedEpoch() {
        var root = start("flowable:candidateUsers=\"initiator,eligible\"");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        claim(task.getId());
        taskInfos.get(task.getId()).delegateTaskToBuRole("BU", "ROLE", "eligible", "synthetic delegation");
        when(adminClient.getUsersByBusinessUnitAndRole("BU", "ROLE")).thenAnswer(call -> {
            jdbc.update("UPDATE ac_assignment_authority_fence SET revision=revision+1 WHERE scope='platform'");
            return List.of("delegate");
        });
        var error = assertThrows(com.platform.common.assignment.AssignmentFailure.class, () ->
                writes.execute(command(task.getId(), AssignmentMutationScope.Operation.COMPLETE, "delegate"), () -> {
                    fail("Stale delegation must not reach the business write"); return null;
                }));
        assertEquals("ASSIGNMENT_STATE_CHANGED", error.getDetails().get("reason"));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
    }

    @Test void standingDelegateCompletionRecordsBothPartiesWithoutSingleTaskDelegation() {
        var root=start("flowable:candidateUsers=\"initiator,eligible\"");
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        claim(task.getId());
        when(standing.read(any())).thenAnswer(call -> new com.platform.common.assignment.AssignmentDelegationSnapshot(
                1,call.getArgument(0),0,true,1L,Instant.now().plusSeconds(10)));
        writes.execute(command(task.getId(),AssignmentMutationScope.Operation.COMPLETE,"delegate"),()->{
            engine.getTaskService().complete(task.getId()); return null;
        });
        assertEquals(java.util.Set.of("eligible","delegate"),new AssignmentResponsibilityRepository(jdbc,
                new ObjectMapper()).effective(root.caseId()).get(0).responsibleUserIds());
        verify(standing).read(argThat(binding->binding.taskId().equals(task.getId()) && binding.nominalHolder().equals("eligible")
                && binding.actor().equals("delegate") && binding.caseId().equals(root.caseId())));
    }

    @Test void standingDelegationExpiryAndInterveningRevocationBlockTheBusinessWrite() {
        var root=start("flowable:candidateUsers=\"initiator,eligible\"");
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        claim(task.getId());
        when(standing.read(any())).thenAnswer(call -> new com.platform.common.assignment.AssignmentDelegationSnapshot(
                1,call.getArgument(0),0,true,1L,Instant.now().minusSeconds(1)));
        assertThrows(com.platform.common.assignment.AssignmentFailure.class,()->writes.execute(
                command(task.getId(),AssignmentMutationScope.Operation.COMPLETE,"delegate"),()->{fail("Expired grant reached write");return null;}));
        when(standing.read(any())).thenAnswer(call -> {
            jdbc.update("UPDATE ac_assignment_authority_fence SET revision=1 WHERE scope='platform'");
            return new com.platform.common.assignment.AssignmentDelegationSnapshot(1,call.getArgument(0),0,true,1L,Instant.now().plusSeconds(10));
        });
        assertThrows(com.platform.common.assignment.AssignmentFailure.class,()->writes.execute(
                command(task.getId(),AssignmentMutationScope.Operation.COMPLETE,"delegate"),()->{fail("Revoked grant reached write");return null;}));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
        assertTrue(new AssignmentResponsibilityRepository(jdbc,new ObjectMapper()).effective(root.caseId()).isEmpty());
    }

    @Test void standingDelegateCannotBypassTheNominalHoldersParallelCompletionResponsibility() {
        parallelDelegationVeto("eligible");
    }

    @Test void standingDelegateCannotBypassItsOwnParallelCompletionResponsibility() {
        parallelDelegationVeto("delegate");
    }

    private void parallelDelegationVeto(String firstActor) {
        prepared=new AssignmentQualificationComponent.Prepared(0,Instant.now().plusSeconds(30),
                List.of("eligible","delegate"),List.of(),true,identities());
        var root=startParallel();
        var tasks=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).orderByTaskDefinitionKey().asc().list();
        assertEquals(2,tasks.size());
        claim(tasks.get(1).getId());
        writes.execute(command(tasks.get(0).getId(),AssignmentMutationScope.Operation.COMPLETE,firstActor),()->{
            engine.getTaskService().complete(tasks.get(0).getId());return null;
        });
        when(standing.read(any())).thenAnswer(call->new com.platform.common.assignment.AssignmentDelegationSnapshot(
                1,call.getArgument(0),0,true,1L,Instant.now().plusSeconds(10)));
        var denied=assertThrows(com.platform.common.assignment.AssignmentFailure.class,()->writes.execute(
                command(tasks.get(1).getId(),AssignmentMutationScope.Operation.COMPLETE,"delegate"),()->{
                    fail("An excluded delegation party reached the business write");return null;
                }));
        assertEquals("ASSIGNMENT_EXCLUDED",denied.getDetails().get("reason"));
        assertEquals("eligible",engine.getTaskService().createTaskQuery().taskId(tasks.get(1).getId()).singleResult().getAssignee());
        assertEquals(1,new AssignmentResponsibilityRepository(jdbc,new ObjectMapper()).effective(root.caseId()).size());
    }

    private AssignmentCaseRegistry.Registration startParallel() {
        String key="parallel_"+java.util.UUID.randomUUID().toString().replace("-","");
        String policy="{&quot;schemaVersion&quot;:1,&quot;excludeInitiator&quot;:false,&quot;excludePreviousHandlers&quot;:true,"
                +"&quot;excludeSelectedNodeHandlers&quot;:false,&quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}";
        String node="<userTask id=\"%s\" flowable:candidateUsers=\"eligible,delegate\"><extensionElements><custom:properties>"
                +"<custom:property name=\"assignmentExclusion\" value=\""+policy+"\"/></custom:properties></extensionElements></userTask>";
        String xml="""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                xmlns:custom="http://workflow/custom" targetNamespace="assignment-p0"><process id="%s" isExecutable="true">
                <extensionElements><custom:properties><custom:property name="assignmentRuntimeContract" value="1"/>
                <custom:property name="assignmentRuntimeTracking" value="true"/><custom:property name="assignmentRuntimeUsers" value="{}"/>
                </custom:properties></extensionElements><startEvent id="start"/><parallelGateway id="fork"/>%s%s<endEvent id="end"/>
                <sequenceFlow id="s1" sourceRef="start" targetRef="fork"/><sequenceFlow id="s2" sourceRef="fork" targetRef="a"/>
                <sequenceFlow id="s3" sourceRef="fork" targetRef="b"/><sequenceFlow id="s4" sourceRef="a" targetRef="end"/>
                <sequenceFlow id="s5" sourceRef="b" targetRef="end"/></process></definitions>
                """.formatted(key,node.formatted("a"),node.formatted("b"));
        return deployAndStart(xml,key);
    }

    @Test void acceptedCompletionSurvivesALostResponseAndLaterRevocationWithoutRepeatingTheTask() {
        var root = start("flowable:candidateUsers=\"initiator,eligible\"");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        var operations = new AssignmentOperationRepository(jdbc, new ObjectMapper());
        var original = mock(com.workflow.component.TaskCompletionService.class);
        when(original.completeTask(eq(task.getId()), eq("eligible"), anyMap(), eq(true), isNull())).thenAnswer(call -> {
            engine.getTaskService().complete(task.getId()); return null;
        });
        var accepted = new AssignmentAcceptedCompletionImpl(writes, operations, execution(original), states, jdbc);
        var request = completionRequest(task.getId(), root.instance());
        var receipt = accepted.complete(request);
        assertEquals("PENDING_SYNC", receipt.state());
        assertEquals(1, receipt.operationSeq());
        assertEquals(root.instance(), receipt.acceptedEffect().get("instanceId"));
        var outcomes=com.platform.common.util.JsonUtils.getObjectMapper().convertValue(
                receipt.acceptedEffect().get(com.platform.common.assignment.AssignmentAcceptedProcessState.EFFECT_KEY),
                new com.fasterxml.jackson.core.type.TypeReference<List<com.platform.common.assignment.AssignmentAcceptedProcessState>>() { });
        assertEquals(1,outcomes.size()); assertEquals(root.instance(),outcomes.get(0).instanceId());
        assertEquals("COMPLETED",outcomes.get(0).state()); assertNotNull(outcomes.get(0).endTime());
        assertTrue(outcomes.get(0).tasks().isEmpty());
        reset(qualification);
        jdbc.update("UPDATE ac_assignment_authority_fence SET revision=revision+1 WHERE scope='platform'");
        assertEquals(receipt, accepted.complete(request));
        verifyNoInteractions(qualification);
        verify(original, times(1)).completeTask(eq(task.getId()), eq("eligible"), anyMap(), eq(true), isNull());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM we_assignment_completion_records WHERE case_id=?", Integer.class, root.caseId()));
    }

    @Test void failureAfterTaskCompletionRollsBackFactsAndAcceptedReceiptTogether() {
        var root = start("flowable:candidateUsers=\"initiator,eligible\"");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        var original = mock(com.workflow.component.TaskCompletionService.class);
        when(original.completeTask(eq(task.getId()), eq("eligible"), anyMap(), eq(true), isNull())).thenAnswer(call -> {
            engine.getTaskService().complete(task.getId()); throw new IllegalStateException("synthetic post-completion failure");
        });
        var operations = new AssignmentOperationRepository(jdbc, new ObjectMapper());
        var accepted = new AssignmentAcceptedCompletionImpl(writes, operations, execution(original), states, jdbc);
        var request = completionRequest(task.getId(), root.instance());
        assertThrows(IllegalStateException.class, () -> accepted.complete(request));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
        assertTrue(operations.find(request.operationId()).isEmpty());
        assertTrue(new AssignmentResponsibilityRepository(jdbc, new ObjectMapper()).effective(root.caseId()).isEmpty());
    }

    @Test void acceptedEffectCannotAttachTheCompletedTaskToAnotherAuditStage() {
        var root=start("flowable:candidateUsers=\"initiator,eligible\"");
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        var original=mock(com.workflow.component.TaskCompletionService.class);
        when(original.completeTask(eq(task.getId()),eq("eligible"),anyMap(),eq(true),isNull())).thenAnswer(call->{
            engine.getTaskService().complete(task.getId());return null;
        });
        var accepted=new AssignmentAcceptedCompletionImpl(writes,new AssignmentOperationRepository(jdbc,new ObjectMapper()),execution(original),states,jdbc);
        var request=new com.platform.common.assignment.AssignmentCompletionRequest(java.util.UUID.randomUUID(),task.getId(),"eligible",null,"APPROVE",Map.of("action","APPROVE"),0,
                new com.platform.common.assignment.AssignmentCompletionRequest.Effect(root.instance(),List.of(),"another-stage","a".repeat(64),List.of(),Map.of()));
        assertThrows(com.platform.common.assignment.AssignmentFailure.class,()->accepted.complete(request));
        verifyNoInteractions(original);
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
    }

    @Test void outcomeSnapshotContainsRootVariablesAndPendingCandidatesWithoutLeakingTaskLocalVariables() {
        prepared=new AssignmentQualificationComponent.Prepared(0,Instant.now().plusSeconds(30),
                List.of("eligible","delegate"),List.of(),true,identities());
        var root=startParallel();
        engine.getRuntimeService().setVariable(root.instance(),"root-value","latest");
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).list().get(0);
        engine.getTaskService().setVariableLocal(task.getId(),"task-only-value","private");
        transaction.executeWithoutResult(status->{
            try (var changes=AssignmentCompletionChanges.open(root.instance())) {
                var snapshot=new AssignmentCompletionOutcomeSnapshot(states,engine.getRuntimeService(),engine.getHistoryService(),
                        engine.getTaskService(),engine.getRepositoryService()).capture(root.instance()).get(0);
                assertEquals("latest",snapshot.variables().get("root-value"));
                assertFalse(snapshot.variables().containsKey("task-only-value"));
                assertEquals(2,snapshot.tasks().size());
                assertTrue(snapshot.tasks().stream().allMatch(work->work.candidateUsers().equals(List.of("delegate","eligible"))));
            }
        });
    }

    @Test void outcomeCaptureFailureRollsBackTheAlreadyCompletedTaskAndItsFacts() {
        var root=start("flowable:candidateUsers=\"initiator,eligible\"");
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        var original=mock(com.workflow.component.TaskCompletionService.class);
        when(original.completeTask(eq(task.getId()),eq("eligible"),anyMap(),eq(true),isNull())).thenAnswer(call->{
            engine.getTaskService().complete(task.getId());return null;
        });
        var snapshot=mock(AssignmentCompletionOutcomeSnapshot.class);
        when(snapshot.variablesBefore(root.instance())).thenReturn(Map.of(root.instance(),new ObjectMapper().createObjectNode()));
        when(snapshot.capture(root.instance())).thenThrow(new IllegalStateException("synthetic projection failure"));
        var operations=new AssignmentOperationRepository(jdbc,new ObjectMapper());
        var accepted=new AssignmentAcceptedCompletionImpl(writes,operations,new AssignmentCompletionExecution(original,snapshot,
                mock(AssignmentCompletionOutputCompiler.class)),states,jdbc);
        var request=completionRequest(task.getId(),root.instance());
        assertThrows(IllegalStateException.class,()->accepted.complete(request));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
        assertTrue(operations.find(request.operationId()).isEmpty());
        assertTrue(new AssignmentResponsibilityRepository(jdbc,new ObjectMapper()).effective(root.caseId()).isEmpty());
    }

    @Test void automaticScalarOutputsUseNarrowOriginalPortalBaselines() {
        String key="automatic_output_"+java.util.UUID.randomUUID().toString().replace("-","");
        String xml="""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                xmlns:custom="http://workflow/custom" targetNamespace="assignment-p0"><process id="%s" isExecutable="true">
                <extensionElements><custom:properties><custom:property name="assignmentRuntimeContract" value="1"/>
                <custom:property name="assignmentRuntimeTracking" value="true"/><custom:property name="assignmentRuntimeUsers" value="{}"/>
                </custom:properties></extensionElements><startEvent id="start"/>
                <userTask id="work" flowable:candidateUsers="eligible"><extensionElements>
                <flowable:executionListener event="end" expression="${execution.setVariable('nextApprover', 'delegate')}"/>
                </extensionElements></userTask><endEvent id="end"/>
                <sequenceFlow id="s1" sourceRef="start" targetRef="work"/><sequenceFlow id="s2" sourceRef="work" targetRef="end"/>
                </process></definitions>
                """.formatted(key);
        var root=deployAndStart(xml,key,Map.of("nextApprover","eligible","keep","stale Engine value","currentUserId","previous-actor"));
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        var original=mock(com.workflow.component.TaskCompletionService.class);
        when(original.completeTask(eq(task.getId()),eq("eligible"),anyMap(),eq(true),isNull())).thenAnswer(call->{
            engine.getTaskService().complete(task.getId(),call.<Map<String,Object>>getArgument(2)); return null;
        });
        var portal=new ObjectMapper().createObjectNode().put("nextApprover","eligible").put("keep","newer Portal value").put("currentUserId","previous-actor");
        var effect=new com.platform.common.assignment.AssignmentCompletionRequest.Effect(root.instance(),List.of(),"work","a".repeat(64),
                List.of(),Map.of(),List.of(new com.platform.common.assignment.AssignmentCompletionRequest.InstanceBaseline(root.instance(),root.definition(),portal)));
        var request=new com.platform.common.assignment.AssignmentCompletionRequest(java.util.UUID.randomUUID(),task.getId(),"eligible",null,
                "APPROVE",Map.of("action","APPROVE","currentUserId","previous-actor"),0,effect);
        var accepted=new AssignmentAcceptedCompletionImpl(writes,new AssignmentOperationRepository(jdbc,new ObjectMapper()),execution(original),states,jdbc);
        var receipt=accepted.complete(request);
        var patches=com.platform.common.util.JsonUtils.getObjectMapper().convertValue(receipt.acceptedEffect().get(
                com.platform.common.assignment.AssignmentRelatedVariablePatch.EFFECT_KEY),
                new com.fasterxml.jackson.core.type.TypeReference<List<com.platform.common.assignment.AssignmentRelatedVariablePatch>>() { });
        assertEquals(1,patches.size());
        var updated=com.platform.common.assignment.AssignmentVariablePatch.apply(portal,patches.get(0).variablePatch());
        assertEquals("delegate",updated.path("nextApprover").asText());
        assertEquals("newer Portal value",updated.path("keep").asText());
        assertEquals("eligible",updated.path("currentUserId").asText());
        assertEquals("previous-actor",request.variables().get("currentUserId"));
        assertTrue(((List<?>)receipt.acceptedEffect().get("baselines")).isEmpty());
    }

    @Test void aProtectedRowCallPublishesItsOutputBeforeTheCallersNextNodeStarts() {
        prepared=new AssignmentQualificationComponent.Prepared(0,Instant.now().plusSeconds(30),
                List.of("eligible","delegate"),List.of(),true,identities());
        var child=start("flowable:candidateUsers=\"eligible,delegate\"");
        String key="call_output_"+java.util.UUID.randomUUID().toString().replace("-","");
        String xml="""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                xmlns:custom="http://workflow/custom" targetNamespace="assignment-p0"><process id="%s" isExecutable="true">
                <extensionElements><custom:properties><custom:property name="assignmentRuntimeContract" value="1"/>
                <custom:property name="assignmentRuntimeTracking" value="true"/><custom:property name="assignmentRuntimeUsers" value="{}"/>
                </custom:properties></extensionElements><startEvent id="start"/>
                <callActivity id="call" calledElement="%s" flowable:calledElementType="id"><extensionElements><custom:properties>
                <custom:property name="callRowsTable" value="items"/>
                <custom:property name="callOutputMapping" value="[{&quot;from&quot;:&quot;returned&quot;,&quot;to&quot;:&quot;row.nextApprover&quot;}]"/>
                </custom:properties></extensionElements><multiInstanceLoopCharacteristics isSequential="false"/></callActivity>
                <userTask id="followup" flowable:candidateUsers="eligible,delegate"><extensionElements>
                <flowable:executionListener event="start" expression="${execution.setVariable('observedOutput', __subTables__['dw:items'][0]['nextApprover'])}"/>
                </extensionElements></userTask><endEvent id="end"/><sequenceFlow id="s1" sourceRef="start" targetRef="call"/>
                <sequenceFlow id="s2" sourceRef="call" targetRef="followup"/><sequenceFlow id="s3" sourceRef="followup" targetRef="end"/>
                </process></definitions>
                """.formatted(key,child.definition());
        xml=com.workflow.util.CallActivityDataMappingCompiler.compile(xml);
        var root=deployAndStart(xml,key,Map.of("__subTables__",Map.of("dw:items",List.of(Map.of("platformRowUuid","row","nextApprover","eligible","untouched",3)))));
        var task=engine.getTaskService().createTaskQuery().processDefinitionId(child.definition()).list().stream()
                .filter(value->registry.instance(value.getProcessInstanceId()).orElseThrow().caseId().equals(root.caseId())).findFirst().orElseThrow();
        writes.execute(command(task.getId(),AssignmentMutationScope.Operation.COMPLETE,"eligible"),()->{
            engine.getTaskService().complete(task.getId(),Map.of("returned","delegate"));return null;
        });
        assertEquals("delegate",engine.getRuntimeService().getVariable(root.instance(),"observedOutput"));
        var tables=(Map<?,?>)engine.getRuntimeService().getVariable(root.instance(),"__subTables__");
        assertEquals(3,((Map<?,?>)((List<?>)tables.get("dw:items")).get(0)).get("untouched"));
    }

    @Test void deniedWriteDoesNotUndoTheReconcilerIndependentRelease() {
        var root = start("flowable:candidateUsers=\"initiator,eligible\"");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        claim(task.getId());
        prepared = new AssignmentQualificationComponent.Prepared(0, Instant.now().plusSeconds(30), List.of("delegate"), List.of(), true, identities());
        assertThrows(com.platform.common.assignment.AssignmentFailure.class, () -> writes.execute(
                command(task.getId(), AssignmentMutationScope.Operation.COMPLETE, "eligible"), () -> {
                    fail("Disqualified holder must not save or complete"); return null;
                }));
        assertEquals("eligible", engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
        reconcile(task.getId());
        assertNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
        var state = states.find(task.getId()).orElseThrow();
        assertEquals("eligible", state.releasedHolder());
        assertFalse(state.handoffConsumed());
        assertEquals(List.of("delegate"), candidates(task.getId()));
        assertThrows(com.platform.common.assignment.AssignmentFailure.class, () -> writes.execute(
                command(task.getId(), AssignmentMutationScope.Operation.COMPLETE, "eligible"), () -> null));
        assertNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
    }

    @Test void primaryRecoveryDoesNotReleaseAStillQualifiedBackupHolder() {
        var root = start("flowable:candidateUsers=\"initiator,eligible\"");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        claim(task.getId());
        transaction.executeWithoutResult(status -> states.held(task.getId(), AssignmentExclusionEvaluator.Source.BACKUP));
        prepared = new AssignmentQualificationComponent.Prepared(0, Instant.now().plusSeconds(30),
                List.of("delegate"), List.of("eligible"), true, identities());
        reconcile(task.getId());
        assertEquals("eligible", engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
        assertEquals("BACKUP", states.find(task.getId()).orElseThrow().holderSource());
        assertEquals(List.of("delegate"), candidates(task.getId()));
    }

    @Test void refreshOfAnUnclaimedTaskDoesNotAppointASoleCandidate() {
        var root = start("flowable:candidateUsers=\"initiator,eligible\"");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        reconcile(task.getId());
        assertNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
        assertEquals(List.of("eligible"), candidates(task.getId()));
    }

    @Test void releasedHolderMayHandoffOnlyOnceToACurrentEligibleTarget() {
        var root=start("flowable:candidateUsers=\"initiator,eligible\"");
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        claim(task.getId());
        prepared=new AssignmentQualificationComponent.Prepared(0,Instant.now().plusSeconds(30),List.of("delegate"),List.of(),true,identities());
        reconcile(task.getId());
        var handoff=new AssignmentHandoffComponent(writes,states,engine.getTaskService(),extended,notifications);
        var reads=new AssignmentActionAvailabilityRead(actionGuard,mock(AssignmentReopenPreparation.class),new AssignmentCaseRepository(jdbc,new ObjectMapper()),
                mock(AssignmentLegacyHistoryCheck.class),transaction.getTransactionManager());
        assertFalse(reads.handoff(task.getId(),"initiator").allowed());
        var available=reads.handoff(task.getId(),"eligible");
        assertTrue(available.allowed()); assertEquals(List.of("delegate"),available.targets());
        assertThrows(com.platform.common.assignment.AssignmentFailure.class,()->handoff.handoff(task.getId(),"initiator","delegate"));
        assertThrows(com.platform.common.assignment.AssignmentFailure.class,()->handoff.handoff(task.getId(),"eligible","initiator"));
        assertTrue(handoff.handoff(task.getId(),"eligible","delegate"));
        assertEquals("delegate",engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
        assertTrue(states.find(task.getId()).orElseThrow().handoffConsumed());
        assertFalse(reads.handoff(task.getId(),"eligible").allowed());
        assertEquals("delegate",taskInfos.get(task.getId()).getClaimedBy());
        assertEquals("eligible",taskInfos.get(task.getId()).getUpdatedBy());
        assertEquals("delegate",jdbc.queryForObject("SELECT release_history->-1->>'targetId' FROM we_assignment_task_states WHERE task_id=?",String.class,task.getId()));
        assertNull(taskInfos.get(task.getId()).getDelegatedTo());
        assertThrows(com.platform.common.assignment.AssignmentFailure.class,()->handoff.handoff(task.getId(),"eligible","delegate"));
    }

    @Test void anotherHolderClaimConsumesTheFormerHoldersHandoffPermission() {
        var root=start("flowable:candidateUsers=\"initiator,eligible\"");
        var task=engine.getTaskService().createTaskQuery().processInstanceId(root.instance()).singleResult();
        claim(task.getId());
        prepared=new AssignmentQualificationComponent.Prepared(0,Instant.now().plusSeconds(30),List.of("delegate"),List.of(),true,identities());
        reconcile(task.getId());
        writes.execute(command(task.getId(),AssignmentMutationScope.Operation.CLAIM,"delegate"),()->{
            engine.getTaskService().claim(task.getId(),"delegate");return null;
        });
        var handoff=new AssignmentHandoffComponent(writes,states,engine.getTaskService(),extended,notifications);
        assertThrows(com.platform.common.assignment.AssignmentFailure.class,()->handoff.handoff(task.getId(),"eligible","delegate"));
        assertEquals("delegate",engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
    }

    private void reconcile(String taskId) {
        var task = engine.getTaskService().createTaskQuery().taskId(taskId).singleResult();
        var state = states.find(taskId).orElseThrow();
        var additional = task.getAssignee() == null ? List.<com.platform.common.assignment.AssignmentIdentitySnapshot.Reference>of()
                : List.of(new com.platform.common.assignment.AssignmentIdentitySnapshot.Reference(task.getAssignee(),
                        com.platform.common.assignment.AssignmentIdentitySnapshot.Kind.ID));
        var decision = evaluator.prepare(task, state.sourceSpec(), additional);
        transaction.executeWithoutResult(status -> projection.apply(decision));
    }

    private com.platform.common.assignment.AssignmentCompletionRequest completionRequest(String taskId, String instance) {
        var process=engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instance).includeProcessVariables().singleResult();
        var baseline=new com.platform.common.assignment.AssignmentCompletionRequest.InstanceBaseline(instance,process.getProcessDefinitionId(),
                com.platform.common.util.JsonUtils.getObjectMapper().valueToTree(process.getProcessVariables()));
        return new com.platform.common.assignment.AssignmentCompletionRequest(java.util.UUID.randomUUID(), taskId, "eligible", null,
                "APPROVE", Map.of("action", "APPROVE"), 0,
                new com.platform.common.assignment.AssignmentCompletionRequest.Effect(instance,List.of(),null,null,List.of(),Map.of(),List.of(baseline)));
    }

    private AssignmentCompletionExecution execution(com.workflow.component.TaskCompletionService original) {
        return new AssignmentCompletionExecution(original,new AssignmentCompletionOutcomeSnapshot(states,engine.getRuntimeService(),
                engine.getHistoryService(),engine.getTaskService(),engine.getRepositoryService()),
                new AssignmentCompletionOutputCompiler(states,new AssignmentDefinitionPolicy(engine.getRepositoryService())));
    }

    private void claim(String task) {
        writes.execute(command(task, AssignmentMutationScope.Operation.CLAIM, "eligible"), () -> {
            engine.getTaskService().claim(task, "eligible"); return null;
        });
    }

    private AssignmentWriteGuard.Command command(String task, AssignmentMutationScope.Operation operation, String actor) {
        return new AssignmentWriteGuard.Command(task, operation, actor, null, null);
    }

    private Map<String,com.platform.common.assignment.AssignmentIdentitySnapshot.User> identities() {
        var users = new java.util.LinkedHashMap<String,com.platform.common.assignment.AssignmentIdentitySnapshot.User>();
        for (String id : List.of("initiator", "eligible", "delegate")) users.put(id,
                new com.platform.common.assignment.AssignmentIdentitySnapshot.User(id, id, true));
        return Map.copyOf(users);
    }

    private List<String> candidates(String task) {
        return engine.getTaskService().getIdentityLinksForTask(task).stream().filter(link -> "candidate".equals(link.getType()))
                .map(org.flowable.identitylink.api.IdentityLink::getUserId).sorted().toList();
    }

    private AssignmentCaseRegistry.Registration start(String assignment) {
        return start(assignment,true);
    }

    private AssignmentCaseRegistry.Registration start(String assignment,boolean enabled) {
        String key = "creation_" + java.util.UUID.randomUUID().toString().replace("-", "");
        String xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                  xmlns:custom="http://workflow/custom" targetNamespace="assignment-p0">
                <process id="%s" isExecutable="true"><extensionElements><custom:properties>
                <custom:property name="assignmentRuntimeContract" value="1"/>
                <custom:property name="assignmentRuntimeTracking" value="true"/>
                <custom:property name="assignmentRuntimeUsers" value="{}"/>
                </custom:properties></extensionElements><startEvent id="start"/><userTask id="work" %s>
                <extensionElements><custom:properties><custom:property name="assignmentExclusion" value="{&quot;schemaVersion&quot;:1,
                &quot;excludeInitiator&quot;:true,&quot;excludePreviousHandlers&quot;:false,&quot;excludeSelectedNodeHandlers&quot;:false,
                &quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}"/></custom:properties></extensionElements>
                </userTask><endEvent id="end"/><sequenceFlow id="s1" sourceRef="start" targetRef="work"/>
                <sequenceFlow id="s2" sourceRef="work" targetRef="end"/></process></definitions>
                """.formatted(key, assignment);
        if (!enabled) xml=xml.replace("&quot;excludeInitiator&quot;:true","&quot;excludeInitiator&quot;:false");
        return deployAndStart(xml,key);
    }

    private AssignmentCaseRegistry.Registration deployAndStart(String xml,String key) {
        return deployAndStart(xml,key,Map.of());
    }
    private AssignmentCaseRegistry.Registration deployAndStart(String xml,String key,Map<String,Object> variables) {
        var deployment = engine.getRepositoryService().createDeployment().addString(key + ".bpmn20.xml", xml).deploy();
        String definition = engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
        return transaction.execute(status -> {
            var registered = registry.newRoot(definition, "initiator", "BU");
            engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(definition)
                    .predefineProcessInstanceId(registered.instance()).variables(variables).start();
            return registered;
        });
    }
}
