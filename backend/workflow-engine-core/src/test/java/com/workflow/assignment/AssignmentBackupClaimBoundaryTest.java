package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.workflow.assignment.impl.AssignmentWriteGuardImpl;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssignmentBackupClaimBoundaryTest {
    final TaskService tasks=mock(TaskService.class);
    final AssignmentCaseEvaluator evaluator=mock(AssignmentCaseEvaluator.class);
    final AssignmentTaskStateRepository states=mock(AssignmentTaskStateRepository.class);
    final ExtendedTaskInfoRepository extended=mock(ExtendedTaskInfoRepository.class);
    final AssignmentDelegationResolver delegation=mock(AssignmentDelegationResolver.class);
    final Task task=mock(Task.class);
    final AssignmentWriteGuard guard=new AssignmentWriteGuardImpl(tasks,evaluator,states,extended,delegation);
    AssignmentCaseEvaluator.Decision decision;

    @BeforeEach void configure() {
        var query=mock(TaskQuery.class,RETURNS_SELF);
        when(tasks.createTaskQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn("task");
        var source=AssignmentSourceSpec.fixedCanonical(List.of("original"),true);
        when(states.find("task")).thenReturn(Optional.of(new AssignmentTaskStateRepository.State("task",UUID.randomUUID(),
                "instance","definition","node",source,null,"BACKUP",List.of("original"),List.of("backup"),"ACTIVE",
                0,0,null,false,true,"hash")));
        var policy=AssignmentExclusionPolicyCodec.decode("""
                {"schemaVersion":1,"excludeInitiator":true,"excludePreviousHandlers":false,"excludeSelectedNodeHandlers":false,
                "selectedNodeKeys":[],"backup":{"enabled":true,"mode":"USERS","userRefs":["backup"]}}
                """);
        var identity=new AssignmentIdentitySnapshot.User("backup","backup",true);
        var qualification=new AssignmentQualificationComponent.Prepared(0,Instant.now().plusSeconds(30),
                List.of("original"),List.of("backup"),true,Map.of("backup",identity));
        decision=new AssignmentCaseEvaluator.Decision("task",UUID.randomUUID(),0,policy,Set.of("original"),qualification,
                new AssignmentExclusionEvaluator.Pool(AssignmentExclusionEvaluator.Source.BACKUP,List.of("backup")),0);
        when(evaluator.prepare(eq(task),eq(source),anyList())).thenAnswer(call->decision);
        when(delegation.resolve(eq(task),any(),any(),any())).thenAnswer(call->new AssignmentDelegationResolver.Grant(false,decision.qualification().validUntil()));
    }

    @ParameterizedTest
    @EnumSource(value=AssignmentMutationScope.Operation.class,names={"WRITE","COMPLETE","RETURN","TRANSFER","DELEGATE"})
    void unclaimedBackupCannotMutateBusinessState(AssignmentMutationScope.Operation operation) {
        var failure=assertThrows(AssignmentFailure.class,()->guard.prepare(command(operation)));
        assertEquals("ASSIGNMENT_EXCLUDED",failure.getDetails().get("reason"));
    }

    @Test void backupMayStillClaim() { assertTrue(guard.prepare(command(AssignmentMutationScope.Operation.CLAIM)).isPresent()); }

    @Test void primaryCandidateKeepsExistingUnclaimedWriteSemantics() {
        var q=decision.qualification();
        decision=new AssignmentCaseEvaluator.Decision("task",decision.caseId(),0,decision.policy(),Set.of(),
                new AssignmentQualificationComponent.Prepared(0,q.validUntil(),List.of("backup"),List.of(),true,q.identities()),
                new AssignmentExclusionEvaluator.Pool(AssignmentExclusionEvaluator.Source.PRIMARY,List.of("backup")),0);
        assertTrue(guard.prepare(command(AssignmentMutationScope.Operation.WRITE)).isPresent());
    }

    @Test void managementReassignmentMayReplaceAStaleHolderWithoutImpersonatingThatHolder() {
        when(task.getAssignee()).thenReturn("stale-holder");
        var identities=new HashMap<>(decision.qualification().identities());
        identities.put("manager",new AssignmentIdentitySnapshot.User("manager","manager",true));
        withIdentities(identities,decision.excluded());
        assertTrue(guard.prepare(new AssignmentWriteGuard.Command("task",AssignmentMutationScope.Operation.REASSIGN,"manager",null,"backup")).isPresent());
        assertThrows(AssignmentFailure.class,()->guard.prepare(new AssignmentWriteGuard.Command("task",AssignmentMutationScope.Operation.TRANSFER,"manager",null,"backup")));
    }

    @Test void managementReassignmentStillRejectsAnExcludedOperatorOrTarget() {
        var identities=new HashMap<>(decision.qualification().identities());
        identities.put("manager",new AssignmentIdentitySnapshot.User("manager","manager",true));
        withIdentities(identities,Set.of("manager"));
        assertThrows(AssignmentFailure.class,()->guard.prepare(new AssignmentWriteGuard.Command("task",AssignmentMutationScope.Operation.REASSIGN,"manager",null,"backup")));
        withIdentities(identities,Set.of("backup"));
        assertThrows(AssignmentFailure.class,()->guard.prepare(new AssignmentWriteGuard.Command("task",AssignmentMutationScope.Operation.REASSIGN,"manager",null,"backup")));
    }

    private void withIdentities(Map<String,AssignmentIdentitySnapshot.User> identities,Set<String> excluded) {
        var q=decision.qualification();
        decision=new AssignmentCaseEvaluator.Decision("task",decision.caseId(),0,decision.policy(),excluded,
                new AssignmentQualificationComponent.Prepared(0,q.validUntil(),q.primary(),q.backup(),true,identities),decision.pool(),0);
    }

    private AssignmentWriteGuard.Command command(AssignmentMutationScope.Operation operation) {
        return new AssignmentWriteGuard.Command("task",operation,"backup",null,null);
    }
}
