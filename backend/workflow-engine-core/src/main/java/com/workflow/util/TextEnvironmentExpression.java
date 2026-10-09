package com.workflow.util;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces {@code ${env:varKey}} with a TEXT environment-variable value.
 * Keys match Admin Center catalog keys (letters, digits, dot, underscore, hyphen).
 */
public final class TextEnvironmentExpression {

    private static final Pattern TOKEN = Pattern.compile(
            "\\$\\{env:([A-Za-z0-9][A-Za-z0-9._-]{0,99})}");

    private TextEnvironmentExpression() {
    }

    public static boolean contains(String template) {
        return template != null && TOKEN.matcher(template).find();
    }

    public static String apply(String template, Function<String, String> resolve, boolean htmlEscape) {
        if (template == null || resolve == null) {
            return template;
        }
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = resolve.apply(matcher.group(1));
            String replacement = value == null ? "" : value;
            if (htmlEscape) {
                replacement = SubTableHtmlFormatter.escapeHtml(replacement);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
