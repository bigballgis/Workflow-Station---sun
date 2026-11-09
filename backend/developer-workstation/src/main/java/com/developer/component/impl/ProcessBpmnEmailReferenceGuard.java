package com.developer.component.impl;

import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.security.WorkspaceRouteOwnership;
import com.platform.common.i18n.I18nService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The email connection and template of every BPMN send task come from the saved XML, i.e. from the request body:
 * they must be the unit's own (platform access model §6.1, §6.2), the same rule as an action's send-email blocks
 * ({@link WorkspaceRouteOwnership#requireEmailReferencesInUnit}). A send task naming another unit's connection or
 * template is refused on save instead of being stored as a reference that resolves to nothing at runtime.
 *
 * <p>The XML is parsed (namespace aware, XXE hardened) and a send task's properties are read the way the engine
 * reads them when the task runs (workflow-engine-core {@code BpmnExtensionUtils.getExtensionProperty}): elements
 * by local name in any namespace, self-closing or not, the first match wins. XML that does not parse is refused:
 * the guard cannot tell what it references, and the engine could not deploy it either.
 */
@Component
@RequiredArgsConstructor
public class ProcessBpmnEmailReferenceGuard {

    static final String ERROR_CODE = "SEND_TASK_EMAIL_REFERENCE_NOT_IN_UNIT";
    static final String NOT_WELL_FORMED = "BPMN_XML_NOT_WELL_FORMED";

    private final WorkspaceRouteOwnership routeOwnership;
    private final I18nService i18nService;

    /**
     * @throws DeveloperBusinessException {@value #ERROR_CODE} naming the send task and the foreign reference, or
     *                                    {@value #NOT_WELL_FORMED} when the XML does not parse
     */
    public void requireInUnit(Long functionUnitId, String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return;
        }
        NodeList sendTasks = parse(bpmnXml).getElementsByTagNameNS("*", "sendTask");
        for (int i = 0; i < sendTasks.getLength(); i++) {
            Element sendTask = (Element) sendTasks.item(i);
            String connectionId = extensionProperty(sendTask, "connectionId");
            String templateId = extensionProperty(sendTask, "emailTemplateId");
            try {
                routeOwnership.requireEmailReferencesInUnit(connectionId, null, functionUnitId);
            } catch (ResourceNotFoundException e) {
                throw refused("process.send_task.connection_not_in_unit", sendTask, connectionId);
            }
            try {
                routeOwnership.requireEmailReferencesInUnit(null, templateId, functionUnitId);
            } catch (ResourceNotFoundException e) {
                throw refused("process.send_task.template_not_in_unit", sendTask, templateId);
            }
        }
    }

    /**
     * The value of a send task property as the engine reads it: for each {@code extensionElements/properties}
     * element in document order, the {@code value} of the first {@code property} child, then the first
     * {@code values} child, whose {@code name} is the given name; {@code null} when there is none.
     */
    static String extensionProperty(Element task, String name) {
        for (Element extensionElements : children(task, "extensionElements")) {
            for (Element properties : children(extensionElements, "properties")) {
                for (String kind : List.of("property", "values")) {
                    for (Element property : children(properties, kind)) {
                        if (name.equals(unqualifiedAttribute(property, "name"))) {
                            return unqualifiedAttribute(property, "value");
                        }
                    }
                }
            }
        }
        return null;
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> found = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                found.add(element);
            }
        }
        return found;
    }

    /** An attribute without a namespace prefix, as the engine reads it ({@code getAttributeValue(null, name)}). */
    private static String unqualifiedAttribute(Element element, String name) {
        Attr attribute = element.getAttributeNodeNS(null, name);
        return attribute == null ? null : attribute.getValue();
    }

    private Document parse(String bpmnXml) {
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
            DocumentBuilder builder = factory.newDocumentBuilder();
            // The parse error goes back to the caller; the parser's default handler would also print it to stderr.
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new InputSource(new StringReader(bpmnXml)));
        } catch (SAXParseException e) {
            throw new DeveloperBusinessException(NOT_WELL_FORMED, i18nService.getMessage(
                    "process.bpmn.not_well_formed", String.valueOf(e.getLineNumber()),
                    String.valueOf(e.getColumnNumber())));
        } catch (SAXException e) {
            throw new DeveloperBusinessException(NOT_WELL_FORMED, i18nService.getMessage(
                    "process.bpmn.not_well_formed", "?", "?"));
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML parser does not support the XXE hardening features", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private DeveloperBusinessException refused(String messageKey, Element sendTask, String reference) {
        return new DeveloperBusinessException(ERROR_CODE,
                i18nService.getMessage(messageKey, sendTask.getAttribute("id"), reference));
    }
}
