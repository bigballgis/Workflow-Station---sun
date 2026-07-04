package com.workflow.service;

import com.platform.common.mail.MailDiagnostics;
import com.platform.common.mail.SmtpTransportProperties;
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
@Slf4j
public class EmailSenderService {

    public void send(Map<String, Object> credentials, EmailSendOptions options) throws Exception {
        String host = (String) credentials.get("host");
        Object port = credentials.get("port");
        Object useTls = credentials.get("useTls");
        log.info("[SMTP-SEND] begin host={} port={} useTls={} to={} cc={} from={}",
                host, port, useTls,
                com.platform.common.util.StringUtils.maskEmail(options.to()),
                com.platform.common.util.StringUtils.maskEmail(options.cc()),
                com.platform.common.util.StringUtils.maskEmail(options.fromEmail()));

        Session session = buildSession(credentials);

        ByteArrayOutputStream debugBuf = new ByteArrayOutputStream();
        PrintStream debugOut = new PrintStream(debugBuf, true, StandardCharsets.UTF_8);
        session.setDebug(true);
        session.setDebugOut(debugOut);

        try {
            MimeMessage message = buildMessage(session, credentials, options);
            Transport.send(message);
            debugOut.flush();
            log.info("[SMTP-SEND] SUCCESS host={} to={}",
                    host, com.platform.common.util.StringUtils.maskEmail(options.to()));
            if (log.isDebugEnabled()) {
                log.debug("[SMTP-SEND] JavaMail trace:\n{}", scrubSmtpTrace(debugBuf.toString(StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            debugOut.flush();
            log.error("[SMTP-SEND] FAILED host={} port={} useTls={} | causeChain={} | rootCause={}\n----- JavaMail trace -----\n{}--------------------------",
                    host, port, useTls, MailDiagnostics.causeChain(e), MailDiagnostics.rootCause(e),
                    scrubSmtpTrace(debugBuf.toString(StandardCharsets.UTF_8)), e);
            throw e;
        }
    }

    /**
     * Removes SMTP AUTH credential material from a JavaMail protocol trace before it is logged,
     * so that base64-encoded username/password and message payloads never reach the logs.
     */
    static String scrubSmtpTrace(String trace) {
        if (trace == null || trace.isEmpty()) {
            return trace;
        }
        return trace
                // AUTH command and its inline argument (AUTH PLAIN <base64>, AUTH LOGIN, XOAUTH2 ...)
                .replaceAll("(?im)^(.*\\bAUTH\\b\\s+\\S+).*$", "$1 [credentials redacted]")
                // Standalone base64 lines (credential challenge responses / encoded payload)
                .replaceAll("(?m)^\\s*[A-Za-z0-9+/]{16,}={0,2}\\s*$", "[redacted]");
    }

    private Session buildSession(Map<String, Object> credentials) {
        String host = (String) credentials.get("host");
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("SMTP host is required");
        }
        if (credentials.get("port") == null) {
            throw new IllegalArgumentException("SMTP port is required");
        }
        int port = ((Number) credentials.get("port")).intValue();
        String username = (String) credentials.get("username");
        String password = (String) credentials.get("password");
        if (credentials.get("useTls") == null) {
            throw new IllegalArgumentException("SMTP useTls is required");
        }
        boolean useTls = Boolean.TRUE.equals(credentials.get("useTls"));
        boolean auth = StringUtils.hasText(username);

        Properties props = new Properties();
        SmtpTransportProperties.apply(props, host.trim(), port, useTls, auth);

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
