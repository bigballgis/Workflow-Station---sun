package com.workflow.component;

import com.workflow.client.AdminCenterClient;
import com.workflow.dto.response.TaskAssignmentResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.exception.WorkflowValidationException;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.service.UserPermissionService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Force Unclaim write path: non-assignee unclaim is gated by
 * {@link AdminCenterClient#canForceUnclaim}; evaluate false (member or fail-closed) rejects.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskActionService.unclaimTask (Force Unclaim)")
class TaskActionServiceForceUnclaimTest {

    private static final String TASK_ID = "task-force-unclaim";
    private static final String HOLDER_ID = "holder-user";
    private static final String ACTOR_ID = "leader-user";
    private static final String PROCESS_DEFINITION_ID = "proc-def-1";
    private static final String TASK_DEFINITION_KEY = "claimPoolTask";
    private static final String PROCESS_INSTANCE_ID = "pi-1";
    private static final String BUSINESS_UNIT_ID = "bu-hase";
    private static final String ROLE_ID = "role-analyst";

    @Mock
    private TaskService taskService;
    @Mock
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;
    @Mock
    private AdminCenterClient adminCenterClient;
    @Mock
    private BpmnActionParser bpmnActionParser;
    @Mock
    private RuntimeService runtimeService;
    @Mock
    private TaskOrphanRepairService taskOrphanRepairService;
    @Mock
    private UserPermissionService userPermissionService;

    @InjectMocks
    private TaskClaimSupport taskClaimSupport;

    @BeforeEach
    void setUp() {
        Task flowableTask = mock(Task.class);
        when(flowableTask.getAssignee()).thenReturn(HOLDER_ID);
        // Identity getters only used on the Force Unclaim branch (non-assignee).
        lenient().when(flowableTask.getId()).thenReturn(TASK_ID);
        lenient().when(flowableTask.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);
        lenient().when(flowableTask.getTaskDefinitionKey()).thenReturn(TASK_DEFINITION_KEY);
        lenient().when(flowableTask.getProcessInstanceId()).thenReturn(PROCESS_INSTANCE_ID);

        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId(TASK_ID)).thenReturn(query);
        when(query.singleResult()).thenReturn(flowableTask);

        ExtendedTaskInfo claimed = ExtendedTaskInfo.builder()
                .taskId(TASK_ID)
                .processInstanceId(PROCESS_INSTANCE_ID)
                .processDefinitionId(PROCESS_DEFINITION_ID)
                .taskDefinitionKey(TASK_DEFINITION_KEY)
                .assignmentType(AssignmentType.VIRTUAL_GROUP)
                .assignmentTarget(ROLE_ID)
                .claimedBy(HOLDER_ID)
                .status("CLAIMED")
                .isDeleted(false)
                .build();
        // Rejected before extended-row lookup; success path still needs this stub.
        lenient().when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
                .thenReturn(Optional.of(claimed));
    }

    @Test
    @DisplayName("Leader / Approver / SYS_ADMIN (evaluate true) can Force Unclaim a held task")
    void authorizedActorForceUnclaimsHeldTask() {
        stubBpmnClaimPool(BUSINESS_UNIT_ID, ROLE_ID);
        when(adminCenterClient.canForceUnclaim(ACTOR_ID, TASK_ID, BUSINESS_UNIT_ID, List.of(ROLE_ID)))
                .thenReturn(true);

        TaskAssignmentResult result = taskClaimSupport.unclaimTask(TASK_ID, ACTOR_ID);

        assertThat(result.isSuccess()).isTrue();
        verify(taskService).unclaim(TASK_ID);
        verify(taskOrphanRepairService).restoreBuRoleClaimPool(any());
        verify(extendedTaskInfoRepository).save(any(ExtendedTaskInfo.class));
    }

    @Test
    @DisplayName("Ordinary Member (evaluate false) cannot Force Unclaim")
    void ordinaryMemberCannotForceUnclaim() {
        stubBpmnClaimPool(BUSINESS_UNIT_ID, ROLE_ID);
        when(adminCenterClient.canForceUnclaim(ACTOR_ID, TASK_ID, BUSINESS_UNIT_ID, List.of(ROLE_ID)))
                .thenReturn(false);

        assertRejectedAsAssigneeOnly();
        verify(taskService, never()).unclaim(any());
        verify(extendedTaskInfoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Admin-center evaluate failure is fail-closed (client returns false → reject)")
    void adminCenterFailureFailsClosed() {
        stubBpmnClaimPool(BUSINESS_UNIT_ID, ROLE_ID);
        when(adminCenterClient.canForceUnclaim(eq(ACTOR_ID), eq(TASK_ID), eq(BUSINESS_UNIT_ID), eq(List.of(ROLE_ID))))
                .thenReturn(false);

        assertRejectedAsAssigneeOnly();
        verify(taskService, never()).unclaim(any());
    }

    @Test
    @DisplayName("Holder can still self-unclaim without Force Unclaim evaluate")
    void holderSelfUnclaimDoesNotCallForceUnclaim() {
        TaskAssignmentResult result = taskClaimSupport.unclaimTask(TASK_ID, HOLDER_ID);

        assertThat(result.isSuccess()).isTrue();
        verify(taskService).unclaim(TASK_ID);
        verify(taskOrphanRepairService).restoreBuRoleClaimPool(any());
        verify(adminCenterClient, never()).canForceUnclaim(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Unclaim rewrites a stale USER extended row to CANDIDATE_USERS from Flowable links")
    void unclaimSyncsStaleUserRowToCandidatePool() {
        org.flowable.identitylink.api.IdentityLink link =
                mock(org.flowable.identitylink.api.IdentityLink.class);
        when(link.getType()).thenReturn("candidate");
        when(link.getUserId()).thenReturn(HOLDER_ID);
        when(taskService.getIdentityLinksForTask(TASK_ID)).thenReturn(List.of(link));

        ExtendedTaskInfo staleUser = ExtendedTaskInfo.builder()
                .taskId(TASK_ID)
                .processInstanceId(PROCESS_INSTANCE_ID)
                .processDefinitionId(PROCESS_DEFINITION_ID)
                .assignmentType(AssignmentType.USER)
                .assignmentTarget(HOLDER_ID)
                .claimedBy(HOLDER_ID)
                .status("CLAIMED")
                .isDeleted(false)
                .build();
        when(extendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(TASK_ID))
                .thenReturn(Optional.of(staleUser));

        TaskAssignmentResult result = taskClaimSupport.unclaimTask(TASK_ID, HOLDER_ID);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAssignmentType()).isEqualTo(AssignmentType.CANDIDATE_USERS);
        assertThat(staleUser.getAssignmentType()).isEqualTo(AssignmentType.CANDIDATE_USERS);
        assertThat(staleUser.getAssignmentTarget()).isEqualTo(HOLDER_ID);
    }

    @Test
    @DisplayName("Missing BPMN BU falls back to process variable before evaluate")
    void missingBpmnBuUsesProcessVariable() {
        when(bpmnActionParser.getUserTaskExtensionPropertyValue(
                PROCESS_DEFINITION_ID, TASK_DEFINITION_KEY, "businessUnitId")).thenReturn(" ");
        when(bpmnActionParser.getUserTaskExtensionPropertyValue(
                PROCESS_DEFINITION_ID, TASK_DEFINITION_KEY, "roleIds")).thenReturn(ROLE_ID);
        when(bpmnActionParser.getUserTaskExtensionPropertyValue(
                PROCESS_DEFINITION_ID, TASK_DEFINITION_KEY, "roleId")).thenReturn(null);
        when(runtimeService.getVariables(PROCESS_INSTANCE_ID))
                .thenReturn(Map.of("activeBusinessUnitId", "bu-from-var"));
        when(adminCenterClient.canForceUnclaim(ACTOR_ID, TASK_ID, "bu-from-var", List.of(ROLE_ID)))
                .thenReturn(true);

        assertThat(taskClaimSupport.unclaimTask(TASK_ID, ACTOR_ID).isSuccess()).isTrue();
        verify(adminCenterClient).canForceUnclaim(ACTOR_ID, TASK_ID, "bu-from-var", List.of(ROLE_ID));
    }

    private void stubBpmnClaimPool(String businessUnitId, String roleId) {
        when(bpmnActionParser.getUserTaskExtensionPropertyValue(
                PROCESS_DEFINITION_ID, TASK_DEFINITION_KEY, "businessUnitId")).thenReturn(businessUnitId);
        when(bpmnActionParser.getUserTaskExtensionPropertyValue(
                PROCESS_DEFINITION_ID, TASK_DEFINITION_KEY, "roleIds")).thenReturn(roleId);
        when(bpmnActionParser.getUserTaskExtensionPropertyValue(
                PROCESS_DEFINITION_ID, TASK_DEFINITION_KEY, "roleId")).thenReturn(null);
    }

    private void assertRejectedAsAssigneeOnly() {
        assertThatThrownBy(() -> taskClaimSupport.unclaimTask(TASK_ID, ACTOR_ID))
                .isInstanceOf(WorkflowValidationException.class)
                .hasMessageContaining("Only assignee can unclaim");
    }
}
