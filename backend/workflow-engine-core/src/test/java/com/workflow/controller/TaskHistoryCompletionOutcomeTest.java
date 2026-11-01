package com.workflow.controller;

import com.workflow.component.TaskManagerComponent;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.task.Comment;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TaskHistoryCompletionOutcomeTest {
    @Test void rejectionUsesTheTaskOutcomeAndKeepsTheUserComment() {
        var history = mock(HistoryService.class);
        var activities = mock(org.flowable.engine.history.HistoricActivityInstanceQuery.class, RETURNS_SELF);
        var taskQuery = mock(org.flowable.task.api.history.HistoricTaskInstanceQuery.class, RETURNS_SELF);
        var processQuery = mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class, RETURNS_SELF);
        when(history.createHistoricActivityInstanceQuery()).thenReturn(activities);
        when(history.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);
        when(history.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
        var tasks = mock(TaskService.class);
        var manager = mock(TaskManagerComponent.class);
        var activity = mock(HistoricActivityInstance.class);
        when(activity.getId()).thenReturn("history-review");
        when(activity.getActivityId()).thenReturn("renamedReview");
        when(activity.getActivityName()).thenReturn("Review");
        when(activity.getActivityType()).thenReturn("userTask");
        when(activity.getTaskId()).thenReturn("task");
        when(activity.getAssignee()).thenReturn("actor");
        when(activity.getStartTime()).thenReturn(new Date());
        when(activity.getEndTime()).thenReturn(new Date());
        when(activities.list()).thenReturn(List.of(activity));
        when(taskQuery.list()).thenReturn(List.of());
        var outcome = mock(Comment.class);
        when(outcome.getTaskId()).thenReturn("task");
        when(outcome.getType()).thenReturn("workflow-completion:REJECT");
        when(outcome.getFullMessage()).thenReturn("");
        var comment = mock(Comment.class);
        when(comment.getTaskId()).thenReturn("task");
        when(comment.getType()).thenReturn("comment");
        when(comment.getFullMessage()).thenReturn("Reviewer's explanation");
        when(tasks.getProcessInstanceComments("process")).thenReturn(List.of(comment, outcome));
        when(manager.resolveUserDisplayNames(any())).thenReturn(Map.of("actor", "Reviewer"));
        var assembler = new TaskHistoryAssembler(history, tasks, manager, mock(RepositoryService.class));
        var result = assembler.assembleProcessInstanceHistory("process");
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("operationType", "REJECT")
                .containsEntry("comment", "Reviewer's explanation");
    }
}
