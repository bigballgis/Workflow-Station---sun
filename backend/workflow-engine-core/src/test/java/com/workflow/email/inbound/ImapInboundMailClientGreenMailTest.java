package com.workflow.email.inbound;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.platform.common.mail.ImapTransportProperties;
import com.workflow.email.extract.EmailMessage;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live IMAP against a local server with a self-signed cert — same class of failure as
 * corporate Exchange (internal CA / CN mismatch). The test builds session properties
 * itself so production IMAP code never exposes an identity-check override.
 */
class ImapInboundMailClientGreenMailTest {

    private static final ServerSetup IMAPS = new ServerSetup(0, "localhost", ServerSetup.PROTOCOL_IMAPS);
    private static final ServerSetup SMTP = new ServerSetup(0, "localhost", ServerSetup.PROTOCOL_SMTP);

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(new ServerSetup[] {SMTP, IMAPS});

    @Test
    void fetchNew_baselineThenNewMailOnImapsWithInternalTrust() {
        greenMail.setUser("monitor@corp.test", "secret");
        ImapInboundMailClient client = new ImapInboundMailClient();
        int port = greenMail.getImaps().getPort();
        MailboxAccess access = new MailboxAccess("localhost", port, true, "monitor@corp.test", "secret");
        Properties props = ImapTransportProperties.apply("localhost", port, true, "imaps");
        props.put("mail.imaps.ssl.checkserveridentity", "false");

        FetchResult baseline = client.fetchNew(access, inbox(null), props);
        assertThat(baseline.messages()).isEmpty();
        assertThat(baseline.nextCursor()).isNotBlank();

        GreenMailUtil.sendTextEmail(
                "monitor@corp.test", "sender@corp.test", "New case", "please create case",
                greenMail.getSmtp().getServerSetup());

        FetchResult next = client.fetchNew(access, inbox(baseline.nextCursor()), props);
        assertThat(next.messages()).hasSize(1);
        assertThat(next.messages().get(0).subject()).isEqualTo("New case");
    }

    /**
     * Rebinding a monitor to another mailbox keeps the old UID cursor, which can sit beyond the
     * new mailbox's UID space. {@code UID FETCH <ahead>:*} then matches only older messages that
     * the {@code uid > cursor} filter drops, so every poll reported zero forever.
     */
    @Test
    void fetchNew_cursorAheadOfMailboxUidSpace_reseedsBaselineAndResumes() {
        greenMail.setUser("monitor@corp.test", "secret");
        ImapInboundMailClient client = new ImapInboundMailClient();
        int port = greenMail.getImaps().getPort();
        MailboxAccess access = new MailboxAccess("localhost", port, true, "monitor@corp.test", "secret");
        Properties props = ImapTransportProperties.apply("localhost", port, true, "imaps");
        props.put("mail.imaps.ssl.checkserveridentity", "false");

        GreenMailUtil.sendTextEmail(
                "monitor@corp.test", "sender@corp.test", "Before rebind", "old mail",
                greenMail.getSmtp().getServerSetup());

        FetchResult reseeded = client.fetchNew(access, inbox("9999"), props);
        assertThat(reseeded.messages()).isEmpty();
        assertThat(reseeded.nextCursor()).isNotBlank().isNotEqualTo("9999");

        GreenMailUtil.sendTextEmail(
                "monitor@corp.test", "sender@corp.test", "After rebind", "please create case",
                greenMail.getSmtp().getServerSetup());

        FetchResult next = client.fetchNew(access, inbox(reseeded.nextCursor()), props);
        assertThat(next.messages()).hasSize(1);
        assertThat(next.messages().get(0).subject()).isEqualTo("After rebind");
    }

    /**
     * The first poll after a deploy or rebind can come minutes later (poll interval, backoff).
     * Mail received in between belongs to the rule; mail from before the deploy does not.
     */
    @Test
    void fetchNew_blankCursorWithWatchFrom_returnsOnlyMailReceivedSinceDeploy() throws Exception {
        greenMail.setUser("monitor@corp.test", "secret");
        ImapInboundMailClient client = new ImapInboundMailClient();
        int port = greenMail.getImaps().getPort();
        MailboxAccess access = new MailboxAccess("localhost", port, true, "monitor@corp.test", "secret");
        Properties props = ImapTransportProperties.apply("localhost", port, true, "imaps");
        props.put("mail.imaps.ssl.checkserveridentity", "false");

        GreenMailUtil.sendTextEmail(
                "monitor@corp.test", "sender@corp.test", "Before deploy", "history",
                greenMail.getSmtp().getServerSetup());
        Thread.sleep(1100);
        Instant deployedAt = Instant.now();
        Thread.sleep(1100);
        GreenMailUtil.sendTextEmail(
                "monitor@corp.test", "sender@corp.test", "After deploy", "please create case",
                greenMail.getSmtp().getServerSetup());

        FetchResult first = client.fetchNew(access, inbox(null, deployedAt), props);

        assertThat(first.messages()).extracting(EmailMessage::subject).containsExactly("After deploy");
        FetchResult next = client.fetchNew(access, inbox(first.nextCursor()), props);
        assertThat(next.messages()).isEmpty();
    }

    @Test
    void fetchNew_blankCursorWithNothingSinceDeploy_seedsSameBaselineAsBefore() {
        greenMail.setUser("monitor@corp.test", "secret");
        ImapInboundMailClient client = new ImapInboundMailClient();
        int port = greenMail.getImaps().getPort();
        MailboxAccess access = new MailboxAccess("localhost", port, true, "monitor@corp.test", "secret");
        Properties props = ImapTransportProperties.apply("localhost", port, true, "imaps");
        props.put("mail.imaps.ssl.checkserveridentity", "false");

        GreenMailUtil.sendTextEmail(
                "monitor@corp.test", "sender@corp.test", "Before deploy", "history",
                greenMail.getSmtp().getServerSetup());

        FetchResult first = client.fetchNew(access, inbox(null, Instant.now().plusSeconds(60)), props);

        assertThat(first.messages()).isEmpty();
        assertThat(first.nextCursor()).isEqualTo(client.fetchNew(access, inbox(null), props).nextCursor());
    }

    @Test
    void fetchNew_cursorFromAnotherUidValidity_reseedsWithoutReplayingHistory() {
        greenMail.setUser("monitor@corp.test", "secret");
        ImapInboundMailClient client = new ImapInboundMailClient();
        int port = greenMail.getImaps().getPort();
        MailboxAccess access = new MailboxAccess("localhost", port, true, "monitor@corp.test", "secret");
        Properties props = ImapTransportProperties.apply("localhost", port, true, "imaps");
        props.put("mail.imaps.ssl.checkserveridentity", "false");

        GreenMailUtil.sendTextEmail(
                "monitor@corp.test", "sender@corp.test", "History", "must not be replayed",
                greenMail.getSmtp().getServerSetup());

        FetchResult baseline = client.fetchNew(access, inbox(null), props);
        long uidValidity = Long.parseLong(baseline.nextCursor().split(":")[0]);

        // Same UID, declared under a UIDVALIDITY this folder never had.
        FetchResult reseeded = client.fetchNew(access, inbox((uidValidity + 1) + ":0"), props);

        assertThat(reseeded.messages()).isEmpty();
        assertThat(reseeded.nextCursor()).isEqualTo(baseline.nextCursor());
    }

    /**
     * A message that cannot be mapped (corrupt MIME, UID expunged between the listing and the
     * read) used to abort the whole fetch. Because the scheduler deliberately keeps the cursor on
     * fetch failure, the rule then stalled on that one message forever and every later email was
     * missed. The poison UID must be consumed instead.
     */
    @Test
    void fetchNew_unreadableMessage_isSkippedSoLaterMailStillArrives() {
        greenMail.setUser("monitor@corp.test", "secret");
        int port = greenMail.getImaps().getPort();
        MailboxAccess access = new MailboxAccess("localhost", port, true, "monitor@corp.test", "secret");
        Properties props = ImapTransportProperties.apply("localhost", port, true, "imaps");
        props.put("mail.imaps.ssl.checkserveridentity", "false");

        ImapInboundMailClient client = new ImapInboundMailClient();
        FetchResult baseline = client.fetchNew(access, inbox(null), props);

        GreenMailUtil.sendTextEmail(
                "monitor@corp.test", "sender@corp.test", "Poison", "cannot be parsed",
                greenMail.getSmtp().getServerSetup());
        GreenMailUtil.sendTextEmail(
                "monitor@corp.test", "sender@corp.test", "Good", "please create case",
                greenMail.getSmtp().getServerSetup());

        ImapInboundMailClient failing = new ImapInboundMailClient() {
            @Override
            EmailMessage toEmailMessage(Message message, long uid) throws Exception {
                if ("Poison".equals(message.getSubject())) {
                    throw new MessagingException("corrupt MIME");
                }
                return super.toEmailMessage(message, uid);
            }
        };

        FetchResult result = failing.fetchNew(access, inbox(baseline.nextCursor()), props);

        assertThat(result.messages()).extracting(EmailMessage::subject).containsExactly("Good");

        // Cursor moved past the poison message, so the next poll does not stall on it again.
        FetchResult after = client.fetchNew(access, inbox(result.nextCursor()), props);
        assertThat(after.messages()).isEmpty();
    }

    private static ImapInboundMailClient.FetchRequest inbox(String cursor) {
        return inbox(cursor, null);
    }

    private static ImapInboundMailClient.FetchRequest inbox(String cursor, Instant watchFrom) {
        return new ImapInboundMailClient.FetchRequest("INBOX", cursor, watchFrom, 20);
    }
}
