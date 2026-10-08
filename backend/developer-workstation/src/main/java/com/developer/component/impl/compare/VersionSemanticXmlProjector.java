package com.developer.component.impl.compare;

import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.developer.component.impl.compare.VersionSemanticDiffSupport.DesignObject;

/** Safe BPMN/DMN projection: elements are matched by their XML ID, never by list position. */
final class VersionSemanticXmlProjector {

    private static final String PLATFORM_CUSTOM_NS = "http://workflow.platform/schema/custom";
    private static final String LEGACY_CUSTOM_NS = "http://custom.bpmn.io/schema";
    // Only designer-owned scalar/list fields are readable. Free-form scripts, HTTP payloads,
    // notification bodies, credentials and unknown extensions remain opaque fingerprints.
    private static final Set<String> READABLE_PROCESS_PROPERTIES = Set.of(
            "assigneeType", "assigneeLabel", "assigneeValue", "assigneeVariable",
            "assigneeAnchor", "assigneeMode", "assigneeField", "candidateUsers",
            "candidateGroups", "manualAssignVariable", "manualAssignBuVariable",
            "manualAssignRoleVariable", "roleId", "roleIds", "roleField",
            "businessUnitId", "buField", "subTableId", "subTableName", "rowIdVariable",
            "formId", "formName", "requestFormId", "requestFormName",
            "actionIds", "actionNames", "globalActionIds", "globalActionNames", "timeoutEnabled", "timeoutDuration",
            "timeoutAction", "multiInstance", "sequential", "collection",
            "completionCondition", "miTaskStatusField", "miTaskCurrentNodeField",
            "serviceType", "decisionTableReferenceKey", "fallbackToDefaultTenant",
            "initiator", "endAction", "timerType", "correlationKey", "signalScope",
            "conditionType");

    record Projection(String status, String scope, Map<String, DesignObject> objects) {}

    private final VersionXmlNormalizer xmlNormalizer = new VersionXmlNormalizer();

    Projection project(String module, VersionSnapshotNormalizer.Normalized snapshot) {
        if (VersionSnapshotNormalizer.DECISIONS.equals(module)) return decisions(snapshot);
        String xmlKey = snapshot.modern() ? "process" : "processXml";
        String xml = snapshot.sanitizedSource().path(xmlKey).asText("");
        String scope = VersionSnapshotNormalizer.AUTOMATION.equals(module)
                ? "REFERENCES_ONLY" : "FULL";
        if (xml.isBlank()) return new Projection("COMPARED", scope, Map.of());
        Document document = xmlNormalizer.parseForSemantic(xml);
        // FALLBACK(migration): historical FU snapshots can contain malformed XML (QA FU 2).
        // Keep the explicit generic diff until those versions age out; never claim no change.
        if (document == null) return new Projection("UNPARSEABLE", scope, Map.of());
        Map<String, DesignObject> objects = new LinkedHashMap<>();
        visit(document.getDocumentElement(), "process", module, objects);
        return new Projection("COMPARED", scope, objects);
    }

    private Projection decisions(VersionSnapshotNormalizer.Normalized snapshot) {
        JsonNode decisions = snapshot.sanitizedSource().path("_compareDecisions");
        String scope = snapshot.sanitizedSource().path("_decisionComparePartial").asBoolean() ? "PARTIAL" : "FULL";
        Map<String, DesignObject> objects = new LinkedHashMap<>();
        for (int index = 0; index < decisions.size(); index++) {
            JsonNode item = decisions.get(index);
            String key = item.path("compareKey").asText();
            if (item.path("metadata").isObject()) {
                ObjectNode properties = ((ObjectNode) item.get("metadata")).deepCopy();
                String label = properties.path("decisionName").asText(properties.path("decisionKey").asText());
                objects.put("decision/" + key + "/metadata", new DesignObject("DECISION_DEFINITION",
                        "decision/" + key + "/metadata", label, "DESIGN", properties));
            }
            String xml = item.path("dmnXml").asText("");
            if (xml.isBlank()) continue;
            if (!item.path("xmlIdentityAvailable").asBoolean()) {
                return new Projection("UNPARSEABLE", scope, Map.of());
            }
            Document document = xmlNormalizer.parseForSemantic(xml);
            // FALLBACK(migration): malformed legacy DMN remains readable as bounded text diff.
            // Remove only when historical snapshots with this encoding are no longer supported.
            if (document == null) return new Projection("UNPARSEABLE", "FULL", Map.of());
            if (key == null || key.isBlank()) {
                // A positional identity would turn a reorder into a false business modification.
                return new Projection("UNPARSEABLE", "FULL", Map.of());
            }
            visit(document.getDocumentElement(), "decision/" + key, VersionSnapshotNormalizer.DECISIONS, objects);
        }
        return new Projection("COMPARED", scope, objects);
    }

    private void visit(Element element, String prefix, String module,
                       Map<String, DesignObject> objects) {
        String id = element.getAttribute("id");
        if (!id.isBlank()) {
            DesignObject object = object(element, prefix + "/" + id, module);
            if (object != null && objects.putIfAbsent(object.key(), object) != null) {
                throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_DUPLICATE_KEY",
                        "A saved version contains duplicate XML element identity: " + id);
            }
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement) visit(childElement, prefix, module, objects);
        }
    }

    private DesignObject object(Element element, String key, String module) {
        if (VersionSnapshotNormalizer.AUTOMATION.equals(module)) return automation(element, key);
        String localName = localName(element);
        boolean layout = layout(element);
        ObjectNode properties = JsonNodeFactory.instance.objectNode();
        properties.put("elementType", localName);
        if (element.getNamespaceURI() != null) {
            properties.put("namespace", element.getNamespaceURI());
        }
        attributes(element, properties, !VersionSnapshotNormalizer.DECISIONS.equals(module));
        childValues(element, properties, VersionSnapshotNormalizer.PROCESS.equals(module));
        if (VersionSnapshotNormalizer.DECISIONS.equals(module) && "decisionTable".equals(localName)) {
            for (String type : List.of("input", "output", "rule")) {
                var order = properties.putArray(type + "Order");
                NodeList children = element.getChildNodes();
                for (int index = 0; index < children.getLength(); index++) {
                    if (children.item(index) instanceof Element child && type.equals(localName(child))) {
                        order.add(child.getAttribute("id"));
                    }
                }
            }
        }
        String label = element.getAttribute("name");
        if (label.isBlank()) label = localName + " · " + element.getAttribute("id");
        String type = VersionSnapshotNormalizer.DECISIONS.equals(module)
                ? dmnType(localName) : bpmnType(localName, layout);
        return new DesignObject(type, key, label, layout ? "LAYOUT_ONLY" : "DESIGN", properties);
    }

    private DesignObject automation(Element element, String key) {
        ObjectNode references = JsonNodeFactory.instance.objectNode();
        collectFlowReferences(element, references);
        if (references.isEmpty()) return null;
        String label = element.getAttribute("name");
        if (label.isBlank()) label = localName(element) + " · " + element.getAttribute("id");
        return new DesignObject("AUTOMATION_REFERENCE", key, label, "REFERENCES_ONLY", references);
    }

    private void collectFlowReferences(Element element, ObjectNode references) {
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            String name = localName(attribute);
            if ("flowKey".equals(name) || "flowId".equals(name)) {
                references.put(name, safeValue(name, attribute.getNodeValue()));
            }
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (!(children.item(index) instanceof Element child)
                    || !child.getAttribute("id").isBlank()) continue;
            String referenceName = flowReferenceName(child);
            if (referenceName != null) {
                references.put(referenceName, safeValue(referenceName, child.getAttribute("value")));
            } else {
                collectFlowReferences(child, references);
            }
        }
    }

    private String flowReferenceName(Element element) {
        if (!element.hasAttribute("value")) return null;
        return switch (element.getAttribute("name")) {
            case "ap:flowKey" -> "flowKey";
            case "ap:flowId" -> "flowId";
            default -> null;
        };
    }

    private void attributes(Element element, ObjectNode properties, boolean excludeFlowReferences) {
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            String name = localName(attribute);
            if ("id".equals(name) || name.startsWith("xmlns")) continue;
            if (excludeFlowReferences && ("flowKey".equals(name) || "flowId".equals(name))) continue;
            properties.put(name, safeValue(name, attribute.getNodeValue()));
        }
    }

    private void childValues(Element element, ObjectNode properties, boolean process) {
        NodeList children = element.getChildNodes();
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (!(child instanceof Element childElement) || !childElement.getAttribute("id").isBlank()) continue;
            Element comparableChild = (Element) childElement.cloneNode(true);
            if (stripFlowReferences(comparableChild)) continue;
            String name = localName(childElement);
            int occurrence = occurrences.merge(name, 1, Integer::sum);
            String key = occurrence == 1 ? name : name + "[" + occurrence + "]";
            if (process && "extensionElements".equals(name)) {
                ObjectNode config = readableProcessProperties(comparableChild);
                if (!config.isEmpty()) {
                    JsonNode current = properties.get("config");
                    if (current instanceof ObjectNode currentConfig) currentConfig.setAll(config);
                    else {
                        if (current != null) properties.set("configXmlAttribute", current);
                        properties.set("config", config);
                    }
                }
                if (emptyExtensionWrapper(comparableChild)) continue;
            }
            if (hasElementChildren(childElement) || opaqueTextElement(name)) {
                // Extension trees and script/credential leaves may contain secrets. Detect a change without
                // returning their content or a reversible excerpt to the comparison client.
                properties.put(key + "Fingerprint", fingerprint(comparableChild));
                continue;
            }
            String text = childElement.getTextContent();
            if (text != null && !text.isBlank()) properties.put(key, safeValue(name, text.trim()));
            NamedNodeMap attributes = childElement.getAttributes();
            for (int attr = 0; attr < attributes.getLength(); attr++) {
                Node attribute = attributes.item(attr);
                String attrName = localName(attribute);
                if ("flowKey".equals(attrName) || "flowId".equals(attrName)) continue;
                properties.put(key + "." + attrName, safeValue(attrName, attribute.getNodeValue()));
            }
        }
    }

    private ObjectNode readableProcessProperties(Element extension) {
        Map<String, String> legacy = new LinkedHashMap<>();
        Map<String, String> platform = new LinkedHashMap<>();
        NodeList containers = extension.getChildNodes();
        for (int index = containers.getLength() - 1; index >= 0; index--) {
            if (!(containers.item(index) instanceof Element container)
                    || !"properties".equals(localName(container))) continue;
            String namespace = container.getNamespaceURI();
            if (!PLATFORM_CUSTOM_NS.equals(namespace) && !LEGACY_CUSTOM_NS.equals(namespace)) continue;
            Map<String, String> propertyEntries = new LinkedHashMap<>();
            Map<String, String> valueEntries = new LinkedHashMap<>();
            NodeList entries = container.getChildNodes();
            for (int entryIndex = entries.getLength() - 1; entryIndex >= 0; entryIndex--) {
                if (!(entries.item(entryIndex) instanceof Element entry)
                        || !namespace.equals(entry.getNamespaceURI())
                        || !("property".equals(localName(entry)) || "values".equals(localName(entry)))
                        || !entry.hasAttribute("name") || !entry.hasAttribute("value")
                        || hasElementChildren(entry) || !entry.getTextContent().isBlank()
                        || hasAdditionalAttributes(entry)) continue;
                String field = entry.getAttribute("name");
                if (!READABLE_PROCESS_PROPERTIES.contains(field) || SnapshotSensitiveDataFilter.isSecret(field)) {
                    continue;
                }
                // Traversal is backwards so putIfAbsent retains the designer's last entry.
                ("values".equals(localName(entry)) ? valueEntries : propertyEntries)
                        .putIfAbsent(field, entry.getAttribute("value"));
                container.removeChild(entry);
            }
            // getExtensionProperties merges the property list before the values list,
            // regardless of their XML order; later containers win within a namespace.
            propertyEntries.putAll(valueEntries);
            Map<String, String> namespaceProperties = PLATFORM_CUSTOM_NS.equals(namespace) ? platform : legacy;
            propertyEntries.forEach(namespaceProperties::putIfAbsent);
            if (emptyExtensionWrapper(container)) extension.removeChild(container);
        }
        legacy.putAll(platform); // Match the designer: custom overrides custom_1 for duplicate keys.
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        legacy.forEach(config::put);
        return config;
    }

    private boolean hasAdditionalAttributes(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            String name = localName(attributes.item(index));
            if (!"name".equals(name) && !"value".equals(name)
                    && !attributes.item(index).getNodeName().startsWith("xmlns")) return true;
        }
        return false;
    }

    private boolean emptyExtensionWrapper(Element element) {
        if (hasElementChildren(element) || !element.getTextContent().isBlank()) return false;
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            if (!attributes.item(index).getNodeName().startsWith("xmlns")) return false;
        }
        return true;
    }

    private boolean stripFlowReferences(Element element) {
        if (flowReferenceName(element) != null) return true;
        NamedNodeMap attributes = element.getAttributes();
        for (int index = attributes.getLength() - 1; index >= 0; index--) {
            Node attribute = attributes.item(index);
            if ("flowKey".equals(localName(attribute)) || "flowId".equals(localName(attribute))) {
                element.removeAttributeNode((org.w3c.dom.Attr) attribute);
            }
        }
        NodeList children = element.getChildNodes();
        for (int index = children.getLength() - 1; index >= 0; index--) {
            Node child = children.item(index);
            if (child instanceof Element nested && stripFlowReferences(nested)) {
                element.removeChild(child);
            }
        }
        String name = localName(element);
        if (!"extensionElements".equals(name) && !"properties".equals(name)) return false;
        if (hasElementChildren(element) || !element.getTextContent().isBlank()) return false;
        for (int index = 0; index < attributes.getLength(); index++) {
            if (!attributes.item(index).getNodeName().startsWith("xmlns")) return false;
        }
        return true;
    }

    private boolean hasElementChildren(Element element) {
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element) return true;
        }
        return false;
    }

    private boolean opaqueTextElement(String name) {
        return SnapshotSensitiveDataFilter.isSecret(name)
                || "script".equalsIgnoreCase(name)
                || "code".equalsIgnoreCase(name);
    }

    private String fingerprint(Element element) {
        StringBuilder content = new StringBuilder();
        appendForFingerprint(element, content);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void appendForFingerprint(Element element, StringBuilder content) {
        content.append('<').append(localName(element));
        NamedNodeMap attrs = element.getAttributes();
        List<Node> sorted = new ArrayList<>();
        for (int index = 0; index < attrs.getLength(); index++) sorted.add(attrs.item(index));
        sorted.sort(Comparator.comparing(this::localName));
        for (Node attr : sorted) content.append(localName(attr)).append('=').append(attr.getNodeValue());
        content.append('>');
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element nested) appendForFingerprint(nested, content);
            else if (child.getNodeType() == Node.TEXT_NODE) content.append(child.getNodeValue().trim());
        }
    }

    private String safeValue(String name, String value) {
        return SnapshotSensitiveDataFilter.isSecret(name) ? "[REDACTED]" : value;
    }

    private String bpmnType(String local, boolean layout) {
        if (layout) return "BPMN_LAYOUT";
        if (local.endsWith("Flow")) return "BPMN_FLOW";
        if (local.equals("process")) return "BPMN_PROCESS";
        return "BPMN_NODE";
    }

    private String dmnType(String local) {
        return switch (local) {
            case "decision" -> "DMN_DECISION";
            case "decisionTable" -> "DMN_TABLE";
            case "rule" -> "DMN_RULE";
            case "input", "inputEntry" -> "DMN_INPUT";
            case "output", "outputEntry" -> "DMN_OUTPUT";
            default -> "DMN_ELEMENT";
        };
    }

    private boolean layout(Element element) {
        String namespace = element.getNamespaceURI();
        String local = localName(element);
        return namespace != null && (namespace.contains("BPMN/20100524/DI")
                || namespace.contains("OMG/20100524/DI") || namespace.contains("OMG/20100524/DC"))
                || local.equals("BPMNShape") || local.equals("BPMNEdge");
    }

    private String localName(Node node) {
        return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
    }
}
