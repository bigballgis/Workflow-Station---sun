package com.workflow.component;

import com.workflow.dto.request.TaskClaimRequest;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.service.UserPermissionService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Claim must follow Flowable candidates, not a stale {@code USER} row left after Unclaim / restore.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskActionService.claimTask (stale USER vs candidate pool)")
class TaskActionServiceClaimPoolTest {

    private static final String TASK_ID = "3f57ba6a-a039-11f1-a865-92da03640ef8";
    private static final String CLAIMER = "c84a748d-8ff5-43b0-a36a-d6665b78d80f";
    private static final String STALE_TARGET = "9b9e94f5-7e69-4ed2-af2e-573d17a09943";
    private static final String OTHER_CANDIDATE = "e30f1bdf-a830-4481-922e-d5d1439dfec0";

    @Mock
    private TaskService taskService;
    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    @Mock
    private UserPermissionService userPermissionService;

    @Mock
    private TaskOrphanRepairService taskOrphanRepairService;

    @InjectMocks
    private TaskClaimSupport taskClaimSupport;

    private Task flowableTask;
    private ExtendedTaskInfo extended;

    @BeforeEach
    void setUp() {
        flowableTask = mock(Task.class);
        when(flowableTask.getId()).thenReturn(TASK_ID);
        when(flowableTask.getAssignee()).thenReturn(null);

        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId(TASK_ID)).thenReturn(query);
        when(query.singleResult()).thenReturn(flowableTask);

        extended = ExtendedTaskInfo.builder()
                .taskId(TASK_ID)
                .processInstanceId("pi")
                .processDefinitionId("pd")
                .assignmentType(AssignmentType.USER)
                .assignmentTarget(STALE_TARGET)
                .status("ASSIGNED")
                .isDeleted(false)
                .build();
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
                .thenReturn(Optional.of(extended));
    }

    @Test
    @DisplayName("unassigned Flowable pool with stale USER row can still be claimed")
    void staleUserRowClaimsWhenActorIsCandidate() {
        stubCandidates(CLAIMER, OTHER_CANDIDATE);

        taskClaimSupport.claimTask(TASK_ID, TaskClaimRequest.builder()
                .taskId(TASK_ID)
                .claimedBy(CLAIMER)
                .build());

        verify(taskService).claim(TASK_ID, CLAIMER);
        ArgumentCaptor<ExtendedTaskInfo> saved = ArgumentCaptor.forClass(ExtendedTaskInfo.class);
        verify(extendedTaskInfoRepository).save(saved.capture());
        assertThat(saved.getValue().getAssignmentType()).isEqualTo(AssignmentType.CANDIDATE_USERS);
        assertThat(saved.getValue().getAssignmentTarget()).isEqualTo(CLAIMER + "," + OTHER_CANDIDATE);
        assertThat(saved.getValue().getClaimedBy()).isEqualTo(CLAIMER);
        verify(userPermissionService, never()).hasTaskPermission(any(), any(), any());
    }

    @Test
    @DisplayName("true direct assignment (USER, no candidates) still cannot be claimed")
    void userWithoutCandidatesStillRejected() {
        when(taskService.getIdentityLinksForTask(TASK_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> taskClaimSupport.claimTask(TASK_ID, TaskClaimRequest.builder()
                .taskId(TASK_ID)
                .claimedBy(CLAIMER)
                .build()))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("Directly assigned tasks cannot be claimed");
        verify(taskService, never()).claim(any(), any());
    }

    @Test
    @DisplayName("candidate pool rejects an actor who is not on the identity links")
    void outsiderCannotClaimPool() {
        stubCandidates(OTHER_CANDIDATE);

        assertThatThrownBy(() -> taskClaimSupport.claimTask(TASK_ID, TaskClaimRequest.builder()
                .taskId(TASK_ID)
                .claimedBy(CLAIMER)
                .build()))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("does not have permission to claim");
        verify(taskService, never()).claim(any(), any());
    }

    @Test
    @DisplayName("VIRTUAL_GROUP with leftover user candidates still uses group permission")
    void virtualGroupDoesNotRewriteFromUserCandidates() {
        extended.setAssignmentType(AssignmentType.VIRTUAL_GROUP);
        extended.setAssignmentTarget("role-analyst");
        stubCandidates(CLAIMER);
        when(userPermissionService.hasTaskPermission(CLAIMER, AssignmentType.VIRTUAL_GROUP, "role-analyst"))
                .thenReturn(true);

        taskClaimSupport.claimTask(TASK_ID, TaskClaimRequest.builder()
                .taskId(TASK_ID)
                .claimedBy(CLAIMER)
                .build());

        verify(userPermissionService).hasTaskPermission(CLAIMER, AssignmentType.VIRTUAL_GROUP, "role-analyst");
        ArgumentCaptor<ExtendedTaskInfo> saved = ArgumentCaptor.forClass(ExtendedTaskInfo.class);
        verify(extendedTaskInfoRepository).save(saved.capture());
        assertThat(saved.getValue().getAssignmentType()).isEqualTo(AssignmentType.VIRTUAL_GROUP);
    }

    private void stubCandidates(String... userIds) {
        List<IdentityLink> links = new java.util.ArrayList<>();
        for (String userId : userIds) {
            IdentityLink link = mock(IdentityLink.class);
            when(link.getType()).thenReturn("candidate");
            when(link.getUserId()).thenReturn(userId);
            links.add(link);
        }
        when(taskService.getIdentityLinksForTask(TASK_ID)).thenReturn(links);
    }
}
