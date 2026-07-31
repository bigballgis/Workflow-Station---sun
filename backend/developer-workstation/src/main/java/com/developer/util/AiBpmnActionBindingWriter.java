package com.developer.util;

import com.developer.entity.ActionDefinition;
import com.developer.exception.AiGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds persisted action IDs to AI-generated BPMN user tasks using generated action stage IDs.
 *
 * <p>The model declares stable user-task IDs in {@code actionDefinitions[].stageIds}; database-generated
 * action IDs are resolved and written only after JPA persistence.</p>
 */
@Slf4j
public final class AiBpmnActionBindingWriter {

    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String CUSTOM_NAMESPACE = "http://custom.bpmn.io/schema";
    private static final Set<String> ACTION_PROPERTY_NAMES = Set.of("actionIds", "actionNames");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AiBpmnActionBindingWriter() {
    }

    /**
     * Enriches each referenced user task with ordered {@code actionIds} and {@code actionNames} properties.
     * Missing IDs or invalid partial bindings are ignored; parser validation handles malformed AI output.
     */
    public static String bindStageActions(String bpmnXml, List<ActionDefinition> actions,
                                          List<Map<String, Object>> generatedActions) {
        if (bpmnXml == null || bpmnXml.isBlank() || actions == null || actions.isEmpty()
                || generatedActions == null || generatedActions.isEmpty()) {
            return bpmnXml;
        }

        try {
            Map<String, ActionDefinition> persistedByName = new LinkedHashMap<>();
            for (ActionDefinition action : actions) {
                if (action != null && action.getId() != null && action.getActionName() != null) {
                    persistedByName.put(action.getActionName(), action);
                }
            }
            if (persistedByName.isEmpty()) {
                return bpmnXml;
            }

            Map<String, LinkedHashSet<ActionDefinition>> actionsByStage = new LinkedHashMap<>();
            for (Map<String, Object> generatedAction : generatedActions) {
                if (generatedAction == null) continue;
                Object actionName = generatedAction.get("actionName");
                ActionDefinition persisted = actionName instanceof String name
                        ? persistedByName.get(name) : null;
                if (persisted == null || !(generatedAction.get("stageIds") instanceof List<?> stageIds)) {
                    continue;
                }
                for (Object stageId : stageIds) {
                    if (stageId instanceof String id && !id.isBlank()) {
                        actionsByStage.computeIfAbsent(id.trim(), ignored -> new LinkedHashSet<>())
                                .add(persisted);
                    }
                }
            }
            if (actionsByStage.isEmpty()) {
                return bpmnXml;
            }

            Document document = parseSecurely(bpmnXml);
            boolean changed = false;
            for (Map.Entry<String, LinkedHashSet<ActionDefinition>> entry : actionsByStage.entrySet()) {
                Element task = findUserTask(document, entry.getKey());
                if (task == null) continue;
                writeActionProperties(document, task, new ArrayList<>(entry.getValue()));
                changed = true;
            }
            return changed ? serialize(document) : bpmnXml;
        } catch (Exception e) {
            // 同 AiBpmnFormBindingWriter：绑定失败意味着 actionIds 没注入、运行时任务没按钮，
            // 是要人看的故障，不能降级成 warn 级的 400；栈同样先落一次。
            log.error("Failed to bind AI-generated actions to BPMN user tasks", e);
            throw new AiGenerationException("AI_BPMN_BINDING_FAILED",
                    "Failed to bind AI-generated actions to BPMN user tasks: " + e.getMessage());
        }
    }

    private static Document parseSecurely(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static Element findUserTask(Document document, String stageId) {
        NodeList tasks = document.getElementsByTagNameNS("*", "userTask");
        for (int i = 0; i < tasks.getLength(); i++) {
            Element task = (Element) tasks.item(i);
            if (stageId.equals(task.getAttribute("id"))) {
                return task;
            }
        }
        return null;
    }

    private static void writeActionProperties(Document document, Element task,
                                              List<ActionDefinition> actions) throws Exception {
        Element root = document.getDocumentElement();
        if (!root.hasAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "custom")) {
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:custom", CUSTOM_NAMESPACE);
        }

        Element extensionElements = directChild(task, "extensionElements");
        if (extensionElements == null) {
            String prefix = task.getPrefix();
            String qualifiedName = prefix == null || prefix.isBlank()
                    ? "extensionElements" : prefix + ":extensionElements";
            extensionElements = document.createElementNS(BPMN_NAMESPACE, qualifiedName);
            task.insertBefore(extensionElements, task.getFirstChild());
        }

        Element properties = directChild(extensionElements, "properties");
        if (properties == null) {
            properties = document.createElementNS(CUSTOM_NAMESPACE, "custom:properties");
            extensionElements.appendChild(properties);
        }

        removeActionProperties(properties);
        List<Long> ids = actions.stream().map(ActionDefinition::getId).toList();
        List<String> names = actions.stream().map(ActionDefinition::getActionName).toList();
        appendProperty(document, properties, "actionIds", OBJECT_MAPPER.writeValueAsString(ids));
        appendProperty(document, properties, "actionNames", OBJECT_MAPPER.writeValueAsString(names));
    }

    private static Element directChild(Element parent, String expectedLocalName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && expectedLocalName.equals(localName(element))) {
                return element;
            }
        }
        return null;
    }

    private static void removeActionProperties(Element properties) {
        NodeList descendants = properties.getElementsByTagNameNS("*", "*");
        for (int i = descendants.getLength() - 1; i >= 0; i--) {
            Node node = descendants.item(i);
            if (node instanceof Element property
                    && ("property".equals(localName(property)) || "values".equals(localName(property)))
                    && ACTION_PROPERTY_NAMES.contains(property.getAttribute("name"))) {
                property.getParentNode().removeChild(property);
            }
        }
    }

    private static void appendProperty(Document document, Element properties, String name, String value) {
        Element property = document.createElementNS(CUSTOM_NAMESPACE, "custom:property");
        property.setAttribute("name", name);
        property.setAttribute("value", value);
        properties.appendChild(property);
    }

    private static String localName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getTagName();
    }

    private static String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
