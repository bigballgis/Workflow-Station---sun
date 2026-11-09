package com.portal.controller;

import com.portal.client.WorkflowEngineClient;
import com.portal.component.TaskDelegateDisplayNames;
import com.portal.component.TaskProcessComponent;
import com.portal.component.TaskQueryComponent;
import com.portal.dto.TaskHistoryInfo;
import com.portal.dto.TaskInfo;
import com.portal.exception.PortalException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Platform access model §7.2: the direct task APIs that read a task's data apply the same
 * "may view this task" rule as {@code GET /tasks/{id}} — a task id alone opens nothing.
 */
class TaskControllerTaskViewAccessTest {

    private static final String TASK_ID = "task-1";
    private static final String INSTANCE_ID = "proc-1";

    private final TaskQueryComponent taskQueryComponent = mock(TaskQueryComponent.class);
    private final TaskProcessComponent taskProcessComponent = mock(TaskProcessComponent.class);
    private final WorkflowEngineClient workflowEngineClient = mock(WorkflowEngineClient.class);
    private final TaskController controller = new TaskController(taskQueryComponent, taskProcessComponent,
            null, null, null, null, null, workflowEngineClient, null, null);

    private final TaskDelegateDisplayNames delegateNames = mock(TaskDelegateDisplayNames.class);

    {
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "taskDelegateDisplayNames", delegateNames);
    }

    private void task(boolean viewable) {
        TaskInfo task = TaskInfo.builder().taskId(TASK_ID).processInstanceId(INSTANCE_ID).build();
        when(taskQueryComponent.getTaskById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskProcessComponent.canViewTaskForm(eq(task), eq("u1"), nullable(String.class))).thenReturn(viewable);
    }

    private static void assertForbidden(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(PortalException.class, e -> assertThat(e.getCode()).isEqualTo("403"));
    }

    /** The task itself answers a refusal with a real HTTP 403 (handlePortalException), like its history. */
    @Test
    void theTaskIsRefusedToSomeoneWhoMayNotViewItWithA403() {
        task(false);

        assertForbidden(() -> controller.getTaskDetail("u1", TASK_ID));
        verify(delegateNames, never()).annotate(any());
    }

    @Test
    void anUnknownTaskIs404() {
        when(taskQueryComponent.getTaskById(TASK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getTaskDetail("u1", TASK_ID))
                .isInstanceOfSatisfying(PortalException.class, e -> assertThat(e.getCode()).isEqualTo("404"));
    }

    @Test
    void theTaskIsServedWithItsDelegatesNamesToSomeoneWhoMayViewIt() {
        task(true);

        var response = controller.getTaskDetail("u1", TASK_ID);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getTaskId()).isEqualTo(TASK_ID);
        verify(delegateNames).annotate(response.getData());
    }

    /** The status the shared handler answers with: the code when it is an HTTP error status, else 500. */
    @Test
    void aPortalExceptionAnswersWithItsHttpStatus() {
        jakarta.servlet.http.HttpServletResponse response = mock(jakarta.servlet.http.HttpServletResponse.class);

        controller.handlePortalException(new PortalException("403", "no"), response);
        controller.handlePortalException(new PortalException("409", "held"), response);
        controller.handlePortalException(new PortalException("FUNCTION_UNIT_NOT_FOUND", "x"), response);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(response);
        order.verify(response).setStatus(403);
        order.verify(response).setStatus(409);
        order.verify(response).setStatus(500);
    }

    @Test
    void historyIsRefusedToSomeoneWhoMayNotViewTheTask() {
        task(false);

        assertForbidden(() -> controller.getTaskHistory(TASK_ID, "u1", null));
        verify(taskQueryComponent, never()).getTaskHistory(anyString(), any());
    }

    /** The optional instance id must be the task's own; it cannot point the read at another request. */
    @Test
    void historyIsRefusedForAnotherRequestsInstanceId() {
        task(true);

        assertForbidden(() -> controller.getTaskHistory(TASK_ID, "u1", "someone-elses-request"));
        verify(taskQueryComponent, never()).getTaskHistory(anyString(), any());
    }

    @Test
    void historyIsServedToSomeoneWhoMayViewTheTask() {
        task(true);
        List<TaskHistoryInfo> history = List.of(TaskHistoryInfo.builder().taskId(TASK_ID).build());
        when(taskQueryComponent.getTaskHistory(TASK_ID, INSTANCE_ID)).thenReturn(history);

        assertThat(controller.getTaskHistory(TASK_ID, "u1", INSTANCE_ID).getData()).isEqualTo(history);
    }

    @Test
    void historyOfAnUnknownTaskIs404() {
        when(taskQueryComponent.getTaskById(TASK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getTaskHistory(TASK_ID, "u1", null))
                .isInstanceOfSatisfying(PortalException.class, e -> assertThat(e.getCode()).isEqualTo("404"));
    }

    @Test
    void subTableDataIsRefusedToSomeoneWhoMayNotViewTheTask() {
        task(false);

        assertForbidden(() -> controller.getSubTableDataAll(TASK_ID, "u1"));
        verify(workflowEngineClient, never()).getSubTableDataAll(anyString());
    }

    @Test
    void subTableDataIsServedToSomeoneWhoMayViewTheTask() {
        task(true);
        Map<String, Object> data = Map.of("rows", List.of());
        when(workflowEngineClient.getSubTableDataAll(TASK_ID)).thenReturn(Optional.of(data));

        assertThat(controller.getSubTableDataAll(TASK_ID, "u1").getData()).isEqualTo(data);
    }
}
