package com.workflow.email.inbound;

/**
 * Reads newly-arrived mail from a connection-defined mailbox.
 *
 * <p>Implementations are selected by the connection (IMAP for app-password mailboxes like
 * QQ / 163 / Outlook / Gmail; OAuth API clients for Gmail/Outlook modern auth as a later option).
 */
public interface InboundMailClient {

    /**
     * Fetches messages newer than {@code cursor} from {@code folder}.
     *
     * @param access mailbox connection parameters
     * @param folder folder/label name (e.g. {@code INBOX})
     * @param cursor last persisted cursor; {@code null}/blank seeds a baseline and returns no messages
     * @param max    maximum messages to return in one poll
     * @return new messages and the advanced cursor
     */
    FetchResult fetchNew(MailboxAccess access, String folder, String cursor, int max);
}
