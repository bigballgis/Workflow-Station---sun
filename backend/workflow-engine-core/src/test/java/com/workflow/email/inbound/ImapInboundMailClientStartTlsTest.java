package com.workflow.email.inbound;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImapInboundMailClientStartTlsTest {

    @Test
    void buildProps_enablesStartTlsWhenSslDisabled() {
        ImapInboundMailClient client = new ImapInboundMailClient();
        MailboxAccess access = new MailboxAccess("imap.corp.internal", 143, false, "user", "secret");

        Properties props = ReflectionTestUtils.invokeMethod(client, "buildProps", access, "imap");

        assertEquals("true", props.get("mail.imap.starttls.enable"));
        assertEquals("true", props.get("mail.imap.starttls.required"));
        assertEquals("false", props.get("mail.imap.ssl.enable"));
    }

    @Test
    void buildProps_doesNotForceStartTlsOnImplicitSsl() {
        ImapInboundMailClient client = new ImapInboundMailClient();
        MailboxAccess access = new MailboxAccess("imap.qq.com", 993, true, "user", "secret");

        Properties props = ReflectionTestUtils.invokeMethod(client, "buildProps", access, "imaps");

        assertEquals("false", props.get("mail.imaps.starttls.enable"));
        assertEquals("true", props.get("mail.imaps.ssl.enable"));
    }
}
