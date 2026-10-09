package com.workflow.email.inbound;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EmailMonitorPollBackoffTest {

    private static final String MAILBOX_A = "conn-a|INBOX";
    private static final String MAILBOX_B = "conn-b|INBOX";

    private final EmailMonitorPollBackoff backoff = new EmailMonitorPollBackoff();

    @Test
    void firstPollIsDueWhenNeverSynced() {
        Instant now = Instant.parse("2026-08-17T10:00:00Z");
        assertThat(backoff.shouldPoll("r1", MAILBOX_A, now, 60, null)).isTrue();
    }

    @Test
    void successCadenceUsesLastSyncedAtNotSchedulerTick() {
        Instant synced = Instant.parse("2026-08-17T10:00:00Z");
        assertThat(backoff.shouldPoll("r1", MAILBOX_A, synced.plusSeconds(30), 60, synced)).isFalse();
        assertThat(backoff.shouldPoll("r1", MAILBOX_A, synced.plusSeconds(61), 60, synced)).isTrue();
    }

    @Test
    void failureDoesNotAdvanceUntilBackoffElapses() {
        Instant t0 = Instant.parse("2026-08-17T10:00:00Z");
        EmailMonitorPollBackoff.FailureState state = backoff.recordFailure("r1", MAILBOX_A, t0, 60);
        assertThat(state.consecutiveFailures()).isEqualTo(1);
        assertThat(state.retryAfter()).isEqualTo(t0.plusSeconds(60));
        assertThat(backoff.shouldPoll("r1", MAILBOX_A, t0.plusSeconds(30), 60, null)).isFalse();
        assertThat(backoff.shouldPoll("r1", MAILBOX_A, t0.plusSeconds(61), 60, null)).isTrue();
    }

    /**
     * The rule id survives a redeploy that switches the connection. Failures of the old mailbox
     * must not delay the first poll of the new one, or mail arriving meanwhile is never seen.
     */
    @Test
    void rebindToAnotherMailboxDropsBackoffOfThePreviousOne() {
        Instant t0 = Instant.parse("2026-08-17T10:00:00Z");
        for (int i = 0; i < EmailMonitorPollBackoff.MAX_CONSECUTIVE_FAILURES; i++) {
            backoff.recordFailure("r1", MAILBOX_A, t0, 60);
        }
        assertThat(backoff.shouldPoll("r1", MAILBOX_A, t0.plusSeconds(30), 60, null)).isFalse();

        assertThat(backoff.shouldPoll("r1", MAILBOX_B, t0.plusSeconds(30), 60, null)).isTrue();
        assertThat(backoff.consecutiveFailures("r1")).isZero();
    }

    @Test
    void backoffDoublesThenCaps() {
        assertThat(EmailMonitorPollBackoff.backoffSeconds(1, 60)).isEqualTo(60);
        assertThat(EmailMonitorPollBackoff.backoffSeconds(2, 60)).isEqualTo(120);
        assertThat(EmailMonitorPollBackoff.backoffSeconds(3, 60)).isEqualTo(240);
        assertThat(EmailMonitorPollBackoff.backoffSeconds(8, 60))
                .isEqualTo(EmailMonitorPollBackoff.MAX_BACKOFF_SECONDS);
        assertThat(EmailMonitorPollBackoff.backoffSeconds(20, 60))
                .isEqualTo(EmailMonitorPollBackoff.MAX_BACKOFF_SECONDS);
    }

    @Test
    void successClearsFailureSoIntervalUsesLastSyncedAt() {
        Instant t0 = Instant.parse("2026-08-17T10:00:00Z");
        backoff.recordFailure("r1", MAILBOX_A, t0, 60);
        backoff.recordSuccess("r1");
        Instant synced = t0.plusSeconds(5);
        assertThat(backoff.shouldPoll("r1", MAILBOX_A, synced.plusSeconds(10), 60, synced)).isFalse();
        assertThat(backoff.consecutiveFailures("r1")).isZero();
    }
}
