package com.portal.component;

import com.platform.common.functionunit.FunctionUnitVersionException;
import com.portal.client.WorkflowEngineClient;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.TaskActionService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskDetailVersionExceptionTest {
    @Test void versionRoutingFailureMustReachTheAuditedHttpHandler() {
        var engine = mock(WorkflowEngineClient.class);
        when(engine.isAvailable()).thenReturn(true);
        when(engine.getTaskById("task")).thenReturn(Optional.of(Map.of("taskId", "task",
                "processInstanceId", "case", "actionIds", List.of("301"))));
        var repository = mock(ProcessInstanceRepository.class);
        when(repository.existsById("case")).thenReturn(true);
        var actions = mock(TaskActionService.class);
        when(actions.getTaskActions("task")).thenThrow(new FunctionUnitVersionException("VERSION_RECOVERY_REQUIRED", "case"));
        var detail = new TaskDetailQueryComponent(repository, engine,
                mock(EngineSubTableHydrator.class), actions, mock(MiParticipantEnrichmentComponent.class), mock(RequestIdEnricher.class));
        assertThatThrownBy(() -> detail.getTaskById("task")).isInstanceOf(FunctionUnitVersionException.class);
    }

    /**
     * A task whose request the Portal never recorded (e.g. a called instance whose sync failed) has it
     * recorded first — pinned to the release it runs — so the merge and request labels read that row.
     */
    @Test void unrecordedRequestIsRecordedBeforeThePageReadsIt() {
        var engine = mock(WorkflowEngineClient.class);
        when(engine.isAvailable()).thenReturn(true);
        when(engine.getTaskById("task")).thenReturn(Optional.of(Map.of("taskId", "task", "processInstanceId", "case")));
        var repository = mock(ProcessInstanceRepository.class);
        when(repository.existsById("case")).thenReturn(false);
        var hydration = mock(ProcessInstanceHydrationComponent.class);
        var requestIds = mock(RequestIdEnricher.class);
        var detail = new TaskDetailQueryComponent(repository, engine, mock(EngineSubTableHydrator.class),
                mock(TaskActionService.class), mock(MiParticipantEnrichmentComponent.class), requestIds);
        ReflectionTestUtils.setField(detail, "hydration", hydration);

        detail.getTaskById("task");

        InOrder order = inOrder(hydration, repository, requestIds);
        order.verify(hydration).requireProcessInstance("case");
        order.verify(repository).findById("case");
        order.verify(requestIds).enrichTaskRequestIds(any());
    }

    @Test void recordedRequestIsNotHydratedAgain() {
        var engine = mock(WorkflowEngineClient.class);
        when(engine.isAvailable()).thenReturn(true);
        when(engine.getTaskById("task")).thenReturn(Optional.of(Map.of("taskId", "task", "processInstanceId", "case")));
        var repository = mock(ProcessInstanceRepository.class);
        when(repository.existsById("case")).thenReturn(true);
        var hydration = mock(ProcessInstanceHydrationComponent.class);
        var detail = new TaskDetailQueryComponent(repository, engine, mock(EngineSubTableHydrator.class),
                mock(TaskActionService.class), mock(MiParticipantEnrichmentComponent.class), mock(RequestIdEnricher.class));
        ReflectionTestUtils.setField(detail, "hydration", hydration);

        detail.getTaskById("task");

        verify(hydration, never()).requireProcessInstance(any());
    }
}
