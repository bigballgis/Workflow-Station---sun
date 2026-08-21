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
 * <p>State is process-local: ShedLock already serializes polling to one replica; a failover
 * resets the circuit, which is acceptable (ops still get ERROR logs and exponential delay
 * on the active node).
 */
final class EmailMonitorPollBackoff {

    static final int DEFAULT_POLL_INTERVAL_SECONDS = 60;
    static final int MAX_BACKOFF_SECONDS = 15 * 60;
    static final int MAX_CONSECUTIVE_FAILURES = 8;

    private final ConcurrentMap<String, FailureState> failures = new ConcurrentHashMap<>();

    boolean shouldPoll(String ruleId, Instant now, Integer pollIntervalSeconds, Instant lastSyncedAt) {
        FailureState state = failures.get(ruleId);
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

    FailureState recordFailure(String ruleId, Instant now, Integer pollIntervalSeconds) {
        FailureState next = failures.compute(ruleId, (id, prev) -> {
            int count = prev == null ? 1 : prev.consecutiveFailures() + 1;
            int delay = backoffSeconds(count, pollIntervalSeconds);
            return new FailureState(count, now.plusSeconds(delay));
        });
        return next;
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

    record FailureState(int consecutiveFailures, Instant retryAfter) {
        boolean atCap() {
            return consecutiveFailures >= MAX_CONSECUTIVE_FAILURES;
        }
    }
}
