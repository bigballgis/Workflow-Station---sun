package com.workflow.email.inbound;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EmailMonitorPollBackoffTest {

    private final EmailMonitorPollBackoff backoff = new EmailMonitorPollBackoff();

    @Test
    void firstPollIsDueWhenNeverSynced() {
        Instant now = Instant.parse("2026-08-17T10:00:00Z");
        assertThat(backoff.shouldPoll("r1", now, 60, null)).isTrue();
    }

    @Test
    void successCadenceUsesLastSyncedAtNotSchedulerTick() {
        Instant synced = Instant.parse("2026-08-17T10:00:00Z");
        assertThat(backoff.shouldPoll("r1", synced.plusSeconds(30), 60, synced)).isFalse();
        assertThat(backoff.shouldPoll("r1", synced.plusSeconds(61), 60, synced)).isTrue();
    }

    @Test
    void failureDoesNotAdvanceUntilBackoffElapses() {
        Instant t0 = Instant.parse("2026-08-17T10:00:00Z");
        EmailMonitorPollBackoff.FailureState state = backoff.recordFailure("r1", t0, 60);
        assertThat(state.consecutiveFailures()).isEqualTo(1);
        assertThat(state.retryAfter()).isEqualTo(t0.plusSeconds(60));
        assertThat(backoff.shouldPoll("r1", t0.plusSeconds(30), 60, null)).isFalse();
        assertThat(backoff.shouldPoll("r1", t0.plusSeconds(61), 60, null)).isTrue();
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
        backoff.recordFailure("r1", t0, 60);
        backoff.recordSuccess("r1");
        Instant synced = t0.plusSeconds(5);
        assertThat(backoff.shouldPoll("r1", synced.plusSeconds(10), 60, synced)).isFalse();
        assertThat(backoff.consecutiveFailures("r1")).isZero();
    }
}
