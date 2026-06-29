package com.developer.util;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.Properties;

@Slf4j
public final class SmtpMailSender {

    private SmtpMailSender() {
    }

    public static void send(SmtpConfig config, String to, String cc, String subject, String body) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.host());
        props.put("mail.smtp.port", String.valueOf(config.port()));
        props.put("mail.smtp.auth", config.username() != null && !config.username().isBlank());
        props.put("mail.smtp.starttls.enable", Boolean.TRUE.equals(config.useTls()));
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");

        Session session;
        if (config.username() != null && !config.username().isBlank()) {
            session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.username(), config.password() != null ? config.password() : "");
                }
            });
        } else {
            session = Session.getInstance(props);
        }

        MimeMessage message = new MimeMessage(session);
        String from = config.fromName() != null && !config.fromName().isBlank()
                ? config.fromName() + " <" + config.fromEmail() + ">"
                : config.fromEmail();
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        if (cc != null && !cc.isBlank()) {
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
        }
        message.setSubject(subject, "UTF-8");
        message.setText(body, "UTF-8", "html");

        Transport.send(message);
        log.info("SMTP email sent to {}", to);
    }

    public record SmtpConfig(
            String host,
            int port,
            String username,
            String password,
            String fromEmail,
            String fromName,
            Boolean useTls
    ) {}
}
