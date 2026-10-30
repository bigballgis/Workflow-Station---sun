package com.portal.component;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentOperationReceipt;
import com.portal.client.AssignmentOperationClient;
import com.portal.dto.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BatchCompletionRecoveryTest {
    private final UUID operation=UUID.randomUUID();
    private final AssignmentOperationClient operations=mock(AssignmentOperationClient.class);
    private final TaskCompletionEndpoint endpoint=mock(TaskCompletionEndpoint.class);
    private final BatchCompletionRecovery recovery=new BatchCompletionRecovery(operations,endpoint);
    private final BatchCompleteRequest batch=new BatchCompleteRequest(List.of("closed"),"original-action","  original comment  ",Map.of("closed",operation));
    private final TaskCompletionResult accepted=new TaskCompletionResult(null,operation,"PENDING_SYNC");
    private AssignmentOperationReceipt receipt() {
        return new AssignmentOperationReceipt(operation,UUID.randomUUID(),"closed","actor",null,"REJECT","digest",1,0,Map.of(),"PENDING_SYNC");
    }
    @Test void acceptedRetryDoesNotReadClosedTaskOrRebindActions() {
        when(operations.find(operation)).thenReturn(Optional.of(receipt()));
        when(endpoint.complete(any(),eq("actor"),eq("user"))).thenReturn(accepted);
        Supplier<TaskCompletionResult> live=()->{throw new AssertionError("Must not read a closed task");};
        assertThat(recovery.run("closed",batch,"actor","user",live)).isSameAs(accepted);
        var request=ArgumentCaptor.forClass(TaskCompleteRequest.class);
        verify(endpoint).complete(request.capture(),eq("actor"),eq("user"));
        assertThat(request.getValue().getOperationId()).isEqualTo(operation);
        assertThat(request.getValue().getAction()).isEqualTo("REJECT");
        assertThat(request.getValue().getActionId()).isEqualTo("original-action");
        assertThat(request.getValue().getComment()).isEqualTo("original comment");
        assertThat(request.getValue().getVariables()).isNull();
    }
    @Test void receiptCommittedDuringLiveTaskReadIsReplayed() {
        when(operations.find(operation)).thenReturn(Optional.empty(),Optional.of(receipt()));
        when(endpoint.complete(any(),eq("actor"),eq("user"))).thenReturn(accepted);
        assertThat(recovery.run("closed",batch,"actor","user",()->{
            throw new com.portal.exception.PortalException("404","Task not found");
        })).isSameAs(accepted);
    }
    @Test void aChangedPayloadOrDifferentActorStillPassesThroughReceiptValidation() {
        when(operations.find(operation)).thenReturn(Optional.of(receipt()));
        when(endpoint.complete(any(),eq("other"),eq("user"))).thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT));
        assertThatThrownBy(()->recovery.run("closed",batch,"other","user",()->accepted)).isInstanceOf(AssignmentFailure.class);
    }
    @Test void missingReceiptRunsLiveValidationAndKeepsOriginalFailure() {
        when(operations.find(operation)).thenReturn(Optional.empty());
        var failure=new com.portal.exception.PortalException("403","Denied");
        assertThatThrownBy(()->recovery.run("closed",batch,"actor","user",()->{throw failure;})).isSameAs(failure);
        verifyNoInteractions(endpoint);
    }
    @Test void lookupOutageNeverInvokesLegacyOrLiveCompletion() {
        when(operations.find(operation)).thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE));
        assertThatThrownBy(()->recovery.run("closed",batch,"actor","user",()->{
            throw new AssertionError("Dependency absence cannot authorize completion");
        })).isInstanceOf(AssignmentFailure.class);
        verifyNoInteractions(endpoint);
    }
    @Test void legacyClientStillUsesTheSharedEndpointWithoutInventingOperationIds() {
        var legacy=new BatchCompleteRequest(List.of("legacy"),"approve",null);
        when(endpoint.complete(any(),eq("actor"),eq("user"))).thenReturn(null);
        assertThat(recovery.run("legacy",legacy,"actor","user",
                ()->recovery.complete("legacy",legacy,"APPROVE","actor","user"))).isNull();
        verifyNoInteractions(operations);
        verify(endpoint).complete(argThat(request->request.getOperationId()==null && "APPROVE".equals(request.getAction())),eq("actor"),eq("user"));
    }
    @Test void operationCannotReferToAnUnselectedTask() {
        assertThatThrownBy(()->new BatchCompleteRequest(List.of("closed"),"action",null,Map.of("other",operation)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void malformedReceiptCannotRedirectAReplayIntoALegacyTransfer() {
        var invalid=new AssignmentOperationReceipt(operation,UUID.randomUUID(),"closed","actor",null,"TRANSFER","digest",1,0,Map.of(),"PENDING_SYNC");
        when(operations.find(operation)).thenReturn(Optional.of(invalid));
        assertThatThrownBy(()->recovery.run("closed",batch,"actor","user",()->accepted)).isInstanceOf(AssignmentFailure.class);
        verifyNoInteractions(endpoint);
    }
}
