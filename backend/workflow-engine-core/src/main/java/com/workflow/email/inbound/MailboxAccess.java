package com.workflow.email.inbound;

/**
 * Connection-resolved access parameters for reading a mailbox via IMAP.
 *
 * <p>{@code username} is the IMAP login (service account). {@code mailboxAddress} is the
 * mailbox to open when it differs from the login (shared / bound mailbox). Null mailbox
 * means the login's primary inbox.
 */
public record MailboxAccess(
        String host,
        int port,
        boolean ssl,
        String username,
        String password,
        String mailboxAddress
) {
    public MailboxAccess(String host, int port, boolean ssl, String username, String password) {
        this(host, port, ssl, username, password, null);
    }
}
