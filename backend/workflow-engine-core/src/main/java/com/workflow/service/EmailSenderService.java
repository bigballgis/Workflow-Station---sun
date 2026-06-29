package com.workflow.service;

import jakarta.activation.DataHandler;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
@Slf4j
public class EmailSenderService {

    public void send(Map<String, Object> credentials, EmailSendOptions options) throws Exception {
        Session session = buildSession(credentials);
        MimeMessage message = buildMessage(session, credentials, options);
        Transport.send(message);
        log.info("Workflow email sent to {}", options.to());
    }

    private Session buildSession(Map<String, Object> credentials) {
        String host = (String) credentials.get("host");
        int port = credentials.get("port") != null ? ((Number) credentials.get("port")).intValue() : 587;
        String username = (String) credentials.get("username");
        String password = (String) credentials.get("password");
        boolean useTls = credentials.get("useTls") == null || Boolean.TRUE.equals(credentials.get("useTls"));

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", username != null && !username.isBlank());
        props.put("mail.smtp.starttls.enable", useTls);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");

        if (username != null && !username.isBlank()) {
            return Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password != null ? password : "");
                }
            });
        }
        return Session.getInstance(props);
    }

    private MimeMessage buildMessage(Session session, Map<String, Object> credentials, EmailSendOptions options)
            throws Exception {
        MimeMessage message = new MimeMessage(session);
        applyFrom(message, credentials, options);
        applyRecipients(message, options);
        applyHeaders(message, options);
        message.setSubject(options.subject(), "UTF-8");
        message.setContent(buildContent(options));
        return message;
    }

    private void applyFrom(MimeMessage message, Map<String, Object> credentials, EmailSendOptions options)
            throws Exception {
        String fromEmail = StringUtils.hasText(options.fromEmail())
                ? options.fromEmail()
                : (String) credentials.get("fromEmail");
        String fromName = StringUtils.hasText(options.fromName())
                ? options.fromName()
                : (String) credentials.get("fromName");
        String from = fromName != null && !fromName.isBlank()
                ? fromName + " <" + fromEmail + ">"
                : fromEmail;
        message.setFrom(new InternetAddress(from));
    }

    private void applyRecipients(MimeMessage message, EmailSendOptions options) throws Exception {
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(options.to()));
        if (StringUtils.hasText(options.cc())) {
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(options.cc()));
        }
        if (StringUtils.hasText(options.bcc())) {
            message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(options.bcc()));
        }
        if (StringUtils.hasText(options.replyTo())) {
            message.setReplyTo(InternetAddress.parse(options.replyTo()));
        }
    }

    private void applyHeaders(MimeMessage message, EmailSendOptions options) throws Exception {
        String importance = options.importance() != null ? options.importance() : "normal";
        switch (importance.toLowerCase()) {
            case "high" -> message.setHeader("X-Priority", "1");
            case "low" -> message.setHeader("X-Priority", "5");
            default -> message.setHeader("X-Priority", "3");
        }
        String sensitivity = options.sensitivity();
        if (StringUtils.hasText(sensitivity) && !"normal".equalsIgnoreCase(sensitivity)) {
            String label = sensitivity.substring(0, 1).toUpperCase() + sensitivity.substring(1).toLowerCase();
            message.setHeader("Sensitivity", label);
            message.setHeader("X-Microsoft-Sensitivity", label);
        }
    }

    private MimeMultipart buildContent(EmailSendOptions options) throws Exception {
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent(options.body() != null ? options.body() : "", "text/html; charset=UTF-8");
        multipart.addBodyPart(bodyPart);
        appendAttachments(multipart, options.attachments());
        return multipart;
    }

    private void appendAttachments(MimeMultipart multipart, List<EmailSendOptions.EmailAttachmentPart> attachments)
            throws Exception {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        for (EmailSendOptions.EmailAttachmentPart attachment : attachments) {
            if (!StringUtils.hasText(attachment.name()) || !StringUtils.hasText(attachment.content())) {
                continue;
            }
            MimeBodyPart part = new MimeBodyPart();
            part.setFileName(attachment.name());
            part.setDataHandler(new DataHandler(new ByteArrayDataSource(decodeContent(attachment.content()),
                    "application/octet-stream")));
            multipart.addBodyPart(part);
        }
    }

    private byte[] decodeContent(String raw) {
        String trimmed = raw.trim();
        try {
            return Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException ex) {
            return trimmed.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
