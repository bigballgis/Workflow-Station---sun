package com.workflow.email.inbound;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ImapInboundMailClientExtractTest {

    @Test
    void extractParts_recursesIntoNestedForwardedMessage() throws Exception {
        Session session = Session.getInstance(new Properties());

        MimeBodyPart innerHtml = new MimeBodyPart();
        innerHtml.setContent("<table><tr><td>Case No: ABC-99</td></tr></table>", "text/html; charset=utf-8");

        MimeMultipart innerMultipart = new MimeMultipart("alternative");
        innerMultipart.addBodyPart(innerHtml);

        MimeMessage inner = new MimeMessage(session);
        inner.setContent(innerMultipart);
        inner.saveChanges();

        MimeBodyPart forwardWrapper = new MimeBodyPart();
        forwardWrapper.setContent(inner, "message/rfc822");

        MimeBodyPart outerText = new MimeBodyPart();
        outerText.setText("Forwarded message");

        MimeMultipart outer = new MimeMultipart("mixed");
        outer.addBodyPart(outerText);
        outer.addBodyPart(forwardWrapper);

        MimeMessage message = new MimeMessage(session);
        message.setContent(outer);
        message.saveChanges();

        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder();
        new ImapInboundMailClient().extractParts(message, text, html);

        assertThat(html.toString()).contains("Case No: ABC-99");
        assertThat(text.toString()).contains("Forwarded message");
    }
}
