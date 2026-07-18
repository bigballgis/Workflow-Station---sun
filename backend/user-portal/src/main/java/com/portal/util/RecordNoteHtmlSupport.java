package com.portal.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * Server-side sanitation for RecordNote rich-text bodies (stored-XSS guard).
 *
 * Policy: relaxed structural HTML, images allowed only from same-origin
 * relative URLs (inline attachment endpoint) or http(s). No data: URIs,
 * no javascript:, no event handlers (jsoup strips attributes not safelisted).
 */
public final class RecordNoteHtmlSupport {

    private static final int MAX_BODY_TEXT = 500;

    private static final Safelist SAFELIST = Safelist.relaxed()
            .addAttributes("span", "style")
            .addAttributes("p", "style")
            .addAttributes("img", "alt", "width", "height")
            // relaxed() restricts img src to http/https, which would strip the relative
            // inline-attachment URLs; lift it here — the src filter below enforces policy.
            .removeProtocols("img", "src", "http", "https");

    private RecordNoteHtmlSupport() {
    }

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String cleaned = Jsoup.clean(html, SAFELIST);
        Document doc = Jsoup.parseBodyFragment(cleaned);
        for (Element img : doc.select("img")) {
            String src = img.attr("src").trim();
            boolean allowed = src.startsWith("/") && !src.startsWith("//")
                    || src.startsWith("http://")
                    || src.startsWith("https://");
            if (!allowed) {
                img.remove();
            }
        }
        doc.outputSettings().prettyPrint(false);
        return doc.body().html();
    }

    /** Plain-text extraction for list summaries and search; truncated. */
    public static String extractText(String sanitizedHtml) {
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            return null;
        }
        String text = Jsoup.parseBodyFragment(sanitizedHtml).text().trim();
        if (text.length() > MAX_BODY_TEXT) {
            text = text.substring(0, MAX_BODY_TEXT);
        }
        return text.isEmpty() ? null : text;
    }
}
