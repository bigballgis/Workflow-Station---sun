package com.developer.component.impl.compare;

import com.developer.dto.VersionCompareResponse;
import com.fasterxml.jackson.databind.JsonNode;

/** #1708: linear, bounded, pair-local excerpts around the first actual edit; never reads current documents. */
final class VersionDocumentPreview {
    private static final int SHORT = 240;
    private static final int LIMIT = 1200;
    private static final int CONTEXT = 60;
    private VersionDocumentPreview() {}

    static VersionCompareResponse.FieldChange field(JsonNode old, JsonNode next) {
        String before = text(old), after = text(next);
        String a = before == null ? "" : before, b = after == null ? "" : after;
        if (Math.max(a.length(), b.length()) <= SHORT) return new VersionCompareResponse.FieldChange("content", before, after);
        int prefix = 0, shared = Math.min(a.length(), b.length());
        while (prefix < shared && a.charAt(prefix) == b.charAt(prefix)) prefix++;
        int suffix = 0;
        while (suffix < shared - prefix && a.charAt(a.length() - 1 - suffix) == b.charAt(b.length() - 1 - suffix)) suffix++;
        int oldStart = boundary(a, Math.max(0, prefix - CONTEXT));
        int newStart = boundary(b, Math.max(0, prefix - CONTEXT));
        int oldEnd = end(a, oldStart, suffix), newEnd = end(b, newStart, suffix);
        boolean omitted = oldEnd < a.length() - suffix || newEnd < b.length() - suffix;
        return new VersionCompareResponse.FieldChange("content", excerpt(before, oldStart, oldEnd),
                excerpt(after, newStart, newEnd), new VersionCompareResponse.TextContext(
                        oldStart, newStart, a.length(), b.length(), omitted));
    }

    private static int end(String value, int start, int suffix) {
        return boundary(value, Math.min(value.length(), Math.min(start + LIMIT, value.length() - suffix + CONTEXT)));
    }

    private static int boundary(String value, int index) {
        return index > 0 && index < value.length() && Character.isLowSurrogate(value.charAt(index))
                && Character.isHighSurrogate(value.charAt(index - 1)) ? index - 1 : index;
    }

    private static String excerpt(String value, int start, int end) {
        if (value == null) return null;
        return (start > 0 ? "…" : "") + value.substring(start, end) + (end < value.length() ? "…" : "");
    }

    private static String text(JsonNode value) {
        return value == null || value.isNull() ? null : value.asText();
    }
}
