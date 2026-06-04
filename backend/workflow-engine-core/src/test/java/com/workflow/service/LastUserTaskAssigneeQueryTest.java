package com.workflow.service;

import org.flowable.engine.HistoryService;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LastUserTaskAssigneeQuery")
class LastUserTaskAssigneeQueryTest {

    @Mock
    private HistoryService historyService;

    @InjectMocks
    private LastUserTaskAssigneeQuery query;

    @Test
    @DisplayName("findLastCompletedAssigneeForActivity returns most recent finished assignee for activity")
    void findLastCompletedAssigneeForActivity_returnsAssignee() {
        HistoricTaskInstance older = mock(HistoricTaskInstance.class);

        HistoricTaskInstance recent = mock(HistoricTaskInstance.class);
        when(recent.getAssignee()).thenReturn("user-dev");

        HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("pi-1")).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("Activity_Submit")).thenReturn(taskQuery);
        when(taskQuery.finished()).thenReturn(taskQuery);
        when(taskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(recent, older));

        Optional<String> result = query.findLastCompletedAssigneeForActivity("pi-1", "Activity_Submit");

        assertThat(result).contains("user-dev");
    }

    @Test
    @DisplayName("findLastCompletedAssigneeForActivity returns empty when no historic tasks")
    void findLastCompletedAssigneeForActivity_empty() {
        HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId(anyString())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(anyString())).thenReturn(taskQuery);
        when(taskQuery.finished()).thenReturn(taskQuery);
        when(taskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(taskQuery);
        when(taskQuery.desc()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());

        assertThat(query.findLastCompletedAssigneeForActivity("pi-1", "Activity_Submit")).isEmpty();
    }
}
