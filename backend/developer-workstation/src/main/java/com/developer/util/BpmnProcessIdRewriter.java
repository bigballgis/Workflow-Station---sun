package com.developer.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites {@code <process id="...">} in BPMN to the function unit {@code code}, using the same process id rule as
 * {@link MinimalBpmnTemplate#build(String)} when creating a new function unit.
 *
 * <p>Clone / import produce a new function unit code, but BPMN often still keeps source process ids such as
 * {@code Process_1}; deployment and the Process Properties panel both require process id to match code.
 */
public final class BpmnProcessIdRewriter {

    private static final Pattern PROCESS_ID_ATTR = Pattern.compile(
            "(<(?:bpmn:)?process\\b[^>]*\\bid\\s*=\\s*[\"'])([^\"']+)([\"'])",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern BPMN_ELEMENT_ATTR = Pattern.compile(
            "(\\bbpmnElement\\s*=\\s*[\"'])([^\"']+)([\"'])",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PROCESS_REF_ATTR = Pattern.compile(
            "(\\bprocessRef\\s*=\\s*[\"'])([^\"']+)([\"'])",
            Pattern.CASE_INSENSITIVE);

    private BpmnProcessIdRewriter() {
    }

    /**
     * Extracts the id of the first {@code process} element in BPMN.
     */
    public static String extractProcessId(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return null;
        }
        String decoded = XmlEncodingUtil.smartDecode(bpmnXml);
        Matcher matcher = PROCESS_ID_ATTR.matcher(decoded);
        return matcher.find() ? matcher.group(2) : null;
    }

    /**
     * Rewrites process id (and related diagram bpmnElement / processRef) to the function unit code.
     */
    public static String rewriteToFunctionUnitCode(String bpmnXml, String functionUnitCode) {
        if (bpmnXml == null || bpmnXml.isBlank()
                || functionUnitCode == null || functionUnitCode.isBlank()) {
            return bpmnXml;
        }

        String decoded = XmlEncodingUtil.smartDecode(bpmnXml);
        boolean wasEncoded = !decoded.equals(bpmnXml);

        String oldProcessId = extractProcessId(decoded);
        if (oldProcessId == null || oldProcessId.equals(functionUnitCode)) {
            return bpmnXml;
        }

        Matcher processMatcher = PROCESS_ID_ATTR.matcher(decoded);
        if (!processMatcher.find()) {
            return bpmnXml;
        }
        StringBuilder processReplaced = new StringBuilder();
        processMatcher.appendReplacement(processReplaced, Matcher.quoteReplacement(
                processMatcher.group(1) + functionUnitCode + processMatcher.group(3)));
        processMatcher.appendTail(processReplaced);
        String rewritten = processReplaced.toString();

        rewritten = replaceAttrWhenValueEquals(rewritten, BPMN_ELEMENT_ATTR, oldProcessId, functionUnitCode);
        rewritten = replaceAttrWhenValueEquals(rewritten, PROCESS_REF_ATTR, oldProcessId, functionUnitCode);

        if (rewritten.equals(decoded)) {
            return bpmnXml;
        }
        return wasEncoded ? XmlEncodingUtil.encode(rewritten) : rewritten;
    }

    private static String replaceAttrWhenValueEquals(String xml, Pattern pattern,
                                                     String oldValue, String newValue) {
        Matcher matcher = pattern.matcher(xml);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String current = matcher.group(2);
            if (oldValue.equals(current)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(
                        matcher.group(1) + newValue + matcher.group(3)));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
