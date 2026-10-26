package com.platform.common.bpmn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.Document;
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
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Action-only XML contract: platform wins legacy; later copies win, including explicit empty. */
public final class BpmnActionBindings {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> KEYS = Set.of("actionIds", "actionNames", "globalActionIds", "globalActionNames");
    private BpmnActionBindings() {}

    public record Property(Element element, int priority) {
        public String value() {
            return element.hasAttribute("value") ? element.getAttribute("value") : element.getTextContent();
        }
        public void setValue(String value) {
            if (element.hasAttribute("value")) element.setAttribute("value", value);
            else element.setTextContent(value);
        }
    }

    public static Document parse(String storedXml) {
        String xml = storedXml.trim();
        if (!xml.startsWith("<")) xml = new String(Base64.getMimeDecoder().decode(xml), StandardCharsets.UTF_8);
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
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new IllegalArgumentException("Invalid BPMN action bindings", ex);
        }
    }

    public static List<Element> scopes(Document document) {
        List<Element> scopes = new ArrayList<>();
        var elements = document.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (Set.of("process", "userTask").contains(element.getLocalName())) scopes.add(element);
        }
        return scopes;
    }

    public static String idsKey(Element scope) {
        return switch (scope.getLocalName()) {
            case "process" -> "globalActionIds";
            case "userTask" -> "actionIds";
            default -> throw new IllegalArgumentException("Unsupported Action binding scope");
        };
    }

    public static Map<String, Property> properties(Element scope) {
        Map<String, Property> result = new LinkedHashMap<>();
        for (Element extension : children(scope, "extensionElements")) {
            for (Element element : children(extension, null)) collect(result, element);
        }
        return result;
    }

    private static void collect(Map<String, Property> result, Element element) {
        int priority = rank(element.getNamespaceURI());
        if (priority < 0) return;
        if ("properties".equals(element.getLocalName())) {
            for (String tag : List.of("property", "values")) {
                for (Element child : children(element, tag)) add(result, child, priority);
            }
        } else add(result, element, priority);
    }

    private static void add(Map<String, Property> result, Element element, int priority) {
        String key = element.getAttribute("name");
        if (!KEYS.contains(key) || !Set.of("property", "values").contains(element.getLocalName())) return;
        Property previous = result.get(key);
        if (previous == null || priority >= previous.priority()) result.put(key, new Property(element, priority));
    }

    private static int rank(String namespace) {
        if ("http://workflow.platform/schema/custom".equals(namespace)) return 2;
        if ("http://custom.bpmn.io/schema".equals(namespace)) return 1;
        // FALLBACK(migration): unqualified legacy properties can inherit the default BPMN namespace.
        return namespace == null || namespace.isEmpty()
                || "http://www.omg.org/spec/BPMN/20100524/MODEL".equals(namespace) ? 0 : -1;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child && (name == null || name.equals(child.getLocalName()))) result.add(child);
        }
        return result;
    }

    public static List<String> ids(String value) {
        if (value == null || value.isBlank()) return List.of();
        String text = value.trim();
        try {
            var array = JSON.readTree(text);
            if (array == null || !array.isArray()) throw new IllegalArgumentException("Invalid action ID list");
            List<String> result = new ArrayList<>();
            for (var id : array) {
                if (!(id.isTextual() || id.isNumber()) || id.asText().trim().isEmpty()) throw new IllegalArgumentException("Invalid action ID list");
                result.add(id.asText().trim());
            }
            return result;
        } catch (JsonProcessingException ex) {
            // FALLBACK(migration): historical lists use unquoted IDs, with optional brackets.
            String inner = text.startsWith("[") && text.endsWith("]") ? text.substring(1, text.length() - 1) : text;
            if (inner.chars().anyMatch(ch -> "[]{}\"'".indexOf(ch) >= 0)) throw new IllegalArgumentException("Invalid action ID list", ex);
            var result = Arrays.stream(inner.split(",", -1)).map(String::trim).toList();
            if (result.stream().anyMatch(String::isEmpty)) throw new IllegalArgumentException("Invalid action ID list", ex);
            return result;
        }
    }

    public static List<String> names(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            var array = JSON.readTree(value);
            if (array == null || !array.isArray()) throw new IllegalArgumentException("Invalid action name list");
            List<String> result = new ArrayList<>();
            for (var name : array) {
                if (!name.isTextual()) throw new IllegalArgumentException("Invalid action name list");
                result.add(name.asText());
            }
            return result;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid action name list", ex);
        }
    }

    public static String json(List<?> values) {
        try { return JSON.writeValueAsString(values); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Invalid action binding list", ex); }
    }

    public static String serialize(Document document) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toString();
        } catch (TransformerException ex) {
            throw new IllegalArgumentException("Unable to write BPMN action bindings", ex);
        }
    }
}
