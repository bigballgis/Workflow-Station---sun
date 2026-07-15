package com.platform.common.mail;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmtpTransportPropertiesTest {

    @Test
    void apply_startTlsOnPort587() {
        Properties props = new Properties();
        SmtpTransportProperties.apply(props, "smtp.example.com", 587, true, true);
        assertEquals("true", props.get("mail.smtp.starttls.enable"));
        assertEquals("true", props.get("mail.smtp.starttls.required"));
        assertEquals("false", props.get("mail.smtp.ssl.enable"));
        assertEquals("smtp.example.com", props.get("mail.smtp.ssl.trust"));
    }

    @Test
    void apply_port25UsesStartTlsWhenTlsFlagTrue() {
        Properties props = new Properties();
        SmtpTransportProperties.apply(props, "internal-smtp-relay.corp.local", 25, true, true);
        assertEquals("true", props.get("mail.smtp.starttls.enable"));
        assertEquals("true", props.get("mail.smtp.starttls.required"));
        assertEquals("false", props.get("mail.smtp.ssl.enable"));
    }

    @Test
    void apply_port25PlainWhenTlsOff() {
        Properties props = new Properties();
        SmtpTransportProperties.apply(props, "internal-smtp-relay.corp.local", 25, false, true);
        assertEquals("false", props.get("mail.smtp.starttls.enable"));
        assertEquals("false", props.get("mail.smtp.ssl.enable"));
    }

    @Test
    void apply_implicitSslOnPort465() {
        Properties props = new Properties();
        SmtpTransportProperties.apply(props, "smtp.example.com", 465, true, true);
        assertEquals("true", props.get("mail.smtp.ssl.enable"));
        assertEquals("false", props.get("mail.smtp.starttls.enable"));
        assertEquals("smtp.example.com", props.get("mail.smtp.ssl.trust"));
    }

    @Test
    void apply_plainWhenTlsOff() {
        Properties props = new Properties();
        SmtpTransportProperties.apply(props, "relay.local", 25, false, false);
        assertEquals("false", props.get("mail.smtp.starttls.enable"));
        assertEquals("false", props.get("mail.smtp.ssl.enable"));
        assertEquals(null, props.get("mail.smtp.ssl.trust"));
    }

    @Test
    void resolveSslTrustHosts_mergesConfiguredHostWithEnvExtra() {
        assertEquals("primary-relay.corp.local,backup-relay.corp.local",
                SmtpTransportProperties.resolveSslTrustHosts("primary-relay.corp.local", "backup-relay.corp.local"));
    }

    @Test
    void applyInternalSslTrust_canDisableServerIdentityCheckViaEnv() {
        Properties props = new Properties();
        SmtpTransportProperties.applyInternalSslTrust(props, "relay.corp.local", null, "false");
        assertEquals("relay.corp.local", props.get("mail.smtp.ssl.trust"));
        assertEquals("false", props.get("mail.smtp.ssl.checkserveridentity"));
        assertEquals("false", props.get("mail.smtps.ssl.checkserveridentity"));
    }

    @Test
    void sslCheckServerIdentityEnabled_defaultsToTrueWhenUnset() {
        assertEquals(true, SmtpTransportProperties.sslCheckServerIdentityEnabled(null));
        assertEquals(true, SmtpTransportProperties.sslCheckServerIdentityEnabled(""));
    }
}
