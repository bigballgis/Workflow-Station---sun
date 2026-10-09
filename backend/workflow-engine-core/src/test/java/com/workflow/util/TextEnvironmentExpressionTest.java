package com.workflow.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextEnvironmentExpressionTest {

    @Test
    void replacesTextKeyAndEscapesHtml() {
        String raw = "To ${env:smtp.from} <b>";
        String plain = TextEnvironmentExpression.apply(raw, key -> "a<b>@x.com", false);
        String html = TextEnvironmentExpression.apply(raw, key -> "a<b>@x.com", true);

        assertThat(plain).isEqualTo("To a<b>@x.com <b>");
        assertThat(html).isEqualTo("To a&lt;b&gt;@x.com <b>");
    }

    @Test
    void leavesFieldTokensUntouched() {
        String raw = "${sender_email} ${env:not a key}";
        assertThat(TextEnvironmentExpression.contains(raw)).isFalse();
        assertThat(TextEnvironmentExpression.apply(raw, key -> "x", false)).isEqualTo(raw);
    }
}
