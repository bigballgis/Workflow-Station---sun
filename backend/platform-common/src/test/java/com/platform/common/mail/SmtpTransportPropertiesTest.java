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
    }

    @Test
    void apply_implicitSslOnPort465() {
        Properties props = new Properties();
        SmtpTransportProperties.apply(props, "smtp.example.com", 465, true, true);
        assertEquals("true", props.get("mail.smtp.ssl.enable"));
        assertEquals("false", props.get("mail.smtp.starttls.enable"));
    }

    @Test
    void apply_plainWhenTlsOff() {
        Properties props = new Properties();
        SmtpTransportProperties.apply(props, "relay.local", 25, false, false);
        assertEquals("false", props.get("mail.smtp.starttls.enable"));
        assertEquals("false", props.get("mail.smtp.ssl.enable"));
    }
}
