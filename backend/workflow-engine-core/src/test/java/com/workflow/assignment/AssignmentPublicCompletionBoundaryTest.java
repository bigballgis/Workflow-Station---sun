package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.workflow.component.TaskCompletionService;
import com.workflow.component.TaskManagerComponent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssignmentPublicCompletionBoundaryTest {
    @Test void aLegacyTaskStillUsesItsExistingCompletionService() {
        var states=mock(AssignmentTaskStateRepository.class);
        when(states.find("legacy-task")).thenReturn(Optional.empty());
        var original=mock(TaskCompletionService.class); var facade=new TaskManagerComponent();
        var boundary=new AssignmentWriteBoundary(states);
        ReflectionTestUtils.setField(boundary,"legacyCheck",mock(AssignmentLegacyTaskCheck.class));
        ReflectionTestUtils.setField(facade,"assignmentBoundary",boundary);
        ReflectionTestUtils.setField(facade,"taskCompletionService",original);
        facade.completeTask("legacy-task","actor",Map.of("action","APPROVE"));
        verify(original).completeTask("legacy-task","actor",Map.of("action","APPROVE"),true,null);
    }
    @Test void aProtectedTaskCannotUseTheLegacyFacadeToBypassTheDurableReceiptAndPortalEffect() {
        var states=mock(AssignmentTaskStateRepository.class);
        when(states.find("task")).thenReturn(Optional.of(mock(AssignmentTaskStateRepository.State.class)));
        var original=mock(TaskCompletionService.class); var writes=mock(AssignmentWriteExecutor.class);
        when(writes.execute(any(),any())).thenAnswer(call->call.<Supplier<?>>getArgument(1).get());
        var facade=new TaskManagerComponent();
        ReflectionTestUtils.setField(facade,"assignmentBoundary",new AssignmentWriteBoundary(states));
        ReflectionTestUtils.setField(facade,"assignmentWrites",writes); ReflectionTestUtils.setField(facade,"taskCompletionService",original);
        assertThrows(AssignmentFailure.class,()->facade.completeTask("task","actor",Map.of("action","APPROVE")));
        verifyNoInteractions(original);
    }
}
