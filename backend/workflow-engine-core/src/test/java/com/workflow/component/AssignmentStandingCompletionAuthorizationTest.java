package com.workflow.component;

import com.platform.common.assignment.AssignmentFailure;
import com.workflow.assignment.AssignmentMutationScope;
import com.workflow.assignment.AssignmentTaskStateRepository;
import com.workflow.assignment.AssignmentWriteBoundary;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentStandingCompletionAuthorizationTest {
    final TaskCompletionService completion=new TaskCompletionService();
    final AssignmentTaskStateRepository states=mock(AssignmentTaskStateRepository.class);
    final Task task=mock(Task.class);
    @BeforeEach void prepare() {
        ReflectionTestUtils.setField(completion,"assignmentBoundary",new AssignmentWriteBoundary(states));
        ReflectionTestUtils.setField(completion,"taskActionService",mock(TaskActionService.class));
        ReflectionTestUtils.setField(completion,"taskService",mock(org.flowable.engine.TaskService.class));
        when(task.getId()).thenReturn("task"); when(task.getAssignee()).thenReturn("nominal");
        when(states.find("task")).thenReturn(Optional.of(mock(AssignmentTaskStateRepository.State.class)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
    @AfterEach void cleanup() {
        for (var sync:TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    @Test void serverVerifiedStandingDelegateReachesExistingCompletionWithoutSingleTaskDelegateMetadata() {
        authorize("actual",Set.of("actual","nominal"));
        assertTrue(completion.isCompleteAuthorized(task,null,"actual",null));
    }
    @Test void clientRepresentedHolderAloneNeverGrantsProtectedCompletion() {
        assertThrows(AssignmentFailure.class,()->completion.isCompleteAuthorized(task,null,"actual","nominal"));
    }
    @Test void anotherActorCannotReuseThePreparedServerScope() {
        authorize("other",Set.of("other","nominal"));
        assertThrows(AssignmentFailure.class,()->completion.isCompleteAuthorized(task,null,"actual","nominal"));
    }
    @Test void aVerifiedScopeForAnotherNominalHolderCannotCompleteThisTask() {
        authorize("actual",Set.of("actual","another-holder"));
        assertThrows(AssignmentFailure.class,()->completion.isCompleteAuthorized(task,null,"actual",null));
    }
    private void authorize(String actor,Set<String> responsible) {
        AssignmentMutationScope.establish(new AssignmentMutationScope.Authorized("task",AssignmentMutationScope.Operation.COMPLETE,
                actor,responsible,Set.of("nominal")));
    }
}
