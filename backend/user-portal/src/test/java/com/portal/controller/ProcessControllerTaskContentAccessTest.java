package com.portal.controller;

import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.ProcessComponent;
import com.portal.component.TaskProcessComponent;
import com.portal.component.TaskQueryComponent;
import com.platform.common.dto.ApiResponse;
import com.portal.dto.TaskInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task-participant grant for FU content endpoints: an assignee/candidate/initiator of a task
 * may load the task's function-unit content (BPMN + forms) via {@code taskId} even without the
 * FU's start-access roles; all other callers still go through the role gate.
 */
@ExtendWith(MockitoExtension.class)
class ProcessControllerTaskContentAccessTest {

    private static final String FU_KEY = "atm-20260623-gaevus";
    private static final String TASK_ID = "task-1";
    private static final String USER_ID = "user-e2e-lina";

    @Mock
    private ProcessComponent processComponent;

    @Mock
    private com.platform.common.i18n.I18nService i18nService;

    @Mock
    private FunctionUnitAccessComponent functionUnitAccessComponent;

    @Mock
    private com.portal.component.PortalPrimaryKeyAllocationComponent portalPrimaryKeyAllocationComponent;

    @Mock
    private TaskQueryComponent taskQueryComponent;

    @Mock
    private TaskProcessComponent taskProcessComponent;

    @InjectMocks
    private ProcessController processController;

    private TaskInfo taskOf(String processDefinitionKey) {
        return TaskInfo.builder()
                .taskId(TASK_ID)
                .processDefinitionKey(processDefinitionKey)
                .build();
    }

    @Test
    void taskAssigneeWithoutStartRoleGetsContentViaTaskId() {
        when(taskQueryComponent.getTaskById(TASK_ID)).thenReturn(Optional.of(taskOf(FU_KEY)));
        when(functionUnitAccessComponent.requireEnabledFunctionUnit(USER_ID, FU_KEY)).thenReturn("fu-id");
        when(taskProcessComponent.canViewTaskForm(any(TaskInfo.class), eq(USER_ID), isNull())).thenReturn(true);
        when(processComponent.getFunctionUnitContent(FU_KEY)).thenReturn(Map.of("forms", java.util.List.of()));

        ApiResponse<Map<String, Object>> response =
                processController.getFunctionUnitContent(USER_ID, FU_KEY, TASK_ID);

        assertThat(response.isSuccess()).isTrue();
        // Role gate must NOT run when the task grant succeeds — that's the whole point of the bypass.
        verify(functionUnitAccessComponent, never()).checkFunctionUnitAccess(anyString(), anyString());
    }

    @Test
    void withoutTaskIdStillGoesThroughRoleGate() {
        doThrow(new FunctionUnitAccessComponent.FunctionUnitAccessDeniedException("denied"))
                .when(functionUnitAccessComponent).checkFunctionUnitAccess(USER_ID, FU_KEY);

        assertThatThrownBy(() -> processController.getFunctionUnitContent(USER_ID, FU_KEY, null))
                .isInstanceOf(FunctionUnitAccessComponent.FunctionUnitAccessDeniedException.class);

        verify(taskQueryComponent, never()).getTaskById(anyString());
        verify(processComponent, never()).getFunctionUnitContent(anyString());
    }

    @Test
    void taskOfDifferentFunctionUnitFallsBackToRoleGate() {
        when(taskQueryComponent.getTaskById(TASK_ID)).thenReturn(Optional.of(taskOf("other-process-key")));
        when(functionUnitAccessComponent.resolveFunctionUnitId(FU_KEY)).thenReturn("fu-a");
        when(functionUnitAccessComponent.resolveFunctionUnitId("other-process-key")).thenReturn("fu-b");
        doThrow(new FunctionUnitAccessComponent.FunctionUnitAccessDeniedException("denied"))
                .when(functionUnitAccessComponent).checkFunctionUnitAccess(USER_ID, FU_KEY);

        assertThatThrownBy(() -> processController.getFunctionUnitContent(USER_ID, FU_KEY, TASK_ID))
                .isInstanceOf(FunctionUnitAccessComponent.FunctionUnitAccessDeniedException.class);

        verify(taskProcessComponent, never()).canViewTaskForm(any(TaskInfo.class), anyString(), any());
    }

    @Test
    void nonParticipantOfTaskFallsBackToRoleGate() {
        when(taskQueryComponent.getTaskById(TASK_ID)).thenReturn(Optional.of(taskOf(FU_KEY)));
        when(functionUnitAccessComponent.requireEnabledFunctionUnit(USER_ID, FU_KEY)).thenReturn("fu-id");
        when(taskProcessComponent.canViewTaskForm(any(TaskInfo.class), eq(USER_ID), isNull())).thenReturn(false);
        doThrow(new FunctionUnitAccessComponent.FunctionUnitAccessDeniedException("denied"))
                .when(functionUnitAccessComponent).checkFunctionUnitAccess(USER_ID, FU_KEY);

        assertThatThrownBy(() -> processController.getFunctionUnitContent(USER_ID, FU_KEY, TASK_ID))
                .isInstanceOf(FunctionUnitAccessComponent.FunctionUnitAccessDeniedException.class);
    }

    @Test
    void disabledFunctionUnitStillRejectedOnTaskGrantPath() {
        when(taskQueryComponent.getTaskById(TASK_ID)).thenReturn(Optional.of(taskOf(FU_KEY)));
        doThrow(new FunctionUnitAccessComponent.FunctionUnitDisabledException("Function unit is disabled"))
                .when(functionUnitAccessComponent).requireEnabledFunctionUnit(USER_ID, FU_KEY);

        assertThatThrownBy(() -> processController.getFunctionUnitContent(USER_ID, FU_KEY, TASK_ID))
                .isInstanceOf(FunctionUnitAccessComponent.FunctionUnitDisabledException.class);

        verify(processComponent, never()).getFunctionUnitContent(anyString());
    }

    @Test
    void unknownTaskFallsBackToRoleGate() {
        when(taskQueryComponent.getTaskById(TASK_ID)).thenReturn(Optional.empty());
        // Role gate passes (e.g. user actually has the start role) — content is returned.
        when(processComponent.getFunctionUnitContent(FU_KEY)).thenReturn(Map.of());

        ApiResponse<Map<String, Object>> response =
                processController.getFunctionUnitContent(USER_ID, FU_KEY, TASK_ID);

        assertThat(response.isSuccess()).isTrue();
        verify(functionUnitAccessComponent).checkFunctionUnitAccess(USER_ID, FU_KEY);
    }
}
