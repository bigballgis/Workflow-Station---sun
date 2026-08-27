package com.workflow.component;

import com.workflow.client.AdminCenterClient;
import com.workflow.dto.response.TaskListResult;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Completed MI tasks are served from {@code wf_extended_task_info} (runtime Flowable
 * row is already gone). That path must resolve Current Assignee the same way historic
 * tasks do — otherwise Portal shows the raw user id.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskInfoAssembler.convertToTaskInfo (assignee display name)")
class TaskInfoAssemblerConvertToTaskInfoTest {

    private static final String USER_ID = "user-1";
    private static final String FULL_NAME = "Alice";

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private AdminCenterClient adminCenterClient;

    @InjectMocks
    private TaskInfoAssembler assembler;

    @Test
    @DisplayName("Completed MI USER row resolves currentAssigneeName from Admin fullName")
    void completedMiUserTaskResolvesDisplayName() {
        when(adminCenterClient.getUserInfo(USER_ID)).thenReturn(Map.of("fullName", FULL_NAME));

        TaskListResult.TaskInfo info = assembler.convertToTaskInfo(completedMiUserTask());

        assertThat(info.getCurrentAssignee()).isEqualTo(USER_ID);
        assertThat(info.getCurrentAssigneeName()).isEqualTo(FULL_NAME);
    }

    @Test
    @DisplayName("Unclaimed VIRTUAL_GROUP row has no currentAssigneeName")
    void unclaimedVirtualGroupHasNoAssigneeName() {
        ExtendedTaskInfo eti = ExtendedTaskInfo.builder()
                .taskId("task-pool")
                .taskName("Mark Completed")
                .processInstanceId("pi-1")
                .processDefinitionId("pd:1:def")
                .taskDefinitionKey("Activity_mark")
                .assignmentType(AssignmentType.VIRTUAL_GROUP)
                .assignmentTarget("role-pool")
                .status("COMPLETED")
                .createdTime(LocalDateTime.now())
                .isDeleted(false)
                .build();

        TaskListResult.TaskInfo info = assembler.convertToTaskInfo(eti);

        assertThat(info.getCurrentAssignee()).isNull();
        assertThat(info.getCurrentAssigneeName()).isNull();
    }

    @Test
    @DisplayName("Admin lookup failure falls back to the user id")
    void adminLookupFailureFallsBackToUserId() {
        when(adminCenterClient.getUserInfo(USER_ID)).thenThrow(new RuntimeException("admin down"));

        TaskListResult.TaskInfo info = assembler.convertToTaskInfo(completedMiUserTask());

        assertThat(info.getCurrentAssignee()).isEqualTo(USER_ID);
        assertThat(info.getCurrentAssigneeName()).isEqualTo(USER_ID);
    }

    private static ExtendedTaskInfo completedMiUserTask() {
        return ExtendedTaskInfo.builder()
                .taskId("task-completed-mi")
                .taskName("Transaction Investigation")
                .processInstanceId("pi-completed-mi")
                .processDefinitionId("pd:1:def")
                .taskDefinitionKey("Activity_1c23xsu")
                .assignmentType(AssignmentType.USER)
                .assignmentTarget(USER_ID)
                .status("COMPLETED")
                .createdTime(LocalDateTime.now())
                .completedBy(USER_ID)
                .isDeleted(false)
                .build();
    }
}
