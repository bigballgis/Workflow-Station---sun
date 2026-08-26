package com.workflow.util;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves Send Email template tokens:
 * <ul>
 *   <li>{@code ${subTableHtml:bindingId}} — sub-table as HTML table (all columns)</li>
 *   <li>{@code ${subTableHtml:bindingId:col1=Header 1,col2=Header 2}} — selected columns with custom headers</li>
 *   <li>{@code ${subTableField:bindingId:fieldName}} — scalar from sub-table rows</li>
 *   <li>{@code ${lookupField:sourceField:targetAttr}} — attribute on Lookup/Related embedded RT row</li>
 *   <li>{@code ${variable}} — top-level process variable; sub-table columns fallback when absent.
 *       Missing or null values resolve to empty (the placeholder is not left in the output).</li>
 * </ul>
 */
public final class EmailTemplateResolver {

    private static final Pattern SUB_TABLE_HTML = Pattern.compile("\\$\\{subTableHtml:([^}]+)}");
    private static final Pattern SUB_TABLE_FIELD = Pattern.compile("\\$\\{subTableField:([^:}]+):([^}]+)}");
    private static final Pattern LOOKUP_FIELD = Pattern.compile("\\$\\{lookupField:([^:}]+):([^}]+)}");
    private static final Pattern BARE_PLACEHOLDER = Pattern.compile("\\$\\{([^{}:]+)}");

    private EmailTemplateResolver() {
    }

    public static String resolve(String template, Map<String, Object> variables) {
        if (template == null) {
            return null;
        }
        String withTables = replaceSubTableHtml(template, variables);
        String withSubFields = replaceSubTableFields(withTables, variables);
        String withLookupFields = replaceLookupFields(withSubFields, variables);
        String withTopLevel = BpmnExtensionUtils.resolveExpression(withLookupFields, variables);
        return resolveBareSubTableFields(withTopLevel, variables);
    }

    private static String replaceSubTableHtml(String template, Map<String, Object> variables) {
        Matcher matcher = SUB_TABLE_HTML.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String raw = matcher.group(1).trim();
            int sep = raw.indexOf(':');
            String bindingId = sep >= 0 ? raw.substring(0, sep).trim() : raw;
            List<SubTableHtmlFormatter.ColumnSpec> columns =
                    sep >= 0 ? parseColumnSpecs(raw.substring(sep + 1)) : null;
            String html = SubTableHtmlFormatter.format(variables, bindingId, columns);
            matcher.appendReplacement(out, Matcher.quoteReplacement(html));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Parses {@code col1=Header 1,col2,col3=Header 3} into ordered column specs (header defaults to field). */
    private static List<SubTableHtmlFormatter.ColumnSpec> parseColumnSpecs(String csv) {
        List<SubTableHtmlFormatter.ColumnSpec> specs = new ArrayList<>();
        for (String part : csv.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            int eq = token.indexOf('=');
            if (eq >= 0) {
                String field = token.substring(0, eq).trim();
                String header = token.substring(eq + 1).trim();
                if (!field.isEmpty()) {
                    specs.add(new SubTableHtmlFormatter.ColumnSpec(field, header.isEmpty() ? field : header));
                }
            } else {
                specs.add(new SubTableHtmlFormatter.ColumnSpec(token, token));
            }
        }
        return specs;
    }

    private static String replaceSubTableFields(String template, Map<String, Object> variables) {
        Matcher matcher = SUB_TABLE_FIELD.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String bindingId = matcher.group(1).trim();
            String fieldName = matcher.group(2).trim();
            String value = SubTableFieldResolver.resolveBindingField(variables, bindingId, fieldName);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String replaceLookupFields(String template, Map<String, Object> variables) {
        Matcher matcher = LOOKUP_FIELD.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String sourceField = matcher.group(1).trim();
            String targetAttr = matcher.group(2).trim();
            String value = LookupFieldResolver.resolve(variables, sourceField, targetAttr);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String resolveBareSubTableFields(String template, Map<String, Object> variables) {
        if (!StringUtils.hasText(template) || variables == null) {
            return template;
        }
        Matcher matcher = BARE_PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            if (name.startsWith("subTableHtml:")
                    || name.startsWith("subTableField:")
                    || name.startsWith("lookupField:")) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            if (variables.containsKey(name)) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(displayValue(variables.get(name))));
                continue;
            }
            String resolved = SubTableFieldResolver.resolveFieldAcrossSubTables(variables, name);
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    StringUtils.hasText(resolved) ? resolved : ""));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String displayValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        return StringUtils.hasText(text) ? text : "";
    }
}
