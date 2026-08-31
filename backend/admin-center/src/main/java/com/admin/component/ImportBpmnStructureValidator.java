package com.admin.component;

import com.admin.exception.AdminBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Cheap import-time BPMN checks: exactly one none start event; designer custom xmlns.
 * Does not call Flowable.
 */
@Slf4j
@Component
public class ImportBpmnStructureValidator {

    public static final String DESIGNER_CUSTOM_NS = "http://workflow.platform/schema/custom";

    public void validate(String bpmnXml, String contentName) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            throw new AdminBusinessException("FU_IMPORT_BPMN_EMPTY",
                    "BPMN content is empty: " + contentName);
        }
        Document doc = parse(bpmnXml, contentName);
        assertCustomNamespace(doc, contentName);
        int noneStarts = countNoneStartEvents(doc);
        if (noneStarts != 1) {
            throw new AdminBusinessException("FU_IMPORT_BPMN_START_EVENTS",
                    "Process '" + contentName + "' must have exactly one none start event (found "
                            + noneStarts + ")");
        }
    }

    private Document parse(String bpmnXml, String contentName) {
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
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
        } catch (AdminBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new AdminBusinessException("FU_IMPORT_BPMN_INVALID",
                    "Invalid BPMN XML in " + contentName + ": " + e.getMessage(), e);
        }
    }

    private void assertCustomNamespace(Document doc, String contentName) {
        Element root = doc.getDocumentElement();
        if (root == null) {
            return;
        }
        String customNs = findCustomNamespaceUri(root);
        boolean usesCustomPrefix = bpmnXmlUsesCustomPrefix(root);
        if (customNs == null && !usesCustomPrefix) {
            return;
        }
        if (customNs == null || !DESIGNER_CUSTOM_NS.equals(customNs)) {
            throw new AdminBusinessException("FU_IMPORT_BPMN_CUSTOM_NS",
                    "Process '" + contentName + "' must declare xmlns:custom=\""
                            + DESIGNER_CUSTOM_NS + "\"");
        }
    }

    private String findCustomNamespaceUri(Element root) {
        NamedNodeMap attrs = root.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            if ("xmlns:custom".equals(attr.getNodeName())) {
                return blankToNull(attr.getNodeValue());
            }
        }
        return blankToNull(root.getAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "custom"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean bpmnXmlUsesCustomPrefix(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName() != null
                && node.getNodeName().startsWith("custom:")) {
            return true;
        }
        NamedNodeMap attrs = node.getAttributes();
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                String name = attrs.item(i).getNodeName();
                if (name != null && name.startsWith("custom:")) {
                    return true;
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (bpmnXmlUsesCustomPrefix(children.item(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * None start events belonging to the process itself — i.e. DIRECT children of {@code <process>}.
     *
     * <p>Deliberately not a document-wide scan. An embedded sub-process carries its own start event
     * by BPMN definition, so counting descendants rejected every valid multi-instance Function Unit
     * (the MI subtask demo's {@code multiInstance} sub-process has one) with "found 2". The rule
     * being enforced is "exactly one entry point into the process", which is a statement about the
     * process's own children; a sub-process's start event is an entry point into that sub-process,
     * not into the process.
     */
    private int countNoneStartEvents(Document doc) {
        int none = 0;
        for (Element process : findProcessElements(doc)) {
            for (Element start : directChildElements(process, "startEvent")) {
                if (isNoneStart(start)) {
                    none++;
                }
            }
        }
        return none;
    }

    private List<Element> findProcessElements(Document doc) {
        NodeList processes = doc.getElementsByTagNameNS("*", "process");
        if (processes.getLength() == 0) {
            processes = doc.getElementsByTagName("process");
        }
        List<Element> out = new ArrayList<>();
        for (int i = 0; i < processes.getLength(); i++) {
            if (processes.item(i) instanceof Element element) {
                out.add(element);
            }
        }
        return out;
    }

    private List<Element> directChildElements(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String local = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
            if (local != null && (local.equals(localName) || local.endsWith(":" + localName))) {
                out.add((Element) child);
            }
        }
        return out;
    }

    private boolean isNoneStart(Element start) {
        NodeList children = start.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String local = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
            if (local != null && local.endsWith("EventDefinition")) {
                return false;
            }
        }
        return true;
    }
}
