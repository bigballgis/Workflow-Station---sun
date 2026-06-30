package com.workflow.email.inbound;

/**
 * Connection-resolved access parameters for reading a mailbox via IMAP.
 *
 * <p>Built by the scheduler from a synced email connection (provider preset host/port +
 * username + decrypted app password). Mirrors Power Automate's "shared mailbox" connection:
 * the mailbox and how to reach it are entirely connection-determined.
 */
public record MailboxAccess(
        String host,
        int port,
        boolean ssl,
        String username,
        String password
) {
}
