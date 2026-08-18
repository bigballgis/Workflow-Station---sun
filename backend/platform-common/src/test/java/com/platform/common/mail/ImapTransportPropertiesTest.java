package com.platform.common.mail;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImapTransportPropertiesTest {

    @Test
    void apply_startTlsOnPort143MatchesSmtpTrust() {
        Properties imap = ImapTransportProperties.apply("mail.corp.internal", 143, false, "imap");
        Properties smtp = new Properties();
        SmtpTransportProperties.apply(smtp, "mail.corp.internal", 587, true, true);

        assertEquals("true", imap.get("mail.imap.starttls.enable"));
        assertEquals("true", imap.get("mail.imap.starttls.required"));
        assertEquals("false", imap.get("mail.imap.ssl.enable"));
        assertEquals("mail.corp.internal", imap.get("mail.imap.ssl.trust"));
        assertEquals(smtp.get("mail.smtp.ssl.trust"), imap.get("mail.imap.ssl.trust"));
        assertEquals("TLSv1.2 TLSv1.3", imap.get("mail.imap.ssl.protocols"));
    }

    @Test
    void apply_implicitSslOnPort993TrustsConfiguredHost() {
        Properties imap = ImapTransportProperties.apply("mail.corp.internal", 993, true, "imaps");

        assertEquals("true", imap.get("mail.imaps.ssl.enable"));
        assertEquals("false", imap.get("mail.imaps.starttls.enable"));
        assertEquals("mail.corp.internal", imap.get("mail.imaps.ssl.trust"));
        assertEquals("TLSv1.2 TLSv1.3", imap.get("mail.imaps.ssl.protocols"));
    }

    @Test
    void applySslTrust_canDisableServerIdentityCheckLikeSmtp() {
        Properties imap = new Properties();
        ImapTransportProperties.applySslTrust(imap, "imaps", "mail.corp.internal", null, "false");
        assertEquals("mail.corp.internal", imap.get("mail.imaps.ssl.trust"));
        assertEquals("false", imap.get("mail.imaps.ssl.checkserveridentity"));

        Properties smtp = new Properties();
        SmtpTransportProperties.applyInternalSslTrust(smtp, "mail.corp.internal", null, "false");
        assertEquals(smtp.get("mail.smtp.ssl.checkserveridentity"),
                imap.get("mail.imaps.ssl.checkserveridentity"));
    }

    @Test
    void applySslTrust_doesNotDisableIdentityCheckByDefault() {
        Properties imap = new Properties();
        ImapTransportProperties.applySslTrust(imap, "imaps", "imap.qq.com", null, null);
        assertNull(imap.get("mail.imaps.ssl.checkserveridentity"));
        assertEquals("imap.qq.com", imap.get("mail.imaps.ssl.trust"));
    }

    @Test
    void applyThenOverrideIdentityCheck_doesNotUseJvmSystemProperty() {
        Properties imap = ImapTransportProperties.apply("localhost", 3993, true, "imaps");
        ImapTransportProperties.applySslTrust(imap, "imaps", "localhost", null, "false");
        assertEquals("localhost", imap.get("mail.imaps.ssl.trust"));
        assertEquals("false", imap.get("mail.imaps.ssl.checkserveridentity"));
    }
}
