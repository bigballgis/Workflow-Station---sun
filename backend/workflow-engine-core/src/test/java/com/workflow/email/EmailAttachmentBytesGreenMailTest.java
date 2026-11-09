package com.workflow.email;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import com.workflow.service.EmailSendOptions;
import com.workflow.service.EmailSenderService;
import jakarta.mail.BodyPart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An attachment arrives byte for byte through a real SMTP exchange (WP11b-4): a plain-text file with LF line ends
 * used to go out 7bit and arrive with CRLF.
 */
class EmailAttachmentBytesGreenMailTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(new ServerSetup[] {
            new ServerSetup(0, "localhost", ServerSetup.PROTOCOL_SMTP)
    });

    @Test
    void aTextFileWithLfLineEndsArrivesUnchanged() throws Exception {
        byte[] original = "vendor,amount\nAcme,42\nlast line\n".getBytes(StandardCharsets.US_ASCII);

        MimeBodyPart received = sendAndReceiveAttachment("lines.csv", original);

        assertThat(new String(received.getInputStream().readAllBytes(), StandardCharsets.US_ASCII))
                .isEqualTo(new String(original, StandardCharsets.US_ASCII));
        assertThat(received.getEncoding()).isEqualTo("base64");
    }

    @Test
    void aTextFileWithCrlfLineEndsKeepsThemWithoutDoubling() throws Exception {
        byte[] original = "first\r\nsecond\r\n".getBytes(StandardCharsets.US_ASCII);

        assertThat(sendAndReceiveAttachment("windows.txt", original).getInputStream().readAllBytes())
                .isEqualTo(original);
    }

    @Test
    void aBinaryFileArrivesUnchanged() throws Exception {
        byte[] original = new byte[256];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) i;
        }

        assertThat(sendAndReceiveAttachment("all-bytes.bin", original).getInputStream().readAllBytes())
                .isEqualTo(original);
    }

    private MimeBodyPart sendAndReceiveAttachment(String name, byte[] content) throws Exception {
        greenMail.reset();
        Map<String, Object> credentials = Map.of("host", "localhost", "port", greenMail.getSmtp().getPort(),
                "useTls", false, "fromEmail", "sender@local.dev");
        EmailSendOptions options = new EmailSendOptions("recipient@local.dev", null, null, "Attachment", "<p>See file</p>",
                null, null, null,
                List.of(new EmailSendOptions.EmailAttachmentPart(name, Base64.getEncoder().encodeToString(content))),
                "sender@local.dev", null);

        new EmailSenderService().send(credentials, options);

        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        MimeMultipart multipart = (MimeMultipart) messages[0].getContent();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || name.equals(part.getFileName())) {
                return (MimeBodyPart) part;
            }
        }
        throw new AssertionError("no attachment named " + name);
    }
}
