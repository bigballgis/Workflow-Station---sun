package com.workflow.component;

import com.platform.common.i18n.I18nService;
import com.platform.messaging.support.NotificationDispatchHelper;
import com.workflow.dto.request.TaskDelegationRequest;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.service.UserPermissionService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Platform access model D-60: a task someone holds is handed on (transfer) or delegated by its holder only; candidates
 * and pool members have no say over it. A task nobody holds keeps its rules.
 */
class TaskActionServiceHeldTaskTest {

    private final TaskService taskService = mock(TaskService.class);
    private final ExtendedTaskInfoRepository repository = mock(ExtendedTaskInfoRepository.class);
    private final UserPermissionService userPermissionService = mock(UserPermissionService.class);
    private final TaskClaimSupport taskClaimSupport = mock(TaskClaimSupport.class);
    private final TaskActionService service = new TaskActionService();
    private final TaskQuery query = mock(TaskQuery.class, RETURNS_SELF);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "taskService", taskService);
        ReflectionTestUtils.setField(service, "extendedTaskInfoRepository", repository);
        ReflectionTestUtils.setField(service, "userPermissionService", userPermissionService);
        ReflectionTestUtils.setField(service, "taskClaimSupport", taskClaimSupport);
        ReflectionTestUtils.setField(service, "notificationDispatchHelper", mock(NotificationDispatchHelper.class));
        ReflectionTestUtils.setField(service, "i18nService",
                mock(I18nService.class, i -> i.getMethod().getReturnType() == String.class ? "text" : null));
        when(taskService.createTaskQuery()).thenReturn(query);
        when(taskClaimSupport.engineActorMatchesPortalUser(anyString(), anyString()))
                .thenAnswer(i -> i.getArgument(0, String.class).trim().equals(i.getArgument(1, String.class).trim()));
        when(repository.findByTaskIdAndIsDeletedFalse("t1")).thenReturn(Optional.empty());
        when(repository.save(any(ExtendedTaskInfo.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void aCandidateCannotTransferATaskSomeoneElseHolds() {
        held("h");
        when(userPermissionService.hasTaskPermission(anyString(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.transferTask("t1", "c", "x", "why"))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("only its holder can transfer");
        verify(taskService, never()).setAssignee(anyString(), anyString());
    }

    @Test
    void theHolderTransfersTheTaskTheyHold() {
        held("h");

        assertThat(service.transferTask("t1", "h", "x", "why").isSuccess()).isTrue();

        verify(taskService).setAssignee("t1", "x");
    }

    @Test
    void aTaskNobodyHoldsIsTransferredUnderItsExtendedRowAsBefore() {
        held(null);
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").assignmentType(AssignmentType.CANDIDATE_USERS)
                .assignmentTarget("c").status("ASSIGNED").build();
        when(repository.findByTaskIdAndIsDeletedFalse("t1")).thenReturn(Optional.of(row));
        when(userPermissionService.hasTaskPermission("c", AssignmentType.CANDIDATE_USERS, "c")).thenReturn(true);

        assertThat(service.transferTask("t1", "c", "x", "why").isSuccess()).isTrue();

        verify(taskService).setAssignee("t1", "x");
    }

    @Test
    void aCandidateCannotDelegateATaskSomeoneElseHolds() {
        held("h");
        ExtendedTaskInfo row = poolRow("h");
        when(userPermissionService.hasTaskPermission(anyString(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.delegateTask("t1", delegation("c")))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("only its holder can delegate");
        assertThat(row.isDelegated()).isFalse();
    }

    @Test
    void theHolderDelegatesEvenWhenTheRowStillNamesWhomItWasFirstAssignedTo() {
        // D-42: a manager handed a leaver's task holds it while the row still names the leaver.
        held("m");
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").assignmentType(AssignmentType.USER)
                .assignmentTarget("left").claimedBy("m").status("CLAIMED").build();
        when(repository.findByTaskIdAndIsDeletedFalse("t1")).thenReturn(Optional.of(row));

        assertThat(service.delegateTask("t1", delegation("m")).isSuccess()).isTrue();

        assertThat(row.getDelegatedTo()).isEqualTo("d");
        assertThat(row.getDelegatedBy()).isEqualTo("m");
    }

    private ExtendedTaskInfo poolRow(String holder) {
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").assignmentType(AssignmentType.CANDIDATE_USERS)
                .assignmentTarget("c," + holder).claimedBy(holder).status("CLAIMED").build();
        when(repository.findByTaskIdAndIsDeletedFalse("t1")).thenReturn(Optional.of(row));
        return row;
    }

    private void held(String assignee) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        when(task.getName()).thenReturn("Confirm");
        when(task.getAssignee()).thenReturn(assignee);
        when(query.singleResult()).thenReturn(task);
    }

    private static TaskDelegationRequest delegation(String by) {
        return TaskDelegationRequest.builder().taskId("t1").delegatedTo("d").delegatedBy(by)
                .delegationReason("leave").sendNotification(false).build();
    }
}
