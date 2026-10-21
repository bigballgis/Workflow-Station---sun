package com.portal.component;

import com.platform.common.functionunit.FunctionUnitVersionException;
import com.portal.client.WorkflowEngineClient;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.TaskActionService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TaskDetailVersionExceptionTest {
    @Test void versionRoutingFailureMustReachTheAuditedHttpHandler() {
        var engine = mock(WorkflowEngineClient.class);
        when(engine.isAvailable()).thenReturn(true);
        when(engine.getTaskById("task")).thenReturn(Optional.of(Map.of("taskId", "task",
                "processInstanceId", "case", "actionIds", List.of("301"))));
        var actions = mock(TaskActionService.class);
        when(actions.getTaskActions("task")).thenThrow(new FunctionUnitVersionException("VERSION_RECOVERY_REQUIRED", "case"));
        var detail = new TaskDetailQueryComponent(mock(ProcessInstanceRepository.class), engine,
                mock(EngineSubTableHydrator.class), actions, mock(MiParticipantEnrichmentComponent.class), mock(RequestIdEnricher.class));
        assertThatThrownBy(() -> detail.getTaskById("task")).isInstanceOf(FunctionUnitVersionException.class);
    }
}
