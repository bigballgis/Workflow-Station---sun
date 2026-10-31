package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Adapter transaction boundaries; policy/identity/delegation are exercised by the separate real guard fixtures. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentTaskPermissionReadDatabaseTest extends AssignmentPostgresFixture {
    final AssignmentWriteGuard guard=mock(AssignmentWriteGuard.class);
    AssignmentTaskPermissionRead component;
    @BeforeEach void setup() {
        reset(guard);
        component=new AssignmentTaskPermissionRead(guard,transaction.getTransactionManager());
    }
    @Test void positiveLegacyRemainsAnAdditionalVetoOnly() {
        when(guard.prepare(any())).thenReturn(Optional.empty());
        var result=component.read("task","actor");
        assertEquals(Boolean.FALSE,result.tracked()); assertTrue(result.exclusionAllowsWrite());
        assertEquals("LEGACY",result.reason()); assertNull(result.stamp());
        verify(guard,never()).validate(any());
    }
    @Test void finalCheckIsLocalAndPreparationDoesNotHoldTheReadTransaction() {
        var prepared=prepared("actor",false);
        when(guard.prepare(any())).thenAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            var command=(AssignmentWriteGuard.Command)call.getArgument(0);
            assertEquals("actor",command.actor()); assertNull(command.representedUser());
            return Optional.of(prepared);
        });
        when(guard.validate(prepared)).thenAnswer(call->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return null;
        });
        var result=component.read("task","actor");
        assertTrue(result.exclusionAllowsWrite()); assertEquals(8,result.stamp().authorityRevision());
        assertEquals(3,result.stamp().caseRevision());
    }
    @Test void heldTaskCanWriteEvenThoughItCannotBeClaimedAgain() {
        var prepared=prepared("actor",true);
        when(guard.prepare(any())).thenReturn(Optional.of(prepared));
        assertTrue(component.read("task","actor").exclusionAllowsWrite());
    }
    @Test void candidateMechanicsDoNotReplaceTheOriginalOperationClaimGate() {
        var prepared=prepared(null,true);
        when(guard.prepare(any())).thenReturn(Optional.of(prepared));
        var result=component.read("task","actor");
        assertTrue(result.exclusionAllowsWrite()); assertEquals("CURRENT",result.reason());
    }
    @Test void excludedUnavailableAndEpochChangesNeverBecomeLegacyPermission() {
        for (var reason:new AssignmentFailure.Reason[]{AssignmentFailure.Reason.ASSIGNMENT_EXCLUDED,
                AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE,AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED}) {
            reset(guard);
            if (reason==AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED) {
                var prepared=prepared("actor",false);
                when(guard.prepare(any())).thenReturn(Optional.of(prepared));
                when(guard.validate(any())).thenThrow(new AssignmentFailure(reason));
            } else when(guard.prepare(any())).thenThrow(new AssignmentFailure(reason));
            var result=component.read("task","actor");
            assertFalse(result.exclusionAllowsWrite()); assertNull(result.tracked()); assertEquals(reason.name(),result.reason());
        }
    }
    @Test void outerTransactionIsRejectedBeforeRemotePreparation() {
        assertThrows(AssignmentFailure.class,()->transaction.execute(status->component.read("task","actor")));
        verifyNoInteractions(guard);
    }
    private AssignmentWriteGuard.Prepared prepared(String holder,boolean requiresClaim) {
        var decision=mock(AssignmentCaseEvaluator.Decision.class);
        var qualification=mock(AssignmentQualificationComponent.Prepared.class);
        when(decision.qualification()).thenReturn(qualification); when(decision.caseRevision()).thenReturn(3L);
        when(qualification.authorityRevision()).thenReturn(8L); when(qualification.validUntil()).thenReturn(Instant.now().plusSeconds(60));
        when(qualification.requiresClaim()).thenReturn(requiresClaim);
        return new AssignmentWriteGuard.Prepared(new AssignmentWriteGuard.Command("task",AssignmentMutationScope.Operation.WRITE,"actor",null,null),
                decision,new AssignmentWriteGuard.Holding(holder,null,null,null,null),false);
    }
}
