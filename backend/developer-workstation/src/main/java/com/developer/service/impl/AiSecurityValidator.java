package com.developer.service.impl;

import com.developer.dto.AiValidationResult;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * AI 生成数据的安全校验协作类
 * <p>
 * 负责 SVG / DMN / BPMN 等 XML 内容的安全性校验（XXE 加固解析、危险标签/属性检测）。
 * 由 {@link AiValidationServiceImpl} 门面委托调用，行为与原实现逐字保持一致。
 */
@Component
public class AiSecurityValidator {

    /**
     * 校验决策定义中的 dmnXml 安全性
     * 检查 DMN XML 解析有效性与危险标签
     */
    void validateDmnXml(String dmnXml, String path, AiValidationResult result) {
        if (dmnXml != null && !dmnXml.isBlank()) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(dmnXml)));
                // 检查危险标签
                for (String tag : new String[]{"script", "iframe"}) {
                    if (doc.getElementsByTagName(tag).getLength() > 0) {
                        result.addError("DMN_VALIDATION", path + ".dmnXml",
                                "DMN XML contains dangerous tag: <" + tag + ">");
                    }
                }
            } catch (Exception e) {
                result.addError("DMN_VALIDATION", path + ".dmnXml",
                        "DMN XML is not valid: " + e.getMessage());
            }
        }
    }

    void validateSvg(Map<String, Object> icon, AiValidationResult result) {
        if (icon == null) return;

        // Validate icon.name length
        String name = (String) icon.get("name");
        if (name != null && name.length() > 100) {
            result.addError("SVG_VALIDATION", "icon.name", "Icon name must not exceed 100 characters");
        }

        String svgContent = (String) icon.get("svgContent");
        if (svgContent == null || svgContent.isBlank()) return;

        // Check size ≤ 10KB
        if (svgContent.getBytes(StandardCharsets.UTF_8).length > 10240) {
            result.addError("SVG_VALIDATION", "icon.svgContent", "SVG content must not exceed 10KB");
        }

        // Parse XML
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(svgContent)));

            // Root element must be <svg>
            if (!"svg".equals(doc.getDocumentElement().getTagName())) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG root element must be <svg>");
            }

            // Check for dangerous tags
            String[] dangerousTags = {"script", "iframe", "object", "embed"};
            for (String tag : dangerousTags) {
                if (doc.getElementsByTagName(tag).getLength() > 0) {
                    result.addError("SVG_VALIDATION", "icon.svgContent", "SVG contains dangerous tag: <" + tag + ">");
                }
            }

            // Check for on* event attributes and javascript: protocol
            checkDangerousAttributes(doc.getDocumentElement(), result);

        } catch (Exception e) {
            result.addError("SVG_VALIDATION", "icon.svgContent", "SVG is not valid XML: " + e.getMessage());
        }
    }

    private void checkDangerousAttributes(Element element, AiValidationResult result) {
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String attrName = attr.getNodeName().toLowerCase();
            String attrValue = attr.getNodeValue().toLowerCase().trim();

            if (attrName.startsWith("on")) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG contains event attribute: " + attrName);
            }
            if (attrValue.contains("javascript:")) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG contains javascript: protocol reference");
            }
            if (attrValue.contains("url(") && (attrValue.contains("http:") || attrValue.contains("https:"))) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG contains external resource reference");
            }
            // Check xlink:href with external URLs
            if (attrName.contains("href") && (attrValue.startsWith("http:") || attrValue.startsWith("https:"))) {
                result.addError("SVG_VALIDATION", "icon.svgContent", "SVG contains external resource reference");
            }
        }

        // Recursively check child elements
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                checkDangerousAttributes((Element) children.item(i), result);
            }
        }
    }

    void validateBpmnXml(Map<String, Object> processDefinition, AiValidationResult result) {
        if (processDefinition == null) return;

        String bpmnXml = (String) processDefinition.get("bpmnXml");
        if (bpmnXml == null || bpmnXml.isBlank()) return;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(bpmnXml)));

            // Check for BPMN 2.0 namespace
            String namespaceURI = doc.getDocumentElement().getNamespaceURI();
            if (namespaceURI == null || !namespaceURI.contains("omg.org/spec/BPMN")) {
                result.addError("BPMN_VALIDATION", "processDefinition.bpmnXml", "BPMN XML missing BPMN 2.0 namespace declaration");
            }
        } catch (Exception e) {
            result.addError("BPMN_VALIDATION", "processDefinition.bpmnXml", "BPMN XML is not valid: " + e.getMessage());
        }
    }
}
