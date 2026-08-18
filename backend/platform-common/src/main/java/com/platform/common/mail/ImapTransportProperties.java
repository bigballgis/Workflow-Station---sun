package com.platform.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * IMAP session properties aligned with {@link SmtpTransportProperties} so corporate
 * mail that already sends (internal CA / STARTTLS) can also be polled.
 *
 * <p>Trust and identity env vars are the same as SMTP ({@code SMTP_SSL_TRUST},
 * {@code SMTP_SSL_CHECK_SERVER_IDENTITY}) — operators do not configure a second allowlist.
 * Production reads getenv only.
 */
public final class ImapTransportProperties {

    private static final Logger log = LoggerFactory.getLogger(ImapTransportProperties.class);
    private static final String SSL_PROTOCOLS = "TLSv1.2 TLSv1.3";

    private ImapTransportProperties() {
    }

    public static Properties apply(String host, int port, boolean ssl, String protocol) {
        Properties props = baseProps(host, port, ssl, protocol);
        applySslTrust(
                props,
                protocol,
                host,
                System.getenv(SmtpTransportProperties.ENV_SSL_TRUST),
                System.getenv(SmtpTransportProperties.ENV_SSL_CHECK_SERVER_IDENTITY));
        return props;
    }

    private static Properties baseProps(String host, int port, boolean ssl, String protocol) {
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", host);
        props.put("mail." + protocol + ".port", String.valueOf(port));
        props.put("mail." + protocol + ".connectiontimeout", "15000");
        props.put("mail." + protocol + ".timeout", "20000");
        props.put("mail." + protocol + ".ssl.protocols", SSL_PROTOCOLS);
        if (ssl) {
            props.put("mail." + protocol + ".ssl.enable", "true");
            props.put("mail." + protocol + ".starttls.enable", "false");
        } else {
            props.put("mail." + protocol + ".ssl.enable", "false");
            props.put("mail." + protocol + ".starttls.enable", "true");
            props.put("mail." + protocol + ".starttls.required", "true");
        }
        return props;
    }

    static void applySslTrust(
            Properties props,
            String protocol,
            String host,
            String extraTrustEnv,
            String checkIdentityEnv) {
        String trustHosts = SmtpTransportProperties.resolveSslTrustHosts(host, extraTrustEnv);
        if (trustHosts.isBlank()) {
            return;
        }
        props.put("mail." + protocol + ".ssl.trust", trustHosts);
        if (!SmtpTransportProperties.sslCheckServerIdentityEnabled(checkIdentityEnv)) {
            props.put("mail." + protocol + ".ssl.checkserveridentity", "false");
            log.warn("[IMAP-CFG] ssl.checkserveridentity=false via {}; use only for internal relays with CN mismatch",
                    SmtpTransportProperties.ENV_SSL_CHECK_SERVER_IDENTITY);
        }
    }
}
