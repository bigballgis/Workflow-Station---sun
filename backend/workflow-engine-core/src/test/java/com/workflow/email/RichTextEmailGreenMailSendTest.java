package com.workflow.email;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.workflow.email.extract.EmailExtractionSpec;
import com.workflow.email.extract.EmailFieldExtractor;
import com.workflow.email.extract.EmailMessage;
import com.workflow.service.EmailSendOptions;
import com.workflow.service.EmailSenderService;
import com.workflow.util.EmailTemplateResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SMTP send/read without Vault: Mailpit-style open relay via GreenMail on localhost.
 */
class RichTextEmailGreenMailSendTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(new ServerSetup[] {
            new ServerSetup(0, "localhost", ServerSetup.PROTOCOL_SMTP)
    });

    @Test
    void smtpDeliversHtmlBodyWithRichTextFormatting() throws Exception {
        String inboundHtml = "<p>Color <span style=\"color: rgb(225, 60, 57);\">red</span> "
                + "and <strong>bold</strong></p>";
        EmailMessage email = new EmailMessage("gm1", "Test", "in@example.com", null, inboundHtml, Map.of());

        EmailExtractionSpec.FieldRule rule = new EmailExtractionSpec.FieldRule();
        rule.setTarget("email_body");
        rule.setSource(EmailExtractionSpec.Source.HTML_RICH);
        rule.setType(EmailExtractionSpec.RuleType.DIRECT);
        EmailExtractionSpec spec = new EmailExtractionSpec();
        spec.setFields(List.of(rule));

        String stored = String.valueOf(EmailFieldExtractor.extract(email, spec).getFields().get("email_body"));
        String htmlBody = EmailTemplateResolver.resolveHtml(
                "<p>Hello,</p>${richText:email_body}", Map.of("email_body", stored));

        int smtpPort = greenMail.getSmtp().getPort();
        Map<String, Object> creds = Map.of(
                "host", "localhost",
                "port", smtpPort,
                "useTls", false,
                "fromEmail", "sender@local.dev",
                "fromName", "Hermes Test");
        EmailSendOptions options = new EmailSendOptions(
                "recipient@local.dev", null, null,
                "Rich text smoke", htmlBody,
                null, null, null, List.of(),
                "sender@local.dev", "Hermes Test");

        new EmailSenderService().send(creds, options);

        assertThat(greenMail.getReceivedMessages()).hasSize(1);
        String receivedHtml = GreenMailUtil.getBody(greenMail.getReceivedMessages()[0]);
        assertThat(receivedHtml).contains("<strong>bold</strong>");
        assertThat(receivedHtml).contains("color: rgb(225, 60, 57)");
        assertThat(receivedHtml).doesNotContain("&lt;strong&gt;");
    }
}
