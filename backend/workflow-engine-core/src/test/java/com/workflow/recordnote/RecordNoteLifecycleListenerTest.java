package com.workflow.recordnote;

import com.platform.common.recordnote.RecordNoteTaskTarget;
import org.flowable.common.engine.api.delegate.event.*;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;
import java.util.Optional;
import static org.mockito.Mockito.*;

class RecordNoteLifecycleListenerTest {
    final RecordNoteTaskContext context=mock(RecordNoteTaskContext.class);
    final RecordNoteRoundRepository rounds=mock(RecordNoteRoundRepository.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final RecordNoteLifecycleListener listener=new RecordNoteLifecycleListener(context,rounds,jdbc);
    final Task task=mock(Task.class);

    @BeforeEach void setup() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(context.enabled("definition","request")).thenReturn(true);
        when(task.getId()).thenReturn("task");
        when(task.getProcessInstanceId()).thenReturn("request");
        when(task.getProcessDefinitionId()).thenReturn("definition");
        when(task.getExecutionId()).thenReturn("execution");
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clear(); }
    RecordNoteTaskTarget target(String mode,String user) {
        return new RecordNoteTaskTarget("task","node","mi","unusual_table",Map.of("custom_pk",42),
                "activation-2",mode,"owner_xyz",user,"ACTIVE",Map.of());
    }
    FlowableEntityEvent event(FlowableEngineEventType type) {
        var event=mock(FlowableEntityEvent.class,withSettings().extraInterfaces(FlowableEngineEvent.class));
        when(event.getType()).thenReturn(type); when(event.getEntity()).thenReturn(task);
        var engine=(FlowableEngineEvent)event;
        when(engine.getProcessInstanceId()).thenReturn("request");
        when(engine.getProcessDefinitionId()).thenReturn("definition");
        return event;
    }
    @Test void createdStoresTheActualAutomaticallyAssignedSoleTeamMember() {
        var target=target("TEAM","only-member"); when(context.capture(task)).thenReturn(target);
        listener.onEvent(event(FlowableEngineEventType.TASK_CREATED));
        verify(rounds).created("request","definition","execution",target);
    }
    @Test void firstClaimAndUnclaimUpdateOnlyTheCurrentTaskPointer() {
        when(rounds.task("task")).thenReturn(Optional.of(target("TEAM",null)));
        when(task.getAssignee()).thenReturn("claimed-handler",null);
        listener.onEvent(event(FlowableEngineEventType.TASK_ASSIGNED));
        listener.onEvent(event(FlowableEngineEventType.TASK_ASSIGNED));
        verify(rounds).changed("task","claimed-handler","ACTIVE");
        verify(rounds).changed("task",null,"WAITING_CLAIM");
    }
    @Test void personCompletionSnapshotsTheConfiguredFieldInsteadOfTaskAssignee() {
        var target=target("PERSON","previous-person");
        when(rounds.task("task")).thenReturn(Optional.of(target));
        when(context.personAtCompletion(task,target)).thenReturn("current-person");
        listener.onEvent(event(FlowableEngineEventType.TASK_COMPLETED));
        verify(rounds).changed("task","current-person","COMPLETED");
        verify(task,never()).getAssignee();
    }
    @Test void missingCompletedIdentityRemainsExplicitlyUnresolved() {
        when(rounds.task("task")).thenReturn(Optional.of(target("TEAM",null)));
        listener.onEvent(event(FlowableEngineEventType.TASK_COMPLETED));
        verify(rounds).changed("task",null,"UNRESOLVED");
    }
    @Test void oldTasksWithoutATrustedPointerDoNotAcquireAGuessedSnapshot() {
        when(rounds.task("task")).thenReturn(Optional.empty());
        listener.onEvent(event(FlowableEngineEventType.TASK_COMPLETED));
        verify(rounds,never()).changed(anyString(),any(),anyString());
    }
    @Test void freshMiActivationInvalidatesPreviousCompletedRound() {
        var event=mock(FlowableActivityEvent.class);
        when(event.getType()).thenReturn(FlowableEngineEventType.MULTI_INSTANCE_ACTIVITY_STARTED);
        when(event.getProcessInstanceId()).thenReturn("request");
        when(event.getProcessDefinitionId()).thenReturn("definition");
        when(event.getActivityId()).thenReturn("mi");
        when(event.getExecutionId()).thenReturn("activation-2");
        when(context.activationForScope("activation-2","mi")).thenReturn(Map.of("mi:request","activation-2"));
        listener.onEvent(event);
        verify(rounds).activate("request",Map.of("mi:request","activation-2"));
    }
    @Test void disabledCatalogDoesNotAddLifecycleQueriesOrChangeTaskAssignment() {
        when(context.enabled("definition","request")).thenReturn(false);
        listener.onEvent(event(FlowableEngineEventType.TASK_CREATED));
        verifyNoInteractions(rounds,jdbc);
    }
}
