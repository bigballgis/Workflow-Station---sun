package com.workflow.email.inbound;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory per-message attempt counter for transient delivery failures.
 *
 * <p>Writing a transient failure into {@code we_email_processed_messages} makes the message
 * permanently skipped by {@code existsByRuleUidAndMessageId}, so a single user-portal restart used
 * to cost one lost case per email in flight. Transient failures are counted here instead and the
 * message is retried while the UID cursor is held; only once {@link #MAX_ATTEMPTS} is reached is it
 * recorded FAILED, so the cursor can advance and later emails are not blocked behind it.
 *
 * <p>State is process-local for the same reason as {@link EmailMonitorPollBackoff}: ShedLock
 * serializes polling to one replica, and a failover merely grants a few extra attempts.
 */
final class EmailMonitorDeliveryRetry {

    static final int MAX_ATTEMPTS = 3;

    private final ConcurrentMap<String, Integer> attempts = new ConcurrentHashMap<>();

    /** @return how many times this message has now failed in a row, starting at 1 */
    int recordFailure(String ruleUid, String messageId) {
        return attempts.merge(key(ruleUid, messageId), 1, Integer::sum);
    }

    void clear(String ruleUid, String messageId) {
        attempts.remove(key(ruleUid, messageId));
    }

    int attempts(String ruleUid, String messageId) {
        return attempts.getOrDefault(key(ruleUid, messageId), 0);
    }

    private static String key(String ruleUid, String messageId) {
        return ruleUid + '\u0000' + messageId;
    }
}
