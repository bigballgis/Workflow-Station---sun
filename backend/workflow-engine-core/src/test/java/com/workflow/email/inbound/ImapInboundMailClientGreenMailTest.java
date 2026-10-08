package com.workflow.email.inbound;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.platform.common.mail.ImapTransportProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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

        FetchResult baseline = client.fetchNew(access, "INBOX", null, 20, props);
        assertThat(baseline.messages()).isEmpty();
        assertThat(baseline.nextCursor()).isNotBlank();

        GreenMailUtil.sendTextEmail(
                "monitor@corp.test", "sender@corp.test", "New case", "please create case",
                greenMail.getSmtp().getServerSetup());

        FetchResult next = client.fetchNew(access, "INBOX", baseline.nextCursor(), 20, props);
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

        FetchResult reseeded = client.fetchNew(access, "INBOX", "9999", 20, props);
        assertThat(reseeded.messages()).isEmpty();
        assertThat(reseeded.nextCursor()).isNotBlank().isNotEqualTo("9999");

        GreenMailUtil.sendTextEmail(
                "monitor@corp.test", "sender@corp.test", "After rebind", "please create case",
                greenMail.getSmtp().getServerSetup());

        FetchResult next = client.fetchNew(access, "INBOX", reseeded.nextCursor(), 20, props);
        assertThat(next.messages()).hasSize(1);
        assertThat(next.messages().get(0).subject()).isEqualTo("After rebind");
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

        FetchResult baseline = client.fetchNew(access, "INBOX", null, 20, props);
        long uidValidity = Long.parseLong(baseline.nextCursor().split(":")[0]);

        // Same UID, declared under a UIDVALIDITY this folder never had.
        FetchResult reseeded = client.fetchNew(access, "INBOX", (uidValidity + 1) + ":0", 20, props);

        assertThat(reseeded.messages()).isEmpty();
        assertThat(reseeded.nextCursor()).isEqualTo(baseline.nextCursor());
    }
}
