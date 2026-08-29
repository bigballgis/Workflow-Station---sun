package com.admin.component;

import java.util.regex.Pattern;

/**
 * Strips executable payloads from imported Function Unit icon SVG before persistence.
 */
public final class FunctionUnitIconSvgSanitizer {

    private static final Pattern SCRIPT = Pattern.compile("(?is)<script[^>]*>.*?</script>");
    private static final Pattern ON_ATTR = Pattern.compile("(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JS_URL = Pattern.compile("(?i)javascript:");
    private static final Pattern DATA_HTML = Pattern.compile("(?i)data:text/html");

    private FunctionUnitIconSvgSanitizer() {
    }

    public static String sanitize(String svg) {
        if (svg == null || svg.isBlank()) {
            return svg;
        }
        String cleaned = SCRIPT.matcher(svg).replaceAll("");
        cleaned = ON_ATTR.matcher(cleaned).replaceAll("");
        cleaned = JS_URL.matcher(cleaned).replaceAll("");
        cleaned = DATA_HTML.matcher(cleaned).replaceAll("");
        return cleaned.isBlank() ? null : cleaned;
    }
}
