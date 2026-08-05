package com.developer.util;

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
import java.util.List;
import java.util.Map;

/**
 * Locates the user tasks of multi-instance sub-processes in AI-generated BPMN and reads their
 * custom extension properties.
 *
 * <p>Shared by {@link AiBpmnMiSubTableWriter} (writes the persisted sub-table id back into the
 * BPMN) and {@link AiSubFormMiAssignmentWriter} (materialises the matching sub-form component),
 * so both see exactly the same set of MI tasks. The traversal mirrors the designer parser in
 * {@code frontend/developer-workstation/src/utils/miAssignmentConfig.ts}: a user task counts as
 * MI only when it sits inside a {@code subProcess} carrying
 * {@code multiInstanceLoopCharacteristics}, which is also the shape
 * {@link com.developer.component.impl.ProcessBpmnValidator} validates at deploy time.</p>
 */
final class AiBpmnMiTaskScanner {

    static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    static final String CUSTOM_NAMESPACE = "http://custom.bpmn.io/schema";
    /** 流程属性面板写入的容器命名空间（customModdle 里的 custom 前缀）。 */
    static final String PLATFORM_NAMESPACE = "http://workflow.platform/schema/custom";

    private AiBpmnMiTaskScanner() {
    }

    /** One multi-instance user task plus the extension properties declared on it. */
    record MiTask(Element userTask, Map<String, String> properties) {

        String property(String name) {
            String value = properties.get(name);
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    static List<MiTask> scan(Document document) {
        List<MiTask> tasks = new ArrayList<>();
        NodeList elements = document.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (!"userTask".equals(localName(element)) || !insideMultiInstanceSubProcess(element)) {
                continue;
            }
            tasks.add(new MiTask(element, readCustomProperties(element)));
        }
        return tasks;
    }

    private static boolean insideMultiInstanceSubProcess(Element userTask) {
        for (Node parent = userTask.getParentNode(); parent instanceof Element element;
                parent = parent.getParentNode()) {
            if ("subProcess".equals(localName(element))
                    && directChild(element, "multiInstanceLoopCharacteristics") != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Read {@code name}/{@code value} extension properties off a task.
     *
     * <p>Deliberately permissive about the container namespace, unlike
     * {@link AiBpmnFormBindingWriter} which has to pick the one container the designer panel
     * reads back. Here we only consume what the model already emitted, and the deploy-time
     * validator's regex is equally namespace-blind — reading less than it does would let a
     * contract slip through unmaterialised and fail at deploy instead.</p>
     */
    static Map<String, String> readCustomProperties(Element task) {
        Map<String, String> properties = new LinkedHashMap<>();
        Element extensionElements = directChild(task, "extensionElements");
        if (extensionElements == null) {
            return properties;
        }
        NodeList descendants = extensionElements.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < descendants.getLength(); i++) {
            if (!(descendants.item(i) instanceof Element element)) {
                continue;
            }
            if (!"property".equals(localName(element)) || !element.hasAttribute("name")) {
                continue;
            }
            properties.put(element.getAttribute("name"), element.getAttribute("value"));
        }
        return properties;
    }

    /** Get or create the {@code custom:properties} container the designer panel reads. */
    static Element ensureCustomProperties(Document document, Element task) {
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

        NodeList children = extensionElements.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element
                    && "properties".equals(localName(element))
                    && (CUSTOM_NAMESPACE.equals(element.getNamespaceURI())
                        || PLATFORM_NAMESPACE.equals(element.getNamespaceURI()))) {
                return element;
            }
        }
        Element properties = document.createElementNS(CUSTOM_NAMESPACE, "custom:properties");
        extensionElements.appendChild(properties);
        return properties;
    }

    /** Set a property, replacing every earlier declaration of the same name on this task. */
    static void putProperty(Document document, Element properties, String name, String value) {
        if (value == null) {
            return;
        }
        NodeList descendants = properties.getElementsByTagNameNS("*", "*");
        for (int i = descendants.getLength() - 1; i >= 0; i--) {
            if (descendants.item(i) instanceof Element element
                    && ("property".equals(localName(element)) || "values".equals(localName(element)))
                    && name.equals(element.getAttribute("name"))) {
                element.getParentNode().removeChild(element);
            }
        }
        Element property = document.createElementNS(CUSTOM_NAMESPACE, "custom:property");
        property.setAttribute("name", name);
        property.setAttribute("value", value);
        properties.appendChild(property);
    }

    static Element directChild(Element parent, String expectedLocalName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && expectedLocalName.equals(localName(element))) {
                return element;
            }
        }
        return null;
    }

    static String localName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getTagName();
    }

    static Document parseSecurely(String xml) throws Exception {
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

    static String serialize(Document document) throws Exception {
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
