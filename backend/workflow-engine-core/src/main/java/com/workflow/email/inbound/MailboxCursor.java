package com.workflow.email.inbound;

import org.springframework.util.StringUtils;

/**
 * Incremental poll position for one mailbox: the IMAP {@code UIDVALIDITY} the UID was observed
 * under, plus the last processed UID.
 *
 * <p>A UID identifies a message only within one mailbox under one UIDVALIDITY (RFC 3501 §2.3.1.1).
 * Storing the validity next to the UID lets a poll recognise that the persisted position belongs
 * to a different UID space — rule rebound to another mailbox, mailbox recreated, server migrated —
 * and reseed a baseline instead of silently fetching nothing forever.
 *
 * <p>Wire format is {@code "<uidValidity>:<lastUid>"}. A bare number is a pre-UIDVALIDITY cursor
 * whose UID space is unknown.
 */
record MailboxCursor(long uidValidity, long lastUid) {

    static final long UNKNOWN_VALIDITY = -1L;
    private static final char SEPARATOR = ':';

    /**
     * @return {@code null} when no usable position is stored, which means "seed a baseline"
     */
    static MailboxCursor parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        int separator = text.indexOf(SEPARATOR);
        try {
            if (separator < 0) {
                return new MailboxCursor(UNKNOWN_VALIDITY, Long.parseLong(text));
            }
            return new MailboxCursor(
                    Long.parseLong(text.substring(0, separator)),
                    Long.parseLong(text.substring(separator + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Servers that do not report a usable UIDVALIDITY keep the bare-UID format. */
    static String format(long uidValidity, long lastUid) {
        return uidValidity > 0 ? uidValidity + String.valueOf(SEPARATOR) + lastUid : String.valueOf(lastUid);
    }

    /**
     * @param uidValidity UIDVALIDITY of the folder that is currently open
     * @param uidNext     folder UIDNEXT, or a non-positive value when the server does not report it
     * @return why this position cannot be trusted against that folder, or {@code null} when it can
     */
    String staleReason(long uidValidity, long uidNext) {
        if (this.uidValidity != UNKNOWN_VALIDITY && this.uidValidity != uidValidity) {
            return "UIDVALIDITY changed (cursor=" + this.uidValidity + " folder=" + uidValidity + ")";
        }
        if (uidNext > 0 && lastUid > uidNext - 1) {
            return "cursor is ahead of the mailbox UID space (cursor=" + lastUid + " uidNext=" + uidNext + ")";
        }
        return null;
    }
}
