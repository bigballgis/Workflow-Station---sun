package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.messaging.event.ActionEmailRequestedEvent;
import com.platform.messaging.service.EventPublisher;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.TaskActionInfo;
import com.portal.dto.TaskInfo;
import com.portal.entity.ActionDefinition;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ActionDefinitionRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.TaskActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionPostEmailComponentImplTest {

    @Mock
    private TaskActionService taskActionService;
    @Mock
    private TaskQueryComponent taskQueryComponent;
    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;
    @Mock
    private ProcessInstanceRepository processInstanceRepository;
    @Mock
    private EventPublisher eventPublisher;

    private ActionPostEmailComponentImpl component;

    @BeforeEach
    void setUp() {
        component = new ActionPostEmailComponentImpl(
                taskActionService, taskQueryComponent, actionDefinitionRepository,
                processInstanceRepository, eventPublisher, new ObjectMapper());
    }

    @Test
    void typeMatches_approveFamily() {
        assertTrue(ActionPostEmailComponentImpl.typeMatches("APPROVE", "PROCESS_SUBMIT"));
        assertTrue(ActionPostEmailComponentImpl.typeMatches("SAVE", "SAVE"));
        assertFalse(ActionPostEmailComponentImpl.typeMatches("SAVE", "APPROVE"));
    }

    @Test
    void blankActionId_runsActionWithoutPublish() {
        AtomicBoolean ran = new AtomicBoolean();
        component.runWithPostEmail(ActionPostEmailContext.builder()
                .actionId("")
                .operation(ActionPostEmailContext.OP_SAVE)
                .taskId("t1")
                .build(), () -> ran.set(true));
        assertTrue(ran.get());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void unboundAction_runsWithoutPublish() {
        when(taskActionService.getTaskActions("t1")).thenReturn(List.of());
        component.runWithPostEmail(ctx("99", ActionPostEmailContext.OP_SAVE), () -> null);
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void typeMismatch_runsWithoutPublish() {
        when(taskActionService.getTaskActions("t1")).thenReturn(List.of(action("7", "APPROVE", enabledJson())));
        component.runWithPostEmail(ctx("7", ActionPostEmailContext.OP_SAVE), () -> null);
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void publishFailure_doesNotThrow() {
        when(taskActionService.getTaskActions("t1")).thenReturn(List.of(action("7", "SAVE", enabledJson())));
        when(taskQueryComponent.getTaskById("t1")).thenReturn(java.util.Optional.of(
                com.portal.dto.TaskInfo.builder().processInstanceId("pi-1").build()));
        when(eventPublisher.publish(any())).thenThrow(new RuntimeException("kafka down"));
        component.runWithPostEmail(ctx("7", ActionPostEmailContext.OP_SAVE), () -> null);
        verify(eventPublisher).publish(any());
    }

    @Test
    void enabledSave_publishesEvent() {
        when(taskActionService.getTaskActions("t1")).thenReturn(List.of(action("7", "SAVE", enabledJson())));
        when(taskQueryComponent.getTaskById("t1")).thenReturn(java.util.Optional.of(
                com.portal.dto.TaskInfo.builder().processInstanceId("pi-1").build()));
        when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        component.runWithPostEmail(ctx("7", ActionPostEmailContext.OP_SAVE), () -> "ok");
        ArgumentCaptor<ActionEmailRequestedEvent> captor = ArgumentCaptor.forClass(ActionEmailRequestedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals("pi-1", captor.getValue().getProcessInstanceId());
        assertEquals("7", captor.getValue().getActionId());
        assertEquals(true, captor.getValue().getPostEmail().get("enabled"));
    }

    @Test
    void startWithoutFunctionUnit_doesNotLookUpBareActionId() {
        component.runWithPostEmail(ActionPostEmailContext.builder()
                .actionId("99")
                .operation(ActionPostEmailContext.OP_START)
                .operatorId("u1")
                .build(), () -> ProcessInstanceInfo.builder().id("pi-1").build());
        verify(eventPublisher, never()).publish(any());
        verify(actionDefinitionRepository, never()).findById(any());
        verify(actionDefinitionRepository, never()).findFromDwById(any());
    }

    @Test
    void startRejectsActionFromAnotherFunctionUnit() {
        when(actionDefinitionRepository.findCatalogActionForFunctionUnit("99", "fu-a"))
                .thenReturn(java.util.Optional.empty());
        when(actionDefinitionRepository.findDwActionForFunctionUnit("99", "fu-a"))
                .thenReturn(java.util.Optional.empty());
        component.runWithPostEmail(ActionPostEmailContext.builder()
                .actionId("99")
                .operation(ActionPostEmailContext.OP_START)
                .functionUnitCode("fu-a")
                .operatorId("u1")
                .build(), () -> ProcessInstanceInfo.builder().id("pi-1").build());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void startPublishesWhenActionBelongsToFunctionUnit() {
        when(actionDefinitionRepository.findCatalogActionForFunctionUnit("12", "fu-a"))
                .thenReturn(java.util.Optional.of(ActionDefinition.builder()
                        .id("12").actionName("Submit").actionType("PROCESS_SUBMIT").configJson(enabledJson()).build()));
        when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        component.runWithPostEmail(ActionPostEmailContext.builder()
                .actionId("12")
                .operation(ActionPostEmailContext.OP_START)
                .functionUnitCode("fu-a")
                .operatorId("u1")
                .build(), () -> ProcessInstanceInfo.builder().id("pi-9").build());
        ArgumentCaptor<ActionEmailRequestedEvent> captor = ArgumentCaptor.forClass(ActionEmailRequestedEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals("pi-9", captor.getValue().getProcessInstanceId());
        assertEquals("12", captor.getValue().getActionId());
    }

    @Test
    void withdrawWithoutTaskUsesInstanceFunctionUnit() {
        when(processInstanceRepository.findById("pi-1")).thenReturn(java.util.Optional.of(
                ProcessInstance.builder().id("pi-1").functionUnitCode("fu-a").build()));
        when(actionDefinitionRepository.findCatalogActionForFunctionUnit("8", "fu-a"))
                .thenReturn(java.util.Optional.of(ActionDefinition.builder()
                        .id("8").actionName("Withdraw").actionType("WITHDRAW").configJson(enabledJson()).build()));
        when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        component.runWithPostEmail(ActionPostEmailContext.builder()
                .actionId("8")
                .operation(ActionPostEmailContext.OP_WITHDRAW)
                .processInstanceId("pi-1")
                .operatorId("u1")
                .build(), () -> null);
        verify(eventPublisher).publish(any());
        verify(actionDefinitionRepository, never()).findById(any());
    }

    @Test
    void withdrawRejectsTaskFromAnotherProcess() {
        when(taskQueryComponent.getTaskById("t-other")).thenReturn(java.util.Optional.of(
                TaskInfo.builder().taskId("t-other").processInstanceId("pi-other").build()));
        component.runWithPostEmail(ActionPostEmailContext.builder()
                .actionId("99")
                .operation(ActionPostEmailContext.OP_WITHDRAW)
                .taskId("t-other")
                .processInstanceId("pi-1")
                .operatorId("u1")
                .build(), () -> null);
        verify(eventPublisher, never()).publish(any());
        verify(taskActionService, never()).getTaskActions(any());
        verify(actionDefinitionRepository, never()).findCatalogActionForFunctionUnit(any(), any());
    }

    @Test
    void withdrawAfterTaskGoneUsesFunctionUnit() {
        when(taskQueryComponent.getTaskById("t1")).thenReturn(java.util.Optional.empty());
        when(taskActionService.getTaskActions("t1")).thenReturn(List.of());
        when(processInstanceRepository.findById("pi-1")).thenReturn(java.util.Optional.of(
                ProcessInstance.builder().id("pi-1").functionUnitCode("fu-a").build()));
        when(actionDefinitionRepository.findCatalogActionForFunctionUnit("8", "fu-a"))
                .thenReturn(java.util.Optional.of(ActionDefinition.builder()
                        .id("8").actionName("Withdraw").actionType("WITHDRAW").configJson(enabledJson()).build()));
        when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        component.runWithPostEmail(ActionPostEmailContext.builder()
                .actionId("8")
                .operation(ActionPostEmailContext.OP_WITHDRAW)
                .taskId("t1")
                .processInstanceId("pi-1")
                .operatorId("u1")
                .build(), () -> null);
        verify(eventPublisher).publish(any());
    }

    private static ActionPostEmailContext ctx(String actionId, String operation) {
        return ActionPostEmailContext.builder()
                .actionId(actionId)
                .operation(operation)
                .taskId("t1")
                .operatorId("u1")
                .build();
    }

    private static TaskActionInfo action(String id, String type, String configJson) {
        return TaskActionInfo.builder().actionId(id).actionName("Save").actionType(type).configJson(configJson).build();
    }

    private static String enabledJson() {
        return "{\"postEmail\":{\"enabled\":true,\"connectionId\":\"c1\",\"emailTo\":\"${initiator}\",\"emailTemplateId\":\"2\"}}";
    }
}
