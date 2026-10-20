package com.workflow.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RichTextEmailHtmlTest {

    @Test
    void sanitize_keepsEditorFormatting() {
        String html = "<p><strong>Bold</strong> <span style=\"color: rgb(225, 60, 57);\">red</span></p>"
                + "<ul><li>one</li></ul><table><tbody><tr><td colspan=\"2\">cell</td></tr></tbody></table>";

        String result = RichTextEmailHtml.sanitize(html);

        assertThat(result).contains("<strong>Bold</strong>")
                .contains("style=\"color: rgb(225, 60, 57)\"")
                .contains("<ul><li>one</li></ul>")
                .contains("<td colspan=\"2\">cell</td>");
    }

    @Test
    void sanitize_stripsScriptsHandlersAndUnsafeUrls() {
        String html = "<html><head><title>T</title><style>p{color:red}</style></head><body>"
                + "<p onclick=\"alert(1)\">hi</p><script>alert(2)</script>"
                + "<a href=\"javascript:alert(3)\">x</a><img src=\"cid:img001\">"
                + "<span style=\"background:url(http://evil/x.png);color:blue\">s</span></body></html>";

        String result = RichTextEmailHtml.sanitize(html);

        assertThat(result).doesNotContain("onclick").doesNotContain("script").doesNotContain("alert")
                .doesNotContain("javascript").doesNotContain("<img").doesNotContain("url(")
                .doesNotContain("<style").doesNotContain("color:red").doesNotContain(">T<");
        assertThat(result).contains("<p>hi</p>").contains("style=\"color:blue\"");
        assertThat(result).contains("x").doesNotContain("<a");
    }

    @Test
    void sanitize_enforcesNoopenerOnLinks() {
        String result = RichTextEmailHtml.sanitize("<a href=\"https://example.com\" target=\"_blank\">e</a>");

        assertThat(result).contains("href=\"https://example.com\"").contains("rel=\"noopener noreferrer\"");
    }

    @Test
    void fromPlainText_escapesAndBuildsParagraphs() {
        String result = RichTextEmailHtml.fromPlainText("hello <all>,\r\nline two\r\n\r\nsecond para");

        assertThat(result).isEqualTo("<p>hello &lt;all&gt;,<br>line two</p><p>second para</p>");
    }

    @Test
    void forEmail_inlinesEditorStylesBeforeOwnStyles() {
        String html = "<table><tbody><tr><th>H</th></tr><tr><td style=\"color: red;\">C</td></tr></tbody></table>"
                + "<blockquote>q</blockquote><pre><code>x = 1</code></pre>";

        String result = RichTextEmailHtml.forEmail(html);

        assertThat(result).contains("<table style=\"border-collapse:collapse\">")
                .contains("<th style=\"border:1px solid #ccc;")
                .contains("<td style=\"border:1px solid #ccc;line-height:1.5;min-width:30px;padding:3px 5px;"
                        + "text-align:left;color: red\">C</td>")
                .contains("<blockquote style=\"background-color:#f5f2f0;border-left:8px solid #B4D5FF;")
                .contains("<code style=\"display:block;");
    }

    @Test
    void forEmail_blankInputIsEmpty() {
        assertThat(RichTextEmailHtml.forEmail(null)).isEmpty();
        assertThat(RichTextEmailHtml.forEmail("   ")).isEmpty();
    }

    @Test
    void toPlainText_dropsTags() {
        assertThat(RichTextEmailHtml.toPlainText("<p>Hello <b>World</b></p>")).isEqualTo("Hello World");
    }
}
