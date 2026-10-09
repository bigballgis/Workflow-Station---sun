package com.workflow.email.inbound;

/**
 * Thrown when an inbound email could not be delivered for a reason that may clear by itself
 * (user-portal or admin-center unavailable, internal token not yet configured).
 *
 * <p>The scheduler reacts by holding the UID cursor so the message is re-fetched on a later poll,
 * instead of the message being written to the idempotency ledger and skipped forever.
 */
public class EmailMonitorRetryableException extends RuntimeException {

    private final int attempt;
    private final int maxAttempts;

    public EmailMonitorRetryableException(String reason, int attempt, int maxAttempts) {
        super(reason);
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
    }

    public int attempt() {
        return attempt;
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}
