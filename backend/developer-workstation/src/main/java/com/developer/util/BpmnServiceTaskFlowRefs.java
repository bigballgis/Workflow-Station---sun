package com.developer.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 BPMN 中提取 service task 引用的 Automation flow（{@code ap:flowId} 扩展属性）。
 *
 * <p>属性形如：
 * <pre>{@code
 *   <flowable:property name="ap:flowId" value="hxJ2K1..." />
 * }</pre>
 * 属性名与引擎侧一致（{@code ServiceTaskExecutor} / {@code ProcessDeploymentManager} 只认
 * {@code ap:flowId}），故此处也只认这一个键，不接受无前缀的 {@code flowId}。</p>
 *
 * <p>只带 {@code ap:webhookUrl} 而无 flowId 的 service task 是环境内直连地址，本身不可移植，
 * 不在此列。</p>
 */
public final class BpmnServiceTaskFlowRefs {

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
        if (xml == null || !xml.contains("ap:flowId")) {
            return List.of();
        }
        Set<String> refs = new LinkedHashSet<>();
        Matcher elements = FLOW_ID_ELEMENT.matcher(xml);
        while (elements.find()) {
            Matcher value = VALUE_ATTR.matcher(elements.group());
            if (value.find()) {
                String ref = value.group(1).trim();
                if (!ref.isEmpty()) {
                    refs.add(ref);
                }
            }
        }
        return List.copyOf(refs);
    }
}
