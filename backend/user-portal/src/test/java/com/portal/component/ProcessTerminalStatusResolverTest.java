package com.portal.component;

import com.portal.entity.ProcessInstance;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rejection and an approval finish the same way in Flowable terms, so the outcome has to come
 * from the approval decision the platform recorded — not from the name a designer gave the end
 * event. These tests pin that, plus the "don't guess" behaviour when no decision was recorded.
 */
class ProcessTerminalStatusResolverTest {

    private final ProcessTerminalStatusResolver resolver = new ProcessTerminalStatusResolver();

    private static ProcessInstance instanceWith(Map<String, Object> variables) {
        ProcessInstance instance = new ProcessInstance();
        instance.setId("pi-1");
        instance.setVariables(variables);
        return instance;
    }

    @Test
    void rejectedApprovalYieldsRejectedStatus() {
        ProcessInstance instance = instanceWith(Map.of("approvalStatus", "REJECTED"));

        assertThat(resolver.resolveFinishedStatus(instance))
                .isEqualTo(ProcessTerminalStatusResolver.STATUS_REJECTED);
        assertThat(resolver.endedInRejection(instance)).isTrue();
    }

    @Test
    void approvedApprovalYieldsCompletedStatus() {
        ProcessInstance instance = instanceWith(Map.of("approvalStatus", "APPROVED"));

        assertThat(resolver.resolveFinishedStatus(instance))
                .isEqualTo(ProcessTerminalStatusResolver.STATUS_COMPLETED);
        assertThat(resolver.endedInRejection(instance)).isFalse();
    }

    /** Plenty of processes finish without any approval step; that is a completion, not a rejection. */
    @Test
    void missingApprovalStatusYieldsCompleted() {
        assertThat(resolver.resolveFinishedStatus(instanceWith(new HashMap<>())))
                .isEqualTo(ProcessTerminalStatusResolver.STATUS_COMPLETED);
        assertThat(resolver.resolveFinishedStatus(instanceWith(null)))
                .isEqualTo(ProcessTerminalStatusResolver.STATUS_COMPLETED);
        assertThat(resolver.resolveFinishedStatus(null))
                .isEqualTo(ProcessTerminalStatusResolver.STATUS_COMPLETED);
    }

    @Test
    void approvalStatusIsMatchedCaseInsensitivelyAndTrimmed() {
        assertThat(resolver.endedInRejection(instanceWith(Map.of("approvalStatus", "rejected")))).isTrue();
        assertThat(resolver.endedInRejection(instanceWith(Map.of("approvalStatus", "  REJECTED  ")))).isTrue();
    }

    @Test
    void nonStringApprovalStatusIsNotTreatedAsRejection() {
        assertThat(resolver.endedInRejection(instanceWith(Map.of("approvalStatus", 42)))).isFalse();
    }

    /**
     * The end event's name must not influence the outcome: this is exactly the guess that made
     * "Declined" / "不通过" invisible to the old keyword matcher.
     */
    @Test
    void endEventNamingDoesNotAffectTheOutcome() {
        Map<String, Object> rejectedButOddlyNamed = new HashMap<>();
        rejectedButOddlyNamed.put("approvalStatus", "REJECTED");
        rejectedButOddlyNamed.put("lastActivityName", "Finished");

        Map<String, Object> approvedButNamedRejected = new HashMap<>();
        approvedButNamedRejected.put("approvalStatus", "APPROVED");
        approvedButNamedRejected.put("lastActivityName", "Rejected");

        assertThat(resolver.resolveFinishedStatus(instanceWith(rejectedButOddlyNamed)))
                .isEqualTo(ProcessTerminalStatusResolver.STATUS_REJECTED);
        assertThat(resolver.resolveFinishedStatus(instanceWith(approvedButNamedRejected)))
                .isEqualTo(ProcessTerminalStatusResolver.STATUS_COMPLETED);
    }

    @Test
    void everyEndedStatusCountsAsTerminal() {
        assertThat(ProcessTerminalStatusResolver.isTerminal("COMPLETED")).isTrue();
        assertThat(ProcessTerminalStatusResolver.isTerminal("REJECTED")).isTrue();
        assertThat(ProcessTerminalStatusResolver.isTerminal("WITHDRAWN")).isTrue();
        assertThat(ProcessTerminalStatusResolver.isTerminal("RUNNING")).isFalse();
        assertThat(ProcessTerminalStatusResolver.isTerminal(null)).isFalse();
    }

    @Test
    void onlyRunningCountsAsRunning() {
        assertThat(ProcessTerminalStatusResolver.isRunning("RUNNING")).isTrue();
        assertThat(ProcessTerminalStatusResolver.isRunning("REJECTED")).isFalse();
        assertThat(ProcessTerminalStatusResolver.isRunning(null)).isFalse();
    }
}
