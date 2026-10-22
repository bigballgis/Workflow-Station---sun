package com.developer.util;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exact-token scan of Action {@code configJson} and BPMN custom properties for email
 * connection / template references. Does not substring-match numeric ids.
 */
public final class EmailResourceUsageScanner {

    private EmailResourceUsageScanner() {}

    public static boolean configReferencesConnection(
            Map<String, Object> config, String connectionUid, Long connectionId) {
        return anyEmailBlock(config, block -> valueEquals(
                block.get(ActionConfigIdRewriter.CONNECTION_ID), connectionUid, idString(connectionId)));
    }

    public static boolean configReferencesTemplate(Map<String, Object> config, Long templateId) {
        return anyEmailBlock(config, block -> valueEquals(
                block.get(ActionConfigIdRewriter.EMAIL_TEMPLATE_ID), idString(templateId)));
    }

    public static boolean bpmnReferencesConnection(String bpmnXml, String connectionUid, Long connectionId) {
        return bpmnPropertyEquals(bpmnXml, "connectionId", connectionUid, idString(connectionId));
    }

    public static boolean bpmnReferencesTemplate(String bpmnXml, Long templateId) {
        return bpmnPropertyEquals(bpmnXml, "emailTemplateId", idString(templateId));
    }

    private static boolean anyEmailBlock(Map<String, Object> config, Predicate<Map<?, ?>> matches) {
        if (config == null) {
            return false;
        }
        if (config.get(ActionConfigIdRewriter.POST_EMAIL_RULES) instanceof List<?> rules) {
            for (Object rule : rules) {
                if (rule instanceof Map<?, ?> block && matches.test(block)) {
                    return true;
                }
            }
            return false;
        }
        return config.get(ActionConfigIdRewriter.POST_EMAIL) instanceof Map<?, ?> post && matches.test(post);
    }

    private static boolean bpmnPropertyEquals(String bpmnXml, String propertyName, String... expected) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return false;
        }
        Matcher matcher = propertyValuePattern(propertyName).matcher(bpmnXml);
        while (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (valueEquals(value, expected)) {
                return true;
            }
        }
        return false;
    }

    private static Pattern propertyValuePattern(String propertyName) {
        String quoted = Pattern.quote(propertyName);
        return Pattern.compile(
                "name\\s*=\\s*[\"']" + quoted + "[\"'][^>]*?value\\s*=\\s*[\"']([^\"']*)[\"']|"
                        + "value\\s*=\\s*[\"']([^\"']*)[\"'][^>]*?name\\s*=\\s*[\"']" + quoted + "[\"']",
                Pattern.DOTALL);
    }

    private static boolean valueEquals(Object raw, String... expected) {
        if (raw == null) {
            return false;
        }
        String actual = String.valueOf(raw).trim();
        if (actual.isEmpty()) {
            return false;
        }
        for (String candidate : expected) {
            if (candidate != null && !candidate.isBlank() && candidate.equals(actual)) {
                return true;
            }
        }
        return false;
    }

    private static String idString(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
