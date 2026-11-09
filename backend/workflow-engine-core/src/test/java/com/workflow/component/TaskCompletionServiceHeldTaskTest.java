package com.workflow.component;

import com.workflow.client.AdminCenterClient;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.service.UserPermissionService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Platform access model D-60: a task someone holds is completed by its holder or by a delegate in effect acting for
 * that holder; candidates, candidate groups and the extended row's assignment no longer count. A task nobody holds
 * keeps its rules.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskCompletionServiceHeldTaskTest {

    private static final Map<String, Object> VARIABLES = Map.of("approved", true);

    @Mock
    private TaskService taskService;
    @Mock
    private RuntimeService runtimeService;
    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    @Mock
    private UserPermissionService userPermissionService;
    @Mock
    private AdminCenterClient adminCenterClient;
    @Mock
    private TaskActionService taskActionService;
    @Mock
    private SingleTaskDelegateCheck singleTaskDelegateCheck;
    @Mock
    private TaskMultiInstanceService taskMultiInstanceService;
    @Mock
    private BpmnActionParser bpmnActionParser;

    @InjectMocks
    private TaskCompletionService service;

    private TaskQuery query;

    @BeforeEach
    void setUp() {
        query = mock(TaskQuery.class, RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(taskActionService.engineActorMatchesPortalUser(anyString(), anyString()))
                .thenAnswer(i -> i.getArgument(0, String.class).trim().equals(i.getArgument(1, String.class).trim()));
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse("t1")).thenReturn(Optional.empty());
        when(runtimeService.createProcessInstanceQuery()).thenReturn(mock(ProcessInstanceQuery.class, RETURNS_SELF));
    }

    @Test
    void theHolderCompletesTheTaskTheyHold() {
        Task held = task("h");
        when(query.singleResult()).thenReturn(held);
        candidates("h", "c");

        assertThat(service.completeTask("t1", "h", vars(), false, null).isSuccess()).isTrue();

        verify(taskService).complete("t1", VARIABLES);
    }

    @Test
    void aCandidateCannotCompleteATaskSomeoneElseHolds() {
        Task held = task("h");
        when(query.singleResult()).thenReturn(held);
        candidates("h", "c");

        assertThatThrownBy(() -> service.completeTask("t1", "c", vars(), false, null))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("held by another user");
        verifyNotCompleted();
    }

    @Test
    void theExtendedRowNoLongerOpensAHeldTaskToItsPool() {
        Task held = task("h");
        when(query.singleResult()).thenReturn(held);
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").assignmentType(AssignmentType.CANDIDATE_USERS)
                .assignmentTarget("c,h").status("ASSIGNED").build();
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse("t1")).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.completeTask("t1", "c", vars(), false, null))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("only its holder or a delegate in effect");
        verify(taskActionService, never()).validateCompletePermission(any(), anyString());
        verifyNotCompleted();
    }

    @Test
    void aDelegateInEffectCompletesForTheHolder() {
        Task held = task("h");
        when(query.singleResult()).thenReturn(held);
        ExtendedTaskInfo row = delegatedRow();
        when(singleTaskDelegateCheck.mayActFor(row, "d", "h")).thenReturn(true);

        assertThat(service.completeTask("t1", "d", vars(), false, "h").isSuccess()).isTrue();

        verify(taskService).complete("t1", VARIABLES);
    }

    @Test
    void aDelegateNotInEffectIsRefused() {
        Task held = task("h");
        when(query.singleResult()).thenReturn(held);
        ExtendedTaskInfo row = delegatedRow();
        when(singleTaskDelegateCheck.mayActFor(row, "d", "h")).thenReturn(false);

        assertThatThrownBy(() -> service.completeTask("t1", "d", vars(), false, "h"))
                .isInstanceOf(WorkflowValidationException.class);
        verifyNotCompleted();
    }

    @Test
    void actingForSomeoneOtherThanTheHolderIsRefused() {
        Task held = task("h");
        when(query.singleResult()).thenReturn(held);
        delegatedRow();

        assertThatThrownBy(() -> service.completeTask("t1", "d", vars(), false, "x"))
                .isInstanceOf(WorkflowValidationException.class);
        verify(singleTaskDelegateCheck, never()).mayActFor(any(), anyString(), anyString());
        verifyNotCompleted();
    }

    @Test
    void aMultiInstanceSubTaskIsWrittenBackOnlyWhenItsHolderCompletesIt() {
        Task held = task("h");
        when(query.singleResult()).thenReturn(held);
        candidates("h", "c");
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").assignmentType(AssignmentType.USER)
                .assignmentTarget("h").status("ASSIGNED").build();
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse("t1")).thenReturn(Optional.of(row));
        when(taskMultiInstanceService.isMultiInstanceSubTask(row)).thenReturn(true);

        assertThatThrownBy(() -> service.completeTask("t1", "c", vars(), false, null))
                .isInstanceOf(WorkflowValidationException.class);
        verify(taskMultiInstanceService, never()).handleMultiInstanceSubTaskCompletion(anyString(), anyMap(), any());

        assertThat(service.completeTask("t1", "h", vars(), false, null).isSuccess()).isTrue();
        verify(taskMultiInstanceService).handleMultiInstanceSubTaskCompletion("t1", VARIABLES, row);
    }

    @Test
    void aCandidateStillCompletesATaskNobodyHolds() {
        Task free = task(null);
        when(query.singleResult()).thenReturn(free);
        candidates("c", "e");

        assertThat(service.completeTask("t1", "c", vars(), false, null).isSuccess()).isTrue();

        verify(taskService).complete("t1", VARIABLES);
    }

    @Test
    void aTaskNobodyHoldsStillOpensToWhomItsExtendedRowAssignsIt() {
        Task free = task(null);
        when(query.singleResult()).thenReturn(free);
        candidates("e");
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").assignmentType(AssignmentType.CANDIDATE_USERS)
                .assignmentTarget("u").status("ASSIGNED").build();
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse("t1")).thenReturn(Optional.of(row));
        doThrow(new WorkflowValidationException(List.of())).when(taskActionService).validateCompletePermission(row, "x");

        assertThat(service.completeTask("t1", "u", vars(), false, null).isSuccess()).isTrue();
        assertThatThrownBy(() -> service.completeTask("t1", "x", vars(), false, null))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("does not have permission");
    }

    @Test
    void theInitiatorOfATaskWithoutHolderOrCandidatesBecomesItsHolderAndCompletesIt() {
        Task free = task(null);
        Task held = task("s");
        when(query.singleResult()).thenReturn(free, held);
        candidates();
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getStartUserId()).thenReturn("s");
        ProcessInstanceQuery instances = mock(ProcessInstanceQuery.class, RETURNS_SELF);
        when(instances.singleResult()).thenReturn(instance);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(instances);

        assertThat(service.completeTask("t1", "s", vars(), false, null).isSuccess()).isTrue();

        verify(taskService).setAssignee("t1", "s");
        verify(taskService).complete("t1", VARIABLES);
    }

    private ExtendedTaskInfo delegatedRow() {
        ExtendedTaskInfo row = ExtendedTaskInfo.builder().taskId("t1").assignmentType(AssignmentType.USER)
                .assignmentTarget("h").claimedBy("h").status("CLAIMED").build();
        row.delegateTask("d", "h", "leave");
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse("t1")).thenReturn(Optional.of(row));
        return row;
    }

    private void verifyNotCompleted() {
        verify(taskService, never()).complete(anyString());
        verify(taskService, never()).complete(anyString(), anyMap());
    }

    private void candidates(String... userIds) {
        List<IdentityLink> links = java.util.Arrays.stream(userIds).map(id -> {
            IdentityLink link = mock(IdentityLink.class);
            when(link.getType()).thenReturn("candidate");
            when(link.getUserId()).thenReturn(id);
            return link;
        }).toList();
        when(taskService.getIdentityLinksForTask("t1")).thenReturn(links);
    }

    private static Task task(String assignee) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        when(task.getProcessDefinitionId()).thenReturn("pd-1");
        when(task.getTaskDefinitionKey()).thenReturn("Confirm");
        when(task.getName()).thenReturn("Confirm");
        when(task.getAssignee()).thenReturn(assignee);
        return task;
    }

    private static Map<String, Object> vars() {
        return new java.util.HashMap<>(VARIABLES);
    }
}
