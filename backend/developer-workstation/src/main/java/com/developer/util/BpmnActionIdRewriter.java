package com.developer.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Objects;

/** Structural Action-only mapping shared by clone, import and snapshot restore. */
final class BpmnActionIdRewriter {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private BpmnActionIdRewriter() {}

    static String rewrite(String xml, Map<Long, Long> mapping) {
        if (mapping == null || mapping.isEmpty()) return xml;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            boolean changed = false;
            var extensions = document.getElementsByTagNameNS("*", "extensionElements");
            for (int i = 0; i < extensions.getLength(); i++) {
                changed |= rewriteExtension((Element) extensions.item(i), mapping);
            }
            if (!changed) return xml;
            TransformerFactory transformers = TransformerFactory.newInstance();
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = transformers.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toString();
        } catch (ParserConfigurationException | SAXException | IOException | TransformerException ex) {
            throw new IllegalArgumentException("Unable to rewrite BPMN action bindings", ex);
        }
    }

    private static boolean rewriteExtension(Element extension, Map<Long, Long> mapping) {
        boolean changed = false;
        for (Node node = extension.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element element) || !supportedNamespace(element.getNamespaceURI())) continue;
            if ("properties".equals(element.getLocalName())) {
                // Historical children may have a different namespace; the container defines their scope.
                for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child instanceof Element property) changed |= rewriteProperty(property, mapping);
                }
            } else {
                changed |= rewriteProperty(element, mapping);
            }
        }
        return changed;
    }

    private static boolean supportedNamespace(String namespace) {
        return namespace == null || namespace.isEmpty()
                || "http://www.omg.org/spec/BPMN/20100524/MODEL".equals(namespace)
                || "http://workflow.platform/schema/custom".equals(namespace)
                || "http://custom.bpmn.io/schema".equals(namespace);
    }

    private static boolean rewriteProperty(Element property, Map<Long, Long> mapping) {
        if (!("property".equals(property.getLocalName()) || "values".equals(property.getLocalName()))) return false;
        String name = property.getAttribute("name");
        if (!("actionIds".equals(name) || "globalActionIds".equals(name))) return false;
        boolean attribute = property.hasAttribute("value");
        String value = attribute ? property.getAttribute("value") : property.getTextContent();
        String rewritten = remapArray(value, mapping);
        if (Objects.equals(value, rewritten)) return false;
        if (attribute) property.setAttribute("value", rewritten);
        else property.setTextContent(rewritten);
        return true;
    }

    private static String remapArray(String value, Map<Long, Long> mapping) {
        if (value == null || value.isBlank()) return value;
        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.contains("\"")) {
            return remapQuotedArray(value, trimmed, mapping);
        }
        // FALLBACK(migration): historical arrays use unquoted IDs, with optional brackets.
        boolean wrapped = trimmed.startsWith("[") && trimmed.endsWith("]");
        String inner = wrapped ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
        if (inner.isBlank()) return value;
        String[] parts = inner.split(",");
        boolean changed = false;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            Long mapped = mappedId(part, mapping);
            if (mapped != null) {
                parts[i] = mapped.toString();
                changed = true;
            } else parts[i] = part;
        }
        String result = String.join(",", parts);
        return changed ? (wrapped ? "[" + result + "]" : result) : value;
    }

    private static String remapQuotedArray(String original, String decoded, Map<Long, Long> mapping) {
        try {
            ArrayNode ids = (ArrayNode) JSON.readTree(decoded);
            boolean changed = false;
            for (int i = 0; i < ids.size(); i++) {
                var id = ids.get(i);
                if (!id.isTextual() && !id.isIntegralNumber()) continue;
                Long mapped = mappedId(id.asText().trim(), mapping);
                if (mapped != null) {
                    ids.set(i, JSON.getNodeFactory().numberNode(mapped));
                    changed = true;
                }
            }
            return changed ? ids.toString() : original;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid action ID list", ex);
        }
    }

    private static Long mappedId(String id, Map<Long, Long> mapping) {
        try {
            return mapping.get(Long.parseLong(id));
        } catch (NumberFormatException ex) {
            return null; // Nonnumeric legacy IDs are not keys in the database ID mapping.
        }
    }
}
