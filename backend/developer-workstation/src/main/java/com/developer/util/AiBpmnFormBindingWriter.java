package com.developer.util;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
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
import java.util.List;
import java.util.Set;

/**
 * Adds persisted form IDs to AI-generated BPMN task nodes using form stage bindings.
 *
 * <p>The model can reliably generate a BPMN task ID and reference it from
 * {@link FormStageBinding#getStageId()}, but it cannot know the database-generated form ID.
 * This writer resolves that final link after JPA has assigned the IDs.</p>
 */
public final class AiBpmnFormBindingWriter {

    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String CUSTOM_NAMESPACE = "http://custom.bpmn.io/schema";
    private static final Set<String> TASK_NAMES = Set.of(
            "task", "userTask", "serviceTask", "scriptTask", "manualTask",
            "sendTask", "receiveTask", "businessRuleTask");
    private static final Set<String> FORM_PROPERTY_NAMES = Set.of(
            "formId", "formName", "formReadOnly");

    private AiBpmnFormBindingWriter() {
    }

    /**
     * Enrich BPMN task extension properties from persisted forms. Bindings whose form ID or
     * stage ID is unavailable are ignored so non-JPA unit tests and partial generation remain safe.
     */
    public static String bindStageForms(String bpmnXml, List<FormDefinition> forms) {
        if (bpmnXml == null || bpmnXml.isBlank() || forms == null || forms.isEmpty()) {
            return bpmnXml;
        }

        try {
            Document document = parseSecurely(bpmnXml);
            boolean changed = false;
            for (FormDefinition form : forms) {
                if (form == null || form.getId() == null || form.getStageBindings() == null) {
                    continue;
                }
                for (FormStageBinding binding : form.getStageBindings()) {
                    if (binding == null || binding.getStageId() == null || binding.getStageId().isBlank()) {
                        continue;
                    }
                    Element task = findTask(document, binding.getStageId().trim());
                    if (task == null) {
                        continue;
                    }
                    writeFormProperties(document, task, form, binding);
                    changed = true;
                }
            }
            return changed ? serialize(document) : bpmnXml;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to bind AI-generated forms to BPMN task nodes", e);
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

    private static Element findTask(Document document, String stageId) {
        NodeList elements = document.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (TASK_NAMES.contains(localName(element)) && stageId.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    private static void writeFormProperties(Document document, Element task,
                                            FormDefinition form, FormStageBinding binding) {
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

        removeFormProperties(properties);
        appendProperty(document, properties, "formId", String.valueOf(form.getId()));
        appendProperty(document, properties, "formName", form.getFormName());
        if (Boolean.TRUE.equals(binding.getReadOnly())) {
            appendProperty(document, properties, "formReadOnly", "true");
        }
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

    private static void removeFormProperties(Element properties) {
        NodeList descendants = properties.getElementsByTagNameNS("*", "*");
        for (int i = descendants.getLength() - 1; i >= 0; i--) {
            Node node = descendants.item(i);
            if (node instanceof Element property
                    && ("property".equals(localName(property)) || "values".equals(localName(property)))
                    && FORM_PROPERTY_NAMES.contains(property.getAttribute("name"))) {
                property.getParentNode().removeChild(property);
            }
        }
    }

    private static void appendProperty(Document document, Element properties, String name, String value) {
        if (value == null) {
            return;
        }
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
