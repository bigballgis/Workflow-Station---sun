package com.developer.util;

import com.platform.common.mail.MailDiagnostics;
import com.platform.common.mail.SmtpTransportProperties;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Slf4j
public final class SmtpMailSender {

    private SmtpMailSender() {
    }

    public static void send(SmtpConfig config, String to, String cc, String subject, String body) throws Exception {
        boolean auth = hasSmtpAuth(config.username(), config.password());
        boolean useTls = Boolean.TRUE.equals(config.useTls());
        String mode = SmtpTransportProperties.describeMode(config.port(), useTls);

        log.info("[SMTP-TEST] begin host={} port={} mode={} useTls={} auth={} username={} from={} to={} cc={} subject={}",
                config.host(), config.port(), mode, useTls, auth,
                mask(config.username()), config.fromEmail(), to, cc, subject);

        Properties props = new Properties();
        SmtpTransportProperties.apply(props, config.host(), config.port(), useTls, auth);

        Session session;
        if (auth) {
            session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.username(), config.password() != null ? config.password() : "");
                }
            });
        } else {
            session = Session.getInstance(props);
        }

        // Capture the full JavaMail protocol dialogue (incl. STARTTLS handshake) into our logs
        // so a single deploy shows exactly where the connection fails.
        ByteArrayOutputStream debugBuf = new ByteArrayOutputStream();
        PrintStream debugOut = new PrintStream(debugBuf, true, StandardCharsets.UTF_8);
        session.setDebug(true);
        session.setDebugOut(debugOut);

        try {
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
            debugOut.flush();
            log.info("[SMTP-TEST] SUCCESS host={} port={} mode={} to={}\n----- JavaMail trace -----\n{}--------------------------",
                    config.host(), config.port(), mode, to, debugBuf.toString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            debugOut.flush();
            log.error("[SMTP-TEST] FAILED host={} port={} mode={} useTls={} | causeChain={} | rootCause={}\n----- JavaMail trace -----\n{}--------------------------",
                    config.host(), config.port(), mode, useTls,
                    MailDiagnostics.causeChain(e), MailDiagnostics.rootCause(e),
                    debugBuf.toString(StandardCharsets.UTF_8), e);
            throw e;
        }
    }

    static boolean hasSmtpAuth(String username, String password) {
        return username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "<none>";
        }
        int at = value.indexOf('@');
        if (at > 1) {
            return value.charAt(0) + "***" + value.substring(at);
        }
        return value.charAt(0) + "***";
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
