package com.developer.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 BPMN 中提取 service task 引用的 Automation flow（{@code ap:flowKey} 业务键，
 * 或 legacy {@code ap:flowId} 扩展属性）。
 *
 * <p>属性形如：
 * <pre>{@code
 *   <flowable:property name="ap:flowKey" value="invoice-sync" />
 *   <flowable:property name="ap:flowId" value="hxJ2K1..." />
 * }</pre>
 * 属性名与引擎侧一致（{@code ServiceTaskExecutor} / {@code ProcessDeploymentManager}
 * 认 {@code ap:flowKey} 优先、回退 {@code ap:flowId}），故此处同键同序：同一个
 * service task 里两者并存时只取 {@code ap:flowKey}（业务键才是可移植引用；旧 flowId
 * 是源环境实值，跨环境本就解析不到）。不接受无前缀的 {@code flowId}。</p>
 *
 * <p>只带 {@code ap:webhookUrl} 而无 flow 引用的 service task 是环境内直连地址，
 * 本身不可移植，不在此列。</p>
 */
public final class BpmnServiceTaskFlowRefs {

    /** serviceTask 块（任意命名空间前缀；自闭合的没有扩展属性，无需匹配） */
    private static final Pattern SERVICE_TASK_BLOCK = Pattern.compile(
            "<(?:\\w+:)?serviceTask\\b.*?</(?:\\w+:)?serviceTask\\s*>",
            Pattern.DOTALL);

    /** 含 {@code name="ap:flowKey"} 的单个元素标签（属性顺序无关） */
    private static final Pattern FLOW_KEY_ELEMENT = Pattern.compile(
            "<[^<>]*\\bname\\s*=\\s*[\"']ap:flowKey[\"'][^<>]*>",
            Pattern.DOTALL);

    /** 含 {@code name="ap:flowId"} 的单个元素标签（属性顺序无关） */
    private static final Pattern FLOW_ID_ELEMENT = Pattern.compile(
            "<[^<>]*\\bname\\s*=\\s*[\"']ap:flowId[\"'][^<>]*>",
            Pattern.DOTALL);

    private static final Pattern VALUE_ATTR = Pattern.compile(
            "\\bvalue\\s*=\\s*[\"']([^\"']*)[\"']");

    private BpmnServiceTaskFlowRefs() {
    }

    /**
     * 提取全部 flow 引用，按出现顺序去重。入参可为 Base64 或明文 BPMN。
     *
     * @return 引用列表；无 service task / 无 flow 配置时为空列表
     */
    public static List<String> extract(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return List.of();
        }
        String xml = XmlEncodingUtil.smartDecode(bpmnXml);
        if (xml == null || (!xml.contains("ap:flowKey") && !xml.contains("ap:flowId"))) {
            return List.of();
        }
        Set<String> refs = new LinkedHashSet<>();
        Matcher blocks = SERVICE_TASK_BLOCK.matcher(xml);
        boolean anyBlock = false;
        while (blocks.find()) {
            anyBlock = true;
            String block = blocks.group();
            // 同一 task 内业务键优先；无业务键才收 legacy flowId
            if (!collectValues(FLOW_KEY_ELEMENT, block, refs)) {
                collectValues(FLOW_ID_ELEMENT, block, refs);
            }
        }
        if (!anyBlock) {
            // 兜底：块匹配不到（非常规序列化）时退回全文提取，宁多校验、不漏引用
            collectValues(FLOW_KEY_ELEMENT, xml, refs);
            collectValues(FLOW_ID_ELEMENT, xml, refs);
        }
        return List.copyOf(refs);
    }

    /** 提取匹配元素的 value 属性到 refs；返回是否收到至少一个非空值 */
    private static boolean collectValues(Pattern elementPattern, String xml, Set<String> refs) {
        boolean found = false;
        Matcher elements = elementPattern.matcher(xml);
        while (elements.find()) {
            Matcher value = VALUE_ATTR.matcher(elements.group());
            if (value.find()) {
                String ref = value.group(1).trim();
                if (!ref.isEmpty()) {
                    refs.add(ref);
                    found = true;
                }
            }
        }
        return found;
    }
}
