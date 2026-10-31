package com.portal.component;

import com.portal.dto.TaskCompleteRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentCompletionEndpointTest {
    @ParameterizedTest @ValueSource(strings={"READY","PENDING_SYNC","SYNC_CONFLICT"})
    void acceptedCompletionReturnsItsDurableStateWithoutRepeatingLegacyCompletionOrEmail(String state) {
        var protectedCompletion=mock(AssignmentCompletionOrchestrator.class); var observation=mock(AssignmentAcceptedObservation.class);
        var legacy=mock(TaskProcessComponent.class); var emails=mock(ActionPostEmailComponent.class); var history=mock(SubTableBatchAssignmentHistory.class);
        var endpoint=new TaskCompletionEndpoint(protectedCompletion,observation,legacy,emails,history);
        var operation=UUID.randomUUID(); var accepted=new AssignmentCompletionOrchestrator.Result(operation,state,null);
        var request=TaskCompleteRequest.builder().taskId("task").operationId(operation).action("APPROVE").build();
        when(protectedCompletion.complete(request,"actor",null)).thenReturn(Optional.of(accepted));
        var result=endpoint.complete(request,"actor",null);
        assertEquals(operation,result.operationId()); assertEquals(state,result.state());
        verifyNoInteractions(legacy,emails,history); verify(observation).observe(accepted,"actor","task");
    }
}
