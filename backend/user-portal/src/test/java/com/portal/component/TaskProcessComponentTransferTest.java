package com.portal.component;

import com.platform.common.i18n.I18nService;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskDelegateRequest;
import com.portal.dto.TaskInfo;
import com.portal.exception.PortalException;
import com.portal.repository.DelegationAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Platform access model D-60 in Portal: a task someone holds is transferred and delegated by its holder only, as the
 * engine requires, through both entries (the action endpoint and completing with a TRANSFER / DELEGATE action). A
 * delegate (single-task or standing) who may otherwise process it gets a business error (403), not the engine's
 * refusal as a 500.
 */
class TaskProcessComponentTransferTest {

    private final TaskQueryComponent taskQueryComponent = mock(TaskQueryComponent.class);
    private final WorkflowEngineClient workflowEngineClient = mock(WorkflowEngineClient.class);
    private final TaskPermissionEvaluator taskPermissionEvaluator = mock(TaskPermissionEvaluator.class);
    private final I18nService i18nService = mock(I18nService.class);
    private final TaskAssignmentHistoryRecorder historyRecorder = mock(TaskAssignmentHistoryRecorder.class);
    private final TaskProcessComponent component = new TaskProcessComponent(
            taskQueryComponent, mock(DelegationAuditRepository.class), workflowEngineClient, taskPermissionEvaluator,
            mock(SubTableRowAssignmentComponent.class), mock(TaskApprovalCompletionComponent.class),
            mock(ProcessInstanceSyncComponent.class), mock(MiOverlayComponent.class),
            mock(ClaimForceUnclaimAnnotator.class), historyRecorder,
            new TaskDelegationGuard(i18nService));

    @BeforeEach
    void setUp() {
        when(workflowEngineClient.isAvailable()).thenReturn(true);
        when(workflowEngineClient.transferTask(any(), any(), any(), any())).thenReturn(Optional.of(Map.of("success", true)));
        when(workflowEngineClient.delegateTask(any(), any())).thenReturn(Optional.of(Map.of("success", true)));
        when(taskPermissionEvaluator.canProcessTask(any(TaskInfo.class), any(), any())).thenReturn(true);
        when(i18nService.getMessage("portal.task_transfer_holder_only")).thenReturn("Only the holder can transfer it");
        when(i18nService.getMessage("portal.task_delegate_holder_only")).thenReturn("Only the holder can delegate it");
    }

    @Test
    void aDelegateWhoMayProcessTheTaskCannotTransferIt() {
        held("holder");

        assertThatThrownBy(() -> component.transferTask("t1", "delegate", "someone", "why", null))
                .isInstanceOfSatisfying(PortalException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("403");
                    assertThat(e.getMessage()).isEqualTo("Only the holder can transfer it");
                });
        verify(workflowEngineClient, never()).transferTask(any(), any(), any(), any());
    }

    @Test
    void theHolderTransfersAsBefore() {
        held("holder");

        assertThatCode(() -> component.transferTask("t1", "holder", "someone", "why", null)).doesNotThrowAnyException();

        verify(workflowEngineClient).transferTask("t1", "holder", "someone", "why");
    }

    /** The engine ends the holder's single-task delegation with the transfer; the change history is told (D-59). */
    @Test
    void aTransferHandsTheTaskBeforeAndAfterToTheChangeHistory() {
        TaskInfo before = TaskInfo.builder().taskId("t1").processInstanceId("pi-1").taskName("Confirm")
                .assignee("holder").delegatedTargetType("USER").delegatedTo("deputy").delegable(true).build();
        TaskInfo after = TaskInfo.builder().taskId("t1").processInstanceId("pi-1").taskName("Confirm")
                .assignee("someone").build();
        when(taskQueryComponent.getTaskById("t1")).thenReturn(Optional.of(before), Optional.of(after));

        component.transferTask("t1", "holder", "someone", "why", null);

        verify(historyRecorder).recordDelegationEnded(before, after, "holder");
    }

    @Test
    void aRefusedTransferTellsTheChangeHistoryNothing() {
        held("holder");
        when(workflowEngineClient.transferTask(any(), any(), any(), any()))
                .thenReturn(Optional.of(Map.of("success", false, "message", "no")));

        assertThatThrownBy(() -> component.transferTask("t1", "holder", "someone", "why", null))
                .isInstanceOf(PortalException.class);
        verify(historyRecorder, never()).recordDelegationEnded(any(), any(), any());
    }

    @Test
    void aTaskNobodyHoldsKeepsItsRules() {
        held(null);

        assertThatCode(() -> component.transferTask("t1", "member", "someone", "why", null)).doesNotThrowAnyException();

        verify(workflowEngineClient).transferTask("t1", "member", "someone", "why");
    }

    @Test
    void completingWithATransferActionIsGatedTheSameWay() {
        held("holder");

        assertThatThrownBy(() -> component.completeTask(complete("TRANSFER"), "delegate", null))
                .isInstanceOfSatisfying(PortalException.class, e -> assertThat(e.getCode()).isEqualTo("403"));
        verify(workflowEngineClient, never()).transferTask(any(), any(), any(), any());

        assertThatCode(() -> component.completeTask(complete("TRANSFER"), "holder", null)).doesNotThrowAnyException();
        verify(workflowEngineClient).transferTask("t1", "holder", "someone", "why");
    }

    @Test
    void aDelegateCannotDelegateTheTaskOnward() {
        held("holder");

        assertThatThrownBy(() -> component.delegateTask("t1", "delegate", delegateBody(), null))
                .isInstanceOfSatisfying(PortalException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("403");
                    assertThat(e.getMessage()).isEqualTo("Only the holder can delegate it");
                });
        verify(workflowEngineClient, never()).delegateTask(any(), any());
    }

    @Test
    void theHolderDelegatesAsBefore() {
        held("holder");

        assertThatCode(() -> component.delegateTask("t1", "holder", delegateBody(), null)).doesNotThrowAnyException();

        verify(workflowEngineClient).delegateTask(eq("t1"), any());
    }

    @Test
    void completingWithADelegateActionIsGatedTheSameWay() {
        held("holder");

        assertThatThrownBy(() -> component.completeTask(complete("DELEGATE"), "delegate", null))
                .isInstanceOfSatisfying(PortalException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("403");
                    assertThat(e.getMessage()).isEqualTo("Only the holder can delegate it");
                });
        verify(workflowEngineClient, never()).delegateTask(any(), any());

        assertThatCode(() -> component.completeTask(complete("DELEGATE"), "holder", null)).doesNotThrowAnyException();
        verify(workflowEngineClient).delegateTask(eq("t1"), any());
    }

    private static TaskCompleteRequest complete(String action) {
        TaskCompleteRequest request = new TaskCompleteRequest();
        request.setTaskId("t1");
        request.setAction(action);
        request.setTargetUserId("someone");
        request.setComment("why");
        return request;
    }

    private static TaskDelegateRequest delegateBody() {
        return TaskDelegateRequest.builder().delegatedTargetType("USER").delegatedTo("someone").reason("why").build();
    }

    private void held(String assignee) {
        TaskInfo task = TaskInfo.builder().taskId("t1").processInstanceId("pi-1").taskName("Confirm").assignee(assignee)
                .delegable(true).build();
        when(taskQueryComponent.getTaskById("t1")).thenReturn(Optional.of(task));
    }
}
