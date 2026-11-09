package com.portal.component;

import com.platform.common.i18n.I18nService;
import com.portal.dto.TaskInfo;
import com.portal.exception.PortalException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Only tasks the engine marks delegable can be delegated (D-56), and a task someone holds only by its holder (D-60);
 * others get a business error, not a 500.
 */
class TaskDelegationGuardTest {

    private final I18nService i18nService = mock(I18nService.class);
    private final TaskDelegationGuard guard = new TaskDelegationGuard(i18nService);

    @Test
    void aDelegableTaskPasses() {
        assertThatCode(() -> guard.requireDelegable(TaskInfo.builder().taskId("t1").delegable(true).build()))
                .doesNotThrowAnyException();
    }

    @Test
    void anyOtherTaskIsRefusedWith400AndTheLocalizedReason() {
        when(i18nService.getMessage("portal.task_not_delegable")).thenReturn("This task cannot be delegated");

        assertThatThrownBy(() -> guard.requireDelegable(TaskInfo.builder().taskId("t2").build()))
                .isInstanceOfSatisfying(PortalException.class, e -> {
                    org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo("400");
                    org.assertj.core.api.Assertions.assertThat(e.getMessage()).isEqualTo("This task cannot be delegated");
                });
    }

    @Test
    void aTaskSomeoneHoldsIsDelegatedByItsHolderOnly() {
        when(i18nService.getMessage("portal.task_delegate_holder_only")).thenReturn("Only the holder can delegate it");
        TaskInfo held = TaskInfo.builder().taskId("t3").assignee("holder").build();

        assertThatCode(() -> guard.requireMayDelegate(held, "holder", null)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireMayDelegate(TaskInfo.builder().taskId("t4").build(), "member", null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireMayDelegate(held, "delegate", null))
                .isInstanceOfSatisfying(PortalException.class, e -> {
                    org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo("403");
                    org.assertj.core.api.Assertions.assertThat(e.getMessage()).isEqualTo("Only the holder can delegate it");
                });
    }
}
