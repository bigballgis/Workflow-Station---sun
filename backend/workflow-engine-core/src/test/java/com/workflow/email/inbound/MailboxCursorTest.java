package com.workflow.email.inbound;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailboxCursorTest {

    @Test
    void parse_blankOrJunk_seedsBaseline() {
        assertThat(MailboxCursor.parse(null)).isNull();
        assertThat(MailboxCursor.parse("  ")).isNull();
        assertThat(MailboxCursor.parse("not-a-uid")).isNull();
        assertThat(MailboxCursor.parse("12:")).isNull();
    }

    @Test
    void parse_bareUid_isLegacyCursorWithUnknownValidity() {
        MailboxCursor cursor = MailboxCursor.parse(" 26 ");

        assertThat(cursor).isNotNull();
        assertThat(cursor.uidValidity()).isEqualTo(MailboxCursor.UNKNOWN_VALIDITY);
        assertThat(cursor.lastUid()).isEqualTo(26);
    }

    @Test
    void parse_compositeCursor_keepsBothParts() {
        MailboxCursor cursor = MailboxCursor.parse("98765:26");

        assertThat(cursor).isNotNull();
        assertThat(cursor.uidValidity()).isEqualTo(98765);
        assertThat(cursor.lastUid()).isEqualTo(26);
    }

    @Test
    void format_omitsValidityWhenServerDoesNotReportIt() {
        assertThat(MailboxCursor.format(98765, 26)).isEqualTo("98765:26");
        assertThat(MailboxCursor.format(0, 26)).isEqualTo("26");
        assertThat(MailboxCursor.format(-1, 26)).isEqualTo("26");
    }

    @Test
    void staleReason_sameValidityWithinUidSpace_isUsable() {
        assertThat(MailboxCursor.parse("98765:26").staleReason(98765, 40)).isNull();
        // lastUid == UIDNEXT-1 means "caught up", not stale.
        assertThat(MailboxCursor.parse("98765:26").staleReason(98765, 27)).isNull();
    }

    @Test
    void staleReason_validityChanged_isStale() {
        assertThat(MailboxCursor.parse("98765:26").staleReason(12345, 400))
                .contains("UIDVALIDITY changed");
    }

    @Test
    void staleReason_cursorAheadOfMailbox_isStale() {
        // Cursor carried over from a mailbox with a larger UID space (the rebind failure).
        assertThat(MailboxCursor.parse("26").staleReason(98765, 6))
                .contains("ahead of the mailbox UID space");
        assertThat(MailboxCursor.parse("98765:26").staleReason(98765, 6))
                .contains("ahead of the mailbox UID space");
    }

    @Test
    void staleReason_unknownUidNext_cannotJudgeUidSpace() {
        assertThat(MailboxCursor.parse("26").staleReason(98765, -1)).isNull();
    }
}
