package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.entity.ProcessHistory;
import com.portal.repository.ProcessHistoryRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubTableBatchHistoryTimelineIsolationTest {
    @Test void localTaskHistoryFallbackDoesNotTreatObservationsAsBpmnActivities() {
        var engine = mock(WorkflowEngineClient.class);
        var repository = mock(ProcessHistoryRepository.class);
        when(engine.isAvailable()).thenReturn(true);
        when(engine.getProcessInstanceHistory("synthetic-instance")).thenReturn(Optional.empty());
        when(repository.findByProcessInstanceIdOrderByOperationTimeAsc("synthetic-instance")).thenReturn(List.of(
                ProcessHistory.builder().id(1L).operationType("SUBMIT").activityName("Intake").build(),
                ProcessHistory.builder().id(2L).operationType(SubTableBatchAssignmentHistory.OPERATION).build()));
        var timeline = new TaskHistoryComponent(engine, repository).getTaskHistory("synthetic-task", "synthetic-instance");
        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).getOperationType()).isEqualTo("SUBMIT");
    }
}
