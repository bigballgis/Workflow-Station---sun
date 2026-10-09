package com.workflow.email.inbound;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory per-rule backoff after IMAP / config failures.
 *
 * <p>Success cadence still uses {@code lastSyncedAt + pollIntervalSeconds}. Failures must not
 * advance that timestamp (it means last successful poll), so without this map a failed rule
 * with a null or stale {@code lastSyncedAt} would retry on every scheduler tick (30s) and
 * hammer the mailbox provider.
 *
 * <p>A failure belongs to the mailbox it happened on, not to the rule id: a redeploy that
 * rebinds the rule to another connection or folder keeps the id, and the new mailbox must be
 * polled right away instead of inheriting the old one's delay.
 *
 * <p>State is process-local: ShedLock already serializes polling to one replica; a failover
 * resets the circuit, which is acceptable (ops still get ERROR logs and exponential delay
 * on the active node).
 */
final class EmailMonitorPollBackoff {

    static final int DEFAULT_POLL_INTERVAL_SECONDS = 60;
    static final int MAX_BACKOFF_SECONDS = 15 * 60;
    static final int MAX_CONSECUTIVE_FAILURES = 8;

    private final ConcurrentMap<String, FailureState> failures = new ConcurrentHashMap<>();

    /**
     * @param mailbox identity of the mailbox the rule currently targets (connection + folder)
     */
    boolean shouldPoll(String ruleId, String mailbox, Instant now, Integer pollIntervalSeconds,
                       Instant lastSyncedAt) {
        FailureState state = failures.get(ruleId);
        if (state != null && !state.mailbox().equals(mailbox)) {
            failures.remove(ruleId, state);
            state = null;
        }
        if (state != null && now.isBefore(state.retryAfter())) {
            return false;
        }
        if (lastSyncedAt == null) {
            return true;
        }
        return now.isAfter(lastSyncedAt.plusSeconds(intervalSeconds(pollIntervalSeconds)));
    }

    void recordSuccess(String ruleId) {
        failures.remove(ruleId);
    }

    FailureState recordFailure(String ruleId, String mailbox, Instant now, Integer pollIntervalSeconds) {
        return failures.compute(ruleId, (id, prev) -> {
            int count = prev == null || !prev.mailbox().equals(mailbox) ? 1 : prev.consecutiveFailures() + 1;
            int delay = backoffSeconds(count, pollIntervalSeconds);
            return new FailureState(mailbox, count, now.plusSeconds(delay));
        });
    }

    int consecutiveFailures(String ruleId) {
        FailureState state = failures.get(ruleId);
        return state == null ? 0 : state.consecutiveFailures();
    }

    static int intervalSeconds(Integer pollIntervalSeconds) {
        if (pollIntervalSeconds == null || pollIntervalSeconds < 1) {
            return DEFAULT_POLL_INTERVAL_SECONDS;
        }
        return pollIntervalSeconds;
    }

    static int backoffSeconds(int consecutiveFailures, Integer pollIntervalSeconds) {
        int base = intervalSeconds(pollIntervalSeconds);
        int shift = Math.min(Math.max(consecutiveFailures, 1) - 1, 20);
        long delay = (long) base << shift;
        return (int) Math.min(delay, MAX_BACKOFF_SECONDS);
    }

    record FailureState(String mailbox, int consecutiveFailures, Instant retryAfter) {
        boolean atCap() {
            return consecutiveFailures >= MAX_CONSECUTIVE_FAILURES;
        }
    }
}
