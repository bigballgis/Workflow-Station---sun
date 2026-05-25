package com.portal.service;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.TaskActionInfo;
import com.portal.entity.ActionDefinition;
import com.portal.repository.ActionDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskActionServiceTest {

    @Mock
    private WorkflowEngineClient workflowEngineClient;

    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;

    @InjectMocks
    private TaskActionService taskActionService;

    @Test
    @DisplayName("getTaskActions should resolve only requested dw action ids")
    void getTaskActions_shouldResolveOnlyRequestedDwActionIds() {
        String taskId = "task-001";
        Map<String, Object> engineTask = Map.of(
                "taskId", taskId,
                "processDefinitionKey", "DigitalLendingProcessV2",
                "actionIds", List.of("19", "20"));

        when(workflowEngineClient.getTaskById(taskId)).thenReturn(Optional.of(engineTask));
        when(actionDefinitionRepository.findAllById(List.of("19", "20"))).thenReturn(List.of());
        when(actionDefinitionRepository.findFromDwByIds(List.of("19", "20"))).thenReturn(List.of(
                action("20", "Submit Application"),
                action("19", "Calculate EMI")));

        List<TaskActionInfo> actions = taskActionService.getTaskActions(taskId);

        assertEquals(2, actions.size());
        assertEquals(List.of("19", "20"),
                actions.stream().map(TaskActionInfo::getActionId).toList());
        assertEquals(List.of("Calculate EMI", "Submit Application"),
                actions.stream().map(TaskActionInfo::getActionName).toList());
        verify(actionDefinitionRepository, never()).findByProcessDefinitionKey(anyString());
    }

    @Test
    @DisplayName("getTaskActions should keep requested order across sys and dw sources")
    void getTaskActions_shouldKeepRequestedOrderAcrossSources() {
        String taskId = "task-002";
        Map<String, Object> engineTask = Map.of(
                "taskId", taskId,
                "actionIds", List.of("uuid-2", "19", "uuid-1"));

        when(workflowEngineClient.getTaskById(taskId)).thenReturn(Optional.of(engineTask));
        when(actionDefinitionRepository.findAllById(List.of("uuid-2", "19", "uuid-1"))).thenReturn(List.of(
                action("uuid-1", "Last"),
                action("uuid-2", "First")));
        when(actionDefinitionRepository.findFromDwByIds(List.of("19"))).thenReturn(List.of(
                action("19", "Middle")));

        List<TaskActionInfo> actions = taskActionService.getTaskActions(taskId);

        assertEquals(List.of("uuid-2", "19", "uuid-1"),
                actions.stream().map(TaskActionInfo::getActionId).toList());
        assertEquals(List.of("First", "Middle", "Last"),
                actions.stream().map(TaskActionInfo::getActionName).toList());
    }

    @Test
    @DisplayName("getTaskActions should return empty when nothing resolves")
    void getTaskActions_shouldReturnEmptyWhenNothingResolves() {
        String taskId = "task-003";
        Map<String, Object> engineTask = Map.of(
                "taskId", taskId,
                "actionIds", List.of("19", "20"));

        when(workflowEngineClient.getTaskById(taskId)).thenReturn(Optional.of(engineTask));
        when(actionDefinitionRepository.findAllById(List.of("19", "20"))).thenReturn(List.of());
        when(actionDefinitionRepository.findFromDwByIds(List.of("19", "20"))).thenReturn(List.of());

        List<TaskActionInfo> actions = taskActionService.getTaskActions(taskId);

        assertTrue(actions.isEmpty());
        verify(actionDefinitionRepository, never()).findByProcessDefinitionKey(anyString());
    }

    private static ActionDefinition action(String id, String name) {
        return ActionDefinition.builder()
                .id(id)
                .actionName(name)
                .actionType("APPROVE")
                .build();
    }
}
