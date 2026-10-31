package com.workflow.assignment;

import com.platform.messaging.event.ActionEmailRequestedEvent;
import com.workflow.email.action.ActionEmailVariableLoader;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.HistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssignmentPostActionVariableLoaderTest {
    @Test void protectedMailUsesFrozenValuesEvenIfTheProcessHasMovedOrEnded() {
        var runtime=mock(RuntimeService.class); var history=mock(HistoryService.class);
        var snapshots=mock(AssignmentPostActionSnapshotClient.class);
        var loader=new ActionEmailVariableLoader(runtime,history); ReflectionTestUtils.setField(loader,"assignmentSnapshots",snapshots);
        var operation=UUID.randomUUID();
        var event=ActionEmailRequestedEvent.builder().assignmentOperationId(operation).assignmentRuleIndex(2).eventId("event")
                .processInstanceId("instance").operatorId("actor").actionName("Original action").comment("original comment").build();
        when(snapshots.read(any())).thenReturn(Map.of("recipient","original-person"));
        var values=loader.load(event);
        assertEquals("original-person",values.get("recipient")); assertEquals("original comment",values.get("actionComment"));
        verifyNoInteractions(runtime,history);
        verify(snapshots).read(new com.platform.common.assignment.AssignmentPostAction.SnapshotRequest(operation,2,"event","instance","actor"));
    }
    @Test void aDamagedProtectedContextCannotFallBackToLaterRuntimeValues() {
        var runtime=mock(RuntimeService.class); var history=mock(HistoryService.class);
        var loader=new ActionEmailVariableLoader(runtime,history);
        assertThrows(IllegalArgumentException.class,()->loader.load(ActionEmailRequestedEvent.builder().assignmentOperationId(UUID.randomUUID()).build()));
        verifyNoInteractions(runtime,history);
    }
}
