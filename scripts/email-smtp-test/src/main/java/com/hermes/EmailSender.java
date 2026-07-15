package com.hermes;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * Standalone SMTP smoke test. All settings come from environment variables (see README.md).
 */
public class EmailSender {

    public static void main(String[] args) {
        String host = requireEnv("SMTP_HOST");
        String port = env("SMTP_PORT", "25");
        String sender = requireEnv("SMTP_SENDER");
        String recipient = requireEnv("SMTP_RECIPIENT");
        String username = env("SMTP_USERNAME", "");
        String password = System.getenv("SMTP_PASSWORD");
        boolean useAuth = Boolean.parseBoolean(env("SMTP_USE_AUTH", "true"));
        boolean useTls = resolveUseTls(port, useAuth);
        boolean debug = Boolean.parseBoolean(env("SMTP_DEBUG", "false"));

        if (useAuth && (password == null || password.isBlank())) {
            throw new IllegalArgumentException(
                    "SMTP_USE_AUTH=true requires SMTP_PASSWORD");
        }
        if (useAuth && username.isBlank()) {
            throw new IllegalArgumentException(
                    "SMTP_USE_AUTH=true requires SMTP_USERNAME");
        }

        Properties prop = new Properties();
        prop.put("mail.smtp.host", host);
        prop.put("mail.smtp.port", port);
        prop.put("mail.debug", String.valueOf(debug));

        if (!useTls) {
            prop.put("mail.smtp.starttls.enable", "false");
            prop.put("mail.smtp.ssl.enable", "false");
        } else if ("465".equals(port)) {
            prop.put("mail.smtp.ssl.enable", "true");
            prop.put("mail.smtp.starttls.enable", "false");
            prop.put("mail.smtp.socketFactory.port", "465");
            prop.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            prop.put("mail.smtp.socketFactory.fallback", "false");
            applySslTrust(prop, host);
        } else {
            prop.put("mail.smtp.starttls.enable", "true");
            prop.put("mail.smtp.starttls.required", "true");
            prop.put("mail.smtp.ssl.enable", "false");
            applySslTrust(prop, host);
        }

        Session session;
        if (useAuth) {
            prop.put("mail.smtp.auth", "true");
            session = Session.getInstance(prop, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
        } else {
            prop.put("mail.smtp.auth", "false");
            session = Session.getInstance(prop);
        }

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(sender, "Hermes SMTP Test"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setSubject("Hermes SMTP Test Email");
            message.setContent(
                    "<h3>Hermes Notification Test</h3>"
                            + "<p>This is an automated test email sent from Java.</p>",
                    "text/html; charset=utf-8");

            System.out.println("Sending email via " + host + ":" + port + " (auth=" + useAuth + ", tls=" + useTls + ")...");
            Transport.send(message);
            System.out.println("Email sent successfully!");
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required environment variable: " + key);
        }
        return value.trim();
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isBlank() ? value.trim() : defaultValue;
    }

    /**
     * When {@code SMTP_USE_TLS} is unset: authenticated relays (typical internal Microsoft ESMTP)
     * require STARTTLS even on port 25 — default TLS on for auth, off only for no-auth plain relay.
     */
    static boolean resolveUseTls(String port, boolean useAuth) {
        String explicit = System.getenv("SMTP_USE_TLS");
        if (explicit != null && !explicit.isBlank()) {
            return Boolean.parseBoolean(explicit.trim());
        }
        if ("465".equals(port) || "587".equals(port)) {
            return true;
        }
        return useAuth;
    }

    static void applySslTrust(Properties prop, String host) {
        String extra = System.getenv("SMTP_SSL_TRUST");
        String trust = host != null ? host.trim() : "";
        if (extra != null && !extra.isBlank()) {
            trust = trust.isBlank() ? extra.trim() : trust + "," + extra.trim();
        }
        if (!trust.isBlank()) {
            prop.put("mail.smtp.ssl.trust", trust);
            prop.put("mail.smtps.ssl.trust", trust);
        }
    }
}
