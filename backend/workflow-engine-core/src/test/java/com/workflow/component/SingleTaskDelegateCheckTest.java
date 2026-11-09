package com.workflow.component;

import com.platform.security.service.PortalEntryService;
import com.workflow.client.AdminCenterClient;
import com.workflow.entity.ExtendedTaskInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A single-task delegation is in effect only while the holder and the acting delegate have Portal entry (D-55). */
@ExtendWith(MockitoExtension.class)
class SingleTaskDelegateCheckTest {

    @Mock
    private TaskActionService taskActionService;
    @Mock
    private AdminCenterClient adminCenterClient;
    @Mock
    private PortalEntryService portalEntryService;
    @InjectMocks
    private SingleTaskDelegateCheck check;

    private final ExtendedTaskInfo delegated = ExtendedTaskInfo.builder().taskId("t1")
            .delegatedTo("delegate").delegatedBy("holder").build();

    @Test
    void theNamedDelegateActsWhileBothHaveEntry() {
        when(taskActionService.engineActorMatchesPortalUser("delegate", "delegate")).thenReturn(true);
        when(portalEntryService.withPortalEntry(anyCollection())).thenReturn(Set.of("delegate", "holder"));

        assertThat(check.mayActFor(delegated, "delegate", "holder")).isTrue();
    }

    @Test
    void aDelegateWithoutEntryDoesNotActUntilTheEntryIsBack() {
        when(taskActionService.engineActorMatchesPortalUser("delegate", "delegate")).thenReturn(true);
        when(portalEntryService.withPortalEntry(anyCollection())).thenReturn(Set.of("holder"));

        assertThat(check.mayActFor(delegated, "delegate", "holder")).isFalse();
    }

    @Test
    void aHolderWithoutEntryStopsTheDelegationToo() {
        when(taskActionService.engineActorMatchesPortalUser("delegate", "delegate")).thenReturn(true);
        when(portalEntryService.withPortalEntry(anyCollection())).thenReturn(Set.of("delegate"));

        assertThat(check.mayActFor(delegated, "delegate", "holder")).isFalse();
    }

    @Test
    void theDelegatorIsWhoDelegatedNotTheCurrentHolder() {
        when(taskActionService.engineActorMatchesPortalUser("delegate", "delegate")).thenReturn(true);
        when(portalEntryService.withPortalEntry(anyCollection())).thenReturn(Set.of("delegate", "manager"));

        // "holder" delegated and left; the manager holds the task now: the delegation is not in effect.
        assertThat(check.mayActFor(delegated, "delegate", "manager")).isFalse();
    }

    @Test
    void someoneElseIsNoDelegateAndCostsNoEntryLookup() {
        lenient().when(taskActionService.engineActorMatchesPortalUser("delegate", "other")).thenReturn(false);

        assertThat(check.mayActFor(delegated, "other", "holder")).isFalse();
        verifyNoInteractions(portalEntryService);
    }
}
