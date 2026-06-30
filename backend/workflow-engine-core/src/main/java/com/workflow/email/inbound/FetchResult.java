package com.workflow.email.inbound;

import com.workflow.email.extract.EmailMessage;

import java.util.List;

/**
 * Result of polling a mailbox: newly arrived messages plus the advanced cursor to persist.
 *
 * @param messages   new messages since the supplied cursor (may be empty)
 * @param nextCursor opaque cursor to store on the rule for the next poll (e.g. last IMAP UID)
 */
public record FetchResult(List<EmailMessage> messages, String nextCursor) {
}
