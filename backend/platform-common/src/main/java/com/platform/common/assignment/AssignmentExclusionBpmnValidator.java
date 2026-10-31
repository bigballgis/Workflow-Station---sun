package com.platform.common.assignment;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Validates portable node policies and references from actual XML, without name-based business inference. */
public final class AssignmentExclusionBpmnValidator {
    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    public static final java.util.Set<String> SERVER_PROPERTY_NAMES=java.util.Set.of("assignmentRuntimeContract",
            "assignmentRuntimeTracking","assignmentRuntimeUsers","assignmentRuntimeDefinitions");

    private AssignmentExclusionBpmnValidator() { }

    /** Return present policies keyed by BPMN userTask ID; absent properties preserve legacy assignment. */
    public static Map<String, AssignmentExclusionPolicy> policies(String bpmn) {
        if (bpmn == null || bpmn.isBlank()) throw new IllegalArgumentException("Missing assignment BPMN");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bpmn.getBytes(StandardCharsets.UTF_8)));
            var users = document.getElementsByTagNameNS(BPMN, "userTask");
            Map<String, Element> tasks = new LinkedHashMap<>();
            for (int i = 0; i < users.getLength(); i++) {
                Element task = (Element) users.item(i);
                if (task.getAttribute("id").isBlank() || tasks.put(task.getAttribute("id"), task) != null) {
                    throw new IllegalArgumentException("Missing or duplicate userTask ID");
                }
            }
            Map<String, AssignmentExclusionPolicy> result = new LinkedHashMap<>();
            var all = document.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                Element property = (Element) all.item(i);
                String tag = property.getLocalName();
                if (!("property".equals(tag) || "values".equals(tag))) continue;
                if (SERVER_PROPERTY_NAMES.contains(property.getAttribute("name"))) {
                    throw new IllegalArgumentException("Portable BPMN cannot provide compiled assignment metadata");
                }
                if (!"assignmentExclusion".equals(property.getAttribute("name"))) continue;
                Element task = propertyTask(property);
                String key = task.getAttribute("id");
                if (result.put(key, AssignmentExclusionPolicyCodec.decode(property.getAttribute("value"))) != null) {
                    throw new IllegalArgumentException("Duplicate assignment exclusion property");
                }
            }
            for (var entry : result.entrySet()) {
                validateNodeBu(entry.getValue(), tasks.get(entry.getKey()));
                for (String selected : entry.getValue().selectedNodeKeys()) {
                    Element target = tasks.get(selected);
                    if (target == null || process(target) != process(tasks.get(entry.getKey()))) {
                        throw new IllegalArgumentException("Assignment exclusion references a missing or foreign userTask");
                    }
                }
            }
            return Map.copyOf(result);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot validate assignment exclusion BPMN", e);
        }
    }

    private static void validateNodeBu(AssignmentExclusionPolicy policy, Element task) {
        if (!policy.backup().enabled() || policy.backup().mode() != AssignmentExclusionPolicy.Mode.BU_ROLE
                || policy.backup().buSource() != AssignmentExclusionPolicy.BuSource.NODE_ASSIGNMENT_BU) return;
        String type = property(task, "assigneeType");
        boolean fixed = "BU_ROLE".equals(type) || "FIXED_BU_ROLE".equals(type);
        boolean row = "ELEMENT_VARIABLE".equals(type) && (nonempty(property(task, "buField")) || "role".equals(property(task, "assigneeMode")));
        if (!fixed && !row) throw new IllegalArgumentException("Backup requires a node source with one explicit assignment business unit");
    }

    private static String property(Element task, String name) {
        String value = null;
        var descendants = task.getElementsByTagName("*");
        for (int i=0; i<descendants.getLength(); i++) {
            var candidate = (Element) descendants.item(i);
            if (!("property".equals(candidate.getLocalName()) || "values".equals(candidate.getLocalName()))
                    || !name.equals(candidate.getAttribute("name")) || propertyTask(candidate) != task) continue;
            if (value != null) throw new IllegalArgumentException("Ambiguous node assignment source property");
            value = candidate.getAttribute("value");
        }
        return value;
    }
    private static boolean nonempty(String value) { return value != null && !value.isBlank(); }

    private static Element propertyTask(Element property) {
        Node node = property.getParentNode();
        boolean extensions = false;
        while (node instanceof Element element) {
            if (BPMN.equals(element.getNamespaceURI()) && "extensionElements".equals(element.getLocalName())) extensions = true;
            if (BPMN.equals(element.getNamespaceURI()) && "userTask".equals(element.getLocalName()) && extensions) return element;
            if (BPMN.equals(element.getNamespaceURI()) && "process".equals(element.getLocalName())) break;
            node = element.getParentNode();
        }
        throw new IllegalArgumentException("Assignment exclusion must belong to a userTask's extension properties");
    }

    private static Element process(Element task) {
        Node node = task;
        while (node instanceof Element element) {
            if (BPMN.equals(element.getNamespaceURI()) && "process".equals(element.getLocalName())) return element;
            node = element.getParentNode();
        }
        throw new IllegalArgumentException("Assignment userTask has no process");
    }
}
