package com.workflow.util;

import java.util.Map;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TaskCompletionCommentsTest {
    @ParameterizedTest @ValueSource(strings = {"APPROVE", "REJECT", "SUBMIT"})
    void keepsEachTaskOutcomeIndependentFromItsDisplayLabel(String outcome) {
        var tasks = mock(TaskService.class);
        TaskCompletionComments.append(tasks, "task", "process", Map.of("action", outcome));
        verify(tasks).addComment("task", "process", "workflow-completion:" + outcome, "");
        verifyNoMoreInteractions(tasks);
    }

    @Test void missingActionKeepsLegacyUserCommentsWithoutFabricatingAnOutcome() {
        var tasks = mock(TaskService.class);
        TaskCompletionComments.appendUserComment(tasks, "task", "process", Map.of("approverComments", "Explanation"));
        verify(tasks).addComment("task", "process", "Explanation");
        verifyNoMoreInteractions(tasks);
    }

    @Test void invalidStoredOutcomeIsNotReportedAsAnApproval() {
        assertThatThrownBy(() -> TaskCompletionComments.requireOutcome("custom-action"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Invalid task completion outcome");
    }
}
