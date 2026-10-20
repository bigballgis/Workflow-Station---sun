package com.workflow.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * HTML for the form "editor" (wangeditor) control on the email path.
 *
 * <p>One allowlist shared by Email Monitor writes ({@code HTML_RICH} source) and Send Email
 * {@code ${richText:field}} rendering. Mail clients never load the editor stylesheet, so
 * {@link #forEmail(String)} inlines the editor's default look (table borders, quote bar, code box).
 */
public final class RichTextEmailHtml {

    private static final Document.OutputSettings OUTPUT = new Document.OutputSettings().prettyPrint(false);

    private static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "br", "div", "span", "strong", "b", "em", "i", "u", "s", "del", "sub", "sup",
                    "h1", "h2", "h3", "h4", "h5", "ul", "ol", "li", "blockquote", "pre", "code", "hr",
                    "a", "img", "table", "thead", "tbody", "tr", "th", "td")
            .addAttributes(":all", "style")
            .addAttributes("a", "href", "target")
            .addAttributes("img", "src", "alt", "width", "height")
            .addAttributes("table", "width")
            .addAttributes("th", "colspan", "rowspan", "width")
            .addAttributes("td", "colspan", "rowspan", "width")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https", "data")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer");

    private static final Pattern UNSAFE_CSS =
            Pattern.compile("(?i)expression\\s*\\(|javascript:|url\\s*\\(|@import|behavior\\s*:");

    private static final String SLIGHT_BG = "#f5f2f0";
    private static final String CELL_STYLE =
            "border:1px solid #ccc;line-height:1.5;min-width:30px;padding:3px 5px;text-align:left";

    /** Values of wangeditor 5 {@code .w-e-text-container [data-slate-editor]} rules. */
    private static final Map<String, String> EDITOR_STYLES = Map.ofEntries(
            Map.entry("p", "margin:15px 0;line-height:1.5"),
            Map.entry("li", "line-height:1.5"),
            Map.entry("h1", "margin:20px 0"),
            Map.entry("h2", "margin:20px 0"),
            Map.entry("h3", "margin:20px 0"),
            Map.entry("h4", "margin:20px 0"),
            Map.entry("h5", "margin:20px 0"),
            Map.entry("blockquote", "background-color:" + SLIGHT_BG
                    + ";border-left:8px solid #B4D5FF;margin:10px 0;padding:10px;line-height:1.5"),
            Map.entry("table", "border-collapse:collapse"),
            Map.entry("td", CELL_STYLE),
            Map.entry("th", CELL_STYLE + ";background-color:" + SLIGHT_BG + ";font-weight:700;text-align:center"),
            Map.entry("code", "background-color:" + SLIGHT_BG + ";border-radius:3px;font-family:monospace;padding:3px"),
            Map.entry("img", "max-width:100%"));

    private static final String PRE_CODE_STYLE = "display:block;background-color:" + SLIGHT_BG
            + ";border:1px solid #e8e8e8;border-radius:4px;font-family:Consolas,Monaco,monospace;"
            + "font-size:14px;line-height:1.5;padding:10px;white-space:pre;text-align:left";

    private RichTextEmailHtml() {
    }

    /** Allowlist-cleans untrusted HTML (inbound mail or stored editor value); never returns null. */
    public static String sanitize(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        Document source = Jsoup.parse(html);
        source.outputSettings(OUTPUT);
        source.body().select("script, style, noscript, template").remove();
        Document doc = Jsoup.parseBodyFragment(Jsoup.clean(source.body().html(), "", SAFELIST, OUTPUT));
        doc.outputSettings(OUTPUT);
        doc.body().select("img:not([src])").remove();
        doc.body().select("a:not([href])").unwrap();
        for (Element element : doc.body().select("[style]")) {
            String css = safeCss(element.attr("style"));
            if (css.isEmpty()) {
                element.removeAttr("style");
            } else {
                element.attr("style", css);
            }
        }
        return doc.body().html();
    }

    /** Plain-text body as editor paragraphs: blank lines split paragraphs, single newlines become {@code <br>}. */
    public static String fromPlainText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        StringBuilder out = new StringBuilder();
        for (String paragraph : normalized.split("\n\\s*\n")) {
            out.append("<p>")
                    .append(SubTableHtmlFormatter.escapeHtml(paragraph.trim()).replace("\n", "<br>"))
                    .append("</p>");
        }
        return out.toString();
    }

    /** Sanitized editor HTML with the editor's default styles inlined, ready for an email body. */
    public static String forEmail(String html) {
        String safe = sanitize(html);
        if (safe.isEmpty()) {
            return safe;
        }
        Document doc = Jsoup.parseBodyFragment(safe);
        doc.outputSettings(OUTPUT);
        for (Element element : doc.body().getAllElements()) {
            String defaults = defaultStyle(element);
            if (defaults == null) {
                continue;
            }
            String own = element.attr("style");
            element.attr("style", own.isEmpty() ? defaults : defaults + ";" + own);
        }
        return doc.body().html();
    }

    /** Text-only rendering for places that cannot carry HTML (e.g. the subject line). */
    public static String toPlainText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        return Jsoup.parse(html).text();
    }

    private static String defaultStyle(Element element) {
        String tag = element.normalName();
        if ("code".equals(tag) && element.parent() != null && "pre".equals(element.parent().normalName())) {
            return PRE_CODE_STYLE;
        }
        return EDITOR_STYLES.get(tag);
    }

    private static String safeCss(String css) {
        StringJoiner kept = new StringJoiner(";");
        for (String declaration : css.split(";")) {
            String trimmed = declaration.trim();
            if (!trimmed.isEmpty() && trimmed.contains(":") && !UNSAFE_CSS.matcher(trimmed).find()) {
                kept.add(trimmed);
            }
        }
        return kept.toString();
    }
}
