package com.workflow.util;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhances BPMN XML before Flowable deployment: converts email Send Tasks to Service Tasks
 * with delegateExpression for {@code sendEmailTaskDelegate}.
 */
@Slf4j
public final class BpmnDeployEnhancer {

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    private static final String CUSTOM_NS = "http://custom.bpmn.io/schema";
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    private BpmnDeployEnhancer() {
    }

    public static String enhance(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return bpmnXml;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            hardenAgainstXxe(factory);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

            Element root = doc.getDocumentElement();
            ensureNamespace(root, "flowable", FLOWABLE_NS);

            NodeList sendTasks = doc.getElementsByTagNameNS(BPMN_NS, "sendTask");
            int converted = 0;
            for (int i = sendTasks.getLength() - 1; i >= 0; i--) {
                Element sendTask = (Element) sendTasks.item(i);
                if (!isEmailSendTask(sendTask)) {
                    continue;
                }
                convertSendTaskToServiceTask(doc, sendTask);
                converted++;
            }

            if (converted > 0) {
                log.info("BpmnDeployEnhancer: converted {} email sendTask(s) to serviceTask", converted);
            }
            return documentToString(doc);
        } catch (Exception e) {
            log.warn("BpmnDeployEnhancer failed, deploying original XML: {}", e.getMessage());
            return bpmnXml;
        }
    }

    /**
     * Disables DTDs and external entity/schema resolution to prevent XXE when parsing
     * client-supplied BPMN XML. Legitimate BPMN 2.0 never relies on DOCTYPE/external entities.
     */
    private static void hardenAgainstXxe(DocumentBuilderFactory factory) {
        setFeatureQuietly(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureQuietly(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureQuietly(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeatureQuietly(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        try {
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // Attribute not supported by this parser implementation; feature flags above still apply.
        }
    }

    private static void setFeatureQuietly(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (javax.xml.parsers.ParserConfigurationException ignored) {
            // Feature not supported by this parser implementation; remaining hardening still applies.
        }
    }

    private static boolean isEmailSendTask(Element sendTask) {
        String sendMode = getCustomProperty(sendTask, "sendMode");
        return sendMode == null || "email".equalsIgnoreCase(sendMode);
    }

    private static String getCustomProperty(Element taskElement, String propertyName) {
        NodeList extensionElements = taskElement.getElementsByTagNameNS(BPMN_NS, "extensionElements");
        if (extensionElements.getLength() == 0) {
            return null;
        }
        Element extensionElement = (Element) extensionElements.item(0);
        NodeList propertiesList = extensionElement.getElementsByTagNameNS(CUSTOM_NS, "properties");
        if (propertiesList.getLength() == 0) {
            return null;
        }
        Element properties = (Element) propertiesList.item(0);
        NodeList propertyNodes = properties.getElementsByTagNameNS(CUSTOM_NS, "property");
        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Element property = (Element) propertyNodes.item(i);
            if (propertyName.equals(property.getAttribute("name"))) {
                return property.getAttribute("value");
            }
        }
        return null;
    }

    private static void convertSendTaskToServiceTask(Document doc, Element sendTask) {
        Element serviceTask = doc.createElementNS(BPMN_NS, "bpmn:serviceTask");
        copyAttributes(sendTask, serviceTask);
        serviceTask.setAttributeNS(FLOWABLE_NS, "flowable:delegateExpression", "${sendEmailTaskDelegate}");

        while (sendTask.hasChildNodes()) {
            Node child = sendTask.getFirstChild();
            sendTask.removeChild(child);
            serviceTask.appendChild(child);
        }

        Node parent = sendTask.getParentNode();
        parent.replaceChild(serviceTask, sendTask);
    }

    private static void copyAttributes(Element source, Element target) {
        for (int i = 0; i < source.getAttributes().getLength(); i++) {
            Node attr = source.getAttributes().item(i);
            target.setAttributeNS(attr.getNamespaceURI(), attr.getNodeName(), attr.getNodeValue());
        }
    }

    private static void ensureNamespace(Element root, String prefix, String uri) {
        if (!root.hasAttribute("xmlns:" + prefix)) {
            root.setAttribute("xmlns:" + prefix, uri);
        }
    }

    private static String documentToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
