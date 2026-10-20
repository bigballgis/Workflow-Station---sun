package com.workflow.email.extract;

import com.workflow.util.EmailTemplateResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end for Monitor → editor field → Send Email, without Vault, SMTP, or Docker.
 */
class EmailRichTextPipelineTest {

    @Test
    void htmlRichDirectThenRichTextToken_rendersFormattedHtmlNotEscaped() {
        String inboundHtml = "<html><body><p>Hi <strong>team</strong></p>"
                + "<table><tr><th>H</th></tr><tr><td>cell</td></tr></table>"
                + "<script>x()</script></body></html>";
        EmailMessage email = new EmailMessage("m-pipe", "Agenda", "from@example.com",
                null, inboundHtml, Map.of());

        EmailExtractionSpec.FieldRule rule = new EmailExtractionSpec.FieldRule();
        rule.setTarget("email_body");
        rule.setSource(EmailExtractionSpec.Source.HTML_RICH);
        rule.setType(EmailExtractionSpec.RuleType.DIRECT);
        EmailExtractionSpec spec = new EmailExtractionSpec();
        spec.setFields(java.util.List.of(rule));

        String stored = String.valueOf(EmailFieldExtractor.extract(email, spec).getFields().get("email_body"));
        assertThat(stored).contains("<strong>team</strong>").doesNotContain("script");

        String sentBody = EmailTemplateResolver.resolveHtml(
                "<p>Forwarded:</p>${richText:email_body}", Map.of("email_body", stored));
        assertThat(sentBody).contains("<strong>team</strong>");
        assertThat(sentBody).contains("border-collapse:collapse");
        assertThat(sentBody).doesNotContain("&lt;strong&gt;").doesNotContain("<script");
    }

    @Test
    void plainOnlyMail_htmlRichBecomesParagraphsThenRichTextRenders() {
        EmailMessage email = new EmailMessage("m-plain", "s", "a@b.com",
                "Line one\n\nLine two", null, Map.of());

        EmailExtractionSpec.FieldRule rule = new EmailExtractionSpec.FieldRule();
        rule.setTarget("body");
        rule.setSource(EmailExtractionSpec.Source.HTML_RICH);
        rule.setType(EmailExtractionSpec.RuleType.DIRECT);
        EmailExtractionSpec spec = new EmailExtractionSpec();
        spec.setFields(java.util.List.of(rule));

        String stored = String.valueOf(EmailFieldExtractor.extract(email, spec).getFields().get("body"));
        String sent = EmailTemplateResolver.resolveHtml("${richText:body}", Map.of("body", stored));

        assertThat(stored).contains("<p>Line one</p>").contains("<p>Line two</p>");
        assertThat(sent).contains("<p style=\"margin:15px 0;line-height:1.5\">Line one</p>");
        assertThat(sent).doesNotContain("&lt;p&gt;");
    }
}
