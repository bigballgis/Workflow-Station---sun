package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.*;
import com.workflow.assignment.impl.AssignmentCaseEvaluatorImpl;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "ASSIGNMENT_TEST_JDBC_URL", matches = ".+")
class AssignmentCaseEvaluatorDatabaseTest extends AssignmentPostgresFixture {
    final AssignmentDefinitionPolicy definitions = mock(AssignmentDefinitionPolicy.class);
    final AssignmentQualificationComponent qualification = mock(AssignmentQualificationComponent.class);
    final Task task = mock(Task.class);
    UUID caseId;
    AssignmentCaseEvaluator evaluator;
    AssignmentQualificationComponent.Prepared prepared;

    @BeforeEach void createCase() {
        reset(definitions, qualification, task);
        caseId = UUID.randomUUID();
        String instance = "instance-" + caseId;
        String taskId = "task-" + caseId;
        var json = new ObjectMapper();
        jdbc.update("UPDATE ac_assignment_authority_fence SET revision=0 WHERE scope='platform'");
        var cases = new AssignmentCaseRepository(jdbc, json);
        cases.createCase(caseId, instance, "initiator", "BU");
        cases.insert(new AssignmentCaseRegistry.Registration(instance, caseId, UUID.randomUUID(), "definition", List.of()), null, null, "FU");
        cases.registerTask(new AssignmentCaseRepository.TaskSnapshot(taskId, "work",
                cases.find(instance).orElseThrow(), new AssignmentSlotIdentity.Position("slot", "{}"), "hash"));
        when(task.getId()).thenReturn(taskId);
        when(task.getProcessInstanceId()).thenReturn(instance);
        when(task.getProcessDefinitionId()).thenReturn("definition");
        when(task.getTaskDefinitionKey()).thenReturn("work");
        when(definitions.node("definition", "work")).thenReturn(new AssignmentExclusionPolicy(1, true, true, false, List.of(),
                new AssignmentExclusionPolicy.Backup(false, null, null, null, List.of(), List.of())));
        prepared = new AssignmentQualificationComponent.Prepared(0, Instant.now().plusSeconds(30),
                List.of("initiator", "eligible"), List.of(), true, Map.of());
        when(qualification.prepare(any())).thenAnswer(call -> prepared);
        evaluator = new AssignmentCaseEvaluatorImpl(definitions, new AssignmentResponsibilityRepository(jdbc, json),
                new AssignmentTaskStateRepository(jdbc, json), qualification, jdbc);
    }

    @Test void finalCheckUsesOriginalInitiatorAndCurrentEffectiveHistory() {
        var decision = prepare();
        assertEquals(Set.of("initiator"), decision.excluded());
        assertEquals(List.of("eligible"), decision.pool().users());
        transaction.executeWithoutResult(status -> evaluator.checkCurrent(decision));
    }

    @Test void revocationCommittedBeforeFinalCheckRejectsSnapshot() {
        var decision = prepare();
        transaction.executeWithoutResult(status -> new AssignmentAuthorityFence(jdbc).beginChange());
        assertChanged(decision);
    }

    @Test void caseChangeAfterPreparationRejectsStaleHistory() {
        var decision = prepare();
        jdbc.update("UPDATE we_assignment_cases SET revision=revision+1 WHERE case_id=?", caseId);
        assertChanged(decision);
    }

    @Test void exactExpiryInstantCannotAuthorizeAWrite() {
        prepared = new AssignmentQualificationComponent.Prepared(0, Instant.EPOCH, List.of("eligible"), List.of(), true, Map.of());
        assertChanged(prepare());
    }

    @Test void finalCheckDoesNotRetainQualificationLockDuringAcceptedTransaction() {
        var decision = prepare();
        transaction.executeWithoutResult(status -> {
            evaluator.checkCurrent(decision);
            var independent = new org.springframework.transaction.support.TransactionTemplate(transaction.getTransactionManager());
            independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            independent.executeWithoutResult(change -> new AssignmentAuthorityFence(jdbc).beginChange());
            jdbc.update("INSERT INTO p0_saved VALUES(?,?)", task.getId(), "accepted");
        });
        assertEquals("accepted", jdbc.queryForObject("SELECT value FROM p0_saved WHERE id=?", String.class, task.getId()));
    }

    @Test void pendingPortalSynchronizationBlocksAnotherBusinessWrite() {
        var decision = prepare();
        jdbc.update("UPDATE we_assignment_cases SET accepted_operation_seq=1 WHERE case_id=?", caseId);
        var error = assertThrows(AssignmentFailure.class, () -> transaction.executeWithoutResult(status -> evaluator.checkCurrent(decision)));
        assertEquals("ASSIGNMENT_SYNC_PENDING", error.getDetails().get("reason"));
    }

    @Test void aLatePortalEffectMustInvalidateEarlierSourcesEvenAfterItsDebtIsCleared() {
        var decision=prepare();
        transaction.executeWithoutResult(status->{
            var fence=new AssignmentCaseFence(jdbc); fence.locked(caseId);
            jdbc.update("UPDATE we_assignment_cases SET accepted_operation_seq=1 WHERE case_id=?",caseId);
            fence.portalApplied(caseId,1,0);
            jdbc.update("UPDATE we_assignment_cases SET confirmed_operation_seq=1 WHERE case_id=?",caseId);
        });
        assertChanged(decision);
    }

    private AssignmentCaseEvaluator.Decision prepare() {
        return evaluator.prepare(task, AssignmentSourceSpec.fixedCanonical(List.of("eligible"), true), List.of());
    }

    private void assertChanged(AssignmentCaseEvaluator.Decision decision) {
        var error = assertThrows(AssignmentFailure.class, () -> transaction.executeWithoutResult(status -> evaluator.checkCurrent(decision)));
        assertEquals("ASSIGNMENT_STATE_CHANGED", error.getDetails().get("reason"));
    }
}
