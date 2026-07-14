package com.platform.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Applies SMTP transport settings from explicit host/port/TLS flags (no provider presets).
 * Port 465 uses implicit SSL; port 587 uses STARTTLS when {@code useTls} is true.
 * Port 25 with auth uses plain SMTP (no STARTTLS) — matches internal relays where
 * IT exempts port 25 from TLS upgrade.
 *
 * <p>When TLS is enabled, the configured SMTP host is added to {@code mail.smtp.ssl.trust}
 * so internal relays signed by a corporate CA (not in the JVM cacerts) can still connect.
 * Optional env overrides:
 * <ul>
 *   <li>{@code SMTP_SSL_TRUST} — extra comma-separated hostnames (appended to configured host)</li>
 *   <li>{@code SMTP_SSL_CHECK_SERVER_IDENTITY=false} — disable hostname/CN check when the cert
 *       subject does not match the connection host (internal relay only)</li>
 * </ul>
 */
public final class SmtpTransportProperties {

    private static final Logger log = LoggerFactory.getLogger(SmtpTransportProperties.class);

    /** Allow both TLS 1.2 and 1.3 so servers that only accept one still negotiate. */
    private static final String SSL_PROTOCOLS = "TLSv1.2 TLSv1.3";

    /** Env: extra trusted SMTP hostnames (comma-separated), merged with configured host. */
    static final String ENV_SSL_TRUST = "SMTP_SSL_TRUST";

    /** Env: set to {@code false} to disable {@code mail.smtp.ssl.checkserveridentity}. */
    static final String ENV_SSL_CHECK_SERVER_IDENTITY = "SMTP_SSL_CHECK_SERVER_IDENTITY";

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

        // Internal relay on port 25: authenticated plain SMTP, no STARTTLS.
        if (port == 25) {
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.ssl.enable", "false");
            log.info("[SMTP-CFG] host={} port={} mode=PLAIN auth={} (port 25 relay, TLS flag ignored)",
                    host, port, auth);
            return;
        }

        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
            applyInternalSslTrust(props, host);
            log.info("[SMTP-CFG] host={} port={} mode=SSL auth={} protocols={} sslTrust={} checkIdentity={}",
                    host, port, auth, SSL_PROTOCOLS, props.get("mail.smtp.ssl.trust"),
                    props.getOrDefault("mail.smtp.ssl.checkserveridentity", "true"));
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.enable", "false");
            applyInternalSslTrust(props, host);
            log.info("[SMTP-CFG] host={} port={} mode=STARTTLS auth={} protocols={} sslTrust={} checkIdentity={}",
                    host, port, auth, SSL_PROTOCOLS, props.get("mail.smtp.ssl.trust"),
                    props.getOrDefault("mail.smtp.ssl.checkserveridentity", "true"));
        }
    }

    /**
     * Trust the configured relay host for TLS/STARTTLS when the server cert chain is signed by
     * an internal CA not present in the JVM truststore (avoids SunCertPathBuilderException).
     */
    static void applyInternalSslTrust(Properties props, String host) {
        applyInternalSslTrust(props, host, System.getenv(ENV_SSL_TRUST), System.getenv(ENV_SSL_CHECK_SERVER_IDENTITY));
    }

    static void applyInternalSslTrust(
            Properties props, String host, String extraTrustEnv, String checkIdentityEnv) {
        String trustHosts = resolveSslTrustHosts(host, extraTrustEnv);
        if (trustHosts.isBlank()) {
            return;
        }
        props.put("mail.smtp.ssl.trust", trustHosts);
        props.put("mail.smtps.ssl.trust", trustHosts);
        if (!sslCheckServerIdentityEnabled(checkIdentityEnv)) {
            props.put("mail.smtp.ssl.checkserveridentity", "false");
            props.put("mail.smtps.ssl.checkserveridentity", "false");
            log.warn("[SMTP-CFG] ssl.checkserveridentity=false via {}; use only for internal relays with CN mismatch",
                    ENV_SSL_CHECK_SERVER_IDENTITY);
        }
    }

    static String resolveSslTrustHosts(String host) {
        return resolveSslTrustHosts(host, System.getenv(ENV_SSL_TRUST));
    }

    static String resolveSslTrustHosts(String host, String extraTrustEnv) {
        String trimmedHost = host != null ? host.trim() : "";
        if (extraTrustEnv == null || extraTrustEnv.isBlank()) {
            return trimmedHost;
        }
        String trimmedExtra = extraTrustEnv.trim();
        if (trimmedHost.isBlank()) {
            return trimmedExtra;
        }
        return trimmedHost + "," + trimmedExtra;
    }

    static boolean sslCheckServerIdentityEnabled() {
        return sslCheckServerIdentityEnabled(System.getenv(ENV_SSL_CHECK_SERVER_IDENTITY));
    }

    static boolean sslCheckServerIdentityEnabled(String envValue) {
        if (envValue == null || envValue.isBlank()) {
            return true;
        }
        return !"false".equalsIgnoreCase(envValue.trim());
    }

    /** Human-readable transport mode for logging / diagnostics. */
    public static String describeMode(int port, boolean useTls) {
        if (!useTls || port == 25) {
            return "PLAIN";
        }
        return port == 465 ? "SSL" : "STARTTLS";
    }
}
