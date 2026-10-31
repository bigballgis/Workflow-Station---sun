package com.portal.component;

import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import com.portal.client.AssignmentOperationClient;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskCompleteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssignmentCompletionOrchestratorTest {
    @Test void publicCompletionCannotForgeAnInitialSubmission() {
        request.setAction("SUBMIT");
        assertThrows(AssignmentFailure.class,()->component.complete(request,"actor",null));
        verifyNoInteractions(tasks,guard,effects,operations,sync);
    }
    final WorkflowEngineClient tasks=mock(WorkflowEngineClient.class);
    final AssignmentPortalWriteGuard guard=mock(AssignmentPortalWriteGuard.class);
    final AssignmentCompletionEffectBuilder effects=mock(AssignmentCompletionEffectBuilder.class);
    final AssignmentOperationClient operations=mock(AssignmentOperationClient.class);
    final AssignmentPortalSync sync=mock(AssignmentPortalSync.class);
    final AssignmentCompletionOrchestrator component=new AssignmentCompletionOrchestrator(tasks,guard,effects,operations,sync);
    TaskCompleteRequest request;
    AssignmentOperationReceipt receipt;
    @BeforeEach void prepare() {
        var principal=com.platform.common.dto.UserPrincipal.builder().userId("actor").username("synthetic").build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,List.of()));
        request=TaskCompleteRequest.builder().taskId("task").action("APPROVE").operationId(UUID.randomUUID()).formData(Map.of("field","accepted")).build();
        receipt=receipt("PENDING_SYNC");
        when(operations.find(request.getOperationId())).thenReturn(Optional.empty());
        when(tasks.getTaskById("task")).thenReturn(Optional.of(Map.of("taskId","task","processInstanceId","instance","currentAssignee","nominal")));
    }
    @AfterEach void cleanup() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    @Test void lostResponseResumesTheFrozenReceiptWithoutTaskLookupOrQualification() {
        when(operations.find(request.getOperationId())).thenReturn(Optional.of(receipt));
        assertEquals("READY",component.complete(request,"actor",null).orElseThrow().state());
        verify(sync).apply(receipt); verify(operations).confirm(receipt);
        verifyNoInteractions(tasks,guard,effects); verify(operations,never()).complete(any());
    }
    @Test void aDifferentActorOrPayloadCannotReuseAnAcceptedOperation() {
        when(operations.find(request.getOperationId())).thenReturn(Optional.of(receipt));
        assertThrows(AssignmentFailure.class,()->component.complete(request,"other",null));
        request.setFormData(Map.of("field","changed"));
        assertThrows(AssignmentFailure.class,()->component.complete(request,"actor",null));
        verifyNoInteractions(tasks,guard,effects,sync); verify(operations,never()).confirm(any());
    }
    @Test void localSyncFailureReportsAcceptedPendingAndNeverRecompletes() {
        when(operations.find(request.getOperationId())).thenReturn(Optional.of(receipt));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic unavailable")).when(sync).apply(receipt);
        assertEquals("PENDING_SYNC",component.complete(request,"actor",null).orElseThrow().state());
        verify(operations,never()).confirm(any()); verify(operations,never()).complete(any());
    }
    @Test void lostConfirmationResponseLeavesRecoveryPendingWithoutRepeatingTheLocalBusinessEffect() {
        when(operations.find(request.getOperationId())).thenReturn(Optional.of(receipt));
        doThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE)).when(operations).confirm(receipt);
        assertEquals("PENDING_SYNC",component.complete(request,"actor",null).orElseThrow().state());
        verify(sync).apply(receipt); verify(operations,never()).complete(any());
    }
    @Test void explicitConflictCannotTurnIntoAnotherCompletionOrAnAutomaticOverwrite() {
        when(operations.find(request.getOperationId())).thenReturn(Optional.of(receipt("SYNC_CONFLICT")));
        assertEquals("SYNC_CONFLICT",component.complete(request,"actor",null).orElseThrow().state());
        verifyNoInteractions(sync,tasks,guard,effects); verify(operations,never()).complete(any());
    }
    @Test void aNewLocalConflictIsPersistedForSubsequentStatusQueries() {
        when(operations.find(request.getOperationId())).thenReturn(Optional.of(receipt));
        doThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT)).when(sync).apply(receipt);
        assertEquals("SYNC_CONFLICT",component.complete(request,"actor",null).orElseThrow().state());
        verify(operations).retry(receipt,true);
        verify(operations,never()).confirm(any()); verify(operations,never()).complete(any());
    }
    @Test void recoveryBookkeepingOutageDoesNotMisreportAnAcceptedCompletionAsFailed() {
        when(operations.find(request.getOperationId())).thenReturn(Optional.of(receipt));
        doThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT)).when(sync).apply(receipt);
        doThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE)).when(operations).retry(receipt,true);
        assertEquals("SYNC_CONFLICT",component.complete(request,"actor",null).orElseThrow().state());
        verify(operations,never()).complete(any());
    }
    @Test void aKnownLegacyTaskRetainsItsOriginalCompletionPath() {
        when(guard.prepare(any())).thenAnswer(call->{
            var value=(AssignmentPortalWriteGuard.Request)call.getArgument(0);
            return AssignmentWriteDecision.legacy(new AssignmentWriteDecision.Request(value.taskId(),value.actor(),null,"COMPLETE",value.digest(),value.instanceId()));
        });
        assertTrue(component.complete(request,"actor",null).isEmpty());
        verifyNoInteractions(effects,sync); verify(operations,never()).complete(any());
    }
    @Test void protectedAcceptanceFreezesTheOriginalEffectAndUsesThePreparedPortalRevision() {
        var effect=prepareAcceptance();
        when(operations.complete(any())).thenReturn(receipt);
        assertEquals("READY",component.complete(request,"actor",null).orElseThrow().state());
        verify(operations).complete(argThat(value->value.operationId().equals(request.getOperationId()) && value.portalBaseRevision()==6
                && value.effect()==effect && "nominal".equals(value.representedUser())));
    }
    @Test void immediateLookupRecoversAnAcceptedCompletionWhoseResponseWasLost() {
        prepareAcceptance();
        when(operations.find(request.getOperationId())).thenReturn(Optional.empty(),Optional.of(receipt));
        when(operations.complete(any())).thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE));
        assertEquals("READY",component.complete(request,"actor",null).orElseThrow().state());
        verify(operations,times(1)).complete(any()); verify(sync).apply(receipt); verify(operations).confirm(receipt);
    }
    @Test void aConcurrentRetryUsesTheOriginalReceiptWhenItsNewServerAuditPreparationDiffers() {
        var effect=prepareAcceptance();
        var changed=new AssignmentCompletionRequest.Effect(effect.instanceId(),effect.variablePatch(),effect.stageId(),effect.clientRequestDigest(),
                List.of(new AssignmentCompletionRequest.AuditChange("caption","before","accepted",AssignmentCompletionRequest.AuditKind.FIELD_UPDATE,
                        null,null,Instant.now(),null,null)),effect.observation());
        when(effects.build(any(),any())).thenReturn(changed);
        when(operations.find(request.getOperationId())).thenReturn(Optional.empty(),Optional.of(receipt));
        when(operations.complete(any())).thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT));
        assertEquals("READY",component.complete(request,"actor",null).orElseThrow().state());
        verify(sync).apply(receipt); verify(operations,times(1)).complete(any());
    }
    @Test void completionRacingBetweenInitialLookupAndQualificationResumesWithoutReauthorizingTheFinishedAction() {
        when(operations.find(request.getOperationId())).thenReturn(Optional.empty(),Optional.of(receipt));
        when(guard.prepare(any())).thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED));
        assertEquals("READY",component.complete(request,"actor",null).orElseThrow().state());
        verify(sync).apply(receipt); verifyNoInteractions(effects); verify(operations,never()).complete(any());
    }
    @Test void aRacedReceiptForADifferentClientPayloadStillCannotBeApplied() {
        request.setFormData(Map.of("field","different payload"));
        prepareAcceptance();
        when(operations.find(request.getOperationId())).thenReturn(Optional.empty(),Optional.of(receipt));
        when(operations.complete(any())).thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT));
        assertThrows(AssignmentFailure.class,()->component.complete(request,"actor",null));
        verifyNoInteractions(sync); verify(operations,never()).confirm(any());
    }
    @Test void realEngineTaskEnvelopeIsUnwrappedWithoutHydratingOrWritingPortalRows() {
        prepareAcceptance();
        when(tasks.getTaskById("task")).thenReturn(Optional.of(Map.of("data",Map.of("taskId","task","processInstanceId","instance","currentAssignee","nominal"))));
        when(operations.complete(any())).thenReturn(receipt);
        assertEquals("READY",component.complete(request,"actor",null).orElseThrow().state());
        verify(effects).prepare(argThat(task->task.getTaskId().equals("task") && task.getAssignee().equals("nominal")),eq(request),eq("actor"),isNull());
    }
    @Test void absenceOfEngineSupportOrOfAStableOperationIdNeverFallsThroughToLegacy() {
        when(guard.prepare(any())).thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_ENGINE_UNSUPPORTED));
        assertThrows(AssignmentFailure.class,()->component.complete(request,"actor",null));
        doReturn(new AssignmentWriteDecision(1,true,"task","actor",null,"COMPLETE","a".repeat(64),UUID.randomUUID(),4,6,7,Instant.now().plusSeconds(20),"instance")).when(guard).prepare(any());
        request.setOperationId(null);
        assertThrows(AssignmentFailure.class,()->component.complete(request,"actor",null));
        verifyNoInteractions(effects,sync); verify(operations,never()).complete(any());
    }
    @Test void publicStatusExposesOnlyTheOriginalActorsMinimalCompletionState() {
        when(operations.find(request.getOperationId())).thenReturn(Optional.of(receipt("APPLIED")));
        var status=component.status(request.getOperationId(),"actor");
        assertEquals("READY",status.state()); assertEquals("task",status.taskId());
        var body=JsonUtils.toJson(status);
        assertFalse(body.contains("acceptedEffect")); assertFalse(body.contains("nominal")); assertFalse(body.contains("clientRequestDigest"));
        assertThrows(AssignmentFailure.class,()->component.status(request.getOperationId(),"another"));
        verifyNoInteractions(tasks,guard,effects,sync); verify(operations,never()).complete(any());
    }
    @Test void anUnknownOperationIsExplicitlyUnacceptedWithoutDisclosingAnotherActorsData() {
        var status=component.status(request.getOperationId(),"actor");
        assertEquals("NOT_ACCEPTED",status.state()); assertNull(status.taskId());
        verifyNoInteractions(tasks,guard,effects,sync);
    }
    private AssignmentCompletionRequest.Effect prepareAcceptance() {
        when(guard.prepare(any())).thenReturn(new AssignmentWriteDecision(1,true,"task","actor",null,"COMPLETE","a".repeat(64),UUID.randomUUID(),4,6,7,Instant.now().plusSeconds(20),"instance"));
        var prepared=new TaskApprovalPreparation(null,request,"actor",Map.of(),Map.of("action","APPROVE"),Map.of(),false,null,List.of(),"frozen","nominal");
        when(effects.prepare(any(),any(),any(),any())).thenReturn(prepared);
        var effect=new AssignmentCompletionRequest.Effect("instance",List.of(),"node",AssignmentCompletionOrchestrator.clientDigest(request),List.of(),Map.of());
        when(effects.build(any(),any())).thenReturn(effect);
        return effect;
    }
    private AssignmentOperationReceipt receipt(String state) {
        var effect=new AssignmentCompletionRequest.Effect("instance",List.of(),"node",AssignmentCompletionOrchestrator.clientDigest(request),List.of(),Map.of());
        Map<String,Object> stored=JsonUtils.getObjectMapper().convertValue(effect,new com.fasterxml.jackson.core.type.TypeReference<>() { });
        return new AssignmentOperationReceipt(request.getOperationId(),UUID.randomUUID(),"task","actor","nominal","APPROVE","a".repeat(64),1,6,stored,state);
    }
}
