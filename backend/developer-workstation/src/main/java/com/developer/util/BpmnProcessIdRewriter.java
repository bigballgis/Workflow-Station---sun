package com.developer.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 BPMN 中 {@code <process id="...">} 重写为功能单元 {@code code}，与新建功能单元时
 * {@link MinimalBpmnTemplate#build(String)} 使用同一 process id 规则。
 *
 * <p>clone / import 会生成新的 function unit code，但 BPMN 往往仍保留源流程的
 * {@code Process_1} 等 id；部署与 Process Properties 面板均依赖 process id 与 code 一致。
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
     * 提取 BPMN 中第一个 {@code process} 元素的 id。
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
     * 将 process id（及关联 diagram 的 bpmnElement / processRef）重写为功能单元 code。
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
