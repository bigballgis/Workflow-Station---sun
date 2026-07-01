package com.platform.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Applies SMTP transport settings from explicit host/port/TLS flags (no provider presets).
 * Port 465 uses implicit SSL; other ports use STARTTLS when {@code useTls} is true.
 */
public final class SmtpTransportProperties {

    private static final Logger log = LoggerFactory.getLogger(SmtpTransportProperties.class);

    /** Allow both TLS 1.2 and 1.3 so servers that only accept one still negotiate. */
    private static final String SSL_PROTOCOLS = "TLSv1.2 TLSv1.3";

    private SmtpTransportProperties() {
    }

    public static void apply(Properties props, String host, int port, boolean useTls, boolean auth) {
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.ssl.protocols", SSL_PROTOCOLS);
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");

        if (!useTls) {
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.ssl.enable", "false");
            log.info("[SMTP-CFG] host={} port={} mode=PLAIN auth={} protocols={}", host, port, auth, SSL_PROTOCOLS);
            return;
        }

        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
            log.info("[SMTP-CFG] host={} port={} mode=SSL auth={} protocols={}", host, port, auth, SSL_PROTOCOLS);
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.enable", "false");
            log.info("[SMTP-CFG] host={} port={} mode=STARTTLS auth={} protocols={}", host, port, auth, SSL_PROTOCOLS);
        }
    }

    /** Human-readable transport mode for logging / diagnostics. */
    public static String describeMode(int port, boolean useTls) {
        if (!useTls) {
            return "PLAIN";
        }
        return port == 465 ? "SSL" : "STARTTLS";
    }
}
