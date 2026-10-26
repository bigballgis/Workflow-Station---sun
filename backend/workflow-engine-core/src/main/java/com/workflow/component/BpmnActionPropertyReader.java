package com.workflow.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.ExtensionElement;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Action-only property precedence, shared by model and XML readers. */
final class BpmnActionPropertyReader {
    private static final String PLATFORM = "http://workflow.platform/schema/custom";
    private static final String LEGACY = "http://custom.bpmn.io/schema";
    private static final ObjectMapper JSON = new ObjectMapper();
    private record Property(String namespace, String name, String value) {}
    record Value(String raw, boolean duplicated) {}
    private BpmnActionPropertyReader() {}

    private static int rank(String namespace) {
        if (PLATFORM.equals(namespace)) return 2;
        if (LEGACY.equals(namespace)) return 1;
        // FALLBACK(migration): unqualified properties may inherit the default BPMN namespace.
        return namespace == null || namespace.isEmpty()
                || "http://www.omg.org/spec/BPMN/20100524/MODEL".equals(namespace) ? 0 : -1;
    }

    static Value model(Map<String, List<ExtensionElement>> extensions, String key) {
        List<Property> properties = new ArrayList<>();
        if (extensions != null) for (var group : extensions.values()) for (var element : group) {
            if (rank(element.getNamespace()) < 0) continue;
            if ("properties".equals(element.getName())) {
                for (String type : List.of("property", "values")) {
                    for (var child : element.getChildElements().getOrDefault(type, List.of())) {
                        add(properties, child, element.getNamespace());
                    }
                }
            } else add(properties, element, element.getNamespace());
        }
        return select(properties, key);
    }

    private static void add(List<Property> properties, ExtensionElement element, String namespace) {
        if (!List.of("property", "values").contains(element.getName())) return;
        String value = element.getAttributeValue(null, "value");
        properties.add(new Property(namespace, element.getAttributeValue(null, "name"),
                value != null ? value : element.getElementText() != null ? element.getElementText() : ""));
    }

    static Value xml(Element scope, String key) {
        List<Property> properties = new ArrayList<>();
        if (scope != null) for (var extension : children(scope, "extensionElements")) {
            for (var child : children(extension, null)) {
                if (rank(child.getNamespaceURI()) < 0) continue;
                if ("properties".equals(localName(child))) {
                    for (String type : List.of("property", "values")) {
                        for (var prop : children(child, type)) add(properties, prop, child.getNamespaceURI());
                    }
                } else add(properties, child, child.getNamespaceURI());
            }
        }
        return select(properties, key);
    }

    private static void add(List<Property> properties, Element element, String namespace) {
        if (!List.of("property", "values").contains(localName(element))) return;
        properties.add(new Property(namespace, element.getAttribute("name"),
                element.hasAttribute("value") ? element.getAttribute("value") : element.getTextContent()));
    }

    private static Value select(List<Property> properties, String key) {
        String raw = null;
        int priority = -1, matches = 0;
        for (var property : properties) {
            int candidate = rank(property.namespace());
            if (!key.equals(property.name()) || candidate < priority || candidate < 0) continue;
            if (candidate > priority) matches = 0;
            priority = candidate;
            raw = property.value();
            matches++;
        }
        return new Value(raw, matches > 1);
    }

    static List<Element> children(Element parent, String name) {
        List<Element> children = new ArrayList<>();
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            if (parent.getChildNodes().item(i) instanceof Element child
                    && (name == null || name.equals(localName(child)))) children.add(child);
        }
        return children;
    }

    private static String localName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getNodeName();
    }

    static List<String> ids(String raw) {
        if (raw == null) return null; // Absent differs from an explicitly empty binding.
        String text = raw.trim();
        if (text.isEmpty()) return List.of();
        try {
            var json = JSON.readTree(text);
            if (json == null || !json.isArray()) throw new IllegalArgumentException("Invalid action ID list");
            List<String> result = new ArrayList<>();
            for (var id : json) {
                if (!(id.isTextual() || id.isNumber()) || id.asText().trim().isEmpty()) {
                    throw new IllegalArgumentException("Invalid action ID list");
                }
                result.add(id.asText().trim());
            }
            return result;
        } catch (JsonProcessingException ex) {
            // FALLBACK(migration): historical lists use unquoted IDs, with optional brackets.
            String inner = text.startsWith("[") && text.endsWith("]") ? text.substring(1, text.length() - 1) : text;
            if (inner.chars().anyMatch(ch -> "[]{}\"'".indexOf(ch) >= 0)) throw new IllegalArgumentException("Invalid action ID list");
            var result = Arrays.stream(inner.split(",", -1)).map(String::trim).toList();
            if (result.stream().anyMatch(String::isEmpty)) throw new IllegalArgumentException("Invalid action ID list");
            return result;
        }
    }
}
