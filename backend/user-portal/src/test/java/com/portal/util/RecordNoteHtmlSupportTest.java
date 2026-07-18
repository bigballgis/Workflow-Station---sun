package com.portal.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordNoteHtmlSupportTest {

    @Test
    void sanitizeStripsScriptTags() {
        String html = "<p>hello</p><script>alert('xss')</script>";
        String cleaned = RecordNoteHtmlSupport.sanitize(html);
        assertThat(cleaned).contains("hello").doesNotContain("script").doesNotContain("alert");
    }

    @Test
    void sanitizeStripsEventHandlers() {
        String html = "<p onclick=\"steal()\">text</p><img src=\"x\" onerror=\"steal()\">";
        String cleaned = RecordNoteHtmlSupport.sanitize(html);
        assertThat(cleaned).doesNotContain("onclick").doesNotContain("onerror");
    }

    @Test
    void sanitizeKeepsBasicFormatting() {
        String html = "<p><strong>bold</strong> and <em>italic</em></p><ul><li>item</li></ul>";
        String cleaned = RecordNoteHtmlSupport.sanitize(html);
        assertThat(cleaned).contains("<strong>").contains("<em>").contains("<li>");
    }

    @Test
    void sanitizeKeepsRelativeInlineImage() {
        String html = "<p><img src=\"/api/portal/record-notes/abc/content\" alt=\"pic\"></p>";
        String cleaned = RecordNoteHtmlSupport.sanitize(html);
        assertThat(cleaned).contains("/api/portal/record-notes/abc/content");
    }

    @Test
    void sanitizeRemovesDataUriAndJavascriptImages() {
        String dataUri = "<img src=\"data:image/png;base64,AAAA\">";
        String jsUri = "<img src=\"javascript:alert(1)\">";
        String protocolRelative = "<img src=\"//evil.example/x.png\">";
        assertThat(RecordNoteHtmlSupport.sanitize(dataUri)).doesNotContain("<img");
        assertThat(RecordNoteHtmlSupport.sanitize(jsUri)).doesNotContain("<img");
        assertThat(RecordNoteHtmlSupport.sanitize(protocolRelative)).doesNotContain("<img");
    }

    @Test
    void sanitizeReturnsNullForBlank() {
        assertThat(RecordNoteHtmlSupport.sanitize(null)).isNull();
        assertThat(RecordNoteHtmlSupport.sanitize("  ")).isNull();
    }

    @Test
    void extractTextFlattensAndTruncates() {
        String html = "<p><strong>hello</strong> world</p>";
        assertThat(RecordNoteHtmlSupport.extractText(html)).isEqualTo("hello world");

        String longHtml = "<p>" + "x".repeat(600) + "</p>";
        String text = RecordNoteHtmlSupport.extractText(longHtml);
        assertThat(text).hasSize(500);
    }
}
