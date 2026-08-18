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
}
